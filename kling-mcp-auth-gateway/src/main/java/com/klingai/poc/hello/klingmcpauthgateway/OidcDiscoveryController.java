package com.klingai.poc.hello.klingmcpauthgateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OidcDiscoveryController {

    private final GatewayProperties properties;

    public OidcDiscoveryController(GatewayProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/.well-known/openid-configuration")
    OidcConfiguration openidConfiguration() {
        return new OidcConfiguration(
                properties.publicBaseUrl(),
                properties.authorizationEndpoint(),
                properties.tokenEndpoint(),
                properties.jwksEndpoint(),
                properties.registrationEndpoint(),
                List.of("code"),
                List.of("authorization_code", "refresh_token"),
                List.of("none", "client_secret_basic", "client_secret_post"),
                List.of("S256"),
                properties.supportedScopes(),
                List.of("public"));
    }

    record OidcConfiguration(
            String issuer,
            @JsonProperty("authorization_endpoint") String authorizationEndpoint,
            @JsonProperty("token_endpoint") String tokenEndpoint,
            @JsonProperty("jwks_uri") String jwksUri,
            @JsonProperty("registration_endpoint") String registrationEndpoint,
            @JsonProperty("response_types_supported") List<String> responseTypesSupported,
            @JsonProperty("grant_types_supported") List<String> grantTypesSupported,
            @JsonProperty("token_endpoint_auth_methods_supported") List<String> tokenEndpointAuthMethodsSupported,
            @JsonProperty("code_challenge_methods_supported") List<String> codeChallengeMethodsSupported,
            @JsonProperty("scopes_supported") List<String> scopesSupported,
            @JsonProperty("subject_types_supported") List<String> subjectTypesSupported) {
    }
}
