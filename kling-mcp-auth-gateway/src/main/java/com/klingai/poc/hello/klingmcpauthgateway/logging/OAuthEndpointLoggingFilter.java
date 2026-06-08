package com.klingai.poc.hello.klingmcpauthgateway.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class OAuthEndpointLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!shouldLog(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startedAt = System.currentTimeMillis();
        log.info("OAuth endpoint request: method={}, path={}, query={}, origin={}, contentType={}",
                request.getMethod(),
                request.getRequestURI(),
                sanitizeQuery(request.getQueryString()),
                request.getHeader("Origin"),
                request.getContentType());
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            log.info("OAuth endpoint response: method={}, path={}, status={}, elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    System.currentTimeMillis() - startedAt);
        }
    }

    private static boolean shouldLog(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/oauth/")
                || path.startsWith("/connect/register")
                || path.startsWith("/login/oauth2/code/")
                || path.startsWith("/.well-known/");
    }

    private static String sanitizeQuery(String query) {
        if (query == null) {
            return null;
        }
        return query
                .replaceAll("code=[^&]+", "code=<redacted>")
                .replaceAll("client_secret=[^&]+", "client_secret=<redacted>")
                .replaceAll("code_verifier=[^&]+", "code_verifier=<redacted>");
    }
}
