package com.safetypin.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midtrans.service.MidtransSnapApi;
import com.safetypin.payment.config.MidtransConfig;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Add this annotation
class MidtransServiceTest {

        @Mock
        private MidtransConfig midtransConfig;

        @Mock
        private RestTemplate restTemplate;

        @Mock
        private ObjectMapper objectMapper;

        // We need to mock the MidtransSnapApi interface, not the implementation
        // directly
        @Mock
        private MidtransSnapApi midtransSnapApi;

        @InjectMocks
        private MidtransService midtransService;

        @Captor
        private ArgumentCaptor<HttpEntity<Map<String, Object>>> httpEntityCaptor;

        @Captor
        private ArgumentCaptor<Map<String, Object>> snapApiBodyCaptor;

        private final String serverKey = "test-server-key";
        private final String clientKey = "test-client-key";
        private final String subscriptionApiUrl = "http://localhost/v1/subscriptions";
        private final String gopayCallbackUrl = "http://localhost/callback";
        private final String getPayAccountApiUrl = "http://localhost/v2/pay/account/";
        private final String orderId = "test-order-123";
        private final double grossAmount = 15000.0;
        private final String subscriptionId = "sub-test-123";

        @BeforeEach
        void setUp() {
                // Mock config values
                when(midtransConfig.getServerKey()).thenReturn(serverKey);
                when(midtransConfig.getClientKey()).thenReturn(clientKey);
                when(midtransConfig.getSubscriptionApiUrl()).thenReturn(subscriptionApiUrl);
                when(midtransConfig.getGopayCallbackUrl()).thenReturn(gopayCallbackUrl);
                when(midtransConfig.getGetPayAccountApiUrl()).thenReturn(getPayAccountApiUrl);

                // --- Mocking MidtransSnapApi instantiation ---
                // We need to mock the static Config.builder() and the MidtransSnapApiImpl
                // constructor
                // This is complex. A simpler approach for testing is to inject the mock
                // MidtransSnapApi
                // directly, bypassing the @PostConstruct initialization logic during the test.
                // We can achieve this by setting the field directly after @InjectMocks has run.
                // Or, ensure the @PostConstruct logic uses the mocked config correctly.

                // Let's try mocking the static builder and constructor call within
                // @PostConstruct
                // This requires mockito-inline dependency if not already present.
                // Assuming mockito-inline is available or we adjust the setup.

                // For simplicity in this example, let's assume @InjectMocks handles the
                // dependencies,
                // and we'll mock the behavior of the injected midtransSnapApi mock directly in
                // tests.
                // The @PostConstruct logic itself won't be explicitly tested here unless we use
                // PowerMock
                // or more advanced Mockito features for static/constructor mocking.

                // Re-initialize MidtransService to trigger @PostConstruct with mocks
                // (This might be redundant with @InjectMocks depending on JUnit/Mockito version
                // behavior)

                // Set the mock snap api directly to bypass complex @PostConstruct mocking
                // This is a common pattern when dealing with complex initialization.
                midtransService = new MidtransService(midtransConfig, restTemplate, objectMapper);
                // Manually set the mock snap api instance after construction
                // Use reflection or make a setter if needed, or adjust constructor/init logic
                // for testability.
                // For now, assume the tests will mock the 'midtransSnapApi' field's methods
                // directly.
                // We will mock the `createTransaction` method in the specific test case.
        }

