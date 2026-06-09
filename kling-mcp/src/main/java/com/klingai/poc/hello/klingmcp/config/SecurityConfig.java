package com.klingai.poc.hello.klingmcp.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@EnableConfigurationProperties(KlingMcpProperties.class)
@Slf4j
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KlingMcpProperties properties) throws Exception {
        String requiredAuthority = "SCOPE_" + requireText(properties.auth().requiredScope(), "kling.mcp.auth.required-scope");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/kling/debug/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/.well-known/oauth-protected-resource",
                                "/.well-known/oauth-protected-resource/**",
                                properties.api().callbackPath(),
                                properties.api().imageCallbackPath())
                        .permitAll()
                        .requestMatchers(properties.endpoint(), properties.endpoint() + "/**").hasAuthority(requiredAuthority)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.warn("Rejecting unauthenticated MCP request path={}, reason={}",
                                    request.getRequestURI(), authException.getMessage());
                            response.addHeader(HttpHeaders.WWW_AUTHENTICATE,
                                    "Bearer resource_metadata=\"" + properties.protectedResourceMetadataUriForEndpoint() + "\"");
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        }))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("Rejecting authenticated MCP request path={}, requiredAuthority={}, reason={}",
                                    request.getRequestURI(), requiredAuthority, accessDeniedException.getMessage());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                    "Forbidden: missing required authority " + requiredAuthority);
                        }));

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(KlingMcpProperties properties) {
        String audience = requireText(properties.auth().audience(), "kling.mcp.auth.audience");
        List<KlingMcpProperties.TrustedIssuer> trustedIssuers = properties.auth().resolvedTrustedIssuers();
        List<JwtDecoder> decoders = new ArrayList<>();

        for (int i = 0; i < trustedIssuers.size(); i++) {
            KlingMcpProperties.TrustedIssuer issuer = trustedIssuers.get(i);
            String issuerUri = requireText(issuer.issuerUri(), "kling.mcp.auth.trusted-issuers[" + i + "].issuer-uri");
            decoders.add(jwtDecoderForIssuer(issuerUri, issuer.resolvedJwkSetUri(), audience));
        }

        if (decoders.size() == 1) {
            return decoders.get(0);
        }

        return token -> {
            JwtException lastException = null;
            for (JwtDecoder decoder : decoders) {
                try {
                    return decoder.decode(token);
                }
                catch (JwtException ex) {
                    lastException = ex;
                }
            }
            throw lastException != null ? lastException : new JwtException("No trusted issuers configured");
        };
    }

    private static JwtDecoder jwtDecoderForIssuer(String issuerUri, String jwkSetUri, String audience) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES384)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(jwtTypeVerifier()))
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = audienceValidator(audience);

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return jwtDecoder;
    }

    private static JOSEObjectTypeVerifier<SecurityContext> jwtTypeVerifier() {
        DefaultJOSEObjectTypeVerifier<SecurityContext> delegate = new DefaultJOSEObjectTypeVerifier<>(
                JOSEObjectType.JWT,
                new JOSEObjectType("at+jwt"),
                new JOSEObjectType("application/at+jwt"));
        return (typ, context) -> {
            if (typ == null) {
                return;
            }
            delegate.verify(typ, context);
        };
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "The required audience is missing",
                        null));
    }

    private static String requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required property: " + propertyName);
        }
        return value;
    }
}
