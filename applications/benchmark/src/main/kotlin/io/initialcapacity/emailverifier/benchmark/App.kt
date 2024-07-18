package io.initialcapacity.emailverifier.benchmark

import io.initialcapacity.emailverifier.fakesendgridendpoints.fakeSendgridRoutes
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * Data class representing a confirmation with an email and a UUID code.
 */
data class Confirmation(
    val email: String,
    val code: UUID,
)

/**
 * Main function to run the benchmark test.
 * It sets up the environment, starts the fake email server, runs the benchmark, and ensures proper cleanup.
 */
fun main(): Unit = runBlocking {
    // Get the port from environment variables or use 9090 as the default
    val port = getEnvInt("PORT", 9090)

    // Create a Benchmark instance with environment configurations or default values
    val benchmark = Benchmark(
        registrationUrl = System.getenv("REGISTRATION_URL") ?: "http://localhost:8081",
        registrationCount = getEnvInt("REGISTRATION_COUNT", 150),
        requestWorkerCount = getEnvInt("REQUEST_WORKER_COUNT", 4),
        registrationWorkerCount = getEnvInt("REGISTRATION_WORKER_COUNT", 4),
        client = HttpClient(Java) {
            expectSuccess = false
        }
    )

    // Start the fake email server
    val fakeEmailServer = fakeEmailServer(port, benchmark).apply { start() }

    try {
        // Run the benchmark
        benchmark.start(this)
    } catch (e: Exception) {
        // Catch and print any exception, then rethrow it to ensure Gradle sees the failure
        println("Caught exception: ${e.message}")
        throw e
    } finally {
        // Ensure the fake email server is stopped
        fakeEmailServer.stop()
    }
}

/**
 * Helper function to get an integer environment variable or return a default value if not set.
 * @param name The name of the environment variable.
 * @param default The default value to return if the environment variable is not set.
 * @return The value of the environment variable or the default value.
 */
private fun getEnvInt(name: String, default: Int): Int = System.getenv(name)?.toInt() ?: default

/**
 * Function to create and configure the fake email server.
 * @param port The port to run the server on.
 * @param benchmark The benchmark instance to process confirmations.
 * @return The configured embedded server instance.
 */
private fun fakeEmailServer(
    port: Int,
    benchmark: Benchmark
) = embeddedServer(
    factory = Jetty,
    port = port,
    module = { fakeSendgridRoutes("super-secret") { benchmark.processConfirmation(it) } }
)
