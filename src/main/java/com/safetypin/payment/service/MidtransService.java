package com.safetypin.payment.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper; // For parsing JSON response
import com.midtrans.Config;
import com.midtrans.service.MidtransSnapApi;
import com.midtrans.service.impl.MidtransSnapApiImpl; // Correct import
import com.safetypin.payment.config.MidtransConfig;

import jakarta.annotation.PostConstruct;

@Service
public class MidtransService {

    private static final Logger logger = LoggerFactory.getLogger(MidtransService.class);
    private final MidtransConfig midtransConfig;
    private MidtransSnapApi midtransSnapApi;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // Inject ObjectMapper

    @Autowired
    public MidtransService(MidtransConfig midtransConfig, RestTemplate restTemplate, ObjectMapper objectMapper) { // Add
                                                                                                                  // ObjectMapper
        this.midtransConfig = midtransConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper; // Initialize ObjectMapper
    }

    @PostConstruct
    private void init() {
        // Initialize Midtrans configuration
        Config configOptions = Config.builder()
                .setServerKey(midtransConfig.getServerKey())
                .setClientKey(midtransConfig.getClientKey())
                // Set isProduction based on your environment, default is false (Sandbox)
                .setIsProduction(false)
                .build();
        // Correct instantiation using the implementation class
        this.midtransSnapApi = new MidtransSnapApiImpl(configOptions);
    }

    // Consider using a more specific exception type later
    public JSONObject createSnapToken(String orderId, double grossAmount) throws Exception {
        // Prepare transaction details
        Map<String, Object> transactionDetails = new HashMap<>();
        transactionDetails.put("order_id", orderId);
        transactionDetails.put("gross_amount", grossAmount);

        // Prepare overall request body
        Map<String, Object> body = new HashMap<>();
        body.put("transaction_details", transactionDetails);

        // TODO: Add customer_details and item_details for more comprehensive data
        // Map<String, String> customerDetails = new HashMap<>();
        // customerDetails.put("first_name", "Test");
        // customerDetails.put("last_name", "User");
        // customerDetails.put("email", "test.user@example.com");
        // customerDetails.put("phone", "081234567890");
        // body.put("customer_details", customerDetails);

        // Create Snap Token
        return midtransSnapApi.createTransaction(body);
    }

