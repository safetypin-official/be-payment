package com.safetypin.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MidtransConfig {

    @Value("${midtrans.server.key}")
    private String serverKey;

    @Value("${midtrans.client.key}")
    private String clientKey;

    @Value("${midtrans.api.url}")
    private String apiUrl;

    @Value("${midtrans.subscription.api.url}")
    private String subscriptionApiUrl;

    @Value("${midtrans.gopay.callback.url}")
    private String gopayCallbackUrl; // Add GoPay callback URL field

    @Value("${midtrans.get.pay.account.api.url}")
    private String getPayAccountApiUrl; // Add Get Pay Account API URL field

    // Getters
    public String getServerKey() {
        return serverKey;
    }

    public String getClientKey() {
        return clientKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getSubscriptionApiUrl() {
        return subscriptionApiUrl;
    }

    public String getGopayCallbackUrl() { // Add getter
        return gopayCallbackUrl;
    }

    public String getGetPayAccountApiUrl() { // Add getter
        return getPayAccountApiUrl;
    }
}
