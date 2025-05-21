package com.safetypin.payment.config;

import com.midtrans.Config;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "midtrans.server.key=testServerKey",
        "midtrans.client.key=testClientKey",
        "midtrans.production=false",
        "midtrans.proxy.enabled=false",
        "midtrans.proxy.host=testProxyHost",
        "midtrans.proxy.port=8080"
})
class MidtransConfigTest {

    @Autowired
    private MidtransConfig midtransConfig;

    @Test
    void testConfig() {
        // Verify that the MidtransConfig bean is created successfully
        assertNotNull(midtransConfig);

        Config config = midtransConfig.configConfig();


        // Verify that the configuration properties are set correctly
        assertEquals("testServerKey", config.getServerKey());
        assertEquals("testClientKey", config.getClientKey());
        assertFalse(config.isProduction());
    }
}
