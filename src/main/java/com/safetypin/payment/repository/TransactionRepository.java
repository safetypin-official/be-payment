package com.safetypin.payment.repository;

import com.safetypin.payment.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByTransactionId(String transactionId);

    List<Transaction> findByOrderId(String orderId);

    List<Transaction> findByPaymentTypeOrderByUpdatedAt(String paymentType);

    List<Transaction> findByTransactionStatusOrderByUpdatedAt(String transactionStatus);
}
