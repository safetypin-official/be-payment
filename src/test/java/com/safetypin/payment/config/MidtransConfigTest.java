package com.safetypin.payment.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "midtrans.server.key=test-server-key",
        "midtrans.client.key=test-client-key",
        "midtrans.api.url=http://test.api.url",
        "midtrans.subscription.api.url=http://test.sub.api.url",
        "midtrans.gopay.callback.url=http://test.gopay.callback.url",
        "midtrans.get.pay.account.api.url=http://test.getpay.api.url"
})
class MidtransConfigTest {

    @Autowired
    private MidtransConfig midtransConfig;

    @Test
    void testGetters() {
        assertNotNull(midtransConfig.getServerKey());
        assertEquals("test-server-key", midtransConfig.getServerKey());

        assertNotNull(midtransConfig.getClientKey());
        assertEquals("test-client-key", midtransConfig.getClientKey());

        assertNotNull(midtransConfig.getApiUrl());
        assertEquals("http://test.api.url", midtransConfig.getApiUrl());

        assertNotNull(midtransConfig.getSubscriptionApiUrl());
        assertEquals("http://test.sub.api.url", midtransConfig.getSubscriptionApiUrl());

        assertNotNull(midtransConfig.getGopayCallbackUrl());
        assertEquals("http://test.gopay.callback.url", midtransConfig.getGopayCallbackUrl());

        assertNotNull(midtransConfig.getGetPayAccountApiUrl());
        assertEquals("http://test.getpay.api.url", midtransConfig.getGetPayAccountApiUrl());
    }
}
