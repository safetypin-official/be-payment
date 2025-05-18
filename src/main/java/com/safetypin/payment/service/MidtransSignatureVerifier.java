package com.safetypin.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class MidtransSignatureVerifier {
    //logging
    private static final Logger log = LoggerFactory.getLogger(MidtransSignatureVerifier.class);

    @Value("${midtrans.server.key}")
    private String serverKey;

    public boolean isValidPaymentSignature(JsonNode payload) {
        // Check if the payload is null or does not contain the expected field
        if (payload == null || !payload.has("signature_key")) {
            return false;
        }

        try {
            // Extract relevant fields from the payload in the correct order
            String signatureHeader = payload.get("signature_key").asText();

            String orderId = payload.get("order_id").asText();
            String statusCode = payload.get("status_code").asText();
            String grossAmount = payload.get("gross_amount").asText();

            String dataToHash = orderId + statusCode + grossAmount + serverKey;
            String generatedSignature = DigestUtils.sha512Hex(dataToHash);

            return generatedSignature.equals(signatureHeader);

        } catch (Exception e) {
            // Log the error
            log.error("Error verifying Midtrans signature: {}", e.getMessage());
            return false;
        }
    }
}
