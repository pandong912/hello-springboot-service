package com.klingai.poc.hello.klingmcp.config;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.klingai.poc.hello.klingfeignclient.CachingKlingAccessTokenProvider;
import com.klingai.poc.hello.klingfeignclient.KlingAccessTokenGenerator;
import com.klingai.poc.hello.klingfeignclient.KlingAccessTokenProvider;

@Configuration
public class KlingFeignClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    KlingAccessTokenProvider klingAccessTokenProvider(KlingAccessTokenGenerator generator) {
        return new CachingKlingAccessTokenProvider(generator);
    }

    @Bean
    @ConditionalOnMissingBean
    KlingAccessTokenGenerator klingAccessTokenGenerator(KlingMcpProperties properties) {
        return () -> sign(properties.api().accessKey(), properties.api().secretKey());
    }

    static String sign(String ak, String sk) {
        if (!StringUtils.hasText(ak) || !StringUtils.hasText(sk)) {
            throw new IllegalStateException("Kling API access-key and secret-key must be configured before calling Kling API");
        }
        try {
            Date expiredAt = new Date(System.currentTimeMillis() + 1800 * 1000);
            Date notBefore = new Date(System.currentTimeMillis() - 5 * 1000);
            Algorithm algo = Algorithm.HMAC256(sk);
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            return JWT.create()
                    .withIssuer(ak)
                    .withHeader(header)
                    .withExpiresAt(expiredAt)
                    .withNotBefore(notBefore)
                    .sign(algo);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Kling API access token", ex);
        }
    }
}
