package com.safetypin.payment.config;

import com.midtrans.Config;
import com.midtrans.ConfigBuilder;
import com.midtrans.proxy.ProxyConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MidtransConfig {
    // Midtrans configuration properties
    @Value("${midtrans.server.key}")
    private String serverKey;
    @Value("${midtrans.client.key}")
    private String clientKey;
    @Value("${midtrans.production}")
    private boolean isProduction;

    // Proxy settings
    @Value("${midtrans.proxy.enabled}")
    private boolean proxyEnabled;
    @Value("${midtrans.proxy.host:}")
    private String proxyHost;
    @Value("${midtrans.proxy.port:80}")
    private int proxyPort;


    @Bean
    public Config configConfig() {
        ConfigBuilder config = Config.builder()
                .setServerKey(serverKey)
                .setClientKey(clientKey)
                .setIsProduction(isProduction);

        if (proxyEnabled) {
            config.setProxyConfig(ProxyConfig.builder()
                    .setHost(proxyHost)
                    .setPort(proxyPort)
                    .build());
        }

        config.enableLog(false); // Enable logging for debugging

        return config.build();
    }

}
