package com.safetypin.payment.controller;

import java.util.Map;

import com.safetypin.payment.dto.PaymentResponse;
import com.safetypin.payment.dto.UserDetails;
import com.safetypin.payment.exception.PaymentException;
import com.safetypin.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/payment")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create/subscription") // Endpoint for Snap token
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestParam(value = "force_gopay_deeplink", defaultValue = "true") boolean forceGopayDeeplink
    ) {
        // Get user ID from authentication context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Map<String, Object> tokens = paymentService.createRecurringTransaction(userDetails);
        if (forceGopayDeeplink) {
            tokens.put("redirect_url", tokens.get("redirect_url") + "?gopayMode=deeplink");
        }

        return ResponseEntity.ok(new PaymentResponse(
                true, "SNAP token created", tokens
        ));
    }


    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<PaymentResponse> handlePaymentException(PaymentException e) {
        logger.error("Payment error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new PaymentResponse(false, e.getMessage(), null));
    }
}
