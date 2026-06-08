package com.klingai.poc.hello.klingfeignclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CachingKlingAccessTokenProviderTest {

    @Test
    void cachesGeneratedTokenUntilTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T00:00:00Z"));
        AtomicInteger generatedCount = new AtomicInteger();
        CachingKlingAccessTokenProvider provider = new CachingKlingAccessTokenProvider(
                () -> "token-" + generatedCount.incrementAndGet(),
                Duration.ofMinutes(30),
                clock);

        assertThat(provider.currentToken()).isEqualTo("token-1");

        clock.advance(Duration.ofMinutes(29).plusSeconds(59));
        assertThat(provider.currentToken()).isEqualTo("token-1");

        clock.advance(Duration.ofSeconds(1));
        assertThat(provider.currentToken()).isEqualTo("token-2");
    }

    @Test
    void normalizesBearerPrefixFromGeneratorResult() {
        CachingKlingAccessTokenProvider provider = new CachingKlingAccessTokenProvider(() -> "Bearer generated-token");

        assertThat(provider.currentToken()).isEqualTo("generated-token");
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
