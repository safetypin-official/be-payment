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
    
}
