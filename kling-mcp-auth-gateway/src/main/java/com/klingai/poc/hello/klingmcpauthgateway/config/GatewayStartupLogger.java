package com.klingai.poc.hello.klingmcpauthgateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class GatewayStartupLogger {

    @Bean
    ApplicationRunner logGatewayConfiguration(GatewayProperties properties) {
        return args -> log.info(
                "Kling MCP OAuth Gateway configured: issuer={}, mcpResource={}, scopes={}, dcrEnabled={}, registrationEndpoint={}",
                properties.publicBaseUrl(),
                properties.mcp().resource(),
                properties.supportedScopes(),
                properties.registration().enabled(),
                properties.registrationEndpoint());
    }
}
