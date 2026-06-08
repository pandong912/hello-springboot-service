package com.klingai.poc.hello.klingmcp.auth;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;

@RestController
@RequiredArgsConstructor
public class McpAuthorizationMetadataController {

    private final KlingMcpProperties properties;

    @GetMapping({
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/**"
    })
    ProtectedResourceMetadata protectedResourceMetadata() {
        return new ProtectedResourceMetadata(
                properties.resourceUri(),
                KlingMcpProperties.TrustedIssuer.issuerUris(properties.auth().resolvedTrustedIssuers()),
                properties.supportedScopes(),
                List.of("header"),
                properties.endpointUri());
    }

    record ProtectedResourceMetadata(
            String resource,
            @JsonProperty("authorization_servers") List<String> authorizationServers,
            @JsonProperty("scopes_supported") List<String> scopesSupported,
            @JsonProperty("bearer_methods_supported") List<String> bearerMethodsSupported,
            @JsonProperty("resource_documentation") String resourceDocumentation) {
    }
}