        private HttpHeaders expectedHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
                String auth = serverKey + ":";
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
                return headers;
        }

        // --- createSnapToken Tests ---

        @Test
        void createSnapToken_Success() throws Exception {
                JSONObject mockResponse = new JSONObject();
                mockResponse.put("token", "snap-token-123");
                mockResponse.put("redirect_url", "http://redirect.url");

                // Mock the behavior of the injected MidtransSnapApi mock
                when(midtransSnapApi.createTransaction(snapApiBodyCaptor.capture())).thenReturn(mockResponse);

                // Re-initialize service or inject mock snap api properly if needed
                // This setup depends heavily on how @PostConstruct interacts with @Mock in your
                // specific setup.
                // A common workaround is to have a package-private setter for the api or use
                // reflection.
                // Let's assume the @InjectMocks setup correctly provides the mock
                // midtransSnapApi.
                midtransService = new MidtransService(midtransConfig, restTemplate, objectMapper);
                // Manually inject the mock snap API instance into the service instance being
                // tested
                // This bypasses the @PostConstruct initialization issue for the snap API
                // dependency
                java.lang.reflect.Field snapApiField = MidtransService.class.getDeclaredField("midtransSnapApi");
                snapApiField.setAccessible(true);
                snapApiField.set(midtransService, midtransSnapApi);

                JSONObject result = midtransService.createSnapToken(orderId, grossAmount);

                assertNotNull(result);
                assertEquals("snap-token-123", result.getString("token"));
                assertEquals("http://redirect.url", result.getString("redirect_url"));

                verify(midtransSnapApi).createTransaction(any());
                Map<String, Object> capturedBody = snapApiBodyCaptor.getValue();
                assertNotNull(capturedBody);
                assertTrue(capturedBody.containsKey("transaction_details"));
                @SuppressWarnings("unchecked")
                Map<String, Object> transactionDetails = (Map<String, Object>) capturedBody.get("transaction_details");
                assertEquals(orderId, transactionDetails.get("order_id"));
                assertEquals(grossAmount, transactionDetails.get("gross_amount"));
        }

        @Test
        void createSnapToken_MidtransApiException() throws Exception {
                // Mock the behavior of the injected MidtransSnapApi mock
                when(midtransSnapApi.createTransaction(any())).thenThrow(new RuntimeException("Midtrans API Error"));

                // Inject the mock snap API instance
                java.lang.reflect.Field snapApiField = MidtransService.class.getDeclaredField("midtransSnapApi");
                snapApiField.setAccessible(true);
                snapApiField.set(midtransService, midtransSnapApi);

                Exception exception = assertThrows(RuntimeException.class, () -> {
                        midtransService.createSnapToken(orderId, grossAmount);
                });

                assertEquals("Midtrans API Error", exception.getMessage());
                verify(midtransSnapApi).createTransaction(any());
        }

        // --- createSubscription Tests ---

        private Map<String, Object> createBasicSubscriptionRequest(String paymentType, String token) {
                Map<String, Object> request = new HashMap<>();
                request.put("name", "Test Subscription");
                request.put("payment_type", paymentType);
                request.put("token", token);
                // Add customer details for edge case testing later
                Map<String, String> customerDetails = new HashMap<>();
                customerDetails.put("first_name", "Test");
                customerDetails.put("last_name", "User");
                customerDetails.put("email", "test.user@example.com");
                customerDetails.put("phone", "081234567890");
                request.put("customer_details", customerDetails);
                return request;
        }

        @Test
        void createSubscription_CreditCard_EnableSuccess() throws Exception {
                Map<String, Object> request = createBasicSubscriptionRequest("credit_card", "cc-token-123");
                String createResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"pending\"}";
                String enableResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"active\"}";
                ResponseEntity<String> createResponse = ResponseEntity.ok(createResponseBody);
                ResponseEntity<String> enableResponse = ResponseEntity.ok(enableResponseBody);

                when(restTemplate.postForEntity(eq(subscriptionApiUrl), httpEntityCaptor.capture(), eq(String.class))) // Use
                                                                                                                       // eq()
                                                                                                                       // for
                                                                                                                       // URL
                                                                                                                       // and
                                                                                                                       // Class
                                .thenReturn(createResponse);
                when(objectMapper.readValue(createResponseBody, Map.class))
                                .thenReturn(Map.of("id", subscriptionId, "status", "pending"));
                when(restTemplate.exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST), // Wrap URL and Method with eq()
                                any(HttpEntity.class), eq(String.class))) // Wrap Class with eq()
                                .thenReturn(enableResponse);

                ResponseEntity<String> response = midtransService.createSubscription(request);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(createResponseBody, response.getBody()); // Returns original create response

                // Verify Create Call
                verify(restTemplate).postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                     // eq()
                                                                                                                     // for
                                                                                                                     // clarity
                                                                                                                     // on
                                                                                                                     // URL
                HttpEntity<Map<String, Object>> capturedCreateEntity = httpEntityCaptor.getValue();
                assertEquals(expectedHeaders(), capturedCreateEntity.getHeaders());
                Map<String, Object> capturedCreateBody = capturedCreateEntity.getBody();
                assertEquals("Test Subscription", capturedCreateBody.get("name"));
                assertEquals("10000", capturedCreateBody.get("amount")); // Fixed amount
                assertEquals("IDR", capturedCreateBody.get("currency"));
                assertEquals("credit_card", capturedCreateBody.get("payment_type"));
                assertEquals("cc-token-123", capturedCreateBody.get("token"));
                assertTrue(capturedCreateBody.containsKey("schedule"));
                assertTrue(capturedCreateBody.containsKey("metadata"));
                assertTrue(capturedCreateBody.containsKey("customer_details")); // Verify customer details included
                assertFalse(capturedCreateBody.containsKey("gopay")); // Ensure gopay details not included

                // Verify ObjectMapper Call
                verify(objectMapper).readValue(createResponseBody, Map.class);

                // Verify Enable Call
                verify(restTemplate).exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class), eq(String.class)); // Use eq() for clarity
        }

        @Test
        void createSubscription_GoPay_EnableSuccess() throws Exception {
                Map<String, Object> request = createBasicSubscriptionRequest("gopay", "gopay-acc-id");
                // Remove customer details for this specific gopay test variation if needed, or
                // keep it
                request.remove("customer_details"); // Example: test without customer details

                String createResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"pending\"}";
                String enableResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"active\"}";
                ResponseEntity<String> createResponse = ResponseEntity.ok(createResponseBody);
                ResponseEntity<String> enableResponse = ResponseEntity.ok(enableResponseBody);

                when(restTemplate.postForEntity(eq(subscriptionApiUrl), httpEntityCaptor.capture(), eq(String.class))) // Use
                                                                                                                       // eq()
                                                                                                                       // for
                                                                                                                       // URL
                                                                                                                       // and
                                                                                                                       // Class
                                .thenReturn(createResponse);
                when(objectMapper.readValue(createResponseBody, Map.class))
                                .thenReturn(Map.of("id", subscriptionId, "status", "pending"));
                when(restTemplate.exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST), // Wrap URL and Method with eq()
                                any(HttpEntity.class), eq(String.class))) // Wrap Class with eq()
                                .thenReturn(enableResponse);

                ResponseEntity<String> response = midtransService.createSubscription(request);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(createResponseBody, response.getBody());

                // Verify Create Call
                verify(restTemplate).postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                     // eq()
                                                                                                                     // for
                                                                                                                     // clarity
                                                                                                                     // on
                                                                                                                     // URL
                HttpEntity<Map<String, Object>> capturedCreateEntity = httpEntityCaptor.getValue();
                Map<String, Object> capturedCreateBody = capturedCreateEntity.getBody();
                assertEquals("gopay", capturedCreateBody.get("payment_type"));
                assertEquals("gopay-acc-id", capturedCreateBody.get("token"));
                assertTrue(capturedCreateBody.containsKey("gopay")); // Ensure gopay details ARE included
                @SuppressWarnings("unchecked")
                Map<String, Object> gopayDetails = (Map<String, Object>) capturedCreateBody.get("gopay");
                assertEquals(gopayCallbackUrl, gopayDetails.get("callback_url"));
                assertFalse(capturedCreateBody.containsKey("customer_details")); // Verify customer details NOT included

                // Verify ObjectMapper Call
                verify(objectMapper).readValue(createResponseBody, Map.class);

                // Verify Enable Call
                verify(restTemplate).exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class), eq(String.class)); // Use eq() for clarity
        }

        @Test
        void createSubscription_EnableFails() throws Exception {
                Map<String, Object> request = createBasicSubscriptionRequest("credit_card", "cc-token-fail");
                String createResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"pending\"}";
                ResponseEntity<String> createResponse = ResponseEntity.ok(createResponseBody);
                ResponseEntity<String> enableResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("Enable failed");

                when(restTemplate.postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                  // eq()
                                                                                                                  // for
                                                                                                                  // URL
                                                                                                                  // and
                                                                                                                  // Class
                                .thenReturn(createResponse);
                when(objectMapper.readValue(createResponseBody, Map.class))
                                .thenReturn(Map.of("id", subscriptionId, "status", "pending"));
                when(restTemplate.exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST), // Wrap URL and Method with eq()
                                any(HttpEntity.class), eq(String.class))) // Wrap Class with eq()
                                .thenReturn(enableResponse); // Simulate enable failure

                ResponseEntity<String> response = midtransService.createSubscription(request);

                assertEquals(HttpStatus.OK, response.getStatusCode()); // Still returns OK from create
                assertEquals(createResponseBody, response.getBody());

                // Verify calls happened
                verify(restTemplate).postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class));
                verify(objectMapper).readValue(createResponseBody, Map.class);
                verify(restTemplate).exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class), eq(String.class));
                // Add log verification if logger is mocked
        }

        @Test
        void createSubscription_EnableThrowsException() throws Exception {
                Map<String, Object> request = createBasicSubscriptionRequest("credit_card", "cc-token-ex");
                String createResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"pending\"}";
                ResponseEntity<String> createResponse = ResponseEntity.ok(createResponseBody);

                when(restTemplate.postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                  // eq()
                                                                                                                  // for
                                                                                                                  // URL
                                                                                                                  // and
                                                                                                                  // Class
                                .thenReturn(createResponse);
                when(objectMapper.readValue(createResponseBody, Map.class))
                                .thenReturn(Map.of("id", subscriptionId, "status", "pending"));
                when(restTemplate.exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST), // Wrap URL and Method with eq()
                                any(HttpEntity.class), eq(String.class))) // Wrap Class with eq()
                                .thenThrow(new RestClientException("Enable network error")); // Simulate enable
                                                                                             // exception

                ResponseEntity<String> response = midtransService.createSubscription(request);

                assertEquals(HttpStatus.OK, response.getStatusCode()); // Still returns OK from create
                assertEquals(createResponseBody, response.getBody());

                // Verify calls happened
                verify(restTemplate).postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class));
                verify(objectMapper).readValue(createResponseBody, Map.class);
                verify(restTemplate).exchange(eq(subscriptionApiUrl + "/" + subscriptionId + "/enable"),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class), eq(String.class));
                // Add log verification if logger is mocked
        }

        @Test
        void createSubscription_IdExtractionFails() throws Exception {
                Map<String, Object> request = createBasicSubscriptionRequest("credit_card", "cc-token-no-id");
                String createResponseBody = "{\"message\":\"Created but no ID\"}"; // No 'id' field
                ResponseEntity<String> createResponse = ResponseEntity.ok(createResponseBody);

                when(restTemplate.postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                  // eq()
                                                                                                                  // for
                                                                                                                  // URL
                                                                                                                  // and
                                                                                                                  // Class
                                .thenReturn(createResponse);
                // Simulate ObjectMapper returning a map without 'id'
                when(objectMapper.readValue(createResponseBody, Map.class))
                                .thenReturn(Map.of("message", "Created but no ID"));

                ResponseEntity<String> response = midtransService.createSubscription(request);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(createResponseBody, response.getBody());

                // Verify create call and ObjectMapper call happened
                verify(restTemplate).postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class)); // Wrap
                                                                                                                     // raw
                                                                                                                     // values
                                                                                                                     // with
                                                                                                                     // eq()
                verify(objectMapper).readValue(createResponseBody, Map.class);
                // Verify enable call DID NOT happen
                verify(restTemplate, never()).exchange(contains("/enable"), eq(HttpMethod.POST), any(HttpEntity.class),
                                eq(String.class));
                // Add log verification if logger is mocked
        }

        @Test
        void createSubscription_ObjectMapperFailsDuringEnable() throws Exception {
                Map<String, Object> request = createBasicSubscriptionRequest("credit_card", "cc-token-om-fail");
                String createResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"pending\"}";
                ResponseEntity<String> createResponse = ResponseEntity.ok(createResponseBody);

                when(restTemplate.postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                  // eq()
                                                                                                                  // for
                                                                                                                  // URL
                                                                                                                  // and
                                                                                                                  // Class
                                .thenReturn(createResponse);
                // Simulate ObjectMapper throwing exception when parsing create response
                when(objectMapper.readValue(createResponseBody, Map.class))
                                .thenThrow(new JsonParseException(null, "Parsing failed"));

                ResponseEntity<String> response = midtransService.createSubscription(request);

                assertEquals(HttpStatus.OK, response.getStatusCode()); // Still returns OK from create
                assertEquals(createResponseBody, response.getBody());

                // Verify create call and ObjectMapper call happened
                verify(restTemplate).postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class)); // Wrap
                                                                                                                     // raw
                                                                                                                     // values
                                                                                                                     // with
                                                                                                                     // eq()
                verify(objectMapper).readValue(createResponseBody, Map.class);
                // Verify enable call DID NOT happen
                verify(restTemplate, never()).exchange(contains("/enable"), eq(HttpMethod.POST), any(HttpEntity.class),
                                eq(String.class));
                // Add log verification if logger is mocked
        }

        @Test
        void createSubscription_CreateFails() throws Exception {
                Map<String, Object> request = createBasicSubscriptionRequest("credit_card", "cc-token-create-fail");
                when(restTemplate.postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                  // eq()
                                                                                                                  // for
                                                                                                                  // URL
                                                                                                                  // and
                                                                                                                  // Class
                                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid request"));

                ResponseEntity<String> response = midtransService.createSubscription(request);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertTrue(response.getBody().contains("Failed to create subscription"));
                assertTrue(response.getBody().contains("Invalid request"));

                verify(restTemplate).postForEntity(eq(subscriptionApiUrl), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                     // eq()
                                                                                                                     // for
                                                                                                                     // clarity
                verify(objectMapper, never()).readValue(anyString(), eq(Map.class));
                verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                eq(String.class));
        }

        // --- getSubscription Tests ---

        @Test
        void getSubscription_Success() {
                String getResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"active\"}";
                ResponseEntity<String> getResponse = ResponseEntity.ok(getResponseBody);
                String url = subscriptionApiUrl + "/" + subscriptionId;

                when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(String.class))) // Use
                                                                                                                       // eq()
                                .thenReturn(getResponse);

                ResponseEntity<String> response = midtransService.getSubscription(subscriptionId);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(getResponseBody, response.getBody());

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                     // eq()
                HttpEntity<?> capturedEntity = httpEntityCaptor.getValue();
                assertEquals(expectedHeaders(), capturedEntity.getHeaders());
                assertNull(capturedEntity.getBody()); // No body for GET
        }

        @Test
        void getSubscription_NotFound() {
                String url = subscriptionApiUrl + "/" + subscriptionId;
                when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                  // eq()
                                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND,
                                                "Subscription not found"));

                ResponseEntity<String> response = midtransService.getSubscription(subscriptionId);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()); // Or map specific errors
                assertTrue(response.getBody().contains("Failed to get subscription"));
                assertTrue(response.getBody().contains("Subscription not found"));

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                     // eq()
        }

        @Test
        void getSubscription_RestClientException() {
                String url = subscriptionApiUrl + "/" + subscriptionId;
                when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                  // eq()
                                .thenThrow(new RestClientException("Network error"));

                ResponseEntity<String> response = midtransService.getSubscription(subscriptionId);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertTrue(response.getBody().contains("Failed to get subscription"));
                assertTrue(response.getBody().contains("Network error"));

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                     // eq()
        }

        // --- disableSubscription Tests ---

        @Test
        void disableSubscription_Success() {
                String disableResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"disabled\"}";
                ResponseEntity<String> disableResponse = ResponseEntity.ok(disableResponseBody);
                String url = subscriptionApiUrl + "/" + subscriptionId + "/disable";

                when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), httpEntityCaptor.capture(), eq(String.class))) // Use
                                                                                                                        // eq()
                                .thenReturn(disableResponse);

                ResponseEntity<String> response = midtransService.disableSubscription(subscriptionId);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(disableResponseBody, response.getBody());

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                      // eq()
                HttpEntity<?> capturedEntity = httpEntityCaptor.getValue();
                assertEquals(expectedHeaders(), capturedEntity.getHeaders());
                assertNull(capturedEntity.getBody()); // No body for disable POST
        }

        @Test
        void disableSubscription_Failure() {
                String url = subscriptionApiUrl + "/" + subscriptionId + "/disable";
                when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                   // eq()
                                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Cannot disable"));

                ResponseEntity<String> response = midtransService.disableSubscription(subscriptionId);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertTrue(response.getBody().contains("Failed to disable subscription"));
                assertTrue(response.getBody().contains("Cannot disable"));

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                      // eq()
        }

        // --- cancelSubscription Tests ---

        @Test
        void cancelSubscription_Success() {
                String cancelResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"cancelled\"}";
                ResponseEntity<String> cancelResponse = ResponseEntity.ok(cancelResponseBody);
                String url = subscriptionApiUrl + "/" + subscriptionId + "/cancel";

                when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), httpEntityCaptor.capture(), eq(String.class))) // Use
                                                                                                                        // eq()
                                .thenReturn(cancelResponse);

                ResponseEntity<String> response = midtransService.cancelSubscription(subscriptionId);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(cancelResponseBody, response.getBody());

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                      // eq()
                HttpEntity<?> capturedEntity = httpEntityCaptor.getValue();
                assertEquals(expectedHeaders(), capturedEntity.getHeaders());
                assertNull(capturedEntity.getBody()); // No body for cancel POST
        }

        @Test
        void cancelSubscription_Failure() {
                String url = subscriptionApiUrl + "/" + subscriptionId + "/cancel";
                when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                   // eq()
                                .thenThrow(new RestClientException("Cancel network error"));

                ResponseEntity<String> response = midtransService.cancelSubscription(subscriptionId);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertTrue(response.getBody().contains("Failed to cancel subscription"));
                assertTrue(response.getBody().contains("Cancel network error"));

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                      // eq()
        }

        // --- enableSubscription Tests (Public Method) ---

        @Test
        void enableSubscription_Public_Success() {
                String enableResponseBody = "{\"id\":\"" + subscriptionId + "\", \"status\":\"active\"}";
                ResponseEntity<String> enableResponse = ResponseEntity.ok(enableResponseBody);
                String url = subscriptionApiUrl + "/" + subscriptionId + "/enable";

                when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), httpEntityCaptor.capture(), eq(String.class))) // Use
                                                                                                                        // eq()
                                .thenReturn(enableResponse);

                // Call the public method
                ResponseEntity<String> response = midtransService.enableSubscription(subscriptionId);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(enableResponseBody, response.getBody());

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                      // eq()
                HttpEntity<?> capturedEntity = httpEntityCaptor.getValue();
                assertEquals(expectedHeaders(), capturedEntity.getHeaders());
                assertNull(capturedEntity.getBody()); // No body for enable POST
        }

        @Test
        void enableSubscription_Public_Failure() {
                String url = subscriptionApiUrl + "/" + subscriptionId + "/enable";
                when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                   // eq()
                                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Already active"));

                // Call the public method
                ResponseEntity<String> response = midtransService.enableSubscription(subscriptionId);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertTrue(response.getBody().contains("Failed to enable subscription"));
                assertTrue(response.getBody().contains("Already active"));

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                      // eq()
        }

        // --- updateSubscription Tests ---

        @Test
        void updateSubscription_Success() {
                Map<String, Object> updateDetails = Map.of("name", "Updated Name", "token", "new-cc-token");
                String updateResponseBody = "{\"id\":\"" + subscriptionId + "\", \"name\":\"Updated Name\"}";
                ResponseEntity<String> updateResponse = ResponseEntity.ok(updateResponseBody);
                String url = subscriptionApiUrl + "/" + subscriptionId;

                when(restTemplate.exchange(eq(url), eq(HttpMethod.PATCH), httpEntityCaptor.capture(), eq(String.class))) // Use
                                                                                                                         // eq()
                                .thenReturn(updateResponse);

                ResponseEntity<String> response = midtransService.updateSubscription(subscriptionId, updateDetails);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(updateResponseBody, response.getBody());

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                       // eq()
                HttpEntity<Map<String, Object>> capturedEntity = httpEntityCaptor.getValue();
                assertEquals(expectedHeaders(), capturedEntity.getHeaders());
                assertEquals(updateDetails, capturedEntity.getBody()); // Body contains update details
        }

        @Test
        void updateSubscription_Failure() {
                Map<String, Object> updateDetails = Map.of("name", "Updated Name");
                String url = subscriptionApiUrl + "/" + subscriptionId;
                when(restTemplate.exchange(eq(url), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class))) // Use
                                                                                                                    // eq()
                                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND,
                                                "Subscription not found"));

                ResponseEntity<String> response = midtransService.updateSubscription(subscriptionId, updateDetails);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertTrue(response.getBody().contains("Failed to update subscription"));
                assertTrue(response.getBody().contains("Subscription not found"));

                verify(restTemplate).exchange(eq(url), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)); // Use
                                                                                                                       // eq()
        }

        // --- Test @PostConstruct indirectly via header creation ---
        // The createMidtransHeaders method relies on config loaded during init.
        // We already verify headers in most tests, implicitly testing config loading.

        @Test
        void createMidtransHeaders_UsesCorrectKey() {
                // This test primarily verifies the helper method used in other tests,
                // indirectly confirming that the config mock provides the key correctly.
                HttpHeaders headers = expectedHeaders(); // Uses the mocked serverKey from setUp

                String expectedAuthPrefix = "Basic ";
                assertTrue(headers.containsKey("Authorization"));
                String authHeader = headers.getFirst("Authorization");
                assertTrue(authHeader.startsWith(expectedAuthPrefix));

                String encodedPart = authHeader.substring(expectedAuthPrefix.length());
                String decoded = new String(Base64.getDecoder().decode(encodedPart));
                assertEquals(serverKey + ":", decoded); // Verify the correct key is used
        }
}
