package com.safetypin.payment.controller;

import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.safetypin.payment.service.MidtransService;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final MidtransService midtransService;

    @Autowired
    public PaymentController(MidtransService midtransService) {
        this.midtransService = midtransService;
    }

    @PostMapping("/payments/create") // Endpoint for Snap token
    public ResponseEntity<String> createPayment(@RequestBody Map<String, Object> request) { // Removed throws Exception
        String orderId = null; // Declare orderId here
        try {
            // Extract amount from request, handle potential errors
            Double amount = Double.parseDouble(request.getOrDefault("amount", "0").toString());
            if (amount <= 0) {
                return ResponseEntity.badRequest().body("Invalid amount");
            }

            // Generate a unique order ID (you might have your own logic for this)
            orderId = "order-" + UUID.randomUUID().toString(); // Assign value inside try

            JSONObject snapTokenResponse = midtransService.createSnapToken(orderId, amount);

            // Return the token part of the response
            return ResponseEntity.ok(snapTokenResponse.toString());
        } catch (NumberFormatException e) {
            // Now orderId is accessible here (though it might be null if parsing failed
            // early)
            logger.error("Invalid amount format in request: {}", request, e);
            // Return 400 with the exact message expected by the test
            return ResponseEntity.badRequest().body("Invalid amount format"); // Exact match for test
        } catch (Exception e) { // Catch other exceptions, likely from the service
            logger.error("Failed to create payment token for orderId: {}. Request: {}", orderId, request, e);
            // Restore exception message for test assertion
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create payment token: " + e.getMessage());
        }
    }

    @PostMapping("/subscriptions/create")
    public ResponseEntity<String> createSubscription(@RequestBody Map<String, Object> request) {
        // Check if required fields exist
        if (!request.containsKey("token") || !request.containsKey("payment_type")) {
            return ResponseEntity.badRequest().body("Missing required fields: token and payment_type");
        }
        String token = String.valueOf(request.get("token"));
        String paymentType = String.valueOf(request.get("payment_type"));
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Token cannot be empty");
        }
        if (paymentType == null || paymentType.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Payment type cannot be empty");
        }

        try {
            // Call the service method. Assume it returns appropriate ResponseEntity (200,
            // 400, 500 etc.)
            return midtransService.createSubscription(request);
        } catch (Exception e) {
            // This catch block handles unexpected runtime exceptions *during* the service
            // call
            logger.error("Unexpected error calling createSubscription service. Request: {}", request, e);
            // Return 500 for these unexpected errors, matching
            // createSubscription_ServiceReturnsError expectation IF it mocks an exception
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error during subscription creation.");
        }
    }

    @GetMapping("/subscriptions/{subscriptionId}") // New endpoint to get subscription
    public ResponseEntity<String> getSubscription(@PathVariable String subscriptionId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Subscription ID cannot be empty");
        }
        return midtransService.getSubscription(subscriptionId);
    }

    @PostMapping("/subscriptions/{subscriptionId}/disable") // New endpoint to disable subscription
    public ResponseEntity<String> disableSubscription(@PathVariable String subscriptionId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Subscription ID cannot be empty");
        }
        return midtransService.disableSubscription(subscriptionId);
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel") // New endpoint to cancel subscription
    public ResponseEntity<String> cancelSubscription(@PathVariable String subscriptionId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Subscription ID cannot be empty");
        }
        return midtransService.cancelSubscription(subscriptionId);
    }

    @PostMapping("/subscriptions/{subscriptionId}/enable") // Endpoint to enable subscription
    public ResponseEntity<String> enableSubscription(@PathVariable String subscriptionId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Subscription ID cannot be empty");
        }
        return midtransService.enableSubscription(subscriptionId);
    }

    @PatchMapping("/subscriptions/{subscriptionId}") // Endpoint to update subscription
    public ResponseEntity<String> updateSubscription(@PathVariable String subscriptionId,
            @RequestBody Map<String, Object> updateDetails) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Subscription ID cannot be empty");
        }
        if (updateDetails == null || updateDetails.isEmpty()) {
            return ResponseEntity.badRequest().body("Update details cannot be empty");
        }
        return midtransService.updateSubscription(subscriptionId, updateDetails);
    }
}
