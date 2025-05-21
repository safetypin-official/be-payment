package com.safetypin.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.safetypin.payment.service.MidtransSignatureVerifier;
import com.safetypin.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/payment/notification")
public class NotificationController {
    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final PaymentService paymentService;

    private final MidtransSignatureVerifier midtransSignatureVerifier;

    public NotificationController(
            PaymentService paymentService,
            MidtransSignatureVerifier midtransSignatureVerifier
    ) {
        this.paymentService = paymentService;
        this.midtransSignatureVerifier = midtransSignatureVerifier;
    }

    /**
     * Endpoint to receive notifications pushed from a web service.
     *
     * @param notificationPayload The notification data sent by the web service.
     * @return ResponseEntity indicating the result of processing the notification.
     */
    @PostMapping("/push")
    public ResponseEntity<String> handleIncomingNotification(@RequestBody JsonNode notificationPayload) {
        if (!midtransSignatureVerifier.isValidPaymentSignature(notificationPayload)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        // Process the notification payload
        paymentService.handlePaymentNotification(notificationPayload);

        // Return a response indicating success (200, no retries)
        return ResponseEntity.status(HttpStatus.OK).body("Notification received successfully!!!");
    }


    @PostMapping("/recurring")
    public ResponseEntity<String> handleIncomingRecurringNotification(@RequestBody Map<String, Object> notificationPayload) {
        // Log the received notification
        logger.info("Received recurring notification:\n{}", notificationPayload);
        // Process the notification payload as needed

        // Return a response indicating success (200, no retries)
        return ResponseEntity.status(HttpStatus.OK).body("Notification received successfully");
    }

    @PostMapping("/gopay-link")
    public ResponseEntity<String> handleIncomingGoPayLinkingNotification(@RequestBody Object notificationPayload) {
        // Log the received notification
        logger.info("Received GoPay notification:\n{}", notificationPayload);
        // Process the notification payload as needed

        // Return a response indicating success (200, no retries)
        return ResponseEntity.status(HttpStatus.OK).body("Notification received successfully");
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        logger.error("Error processing notification: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
}