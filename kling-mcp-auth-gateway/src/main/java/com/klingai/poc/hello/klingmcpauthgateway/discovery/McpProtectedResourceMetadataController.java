package com.klingai.poc.hello.klingmcpauthgateway.discovery;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.klingai.poc.hello.klingmcpauthgateway.config.GatewayProperties;

@RestController
@RequiredArgsConstructor
public class McpProtectedResourceMetadataController {

    private final GatewayProperties properties;

    @GetMapping({
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/**"
    })
    ProtectedResourceMetadata protectedResourceMetadata() {
        return new ProtectedResourceMetadata(
                properties.mcp().resource(),
                List.of(properties.publicBaseUrl()),
                properties.supportedScopes(),
                List.of("header"),
                properties.mcp().endpoint());
    }

    record ProtectedResourceMetadata(
            String resource,
            @JsonProperty("authorization_servers") List<String> authorizationServers,
            @JsonProperty("scopes_supported") List<String> scopesSupported,
            @JsonProperty("bearer_methods_supported") List<String> bearerMethodsSupported,
            @JsonProperty("resource_documentation") String resourceDocumentation) {
    }
}
