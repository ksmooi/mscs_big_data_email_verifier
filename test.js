import http from 'k6/http'; // Importing the http module from k6 for making HTTP requests
import { check } from 'k6'; // Importing the check function from k6 for validating responses

// Define options for the test
export const options = {
  duration: '30s', // Test duration: the test will run for 30 seconds
  vus: 10, // Virtual Users: the test will simulate 10 concurrent users
};

export default function () {
  const url = 'http://localhost:8081/request-registration'; // URL to send the POST request to
  const payload = JSON.stringify({
    email: 'test@example.com', // JSON payload containing the email address to register
  });

  // Parameters for the request, including headers
  const params = {
    headers: {
      'Content-Type': 'application/json', // Specify that the request body is in JSON format
    },
  };

  // Make the POST request with the URL, payload, and parameters
  const response = http.post(url, payload, params);

  // Log the response status and body for debugging purposes
  console.log(`Response status: ${response.status}`);
  console.log(`Response body: ${response.body}`);

  // Optional: Add checks to verify the response
  check(response, {
    'is status 204': (r) => r.status === 204, // Check if the response status is 204 (No Content)
    'response body is null': (r) => r.body === null, // Check if the response body is null
  });
}
