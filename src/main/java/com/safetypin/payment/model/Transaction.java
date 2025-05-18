package com.safetypin.payment.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Data
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Add fields relevant to Midtrans transactions
    @Column(unique = true)
    private String transactionId;   // Midtrans transaction ID
    private String transactionStatus;       // Transaction status (settlement, capture, etc.)
    private LocalDateTime transactionTime; // Time of transaction

    private String paymentType;     // Type of payment (e.g., credit card, bank transfer)
    private String orderId;         // Order ID associated with the transaction
    private BigDecimal grossAmount; // Total amount of the transaction
    private String currency;        // Currency of the transaction

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> paymentDetails; // Store payment type specific details from Midtrans

    // Link back to the User
    private UUID userId;

    // Link back to the Subscription
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id") // This will be the FK column in 'transactions' table
    private Subscription subscription;

    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Builder.Default
    private boolean requiredServerConfirm = false;  // Flag to indicate if server confirmation is required

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }



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

    // Method to convert a notification payload from Midtrans into a Transaction object
    public static Transaction fromNotificationPayload(JsonNode notificationPayload) {
        TransactionBuilder transactionBuilder = Transaction.builder();
        transactionBuilder.transactionId(notificationPayload.get("transaction_id").asText());
        transactionBuilder.transactionStatus(notificationPayload.get("transaction_status").asText());
        transactionBuilder.paymentType(notificationPayload.get("payment_type").asText());
        transactionBuilder.grossAmount(new BigDecimal(notificationPayload.get("gross_amount").asText()));
        transactionBuilder.transactionTime(LocalDateTime.parse(
                notificationPayload.get("transaction_time").asText(), MIDTRANS_DATE_TIME_FORMATTER)
        );
        transactionBuilder.orderId(notificationPayload.get("order_id").asText());
        transactionBuilder.currency(notificationPayload.get("currency").asText());
        transactionBuilder.userId(Transaction.retrieveUserIdIfPresent(notificationPayload));

        Map<String, Object> paymentDetails = new HashMap<>();
        if (transactionBuilder.paymentType.equals("credit_card")) {
            for (String field : new String[]{"masked_card", "eci", "card_type", "bank", "approval_code"}) {
                if (notificationPayload.has(field)) {
                    paymentDetails.put(field, notificationPayload.get(field).asText());
                }
            }
        } else if (transactionBuilder.paymentType.equals("gopay")) {
            for (String field : new String[]{"settlement_time"}) {
                if (notificationPayload.has(field)) {
                    paymentDetails.put(field, notificationPayload.get(field).asText());
                }
            }
        }
        transactionBuilder.paymentDetails(paymentDetails);


        return transactionBuilder.build();
    }

    // Method to update the transaction object with the notification payload
    public void updateFromNotificationPayload(JsonNode notificationPayload) {
        this.transactionStatus = notificationPayload.get("transaction_status").asText();
        this.transactionTime = LocalDateTime.parse(
                notificationPayload.get("transaction_time").asText(), MIDTRANS_DATE_TIME_FORMATTER);
        this.paymentType = notificationPayload.get("payment_type").asText();
        this.grossAmount = new BigDecimal(notificationPayload.get("gross_amount").asText());
        this.orderId = notificationPayload.get("order_id").asText();
        this.currency = notificationPayload.get("currency").asText();

        if (this.paymentType.equals("credit_card")) {
            for (String field : new String[]{"masked_card", "eci", "card_type", "bank", "approval_code"}) {
                if (notificationPayload.has(field)) {
                    this.paymentDetails.put(field, notificationPayload.get(field).asText());
                }
            }
        } else if (this.paymentType.equals("gopay")) {
            for (String field : new String[]{"settlement_time"}) {
                if (notificationPayload.has(field)) {
                    this.paymentDetails.put(field, notificationPayload.get(field).asText());
                }
            }
        }
    }

}
