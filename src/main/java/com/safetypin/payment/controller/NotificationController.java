package com.safetypin.payment.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/payment/notification")
public class NotificationController {
    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    /**
     * Endpoint to receive notifications pushed from a web service.
     *
     * @param notificationPayload The notification data sent by the web service.
     * @return ResponseEntity indicating the result of processing the notification.
     */
    @PostMapping("/push")
    public ResponseEntity<String> handleIncomingNotification(@RequestBody Object notificationPayload) {
        // Log the received notification
        logger.info("Received notification: {}", notificationPayload);
        // Process the notification payload as needed

        // Return a response indicating success (200, no retries)
        return ResponseEntity.status(HttpStatus.OK).body("Notification received successfully");
    }
}