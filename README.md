# Email Verifier Project

## Overview

The Email Verifier project is designed to provide a reliable and efficient system for verifying email addresses. It consists of several components working together to handle registration requests, process confirmations, and manage notifications. The project leverages RabbitMQ for messaging, Ktor for web server functionality, and a consistent hash exchange for efficient load distribution among consumers.

## Key Components

### 1. Registration Server
**Purpose:** Handles incoming registration requests and generates verification codes.  
**Technologies:** Ktor, RabbitMQ, PostgreSQL.  
**Functionality:**
- Listens for registration requests.
- Generates and stores verification codes.
- Publishes notifications to a RabbitMQ exchange for further processing.

### 2. Notification Server
**Purpose:** Processes registration notifications and sends out email confirmations.  
**Technologies:** Ktor, RabbitMQ, SendGrid.  
**Functionality:**
- Listens for registration notifications.
- Sends confirmation emails via SendGrid.
- Stores notification data in the database.

### 3. Benchmarking Tool
**Purpose:** Measures the performance and throughput of the registration server.  
**Technologies:** Kotlin, Ktor, RabbitMQ.  
**Functionality:**
- Simulates a high load of registration requests.
- Measures the time taken to process a fixed number of requests.
- Verifies that the system meets the required performance threshold.

### 4. Fake SendGrid Server
**Purpose:** Simulates the SendGrid email service for testing purposes.  
**Technologies:** Ktor.  
**Functionality:**
- Receives and logs email requests.
- Provides a mock endpoint for the notification server to send emails during testing.

## Core Features

- **Consistent Hash Exchange:** Implements a consistent hash exchange type in RabbitMQ to evenly distribute load among multiple consumers. This ensures efficient handling of high traffic by balancing the workload across available resources.
- **Asynchronous Processing:** Utilizes RabbitMQ to decouple the components, allowing for asynchronous processing of registration and notification tasks. This improves the system's scalability and responsiveness.
- **Performance Benchmarking:** Includes a benchmarking tool to validate the system's ability to handle a high volume of requests, ensuring it meets performance requirements.
- **Environment Configuration:** Easily configurable through environment variables, allowing for seamless deployment and testing in different environments.

## Benefits

- **Scalability:** The use of RabbitMQ and consistent hashing ensures that the system can handle increasing loads by distributing tasks efficiently.
- **Reliability:** Asynchronous processing and robust error handling ensure that the system remains reliable even under high load conditions.
- **Flexibility:** The project is designed to be easily configurable and extendable, making it suitable for various use cases and environments.
- **Performance:** The benchmarking tool helps maintain high performance standards, ensuring the system can meet the demands of real-world applications.

The Email Verifier project is a comprehensive solution for managing email verifications, providing a scalable, reliable, and efficient system for handling registration and notification workflows.

## Key Features of the Email Verifier Project

### 1. Using k6 for Load Testing
**Overview:**
The Email Verifier project leverages k6, a modern load testing tool, to ensure that the system can handle high volumes of registration requests efficiently.

**Key Points:**
- **Purpose:** k6 is used to simulate high loads and stress test the registration endpoint of the Email Verifier system, validating its performance and stability under heavy traffic.
- **Implementation:**
  - The `test.js` file in the project root contains the k6 test script.
  - This script defines the load testing parameters, such as the number of virtual users (VUs) and the duration of the test.
  - It sends a series of HTTP POST requests to the registration endpoint with a predefined payload.
  - The script includes checks to verify that the server responses are as expected, ensuring the endpoint's correctness under load.
- **Benefits:**
  - Identifies performance bottlenecks and potential points of failure.
  - Provides insights into the system's scalability and robustness.
  - Helps in optimizing the system for better performance and user experience.

**Example:**
```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  duration: '30s',
  vus: 10,
};

export default function () {
  const url = 'http://localhost:8081/request-registration';
  const payload = JSON.stringify({
    email: 'test@example.com',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const response = http.post(url, payload, params);

  check(response, {
    'is status 200': (r) => r.status === 200,
    'response body is not null': (r) => r.body !== null,
    'response body contains confirmation': (r) => r.body && r.body.includes('confirmation'),
  });
}
```

### 2. The Consistent Hash Exchange Feature of RabbitMQ
**Overview:**
The Email Verifier project utilizes the consistent hash exchange feature of RabbitMQ to distribute load evenly across multiple consumers, ensuring efficient processing of registration requests.

**Key Points:**
- **Purpose:** Consistent hashing helps in evenly distributing messages across queues, making the system more resilient and scalable. It ensures that messages with the same routing key are consistently directed to the same queue.
- **Implementation:**
  - A consistent hash exchange (`x-consistent-hash`) is set up in RabbitMQ.
  - Queues are bound to this exchange with specific weights, which determine the number of hash space partitions each queue will handle.
  - This configuration allows for an even distribution of messages, optimizing the load across consumers.
- **Benefits:**
  - Provides a uniform distribution of messages, reducing the risk of overloading a single consumer.
  - Enhances scalability by allowing easy addition or removal of queues without significant reconfiguration.
  - Maintains high availability and fault tolerance, ensuring continuous operation even if some queues fail.

