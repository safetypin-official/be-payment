package com.safetypin.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    private String subscriptionId;  // Midtrans subscription ID
    private String subscriptionName; // Name of the subscription
    private String status;           // Subscription status (e.g., active, inactive, pending)

    // Scheduled payment details
//    private OffsetDateTime nextPaymentDate;

    // Link back to the User
    private UUID userId;
    private String token; // Token for the subscription

    // Link back to the Transactions
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL)
    private List<Transaction> transactions;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> subscriptionDetails; // Store other details from Midtrans


    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
