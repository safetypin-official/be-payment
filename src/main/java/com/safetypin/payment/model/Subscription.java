package com.safetypin.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.*;
import java.time.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Data
@Table(name = "subscriptions") // Separate table for subscriptions
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Add fields relevant to Midtrans subscriptions
    @Column(unique = true)
    private String subscriptionId;      // Midtrans subscription ID  // TODO test double null value
    private String subscriptionName;    // Name of the subscription
    private String subscriptionStatus;  // Subscription status (e.g., active, inactive, pending)
    private BigDecimal amount;          // Amount for the subscription
    private String currency;            // Currency of the subscription
    private String paymentType;         // Payment type (e.g., credit_card, bank_transfer)
    // Scheduled payment details
    private LocalDateTime subscriptionExpiry; // Expiry date of the subscription

    // Token information
    private String token; // Token for the subscription
    private LocalDateTime tokenExpiry; // Expiry date of the token

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> subscriptionDetails; // Store other details from Midtrans

    // Link back to the User
    @Column(nullable = false)
    private UUID userId;
    private String userName;

    // Link back to the Transactions
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL)
    private List<Transaction> transactions;


    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
