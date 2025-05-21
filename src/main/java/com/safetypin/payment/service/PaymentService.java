package com.safetypin.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.midtrans.Config;
import com.midtrans.ConfigFactory;
import com.midtrans.httpclient.error.MidtransError;
import com.midtrans.service.MidtransSnapApi;
import com.safetypin.payment.dto.UserDetails;
import com.safetypin.payment.exception.PaymentException;
import com.safetypin.payment.model.Subscription;
import com.safetypin.payment.model.Subscription.SubscriptionBuilder;
import com.safetypin.payment.model.Transaction;
import com.safetypin.payment.repository.SubscriptionRepository;
import com.safetypin.payment.repository.TransactionRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String WEBHOOK_BASE_URL = "https://a2c13d13b57b23.lhr.life";

    private final MidtransSnapApi midtransSnapApi;
    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;

    private final SubscriptionService subscriptionService;

    private final String subscriptionName;

    public PaymentService(
            Config config,
            TransactionRepository transactionRepository, SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService,
            @Value("${safetypin.subscription.name}") String subscriptionName) {
        this.midtransSnapApi = new ConfigFactory(config).getSnapApi();
        this.transactionRepository = transactionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.subscriptionName = subscriptionName;
    }

    // Create snap tokens for payment
    public Map<String, Object> createRecurringTransaction(UserDetails user) throws PaymentException {
        // Set transaction details
        Map<String, Object> transactionDetails = new HashMap<>();
        // include timestamp in order_id and random salt
        String orderId = "PREMIUM_SUB-"
                        + UUID.randomUUID().toString().substring(0, 8) + "-"
                        + Instant.now().getEpochSecond();

        transactionDetails.put("order_id", orderId);
        transactionDetails.put("gross_amount", 49000); // Amount in IDR

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

        // Testing
        config.paymentOverrideNotification(
                WEBHOOK_BASE_URL + "/payment/notification/push");

        // Send request to Midtrans (try 3 times)
        int attempts = 0;
        final int MAX_ATTEMPTS = 3;
        while (attempts < MAX_ATTEMPTS) {
            try {
                log.info("Attempting to create Midtrans Snap transaction (Attempt {}/{}) for order_id: {}", attempts + 1, MAX_ATTEMPTS, orderId);
                JSONObject response = midtransSnapApi.createTransaction(requestBody);
                log.info("Midtrans Snap API createTransaction successful for order_id: {}. Response: {}", orderId, response.toMap());
                return response.toMap();
            } catch (MidtransError e) {
                log.error("Midtrans Snap API createTransaction (Attempt {}/{}) failed for order_id: {}. Error: {}", attempts + 1, MAX_ATTEMPTS, orderId, e.getMessage());
                if (e.getResponseBody() != null) {
                    log.error("Midtrans error response body: {}", e.getResponseBody());
                }
                if (attempts == MAX_ATTEMPTS - 1) { // Last attempt
                    throw new PaymentException("Failed to create Snap transaction after " + MAX_ATTEMPTS + " attempts for order_id: " + orderId, e);
                }
                // Optional: add a small delay before retrying
                try {
                    Thread.sleep(1000L * (attempts + 1)); // e.g., 1s, 2s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new PaymentException("Snap transaction creation interrupted during retry delay.", ie);
                }
            }
            attempts++;
        }
        // Should not be reached if MAX_ATTEMPTS > 0 due to throw in loop, but as a fallback:
        throw new PaymentException("Failed to create Snap transaction after " + MAX_ATTEMPTS + " attempts for order_id: " + orderId);
    }


    // Handle notification from Midtrans
    @Transactional
    public void handlePaymentNotification(JsonNode notificationPayload) throws PaymentException {
        boolean isNewPayment = false;
        Transaction transaction;

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
            // If it exists, update it; if not, create a new one
            if (existingTransactionOpt.isPresent()) {
                transaction = existingTransactionOpt.get();
                updateTransactionFromNotificationPayload(transaction, notificationPayload);
                log.info("Updating existing transaction: {}", transaction);
            } else {
                isNewPayment = true;
                transaction = createTransactionFromNotificationPayload(notificationPayload);
                log.info("Creating new transaction: {}", transaction);
            }

            transactionRepository.save(transaction);
            log.info("Successfully processed and stored notification for transaction: {}", notificationPayload);
        } catch (Exception e) {
            log.error("Error processing notification: {}", e.getMessage(), e);
            // Decide if you want to rethrow or just log.
            throw new PaymentException("Failed to process notification");
        }

        // If the transaction is a new payment and not a subscription, create a new subscription (if possible)
        if (isNewPayment) {
            handleSubscriptionCreation(notificationPayload, transaction);
        }
    }

    // Tries to create a subscription, if the data received has the required fields
    // Does nothing if the data does not have the required fields
    private void handleSubscriptionCreation(JsonNode notificationPayload, Transaction transaction) {
        // Create default subscription without midtrans sub (non-recurring)
        Subscription subscription = createDefaultSubscriptionFromNotification(
                notificationPayload, transaction);

        // Handle credit card subscription
        if (notificationPayload.get("payment_type").asText().equals("credit_card")) {
            updateSubscriptionFromCreditCardNotification(
                    subscription, notificationPayload);
            // TODO Create subscription to the Midtrans API


        }

        // Save the subscription to the database
        subscriptionRepository.save(subscription);
    }





    // Helper private methods for Transaction creation/updating

    // Define the formatter for Midtrans' transaction_time
    private static final DateTimeFormatter MIDTRANS_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static UUID retrieveUserIdIfPresent(JsonNode notificationPayload) {
        if (!notificationPayload.has("metadata")) return null;
        JsonNode metadata = notificationPayload.get("metadata");

        if (!metadata.has("extra_info")) return null;
        JsonNode extraInfo = metadata.get("extra_info");

        // Check if the user_id field is present in the extra_info
        if (!extraInfo.has("user_id")) return null;

        return UUID.fromString(extraInfo.get("user_id").asText());
    }

    private Transaction createTransactionFromNotificationPayload(JsonNode notificationPayload) {
        Transaction.TransactionBuilder transactionBuilder = Transaction.builder();
        transactionBuilder.transactionId(notificationPayload.get("transaction_id").asText());
        transactionBuilder.transactionStatus(notificationPayload.get("transaction_status").asText());

        String paymentType = notificationPayload.get("payment_type").asText();
        transactionBuilder.paymentType(paymentType);
        transactionBuilder.grossAmount(new BigDecimal(notificationPayload.get("gross_amount").asText()));
        transactionBuilder.transactionTime(LocalDateTime.parse(
                notificationPayload.get("transaction_time").asText(), MIDTRANS_DATE_TIME_FORMATTER)
        );
        transactionBuilder.orderId(notificationPayload.get("order_id").asText());
        transactionBuilder.currency(notificationPayload.get("currency").asText());
        transactionBuilder.userId(retrieveUserIdIfPresent(notificationPayload));
        // Set user name from customer details
        JsonNode customerDetails = notificationPayload.get("customer_details");
        if (customerDetails != null && customerDetails.has("first_name")) {
            transactionBuilder.userName(customerDetails.get("first_name").asText());
        }


        Map<String, Object> paymentDetails = new HashMap<>();
        if (paymentType.equals("credit_card")) {
            for (String field : new String[]{"masked_card", "eci", "card_type", "bank", "approval_code"}) {
                if (notificationPayload.has(field)) {
                    paymentDetails.put(field, notificationPayload.get(field).asText());
                }
            }
        } else if (paymentType.equals("gopay")) {
            for (String field : new String[]{"settlement_time"}) {
                if (notificationPayload.has(field)) {
                    paymentDetails.put(field, notificationPayload.get(field).asText());
                }
            }
        }
        transactionBuilder.paymentDetails(paymentDetails);


        return transactionBuilder.build();
    }

    private void updateTransactionFromNotificationPayload(Transaction transaction, JsonNode notificationPayload) {
        transaction.setTransactionStatus(notificationPayload.get("transaction_status").asText());
        transaction.setTransactionTime(LocalDateTime.parse(
                notificationPayload.get("transaction_time").asText(), MIDTRANS_DATE_TIME_FORMATTER));
        transaction.setPaymentType(notificationPayload.get("payment_type").asText());
        transaction.setGrossAmount(new BigDecimal(notificationPayload.get("gross_amount").asText()));
        transaction.setOrderId(notificationPayload.get("order_id").asText());
        transaction.setCurrency(notificationPayload.get("currency").asText());

        if (transaction.getPaymentType().equals("credit_card")) {
            for (String field : new String[]{"masked_card", "eci", "card_type", "bank", "approval_code"}) {
                if (notificationPayload.has(field)) {
                    transaction.getPaymentDetails().put(field, notificationPayload.get(field).asText());
                }
            }
        } else if (transaction.getPaymentType().equals("gopay")) {
            for (String field : new String[]{"settlement_time"}) {
                if (notificationPayload.has(field)) {
                    transaction.getPaymentDetails().put(field, notificationPayload.get(field).asText());
                }
            }
        }

    }

    private Subscription createDefaultSubscriptionFromNotification(
            JsonNode notificationPayload, Transaction firstTransaction) {
        SubscriptionBuilder subscriptionBuilder = Subscription.builder();
        subscriptionBuilder.subscriptionName(subscriptionName);
        // Set expiry to 1 month from now
        subscriptionBuilder.subscriptionExpiry(LocalDateTime.now().plusMonths(1));
        subscriptionBuilder.subscriptionStatus("active");

        subscriptionBuilder.paymentType(notificationPayload.get("payment_type").asText());

        subscriptionBuilder.amount(new BigDecimal(notificationPayload.get("gross_amount").asText()));
        subscriptionBuilder.currency(notificationPayload.get("currency").asText());

        subscriptionBuilder.userId(retrieveUserIdIfPresent(notificationPayload));
        // Set username from transaction
        subscriptionBuilder.userName(firstTransaction.getUserName());


        List<Transaction> transactions = new ArrayList<>();
        transactions.add(firstTransaction);
        subscriptionBuilder.transactions(transactions);

        return subscriptionBuilder.build();
    }


    private void updateSubscriptionFromCreditCardNotification(
                Subscription subscription, JsonNode notificationPayload) {
        if (notificationPayload.has("saved_token_id"))
            subscription.setToken(notificationPayload.get("saved_token_id").asText());
        if (notificationPayload.has("saved_token_id_expired_at"))
            subscription.setTokenExpiry(LocalDateTime.parse(
                    notificationPayload.get("saved_token_id_expired_at").asText(), MIDTRANS_DATE_TIME_FORMATTER));
    }
}