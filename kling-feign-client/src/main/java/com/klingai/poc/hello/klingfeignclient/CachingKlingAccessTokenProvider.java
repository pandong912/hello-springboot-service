package com.klingai.poc.hello.klingfeignclient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.util.StringUtils;

public class CachingKlingAccessTokenProvider implements KlingAccessTokenProvider {

    public static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(30);

    private final KlingAccessTokenGenerator generator;
    private final Duration tokenTtl;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public CachingKlingAccessTokenProvider(KlingAccessTokenGenerator generator) {
        this(generator, DEFAULT_TOKEN_TTL, Clock.systemUTC());
    }

    public CachingKlingAccessTokenProvider(KlingAccessTokenGenerator generator, Duration tokenTtl, Clock clock) {
        this.generator = generator;
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    @Override
    public String currentToken() {
        Instant now = clock.instant();
        CachedToken token = cachedToken;
        if (token != null && now.isBefore(token.expiresAt())) {
            return token.value();
        }

        synchronized (this) {
            token = cachedToken;
            if (token != null && now.isBefore(token.expiresAt())) {
                return token.value();
            }
            String generatedToken = normalizeToken(generator.generateToken());
            cachedToken = new CachedToken(generatedToken, now.plus(tokenTtl));
            return generatedToken;
        }
    }

    private static String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Kling access token generator returned an empty token");
        }
        String trimmed = token.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? trimmed.substring("Bearer ".length()).trim()
                : trimmed;
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
