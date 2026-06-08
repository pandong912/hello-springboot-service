package com.klingai.poc.hello.klingmcp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class KlingMcpStartupLogger {

    @Bean
    ApplicationRunner logKlingMcpConfiguration(KlingMcpProperties properties) {
        return args -> log.info(
                "Kling MCP auth configured: endpoint={}, resource={}, trustedIssuers={}, requiredScope={}, metadata={}",
                properties.endpointUri(),
                properties.resourceUri(),
                KlingMcpProperties.TrustedIssuer.issuerUris(properties.auth().resolvedTrustedIssuers()),
                properties.auth().requiredScope(),
                properties.protectedResourceMetadataUriForEndpoint());
    }
}
