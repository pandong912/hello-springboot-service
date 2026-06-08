package com.klingai.poc.hello.klingmcpauthgateway;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PublicClientRegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(PublicClientRegistrationController.class);

    private final GatewayProperties properties;
    private final InMemoryRegisteredClientRepository registeredClientRepository;

    public PublicClientRegistrationController(
            GatewayProperties properties,
            InMemoryRegisteredClientRepository registeredClientRepository) {
        this.properties = properties;
        this.registeredClientRepository = registeredClientRepository;
    }

    @PostMapping({"/oauth/register", "/connect/register"})
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationResponse registerClient(@Valid @RequestBody RegistrationRequest request) {
        logger.info("Handling public client registration: clientName={}, requestedClientId={}, redirectUris={}, scope={}",
                request.clientName(), request.clientId(), request.redirectUris(), request.scope());
        if (!properties.registration().enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dynamic client registration is disabled");
        }
        if (!properties.registration().allowPublicClients()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public clients are disabled");
        }

        Set<String> redirectUris = validateRedirectUris(request.redirectUris());
        Set<String> scopes = validateScopes(request.scope());
        String clientId = resolveClientId(request.clientId());

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(values -> values.addAll(redirectUris))
                .scopes(values -> values.addAll(scopes))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .build();
        registeredClientRepository.save(registeredClient);
        logger.info("Registered public PKCE client: clientId={}, redirectUris={}, scopes={}",
                clientId, redirectUris, scopes);

        return new RegistrationResponse(
                clientId,
                null,
                "none",
                List.copyOf(redirectUris),
                List.of("code"),
                List.of("authorization_code", "refresh_token"),
                String.join(" ", scopes));
    }

    private Set<String> validateRedirectUris(List<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirect_uris is required");
        }
        Set<String> allowed = Set.copyOf(properties.registration().redirectUriAllowList());
        Set<String> validated = new LinkedHashSet<>();
        for (String redirectUri : redirectUris) {
            if (!allowed.contains(redirectUri)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirect_uri is not allowed: " + redirectUri);
            }
            validated.add(redirectUri);
        }
        return validated;
    }

    private Set<String> validateScopes(String scope) {
        Set<String> supported = Set.copyOf(properties.supportedScopes());
        Set<String> requested = new LinkedHashSet<>();
        if (StringUtils.hasText(scope)) {
            requested.addAll(List.of(scope.split(" ")));
        }
        else {
            requested.addAll(supported);
        }
        if (!supported.containsAll(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope contains unsupported values");
        }
        return requested;
    }

    private String resolveClientId(String requestedClientId) {
        if (StringUtils.hasText(requestedClientId)) {
            if (!requestedClientId.startsWith("https://")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "client_id metadata document must be an HTTPS URL");
            }
            return requestedClientId;
        }
        return "kling-mcp-client-" + UUID.randomUUID();
    }

    record RegistrationRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("redirect_uris") @NotEmpty List<String> redirectUris,
            @JsonProperty("scope") String scope,
            @JsonProperty("client_name") String clientName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RegistrationResponse(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("client_secret") String clientSecret,
            @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod,
            @JsonProperty("redirect_uris") List<String> redirectUris,
            @JsonProperty("response_types") List<String> responseTypes,
            @JsonProperty("grant_types") List<String> grantTypes,
            String scope) {
    }
}
