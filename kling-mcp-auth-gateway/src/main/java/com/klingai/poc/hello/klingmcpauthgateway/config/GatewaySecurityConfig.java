package com.klingai.poc.hello.klingmcpauthgateway.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadataClaimNames;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class GatewaySecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http, GatewayProperties properties) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer -> authorizationServer
                        .authorizationServerMetadataEndpoint(metadata -> metadata
                                .authorizationServerMetadataCustomizer(builder -> builder
                                        .claim(OAuth2AuthorizationServerMetadataClaimNames.REGISTRATION_ENDPOINT,
                                                properties.registrationEndpoint())
                                        .claim(OAuth2AuthorizationServerMetadataClaimNames.SCOPES_SUPPORTED,
                                                properties.supportedScopes())
                                        .claim("token_endpoint_auth_methods_supported",
                                                List.of("none", "client_secret_basic", "client_secret_post")))))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/logto")));

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/.well-known/openid-configuration",
                                "/.well-known/oauth-protected-resource",
                                "/.well-known/oauth-protected-resource/**",
                                "/oauth/register",
                                "/connect/register")
                        .permitAll()
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/oauth/register", "/connect/register"))
                .oauth2Login(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    InMemoryRegisteredClientRepository registeredClientRepository() {
        RegisteredClient registrationClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("kling-mcp-registration")
                .clientSecret("{noop}kling-mcp-registration-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("client.create")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(registrationClient);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(GatewayProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.publicBaseUrl())
                .authorizationEndpoint("/oauth/authorize")
                .tokenEndpoint("/oauth/token")
                .jwkSetEndpoint("/.well-known/jwks.json")
                .tokenIntrospectionEndpoint("/oauth/introspect")
                .tokenRevocationEndpoint("/oauth/revoke")
                .build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accessTokenCustomizer(GatewayProperties properties) {
        return context -> {
            if ("access_token".equals(context.getTokenType().getValue())) {
                Map<String, Object> userClaims = userClaims(context);
                context.getClaims()
                        .audience(List.of(properties.mcp().resource()))
                        .claim("resource", properties.mcp().resource())
                        .claim("client_id", context.getRegisteredClient().getClientId());
                copyUserClaims(context, userClaims);
            }
        };
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GatewayProperties properties) {
        String issuerUri = requireText(properties.logto().issuerUri(), "kling.mcp.auth-gateway.logto.issuer-uri");
        String clientId = requireText(properties.logto().clientId(), "kling.mcp.auth-gateway.logto.client-id");
        String clientSecret = requireText(properties.logto().clientSecret(), "kling.mcp.auth-gateway.logto.client-secret");
        String issuer = GatewayProperties.trimTrailingSlash(issuerUri);

        ClientRegistration logto = ClientRegistration.withRegistrationId("logto")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email", "roles",
                        "urn:logto:scope:organizations", "urn:logto:scope:organization_roles")
                .authorizationUri(issuer + "/auth")
                .tokenUri(issuer + "/token")
                .jwkSetUri(issuer + "/jwks")
                .userInfoUri(issuer + "/me")
                .userNameAttributeName("sub")
                .issuerUri(issuer)
                .clientName("Logto")
                .build();

        return new InMemoryClientRegistrationRepository(logto);
    }

    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(clientRegistration -> SignatureAlgorithm.ES384);
        return factory;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "https://www.cursor.com",
                "http://localhost:8787",
                "http://127.0.0.1:8787",
                "http://localhost:6274",
                "http://127.0.0.1:6274"));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    private static RSAKey generateRsa() {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }

    private static Map<String, Object> userClaims(JwtEncodingContext context) {
        Object principal = context.getPrincipal().getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getClaims();
        }
        if (principal instanceof OAuth2User oauth2User) {
            return oauth2User.getAttributes();
        }
        return Map.of();
    }

    private static void copyUserClaims(JwtEncodingContext context, Map<String, Object> userClaims) {
        Object subject = userClaims.get("sub");
        if (subject instanceof String value && StringUtils.hasText(value)) {
            context.getClaims()
                    .subject(value)
                    .claim("logto_sub", value);
        }
        copyClaim(context, userClaims, "email");
        copyClaim(context, userClaims, "email_verified");
        copyClaim(context, userClaims, "name");
        copyClaim(context, userClaims, "username");
        copyClaim(context, userClaims, "picture");
        copyClaim(context, userClaims, "roles");
        copyClaim(context, userClaims, "organizations");
        copyClaim(context, userClaims, "organization_data");
        copyClaim(context, userClaims, "organization_roles");
    }

    private static void copyClaim(JwtEncodingContext context, Map<String, Object> userClaims, String claimName) {
        Object value = userClaims.get(claimName);
        if (value != null) {
            context.getClaims().claim(claimName, value);
        }
    }

    private static String requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required property: " + propertyName);
        }
        return value;
    }
}
