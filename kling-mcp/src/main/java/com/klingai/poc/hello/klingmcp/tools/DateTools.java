package com.klingai.poc.hello.klingmcp.tools;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DateTools {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = "current_date", description = "Return the current date in ISO-8601 format using the server default time zone.")
    public String currentDate() {
        return LocalDate.now().toString();
    }


    @Tool(name = "who_am_i", description = "Return the current authenticated user's non-sensitive identity information.")
    public String whoami() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return toJson(Map.of("authenticated", false));
        }

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("authenticated", true);
        identity.put("authenticationType", authentication.getClass().getSimpleName());
        identity.put("name", authentication.getName());
        identity.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList());

        Jwt jwt = extractJwt(authentication);
        if (jwt != null) {
            Map<String, Object> token = new LinkedHashMap<>();
            token.put("subject", jwt.getSubject());
            token.put("issuer", jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
            token.put("audience", jwt.getAudience());
            token.put("issuedAt", jwt.getIssuedAt() == null ? null : jwt.getIssuedAt().toString());
            token.put("expiresAt", jwt.getExpiresAt() == null ? null : jwt.getExpiresAt().toString());
            token.put("clientId", jwt.getClaimAsString("client_id"));
            token.put("scope", claimAsStringList(jwt.getClaim("scope")));
            token.put("resource", jwt.getClaimAsString("resource"));
            token.put("organizationId", jwt.getClaimAsString("organization_id"));
            identity.put("token", token);
        }

        return toJson(identity);
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    private static List<String> claimAsStringList(Object claim) {
        if (claim instanceof String value) {
            return value.isBlank() ? List.of() : List.of(value.split(" "));
        }
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }

    private static String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize identity information", ex);
        }
    }
}
