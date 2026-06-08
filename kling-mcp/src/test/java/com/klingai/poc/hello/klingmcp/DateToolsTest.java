package com.klingai.poc.hello.klingmcp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class DateToolsTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentDateReturnsTodayAsIsoDate() {
        DateTools dateTools = new DateTools();

        assertThat(dateTools.currentDate()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void whoamiReturnsUnauthenticatedWhenSecurityContextIsEmpty() throws Exception {
        DateTools dateTools = new DateTools();

        JsonNode json = objectMapper.readTree(dateTools.whoami());

        assertThat(json.get("authenticated").asBoolean()).isFalse();
    }

    @Test
    void whoamiReturnsJwtIdentityWithoutTokenValue() throws Exception {
        Jwt jwt = Jwt.withTokenValue("secret-token-value")
                .header("alg", "RS256")
                .subject("user-123")
                .issuer("https://auth.example.com")
                .audience(List.of("https://mcp.example.com/mcp"))
                .issuedAt(Instant.parse("2026-06-08T00:00:00Z"))
                .expiresAt(Instant.parse("2026-06-08T01:00:00Z"))
                .claims(claims -> {
                    claims.put("client_id", "agent-client");
                    claims.put("scope", "kling:invoke kling:task:read");
                    claims.put("resource", "https://mcp.example.com/mcp");
                    claims.put("organization_id", "org-123");
                })
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_kling:invoke")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        DateTools dateTools = new DateTools();

        JsonNode json = objectMapper.readTree(dateTools.whoami());

        assertThat(json.get("authenticated").asBoolean()).isTrue();
        assertThat(json.get("name").asText()).isEqualTo("user-123");
        assertThat(json.at("/token/subject").asText()).isEqualTo("user-123");
        assertThat(json.at("/token/issuer").asText()).isEqualTo("https://auth.example.com");
        assertThat(json.at("/token/clientId").asText()).isEqualTo("agent-client");
        assertThat(json.at("/token/scope").get(0).asText()).isEqualTo("kling:invoke");
        assertThat(json.at("/token/scope").get(1).asText()).isEqualTo("kling:task:read");
        assertThat(dateTools.whoami()).doesNotContain("secret-token-value");
    }
}