**Example Configuration:**
```kotlin
val registrationRequestExchange = RabbitExchange(
    name = "registration-request-consistent-hash-exchange",
    type = "x-consistent-hash",
    routingKeyGenerator = { message: String -> message.hashCode().toString() },
)
val registrationRequestQueue = RabbitQueue("registration-request")
connectionFactory.declareAndBind(exchange = registrationRequestExchange, queue = registrationRequestQueue, routingKey = "42")
```

**Usage Example in Code:**
```kotlin
fun main(): Unit = runBlocking {
    val port = System.getenv("PORT")?.toInt() ?: 8081
    val rabbitUrl = System.getenv("RABBIT_URL")?.let(::URI)
        ?: throw RuntimeException("Please set the RABBIT_URL environment variable")
    val databaseUrl = System.getenv("DATABASE_URL")
        ?: throw RuntimeException("Please set the DATABASE_URL environment variable")
    val registrationRequestQueueName = System.getenv("REGISTRATION_REQUEST_QUEUE") ?: "registration-request"
    val registrationRequestRoutingKey = System.getenv("REGISTRATION_REQUEST_ROUTING_KEY") ?: "42"

    val dbConfig = DatabaseConfiguration(databaseUrl)
    val dbTemplate = DatabaseTemplate(dbConfig.db)

    val connectionFactory = buildConnectionFactory(rabbitUrl)
    val registrationRequestGateway = RegistrationRequestDataGateway(dbTemplate)
    val registrationGateway = RegistrationDataGateway(dbTemplate)

    val registrationNotificationExchange = RabbitExchange(
        name = "registration-notification-exchange",
        type = "direct",
        routingKeyGenerator = { _: String -> "42" },
    )
    val registrationNotificationQueue = RabbitQueue("registration-notification")
    connectionFactory.declareAndBind(exchange = registrationNotificationExchange, queue = registrationNotificationQueue, routingKey = "42")

    val registrationRequestExchange = RabbitExchange(
        name = "registration-request-consistent-hash-exchange",
        type = "x-consistent-hash",
        routingKeyGenerator = { message: String -> message.hashCode().toString() },
    )
    val registrationRequestQueue = RabbitQueue(registrationRequestQueueName)
    connectionFactory.declareAndBind(exchange = registrationRequestExchange, queue = registrationRequestQueue, routingKey = registrationRequestRoutingKey)

    listenForRegistrationRequests(
        connectionFactory,
        registrationRequestGateway,
        registrationNotificationExchange,
        registrationRequestQueue
    )
    registrationServer(
        port,
        registrationRequestGateway,
        registrationGateway,
        connectionFactory,
        registrationRequestExchange
    ).start()
}
```

These features ensure that the Email Verifier system is robust, scalable, and capable of handling high loads efficiently, making it a reliable solution for managing email verifications.

## Source Tree Overview

### 1. Registration Server
**File Path:** `applications/registration-server/src/main/kotlin/io/initialcapacity/emailverifier/registrationserver/App.kt`

**Description:**
- This file contains the main entry point for the Registration Server application.
- **Key Functions:**
  - **main:** Initializes and starts the registration server.
  - **registrationServer:** Configures and launches the Ktor server.
  - **module:** Sets up the server modules, including routes and plugins.
  - **listenForRegistrationRequests:** Listens for incoming registration requests from RabbitMQ and processes them.

**Purpose:** 
Handles incoming user registration requests, generates verification codes, and publishes notifications for further processing.

### 2. Notification Server
**File Path:** `applications/notification-server/src/main/kotlin/io/initialcapacity/emailverifier/notificationserver/App.kt`

**Description:**
- This file contains the main entry point for the Notification Server application.
- **Key Functions:**
  - **main:** Initializes and starts the notification server.
  - **start:** Configures and starts the notification processing.
  - **createNotifier:** Creates an instance of the Notifier

 class, which handles sending emails.
  - **listenForNotificationRequests:** Listens for registration notification messages from RabbitMQ and processes them.

**Purpose:** 
Processes registration notifications, sends out confirmation emails, and manages notification data storage.

### 3. Fake SendGrid Server
**File Path:** `platform-support/fake-sendgrid/src/main/kotlin/io/initialcapacity/emailverifier/fakesendgrid/App.kt`

**Description:**
- This file contains the main entry point for the Fake SendGrid Server application.
- **Key Functions:**
  - **main:** Initializes and starts the fake SendGrid server.
  
**Purpose:** 
Simulates the SendGrid email service for testing purposes, allowing the notification server to send emails during testing without actually sending real emails.

### 4. Benchmarking Tool
**File Path:** `applications/benchmark/src/main/kotlin/io/initialcapacity/emailverifier/benchmark/App.kt`

**Description:**
- This file contains the main entry point for the Benchmarking Tool application.
- **Key Functions:**
  - **main:** Initializes and starts the benchmarking process.
  - **fakeEmailServer:** Creates and starts a fake email server for testing.
  - **getEnvInt:** Helper function to retrieve environment variables as integers.

**Purpose:** 
Simulates a high load of registration requests to measure and validate the performance and throughput of the registration server.

### 5. Test Script
**File Path:** `test.js`

**Description:**
- This file contains a K6 test script for load testing the registration endpoint.
- **Key Functions:**
  - **options:** Configuration for the K6 load test, including duration and virtual users.
  - **default:** The main function that sends POST requests to the registration endpoint and checks the responses.

**Purpose:** 
Performs load testing on the registration endpoint to ensure it can handle the specified number of requests per second and validates the response correctness.
