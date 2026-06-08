package com.klingai.poc.hello.klingmcpauthgateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayStartupLogger {

    private static final Logger logger = LoggerFactory.getLogger(GatewayStartupLogger.class);

    @Bean
    ApplicationRunner logGatewayConfiguration(GatewayProperties properties) {
        return args -> logger.info(
                "Kling MCP OAuth Gateway configured: issuer={}, mcpResource={}, scopes={}, dcrEnabled={}, registrationEndpoint={}",
                properties.publicBaseUrl(),
                properties.mcp().resource(),
                properties.supportedScopes(),
                properties.registration().enabled(),
                properties.registrationEndpoint());
    }
}
