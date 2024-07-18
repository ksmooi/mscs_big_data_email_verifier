package io.initialcapacity.emailverifier.notificationserver

import com.rabbitmq.client.ConnectionFactory
import io.initialcapacity.emailverifier.databasesupport.DatabaseTemplate
import io.initialcapacity.emailverifier.notification.Emailer
import io.initialcapacity.emailverifier.notification.NotificationDataGateway
import io.initialcapacity.emailverifier.notification.Notifier
import io.initialcapacity.emailverifier.rabbitsupport.*
import io.initialcapacity.serializationsupport.UUIDSerializer
import io.ktor.client.*
import io.ktor.client.engine.java.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.util.*

/**
 * Entry point for the notification server application.
 * This class configures and starts the server and initializes all necessary components.
 */
class App

private val logger = LoggerFactory.getLogger(App::class.java)

/**
 * Main function to run the notification server.
 * It sets up the environment, configures the RabbitMQ exchanges and queues,
 * and starts listening for notification requests.
 */
fun main() = runBlocking {
    // Read necessary environment variables
    val rabbitUrl = System.getenv("RABBIT_URL")?.let(::URI)
        ?: throw RuntimeException("Please set the RABBIT_URL environment variable")
    val sendgridUrl = System.getenv("SENDGRID_URL")?.let { URI(it).toURL() }
        ?: throw RuntimeException("Please set the SENDGRID_URL environment variable")
    val sendgridApiKey = System.getenv("SENDGRID_API_KEY")
        ?: throw RuntimeException("Please set the SENDGRID_API_KEY environment variable")
    val fromAddress = System.getenv("FROM_ADDRESS")
        ?: throw RuntimeException("Please set the FROM_ADDRESS environment variable")
    val databaseUrl = System.getenv("DATABASE_URL")
        ?: throw RuntimeException("Please set the DATABASE_URL environment variable")
    val connectionFactory = buildConnectionFactory(rabbitUrl)

    // Database configuration and initialization
    val dbConfig = DatabaseConfiguration(databaseUrl)
    val dbTemplate = DatabaseTemplate(dbConfig.db)

    // Start the notification server
    start(
        sendgridUrl = sendgridUrl,
        sendgridApiKey = sendgridApiKey,
        fromAddress = fromAddress,
        connectionFactory = connectionFactory,
        registrationNotificationExchange = RabbitExchange(
            name = "registration-notification-exchange",
            type = "direct",
            routingKeyGenerator = { _: String -> "42" },
        ),
        registrationNotificationQueue = RabbitQueue("registration-notification"),
        dbTemplate = dbTemplate,
    )
}

/**
 * Starts the notification server by setting up the notifier and listening for registration notifications.
 *
 * @param sendgridUrl URL for SendGrid API.
 * @param sendgridApiKey API key for SendGrid.
 * @param fromAddress Email address to send from.
 * @param connectionFactory Connection factory for RabbitMQ.
 * @param registrationNotificationExchange Exchange for registration notifications.
 * @param registrationNotificationQueue Queue for registration notifications.
 * @param dbTemplate Database template for database operations.
 */
suspend fun start(
    sendgridUrl: URL,
    sendgridApiKey: String,
    fromAddress: String,
    connectionFactory: ConnectionFactory,
    registrationNotificationExchange: RabbitExchange,
    registrationNotificationQueue: RabbitQueue,
    dbTemplate: DatabaseTemplate,
) {
    // Create a notifier
    val notifier = createNotifier(sendgridUrl, sendgridApiKey, fromAddress, dbTemplate)
    connectionFactory.declareAndBind(exchange = registrationNotificationExchange, queue = registrationNotificationQueue, routingKey = "42")

    logger.info("listening for registration notifications")
    listenForNotificationRequests(connectionFactory, notifier, registrationNotificationQueue)
}

/**
 * Creates a notifier for handling notification requests.
 *
 * @param sendgridUrl URL for SendGrid API.
 * @param sendgridApiKey API key for SendGrid.
 * @param fromAddress Email address to send from.
 * @param dbTemplate Database template for database operations.
 * @return A configured Notifier instance.
 */
private fun createNotifier(
    sendgridUrl: URL,
    sendgridApiKey: String,
    fromAddress: String,
    dbTemplate: DatabaseTemplate,
): Notifier {
    val emailer = Emailer(
        client = HttpClient(Java) { expectSuccess = false },
        sendgridUrl = sendgridUrl,
        sendgridApiKey = sendgridApiKey,
        fromAddress = fromAddress,
    )
    val gateway = NotificationDataGateway(dbTemplate)
    return Notifier(gateway, emailer)
}

/**
 * Listens for notification requests and processes them.
 *
 * @param connectionFactory Connection factory for RabbitMQ.
 * @param notifier Notifier instance for processing notifications.
 * @param registrationNotificationQueue Queue for registration notifications.
 */
private suspend fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    notifier: Notifier,
    registrationNotificationQueue: RabbitQueue
) {
    val channel = connectionFactory.newConnection().createChannel()

    listen(queue = registrationNotificationQueue, channel = channel) {
        val message = Json.decodeFromString<NotificationMessage>(it)
        logger.debug("received registration notification {}", message)
        notifier.notify(message.email, message.confirmationCode)
    }
}

/**
 * Data class representing a notification message.
 *
 * @property email Email address for the notification.
 * @property confirmationCode Confirmation code for the notification.
 */
@Serializable
private data class NotificationMessage(
    val email: String,
    @Serializable(with = UUIDSerializer::class)
    val confirmationCode: UUID,
)
