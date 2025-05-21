package com.safetypin.payment.repository;

import com.safetypin.payment.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Subscription findBySubscriptionId(String subscriptionId);
    Subscription findByUserId(UUID userId);
}
