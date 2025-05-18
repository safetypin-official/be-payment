package com.safetypin.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.midtrans.Config;
import com.midtrans.ConfigFactory;
import com.midtrans.httpclient.error.MidtransError;
import com.midtrans.service.MidtransSnapApi;
import com.safetypin.payment.dto.UserDetails;
import com.safetypin.payment.exception.PaymentException;
import com.safetypin.payment.model.Transaction;
import com.safetypin.payment.repository.TransactionRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final MidtransSnapApi midtransSnapApi;

    private final TransactionRepository transactionRepository;

    public TransactionService(Config config, TransactionRepository transactionRepository) {
        this.midtransSnapApi = new ConfigFactory(config).getSnapApi();
        this.transactionRepository = transactionRepository;
    }

    // Create snap tokens for payment
    public Map<String, Object> createRecurringTransaction(UserDetails user) throws PaymentException {
        // Set transaction details
        Map<String, Object> transactionDetails = new HashMap<>();
        // include timestamp in order_id and random salt
        transactionDetails.put("order_id",
                ("PREMIUM_SUB-" + UUID.randomUUID().toString().substring(0, 8) + "-" + Instant.now().getEpochSecond()));
        transactionDetails.put("gross_amount", 69000); // Example amount

        // Set enabled payment types
        List<String> enabledPayments = List.of("gopay", "credit_card");

        // Set recurring payment settings
        Map<String, Object> recurring = new HashMap<>();
        recurring.put("required", true);
        recurring.put("interval_unit", "month");

        // Set Credit Card settings
        Map<String, Object> creditCard = new HashMap<>();
        creditCard.put("secure", true);
        creditCard.put("save_card", true);

        // Set GoPay settings
        Map<String, Object> gopay = new HashMap<>();
        gopay.put("enable_callback", true);
        gopay.put("tokenization", true);

        // Set available customer details
        Map<String, Object> customerDetails = new HashMap<>();
        customerDetails.put("first_name", user.getName());

        // Combine details into one
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("transaction_details", transactionDetails);
        requestBody.put("enabled_payments", enabledPayments);
        requestBody.put("recurring", recurring);
        requestBody.put("credit_card", creditCard);
        requestBody.put("gopay", gopay);
        requestBody.put("user_id", user.getUserId().toString());
        requestBody.put("customer_details", customerDetails);

        // Setup Midtrans configuration
        Config config = midtransSnapApi.apiConfig();
        config.setPaymentIdempotencyKey(UUID.randomUUID().toString());

        // Send request to Midtrans (try 3 times)
        int attempts = 0;
        while (attempts < 3) {
            try {
                JSONObject response = midtransSnapApi.createTransaction(requestBody);
                log.info("Midtrans response: {}", response);
                return response.toMap();
            } catch (MidtransError e) {
                log.error("Attempt {} failed: {}", attempts+1, e.toString());
            }
            attempts++;
        }

        throw new PaymentException("Failed to create transaction");
    }


    // Handle notification from Midtrans
    public void handlePaymentNotification(JsonNode notificationPayload) throws PaymentException {
        try {
            // Check some fields given below, to ensure that the process is successful.
            // Transaction notification can check this 3 fields:
            // status_code: Should be 200 for successful transactions.
            // fraud_status: ACCEPT.
            // transaction_status: settlement/capture.
            // Don't create/update the transaction
            String statusCode = notificationPayload.get("status_code").asText();
            String fraudStatus = notificationPayload.get("fraud_status").asText();
            String transactionStatus = notificationPayload.get("transaction_status").asText();
            if (!statusCode.equals("200") || !fraudStatus.equalsIgnoreCase("accept")) {
                log.error("Transaction not successful: {}", notificationPayload);
                return;
            }
            if (!transactionStatus.equalsIgnoreCase("settlement") &&
                    !transactionStatus.equalsIgnoreCase("capture")) {
                log.error("Transaction not settled or captured: {}", notificationPayload);
                return;
            }


            // Check if the transaction already exists
            String transactionId = notificationPayload.get("transaction_id").asText();
            Optional<Transaction> existingTransactionOpt = transactionRepository.findByTransactionId(transactionId);
            Transaction transaction;
            // If it exists, update it; if not, create a new one
            if (existingTransactionOpt.isPresent()) {
                transaction = existingTransactionOpt.get();
                transaction.updateFromNotificationPayload(notificationPayload);
                log.info("Updating existing transaction: {}", transaction);
            } else {
                transaction = Transaction.fromNotificationPayload(notificationPayload);
                log.info("Creating new transaction: {}", transaction);
            }

            transactionRepository.save(transaction);
            log.info("Successfully processed and stored notification for transaction: {}", notificationPayload);
        } catch (Exception e) {
            log.error("Error processing notification: {}", e.getMessage(), e);
            // Decide if you want to rethrow or just log.
            throw new PaymentException("Failed to process notification");
        }
    }
}