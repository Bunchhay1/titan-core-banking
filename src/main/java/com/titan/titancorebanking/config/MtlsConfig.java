package com.titan.titancorebanking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mTLS Configuration for internal service-to-service communication.
 * Enabled when mtls.enabled=true
 */
@Configuration
@ConditionalOnProperty(name = "mtls.enabled", havingValue = "true")
public class MtlsConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainer() {
        return factory -> {
            factory.addConnectorCustomizers(connector -> {
                connector.setSecure(true);
                connector.setScheme("https");
                connector.setProperty("clientAuth", "true");
                connector.setProperty("protocol", "HTTP/1.1");
                connector.setProperty("sslProtocol", "TLS");
                connector.setProperty("keystoreFile", System.getProperty("javax.net.ssl.keyStore"));
                connector.setProperty("keystorePass", System.getProperty("javax.net.ssl.keyStorePassword"));
                connector.setProperty("truststoreFile", System.getProperty("javax.net.ssl.trustStore"));
                connector.setProperty("truststorePass", System.getProperty("javax.net.ssl.trustStorePassword"));
            });
        };
    }
}
