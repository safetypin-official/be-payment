package com.safetypin.payment.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midtrans.Config;
import com.midtrans.ConfigFactory;
import com.midtrans.httpclient.error.MidtransError;
import com.midtrans.service.MidtransCoreApi;
import com.safetypin.payment.exception.PaymentException;
import com.safetypin.payment.model.Subscription;
import com.safetypin.payment.repository.SubscriptionRepository;
import com.safetypin.payment.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

@Service
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final DateTimeFormatter MIDTRANS_DATE_FORMATTER
                = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

    private static final int MAX_ATTEMPTS = 3;


    private final MidtransCoreApi midtransCoreApi;
    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;


    public SubscriptionService(
            Config config,
            TransactionRepository transactionRepository,
            SubscriptionRepository subscriptionRepository,
            ObjectMapper objectMapper) {
        this.midtransCoreApi = new ConfigFactory(config).getCoreApi();
        this.transactionRepository = transactionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }



    // =====================
    // Public Methods
    // =====================

    /**
     * Creates a new subscription in Midtrans.
     *
     * todo add required values of subscription object
     *
     * @param subscription The subscription object to be pushed to Midtrans.
     * @throws PaymentException If an error occurs while pushing the subscription.
     */
    public void createSubscriptionToMidtrans(Subscription subscription) throws PaymentException {
        // Format the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("token", subscription.getToken());
        requestBody.put("name", subscription.getSubscriptionName());
        requestBody.put("amount", subscription.getAmount());
        requestBody.put("currency", subscription.getCurrency());
        requestBody.put("payment_type", subscription.getPaymentType());
        // schedule: 1 month
        requestBody.put("schedule", Map.of(
                "interval", 1,
                "interval_unit", "month"
        ));

        // metadata
        Map<String, Object> metadata = new HashMap<>(Map.of(
                "user_id", subscription.getUserId()
        ));
        if (subscription.getTokenExpiry() != null) {
            metadata.put("token_expired", subscription.getTokenExpiry().format(MIDTRANS_DATE_FORMATTER));
        }
        requestBody.put("metadata", metadata);

        // customer details
        requestBody.put("customer_details", Map.of(
                "first_name", subscription.getUserName()
        ));

        // Call Midtrans API to create the subscription
        JsonNode response = pushCreateSubscriptionToMidtransServer(requestBody);

        // Update subscription object with values from the response
        updateSubscriptionFromResponseJson(subscription, response.get("subscription"));

        // Save the subscription object to the database
        subscriptionRepository.save(subscription);
    }

    /**
     * Handles subscription notifications from Midtrans.
     * This method is called when a subscription notification is received.
     *
     * @param notificationPayload The notification payload received from Midtrans.
     */
    @Transactional
    public void handleSubscriptionNotification (JsonNode notificationPayload) {
        log.info("Received subscription notification:\n{}", notificationPayload);
        // Check event attribute and handle accordingly
        String event = notificationPayload.get("event_name").asText();
        JsonNode subscriptionObject = notificationPayload.get("subscription");
        JsonNode transactionObject = notificationPayload.get("transaction");

        switch (event) {
            case "subscription.create" -> handleSubscriptionCreation(subscriptionObject);
            case "subscription.charge", "subscription.update_status" -> {/* placeholder */}
            default -> log.warn("Unknown subscription event: {}", event);
        }
    }



    // =====================
    // Subscription Handler Methods
    // =====================


    private void handleSubscriptionCreation (JsonNode subscriptionObject) throws PaymentException {
        // Verify subscription details by getting information from Midtrans
        String subscriptionId = subscriptionObject.get("id").asText();

        // Call Midtrans API to get actual subscription details if available
        JsonNode serverSubscriptionDetails = retrieveSubscriptionFromMidtransServer(subscriptionId);

        // TODO stub
    }



    // =====================
    // API Call Methods
    // =====================

    // Wrapper lambda for retry policy
    // Pass call function into this method
    /**
     * Wrapper for Midtrans API calls with retry logic.
     * This method retries the API call a specified number of times if it fails.
     *
     * @param call The API call to be executed.
     * @return The result of the API call as a JsonNode.
     * @throws PaymentException If an error occurs during the API call.
     */
    static Supplier<JsonNode> midtransApiCallRetryWrapper (Supplier<JsonNode> call) throws PaymentException {
        return () -> {
            int attempts = 0;
            while (attempts < MAX_ATTEMPTS) {
                // Call the API
                JsonNode result = call.get();
                if (result != null) {return result; }

                // add a small delay before attempting again
                attempts++;
                try {
                    Thread.sleep(1000L * attempts); // e.g., 1s, 2s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new PaymentException("Midtrans API call interrupted during retry.", ie);
                }
            }
            return null;
        };
    }

    /**
     * Handles exceptions that occur during API calls to Midtrans.
     * This method logs the error and throws a PaymentException.
     *
     * @param e        The exception that occurred.
     * @param apiName  The name of the API that was called.
     * @throws PaymentException If an error occurs while handling the exception.
     */
    static void handleApiCallException (Exception e, String apiName) throws PaymentException {
        if (e instanceof MidtransError error) {
            // Handle Midtrans error
            log.error("Midtrans API {} failed", apiName);
            log.error("Midtrans error response body: {}", error.getResponseBody());
        } else if (e instanceof JacksonException) {
            // Handle JSON parsing error
            log.error("Object parsing error: {}", e.getMessage());
            throw new PaymentException("Failed to parse Midtrans API response", e);
        } else {
            // Handle other exceptions
            log.error("Unexpected error: {}", e.getMessage());
            throw new PaymentException("Unexpected error during Midtrans API call", e);
        }
    }

    /**
     * Retrieves subscription details from the Midtrans server.
     *
     * @param subscriptionId The ID of the subscription to retrieve.
     * @return The subscription details as a JsonNode.
     * @throws PaymentException If an error occurs while retrieving the subscription.
     */
    private JsonNode retrieveSubscriptionFromMidtransServer
            (String subscriptionId) throws PaymentException {
        return midtransApiCallRetryWrapper(() -> {
            try {
                JSONObject response = midtransCoreApi.getSubscription(subscriptionId);
                return objectMapper.readTree(response.toString());
            } catch (Exception e) {
                handleApiCallException(e, "getSubscription");
                return null;
            }
        }).get();
    }

    /**
     * Pushes a subscription to the Midtrans server.
     *
     * @param subscription The subscription details to push.
     * @return The response from the Midtrans server as a JsonNode.
     * @throws PaymentException If an error occurs while pushing the subscription.
     */
    private JsonNode pushCreateSubscriptionToMidtransServer
            (Map<String, Object> subscription) throws PaymentException {
        return midtransApiCallRetryWrapper(() -> {
            try {
                JSONObject response = midtransCoreApi.createSubscription(subscription);
                return objectMapper.readTree(response.toString());
            } catch (Exception e) {
                handleApiCallException(e, "createSubscription");
                return null;
            }
        }).get();
    }




    // =====================
    // Object Methods
    // =====================

    /**
     * Updates the subscription object with values from the response JSON.
     *
     * @param subscription The subscription object to be updated.
     * @param subscriptionObject The JSON object containing the subscription details.
     */
    private void updateSubscriptionFromResponseJson(Subscription subscription, JsonNode subscriptionObject) {
        // Update subscription object with novel values from the response
        subscription.setSubscriptionId(subscriptionObject.get("id").asText());
        subscription.setSubscriptionName(subscriptionObject.get("name").asText());
        subscription.setSubscriptionStatus(subscriptionObject.get("status").asText());
        subscription.setAmount(new BigDecimal(subscriptionObject.get("amount").asText()));
        subscription.setCurrency(subscriptionObject.get("currency").asText());
        subscription.setPaymentType(subscriptionObject.get("payment_type").asText());

        // Set extra subscription details
        Map<String, Object> subscriptionDetails = new HashMap<>();
        subscriptionDetails.put("created_at", subscriptionObject.get("created_at").asText());
        subscriptionDetails.put("schedule", subscriptionObject.get("schedule"));
        subscriptionDetails.put("retry_schedule", subscriptionObject.get("retry_schedule"));
        subscriptionDetails.put("metadata", subscriptionObject.get("metadata"));
        subscription.setSubscriptionDetails(subscriptionDetails);
    }
}