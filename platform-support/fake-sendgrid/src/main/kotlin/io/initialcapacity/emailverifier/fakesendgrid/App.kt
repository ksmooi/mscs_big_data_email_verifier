package io.initialcapacity.emailverifier.fakesendgrid

import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import kotlinx.coroutines.runBlocking
import io.initialcapacity.emailverifier.fakesendgridendpoints.fakeSendgridRoutes
import org.slf4j.LoggerFactory

/**
 * Entry point for the fake SendGrid server application.
 * This class configures and starts the server that simulates SendGrid's email sending functionality.
 */
class App

private val logger = LoggerFactory.getLogger(App::class.java)

/**
 * Main function to run the fake SendGrid server.
 * It sets up the environment, configures the server, and starts listening for email sending requests.
 */
fun main(): Unit = runBlocking {
    // Read the PORT environment variable or use 9090 as the default port
    val port = System.getenv("PORT")?.toInt() ?: 9090

    // Log a message indicating that the server is waiting for email requests
    logger.info("waiting for mail")

    // Start the embedded Jetty server with the specified port and module
    embeddedServer(
        factory = Jetty,
        port = port,
        module = { fakeSendgridRoutes("super-secret") } // Configure routes for the fake SendGrid endpoints
    ).start()
}
