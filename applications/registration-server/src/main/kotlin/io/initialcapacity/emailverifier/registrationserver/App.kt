package io.initialcapacity.emailverifier.registrationserver

import com.rabbitmq.client.ConnectionFactory
import io.initialcapacity.emailverifier.databasesupport.DatabaseTemplate
import io.initialcapacity.emailverifier.rabbitsupport.*
import io.initialcapacity.emailverifier.registration.RegistrationConfirmationService
import io.initialcapacity.emailverifier.registration.RegistrationDataGateway
import io.initialcapacity.emailverifier.registration.register
import io.initialcapacity.emailverifier.registrationrequest.RegistrationRequestDataGateway
import io.initialcapacity.emailverifier.registrationrequest.RegistrationRequestService
import io.initialcapacity.emailverifier.registrationrequest.UuidProvider
import io.initialcapacity.emailverifier.registrationrequest.registrationRequest
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*

/**
 * Entry point for the registration server application.
 * This class configures and starts the server and initializes all necessary components.
 */
class App

private val logger = LoggerFactory.getLogger(App::class.java)

/**
 * Main function to run the registration server.
 * It sets up the environment, configures the RabbitMQ exchanges and queues,
 * and starts listening for registration requests.
 */
fun main(): Unit = runBlocking {
    val port = System.getenv("PORT")?.toInt() ?: 8081
    val rabbitUrl = System.getenv("RABBIT_URL")?.let(::URI)
        ?: throw RuntimeException("Please set the RABBIT_URL environment variable")
    val databaseUrl = System.getenv("DATABASE_URL")
        ?: throw RuntimeException("Please set the DATABASE_URL environment variable")
    val registrationRequestQueueName = System.getenv("REGISTRATION_REQUEST_QUEUE") ?: "registration-request"
    val registrationRequestRoutingKey = System.getenv("REGISTRATION_REQUEST_ROUTING_KEY") ?: "42"

    // Database configuration and initialization
    val dbConfig = DatabaseConfiguration(databaseUrl)
    val dbTemplate = DatabaseTemplate(dbConfig.db)

    // RabbitMQ connection factory setup
    val connectionFactory = buildConnectionFactory(rabbitUrl)
    val registrationRequestGateway = RegistrationRequestDataGateway(dbTemplate)
    val registrationGateway = RegistrationDataGateway(dbTemplate)

    // Define and bind the registration notification exchange and queue
    val registrationNotificationExchange = RabbitExchange(
        name = "registration-notification-exchange",
        type = "direct",
        routingKeyGenerator = { _: String -> "42" },
    )
    val registrationNotificationQueue = RabbitQueue("registration-notification")
    connectionFactory.declareAndBind(exchange = registrationNotificationExchange, queue = registrationNotificationQueue, routingKey = "42")

    // Define and bind the registration request exchange and queue using consistent hash exchange
    val registrationRequestExchange = RabbitExchange(
        name = "registration-request-consistent-hash-exchange",
        type = "x-consistent-hash",
        routingKeyGenerator = { message: String -> message.hashCode().toString() },
    )
    val registrationRequestQueue = RabbitQueue(registrationRequestQueueName)
    connectionFactory.declareAndBind(exchange = registrationRequestExchange, queue = registrationRequestQueue, routingKey = registrationRequestRoutingKey)

    // Start listening for registration requests
    listenForRegistrationRequests(
        connectionFactory,
        registrationRequestGateway,
        registrationNotificationExchange,
        registrationRequestQueue
    )
    // Start the registration server
    registrationServer(
        port,
        registrationRequestGateway,
        registrationGateway,
        connectionFactory,
        registrationRequestExchange
    ).start()
}

/**
 * Function to configure and start the registration server.
 *
 * @param port Port number on which the server will run.
 * @param registrationRequestGateway Gateway for handling registration requests.
 * @param registrationGateway Gateway for handling registration data.
 * @param connectionFactory Connection factory for RabbitMQ.
 * @param registrationRequestExchange Exchange for registration requests.
 */
fun registrationServer(
    port: Int,
    registrationRequestGateway: RegistrationRequestDataGateway,
    registrationGateway: RegistrationDataGateway,
    connectionFactory: ConnectionFactory,
    registrationRequestExchange: RabbitExchange,
) = embeddedServer(
    factory = Jetty,
    port = port,
    module = { module(registrationRequestGateway, registrationGateway, connectionFactory, registrationRequestExchange) }
)

/**
 * Application module to configure the server's plugins and routes.
 *
 * @param registrationRequestGateway Gateway for handling registration requests.
 * @param registrationGateway Gateway for handling registration data.
 * @param connectionFactory Connection factory for RabbitMQ.
 * @param registrationRequestExchange Exchange for registration requests.
 */
fun Application.module(
    registrationRequestGateway: RegistrationRequestDataGateway,
    registrationGateway: RegistrationDataGateway,
    connectionFactory: ConnectionFactory,
    registrationRequestExchange: RabbitExchange,
) {
    // Install necessary plugins
    install(Resources)
    install(CallLogging)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json()
    }

    // Define the request publisher
    val publishRequest = publish(connectionFactory, registrationRequestExchange)

    // Set up the routing for the application
    install(Routing) {
        info()
        registrationRequest(publishRequest)
        register(RegistrationConfirmationService(registrationRequestGateway, registrationGateway))
    }
}

/**
 * Function to listen for registration requests and process them.
 *
 * @param connectionFactory Connection factory for RabbitMQ.
 * @param registrationRequestDataGateway Gateway for handling registration request data.
 * @param registrationNotificationExchange Exchange for registration notifications.
 * @param registrationRequestQueue Queue for registration requests.
 * @param uuidProvider Provider for generating UUIDs.
 */
fun CoroutineScope.listenForRegistrationRequests(
    connectionFactory: ConnectionFactory,
    registrationRequestDataGateway: RegistrationRequestDataGateway,
    registrationNotificationExchange: RabbitExchange,
    registrationRequestQueue: RabbitQueue,
    uuidProvider: UuidProvider = { UUID.randomUUID() },
) {
    // Define the notification publisher
    val publishNotification = publish(connectionFactory, registrationNotificationExchange)

    // Create the registration request service
    val registrationRequestService = RegistrationRequestService(
        gateway = registrationRequestDataGateway,
        publishNotification = publishNotification,
        uuidProvider = uuidProvider,
    )

    // Launch a coroutine to listen for registration requests
    launch {
        logger.info("listening for registration requests")
        val channel = connectionFactory.newConnection().createChannel()
        listen(queue = registrationRequestQueue, channel = channel) { email ->
            logger.debug("received registration request for {}", email)
            registrationRequestService.generateCodeAndPublish(email)
        }
    }
}