    public ResponseEntity<String> createSubscription(Map<String, Object> subscriptionRequest) {
        String url = midtransConfig.getSubscriptionApiUrl();
        HttpHeaders headers = createMidtransHeaders();

        // --- Construct the request body based on Midtrans docs ---
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", subscriptionRequest.getOrDefault("name", "DEFAULT_SUB_NAME"));
        requestBody.put("amount", "10000"); // Fixed amount
        requestBody.put("currency", "IDR");

        String paymentType = (String) subscriptionRequest.get("payment_type");
        requestBody.put("payment_type", paymentType);
        requestBody.put("token", subscriptionRequest.get("token")); // Card token or GoPay account ID/token

        // Schedule details (example: monthly starting now)
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("interval", 1);
        schedule.put("interval_unit", "month");
        schedule.put("max_interval", 12);
        schedule.put("start_time",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " +0700");
        requestBody.put("schedule", schedule);

        // --- Add gopay object conditionally ---
        if ("gopay".equalsIgnoreCase(paymentType)) {
            Map<String, Object> gopayDetails = new HashMap<>();
            gopayDetails.put("callback_url", midtransConfig.getGopayCallbackUrl());
            // You might need other fields in gopayDetails based on specific GoPay flows
            requestBody.put("gopay", gopayDetails);
        }

        // Optional: Add customer details if provided
        if (subscriptionRequest.containsKey("customer_details")) {
            requestBody.put("customer_details", subscriptionRequest.get("customer_details"));
        }

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", "Subscription description");
        requestBody.put("metadata", metadata);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> createResponse = restTemplate.postForEntity(url, entity, String.class);

            // --- Automatically enable after successful creation ---
            if (createResponse.getStatusCode().is2xxSuccessful() && createResponse.getBody() != null) {
                try {
                    // Parse the response to get the subscription ID
                    Map<String, Object> responseMap = objectMapper.readValue(createResponse.getBody(), Map.class);
                    String subscriptionId = (String) responseMap.get("id");
                    if (subscriptionId != null && !subscriptionId.isEmpty()) {
                        logger.info("Subscription {} created successfully. Attempting to enable...", subscriptionId);
                        // Call enableSubscription internally
                        ResponseEntity<String> enableResponse = enableSubscriptionInternal(subscriptionId);
                        if (enableResponse.getStatusCode().is2xxSuccessful()) {
                            logger.info("Subscription {} enabled successfully.", subscriptionId);
                        } else {
                            logger.warn("Failed to automatically enable subscription {}. Status: {}, Body: {}",
                                    subscriptionId, enableResponse.getStatusCode(), enableResponse.getBody());
                            // Decide if failure to enable should affect the overall response.
                            // For now, we still return the original successful creation response.
                        }
                    } else {
                        logger.error("Could not extract subscription ID from create response: {}",
                                createResponse.getBody());
                    }
                } catch (Exception e) {
                    logger.error("Error parsing create subscription response or enabling subscription: {}",
                            e.getMessage(), e);
                    // Continue to return the original success response despite auto-enable failure
                }
            }
            // --- End of auto-enable ---

            return createResponse; // Return the original response from the create call
        } catch (Exception e) {
            logger.error("Failed to create subscription: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create subscription: " + e.getMessage());
        }
    }

    public ResponseEntity<String> getSubscription(String subscriptionId) {
        // Construct the URL for the specific subscription
        String url = midtransConfig.getSubscriptionApiUrl() + "/" + subscriptionId;
        HttpHeaders headers = createMidtransHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers); // No body needed for GET

        try {
            // Make the GET request
            return restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);
        } catch (Exception e) {
            // Basic error handling, consider more specific exception handling
            // You might want to check for specific HTTP status codes like 404 Not Found
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to get subscription: " + e.getMessage());
        }
    }

    public ResponseEntity<String> disableSubscription(String subscriptionId) {
        // Construct the URL for disabling the specific subscription
        String url = midtransConfig.getSubscriptionApiUrl() + "/" + subscriptionId + "/disable";
        HttpHeaders headers = createMidtransHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers); // No body needed for this POST request

        try {
            // Make the POST request to disable the subscription
            return restTemplate.exchange(
                    url,
                    HttpMethod.POST, // Use POST method as per Midtrans docs
                    entity,
                    String.class);
        } catch (Exception e) {
            // Basic error handling, consider checking for specific HTTP status codes
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to disable subscription: " + e.getMessage());
        }
    }

    public ResponseEntity<String> cancelSubscription(String subscriptionId) {
        // Construct the URL for cancelling the specific subscription
        String url = midtransConfig.getSubscriptionApiUrl() + "/" + subscriptionId + "/cancel";
        HttpHeaders headers = createMidtransHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers); // No body needed for this POST request

        try {
            // Make the POST request to cancel the subscription
            return restTemplate.exchange(
                    url,
                    HttpMethod.POST, // Use POST method as per Midtrans docs
                    entity,
                    String.class);
        } catch (Exception e) {
            // Basic error handling, consider checking for specific HTTP status codes
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to cancel subscription: " + e.getMessage());
        }
    }

    // Public method exposed via controller
    public ResponseEntity<String> enableSubscription(String subscriptionId) {
        return enableSubscriptionInternal(subscriptionId);
    }

    // Internal method for enabling, used by createSubscription and the public
    // enable endpoint
    private ResponseEntity<String> enableSubscriptionInternal(String subscriptionId) {
        String url = midtransConfig.getSubscriptionApiUrl() + "/" + subscriptionId + "/enable";
        HttpHeaders headers = createMidtransHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers); // No body needed

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);
            logger.info("Enable subscription request for ID {} completed with status: {}", subscriptionId,
                    response.getStatusCode());
            return response;
        } catch (Exception e) {
            logger.error("Failed to enable subscription {}: {}", subscriptionId, e.getMessage(), e);
            // Consider mapping Midtrans errors (e.g., 404) to specific Spring responses
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to enable subscription: " + e.getMessage());
        }
    }

    // Method to update a subscription
    public ResponseEntity<String> updateSubscription(String subscriptionId, Map<String, Object> updateDetails) {
        String url = midtransConfig.getSubscriptionApiUrl() + "/" + subscriptionId;
        HttpHeaders headers = createMidtransHeaders();

        // Ensure the request body contains only the fields to be updated
        // Midtrans typically uses PATCH for updates, allowing partial modification.
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(updateDetails, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH, // Use PATCH for updating
                    entity,
                    String.class);
            logger.info("Update subscription request for ID {} completed with status: {}", subscriptionId,
                    response.getStatusCode());
            return response;
        } catch (Exception e) {
            logger.error("Failed to update subscription {}: {}", subscriptionId, e.getMessage(), e);
            // Consider mapping Midtrans errors (e.g., 404, 400) to specific Spring
            // responses
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update subscription: " + e.getMessage());
        }
    }

    private HttpHeaders createMidtransHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        // Basic Authentication with Server Key
        String auth = midtransConfig.getServerKey() + ":";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }
}
