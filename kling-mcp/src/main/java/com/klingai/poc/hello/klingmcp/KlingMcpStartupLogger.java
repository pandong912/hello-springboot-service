package com.klingai.poc.hello.klingmcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KlingMcpStartupLogger {

    private static final Logger logger = LoggerFactory.getLogger(KlingMcpStartupLogger.class);

    @Bean
    ApplicationRunner logKlingMcpConfiguration(KlingMcpProperties properties) {
        return args -> logger.info(
                "Kling MCP auth configured: endpoint={}, resource={}, trustedIssuers={}, requiredScope={}, metadata={}",
                properties.endpointUri(),
                properties.resourceUri(),
                KlingMcpProperties.TrustedIssuer.issuerUris(properties.auth().resolvedTrustedIssuers()),
                properties.auth().requiredScope(),
                properties.protectedResourceMetadataUriForEndpoint());
    }
}
