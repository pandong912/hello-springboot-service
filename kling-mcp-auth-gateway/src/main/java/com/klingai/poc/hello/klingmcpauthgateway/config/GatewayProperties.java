package com.klingai.poc.hello.klingmcpauthgateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "kling.mcp.auth-gateway")
public record GatewayProperties(
        String issuer,
        Mcp mcp,
        Registration registration,
        Logto logto) {

    public String publicBaseUrl() {
        return trimTrailingSlash(issuer);
    }

    public String authorizationEndpoint() {
        return publicBaseUrl() + "/oauth/authorize";
    }

    public String tokenEndpoint() {
        return publicBaseUrl() + "/oauth/token";
    }

    public String registrationEndpoint() {
        return publicBaseUrl() + "/oauth/register";
    }

    public String jwksEndpoint() {
        return publicBaseUrl() + "/.well-known/jwks.json";
    }

    public List<String> supportedScopes() {
        return mcp.scopes();
    }

    public record Mcp(
            String resource,
            String endpoint,
            List<String> scopes) {
    }

    public record Registration(
            boolean enabled,
            boolean allowPublicClients,
            List<String> redirectUriAllowList) {
    }

    public record Logto(
            String issuerUri,
            String clientId,
            String clientSecret) {
    }

    static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
