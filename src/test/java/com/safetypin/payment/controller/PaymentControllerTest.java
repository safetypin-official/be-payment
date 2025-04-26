package com.safetypin.payment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.safetypin.payment.service.MidtransService;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private MidtransService midtransService;

    @InjectMocks
    private PaymentController paymentController;

    private Map<String, Object> requestBody;
    private final String subscriptionId = "sub-test-123";

    @BeforeEach
    void setUp() {
        requestBody = new HashMap<>();
    }

    // --- createPayment Tests ---

    @Test
    void createPayment_Success() throws JSONException { // Add throws JSONException
        requestBody.put("amount", "50000.00");
        JSONObject mockSnapResponse = new JSONObject();
        mockSnapResponse.put("token", "snap-token-success");

        try {
            when(midtransService.createSnapToken(anyString(), eq(50000.00))).thenReturn(mockSnapResponse); // Use eq()
                                                                                                           // for amount

            ResponseEntity<String> response = paymentController.createPayment(requestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("snap-token-success"));
            verify(midtransService).createSnapToken(anyString(), eq(50000.00));
        } catch (Exception e) {
            // This won't be reached in the test, but handles the method signature
            // requirement
            throw new RuntimeException("Test failed", e);
        }
    }

    @Test
    void createPayment_InvalidAmountFormat() throws Exception {
        requestBody.put("amount", "invalid-amount");

        ResponseEntity<String> response = paymentController.createPayment(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid amount format", response.getBody());
        verify(midtransService, never()).createSnapToken(anyString(), anyDouble());
    }

    @Test
    void createPayment_ZeroAmount() throws Exception {
        requestBody.put("amount", "0");

        ResponseEntity<String> response = paymentController.createPayment(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid amount", response.getBody());
        verify(midtransService, never()).createSnapToken(anyString(), anyDouble());
    }

    @Test
    void createPayment_NegativeAmount() throws Exception {
        requestBody.put("amount", "-100.0");

        ResponseEntity<String> response = paymentController.createPayment(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid amount", response.getBody());
        verify(midtransService, never()).createSnapToken(anyString(), anyDouble());
    }

    @Test
    void createPayment_MissingAmount() throws Exception {
        // Amount missing, defaults to "0"
        ResponseEntity<String> response = paymentController.createPayment(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid amount", response.getBody()); // Because default "0" is invalid
        verify(midtransService, never()).createSnapToken(anyString(), anyDouble());
    }

    @Test
    void createPayment_ServiceThrowsException() throws Exception {
        requestBody.put("amount", "50000.00");
        when(midtransService.createSnapToken(anyString(), eq(50000.00))) // Use eq() for amount
                .thenThrow(new RuntimeException("Midtrans service error"));

        ResponseEntity<String> response = paymentController.createPayment(requestBody);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        // Assert exact body content
        assertEquals("Failed to create payment token: Midtrans service error", response.getBody());
        verify(midtransService).createSnapToken(anyString(), eq(50000.00));
    }

    // --- createSubscription Tests ---

    @Test
    void createSubscription_Success() {
        requestBody.put("token", "cc-token-123");
        requestBody.put("payment_type", "credit_card");
        // Fix JSON string escaping and variable name
        ResponseEntity<String> mockServiceResponse = ResponseEntity
                .ok("{\"id\":\"" + subscriptionId + "\", \"status\":\"pending\"}");

        when(midtransService.createSubscription(eq(requestBody))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.createSubscription(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockServiceResponse.getBody(), response.getBody());
        verify(midtransService).createSubscription(eq(requestBody));
    }

    @Test
    void createSubscription_MissingToken() {
        requestBody.put("payment_type", "credit_card"); // Missing token

        ResponseEntity<String> response = paymentController.createSubscription(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required fields: token and payment_type", response.getBody());
        verify(midtransService, never()).createSubscription(anyMap());
    }

    @Test
    void createSubscription_MissingPaymentType() {
        requestBody.put("token", "cc-token-123"); // Missing payment_type

        ResponseEntity<String> response = paymentController.createSubscription(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required fields: token and payment_type", response.getBody());
        verify(midtransService, never()).createSubscription(anyMap());
    }

    @Test
    void createSubscription_ServiceReturnsError() {
        requestBody.put("token", "cc-token-error");
        requestBody.put("payment_type", "credit_card");
        ResponseEntity<String> mockServiceResponse = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Service failure");

        when(midtransService.createSubscription(eq(requestBody))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.createSubscription(requestBody);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Service failure", response.getBody());
        verify(midtransService).createSubscription(eq(requestBody));
    }

    // --- getSubscription Tests ---

    @Test
    void getSubscription_Success() {
        // Fix JSON string escaping
        ResponseEntity<String> mockServiceResponse = ResponseEntity
                .ok("{\"id\":\"" + subscriptionId + "\", \"status\":\"active\"}");
        when(midtransService.getSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.getSubscription(subscriptionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockServiceResponse.getBody(), response.getBody());
        verify(midtransService).getSubscription(eq(subscriptionId));
    }

    @Test
    void getSubscription_NullId() {
        ResponseEntity<String> response = paymentController.getSubscription(null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).getSubscription(any());
    }

    @Test
    void getSubscription_EmptyId() {
        ResponseEntity<String> response = paymentController.getSubscription("  ");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).getSubscription(any());
    }

    @Test
    void getSubscription_ServiceReturnsError() {
        ResponseEntity<String> mockServiceResponse = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not Found");
        when(midtransService.getSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.getSubscription(subscriptionId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody());
        verify(midtransService).getSubscription(eq(subscriptionId));
    }

    // --- disableSubscription Tests ---

    @Test
    void disableSubscription_Success() {
        // Fix JSON string escaping
        ResponseEntity<String> mockServiceResponse = ResponseEntity
                .ok("{\"id\":\"" + subscriptionId + "\", \"status\":\"disabled\"}");
        when(midtransService.disableSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.disableSubscription(subscriptionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockServiceResponse.getBody(), response.getBody());
        verify(midtransService).disableSubscription(eq(subscriptionId));
    }

    @Test
    void disableSubscription_NullId() {
        ResponseEntity<String> response = paymentController.disableSubscription(null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).disableSubscription(any());
    }

    @Test
    void disableSubscription_EmptyId() {
        ResponseEntity<String> response = paymentController.disableSubscription("");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).disableSubscription(any());
    }

    @Test
    void disableSubscription_ServiceReturnsError() {
        ResponseEntity<String> mockServiceResponse = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Disable failed");
        when(midtransService.disableSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.disableSubscription(subscriptionId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Disable failed", response.getBody());
        verify(midtransService).disableSubscription(eq(subscriptionId));
    }

    // --- cancelSubscription Tests ---

    @Test
    void cancelSubscription_Success() {
        // Fix JSON string escaping
        ResponseEntity<String> mockServiceResponse = ResponseEntity
                .ok("{\"id\":\"" + subscriptionId + "\", \"status\":\"cancelled\"}");
        when(midtransService.cancelSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.cancelSubscription(subscriptionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockServiceResponse.getBody(), response.getBody());
        verify(midtransService).cancelSubscription(eq(subscriptionId));
    }

    @Test
    void cancelSubscription_NullId() {
        ResponseEntity<String> response = paymentController.cancelSubscription(null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).cancelSubscription(any());
    }

    @Test
    void cancelSubscription_EmptyId() {
        ResponseEntity<String> response = paymentController.cancelSubscription(" ");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).cancelSubscription(any());
    }

    @Test
    void cancelSubscription_ServiceReturnsError() {
        ResponseEntity<String> mockServiceResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Cancel failed");
        when(midtransService.cancelSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.cancelSubscription(subscriptionId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Cancel failed", response.getBody());
        verify(midtransService).cancelSubscription(eq(subscriptionId));
    }

    // --- enableSubscription Tests ---

    @Test
    void enableSubscription_Success() {
        // Fix JSON string escaping
        ResponseEntity<String> mockServiceResponse = ResponseEntity
                .ok("{\"id\":\"" + subscriptionId + "\", \"status\":\"active\"}");
        when(midtransService.enableSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.enableSubscription(subscriptionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockServiceResponse.getBody(), response.getBody());
        verify(midtransService).enableSubscription(eq(subscriptionId));
    }

    @Test
    void enableSubscription_NullId() {
        ResponseEntity<String> response = paymentController.enableSubscription(null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).enableSubscription(any());
    }

    @Test
    void enableSubscription_EmptyId() {
        ResponseEntity<String> response = paymentController.enableSubscription("\t");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).enableSubscription(any());
    }

    @Test
    void enableSubscription_ServiceReturnsError() {
        ResponseEntity<String> mockServiceResponse = ResponseEntity.status(HttpStatus.CONFLICT).body("Already active");
        when(midtransService.enableSubscription(eq(subscriptionId))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.enableSubscription(subscriptionId);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Already active", response.getBody());
        verify(midtransService).enableSubscription(eq(subscriptionId));
    }

    // --- updateSubscription Tests ---

    @Test
    void updateSubscription_Success() {
        Map<String, Object> updateDetails = Map.of("name", "New Name");
        // Fix JSON string escaping
        ResponseEntity<String> mockServiceResponse = ResponseEntity
                .ok("{\"id\":\"" + subscriptionId + "\", \"name\":\"New Name\"}");
        when(midtransService.updateSubscription(eq(subscriptionId), eq(updateDetails))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.updateSubscription(subscriptionId, updateDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockServiceResponse.getBody(), response.getBody());
        verify(midtransService).updateSubscription(eq(subscriptionId), eq(updateDetails));
    }

    @Test
    void updateSubscription_NullId() {
        Map<String, Object> updateDetails = Map.of("name", "New Name");
        ResponseEntity<String> response = paymentController.updateSubscription(null, updateDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).updateSubscription(any(), any());
    }

    @Test
    void updateSubscription_EmptyId() {
        Map<String, Object> updateDetails = Map.of("name", "New Name");
        ResponseEntity<String> response = paymentController.updateSubscription(" ", updateDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Subscription ID cannot be empty", response.getBody());
        verify(midtransService, never()).updateSubscription(any(), any());
    }

    @Test
    void updateSubscription_NullDetails() {
        ResponseEntity<String> response = paymentController.updateSubscription(subscriptionId, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Update details cannot be empty", response.getBody());
        verify(midtransService, never()).updateSubscription(any(), any());
    }

    @Test
    void updateSubscription_EmptyDetails() {
        Map<String, Object> updateDetails = new HashMap<>();
        ResponseEntity<String> response = paymentController.updateSubscription(subscriptionId, updateDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Update details cannot be empty", response.getBody());
        verify(midtransService, never()).updateSubscription(any(), any());
    }

    @Test
    void updateSubscription_ServiceReturnsError() {
        Map<String, Object> updateDetails = Map.of("name", "New Name");
        ResponseEntity<String> mockServiceResponse = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not Found");
        when(midtransService.updateSubscription(eq(subscriptionId), eq(updateDetails))).thenReturn(mockServiceResponse);

        ResponseEntity<String> response = paymentController.updateSubscription(subscriptionId, updateDetails);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody());
        verify(midtransService).updateSubscription(eq(subscriptionId), eq(updateDetails));
    }
}
