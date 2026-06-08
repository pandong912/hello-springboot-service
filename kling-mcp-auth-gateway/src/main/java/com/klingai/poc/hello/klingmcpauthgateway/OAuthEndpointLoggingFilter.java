package com.klingai.poc.hello.klingmcpauthgateway;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuthEndpointLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(OAuthEndpointLoggingFilter.class);

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
        logger.info("OAuth endpoint request: method={}, path={}, query={}, origin={}, contentType={}",
                request.getMethod(),
                request.getRequestURI(),
                sanitizeQuery(request.getQueryString()),
                request.getHeader("Origin"),
                request.getContentType());
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            logger.info("OAuth endpoint response: method={}, path={}, status={}, elapsedMs={}",
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
