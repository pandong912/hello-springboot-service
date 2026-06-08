package com.klingai.poc.hello.klingmcp.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "kling.mcp")
public record KlingMcpProperties(
        String publicBaseUrl,
        String endpoint,
        Auth auth,
        Api api) {

    public String resourceUri() {
        return auth.audience();
    }

    public String endpointUri() {
        return trimTrailingSlash(publicBaseUrl) + normalizePath(endpoint);
    }

    public String protectedResourceMetadataUri() {
        return trimTrailingSlash(publicBaseUrl) + "/.well-known/oauth-protected-resource";
    }

    public String protectedResourceMetadataUriForEndpoint() {
        return protectedResourceMetadataUri() + normalizePath(endpoint);
    }

    public List<String> supportedScopes() {
        return List.of(auth.requiredScope());
    }

    public String callbackUri() {
        return trimTrailingSlash(publicBaseUrl) + normalizePath(api().callbackPath());
    }

    @Override
    public Api api() {
        if (api == null) {
            return Api.defaults();
        }
        return api;
    }

    public record Auth(
            String issuerUri,
            String jwkSetUri,
            String audience,
            String requiredScope,
            List<TrustedIssuer> trustedIssuers) {

        public String resolvedJwkSetUri() {
            if (StringUtils.hasText(jwkSetUri)) {
                return jwkSetUri;
            }
            return trimTrailingSlash(issuerUri) + "/jwks";
        }

        public List<TrustedIssuer> resolvedTrustedIssuers() {
            if (trustedIssuers != null && !trustedIssuers.isEmpty()) {
                return trustedIssuers;
            }
            return List.of(new TrustedIssuer(issuerUri, jwkSetUri));
        }
    }

    public record TrustedIssuer(
            String issuerUri,
            String jwkSetUri) {

        public String resolvedJwkSetUri() {
            if (StringUtils.hasText(jwkSetUri)) {
                return jwkSetUri;
            }
            return trimTrailingSlash(issuerUri) + "/jwks";
        }

        public static List<String> issuerUris(List<TrustedIssuer> issuers) {
            List<String> issuerUris = new ArrayList<>();
            for (TrustedIssuer issuer : issuers) {
                issuerUris.add(issuer.issuerUri());
            }
            return issuerUris;
        }
    }

    public record Api(
            String baseUrl,
            String apiKey,
            String authHeader,
            String createVideoPath,
            String cancelVideoPath,
            String callbackPath,
            String callbackSecret,
            String callbackSignatureHeader,
            String callbackTimestampHeader) {

        static Api defaults() {
            return new Api(null, null, null, null, null, null, null, null, null);
        }

        public String resolvedBaseUrl() {
            return StringUtils.hasText(baseUrl) ? trimTrailingSlash(baseUrl) : "http://localhost:8089";
        }

        public String resolvedAuthHeader() {
            return StringUtils.hasText(authHeader) ? authHeader : "Authorization";
        }

        public String resolvedCreateVideoPath() {
            return StringUtils.hasText(createVideoPath) ? normalizePath(createVideoPath) : "/v1/videos/generations";
        }

        public String resolvedCancelVideoPath() {
            return StringUtils.hasText(cancelVideoPath)
                    ? normalizePath(cancelVideoPath)
                    : "/v1/videos/generations/{providerTaskId}/cancel";
        }

        public String callbackPath() {
            return StringUtils.hasText(callbackPath) ? normalizePath(callbackPath) : "/api/kling/callbacks/video-generation";
        }

        public String resolvedCallbackSignatureHeader() {
            return StringUtils.hasText(callbackSignatureHeader) ? callbackSignatureHeader : "X-Kling-Signature";
        }

        public String resolvedCallbackTimestampHeader() {
            return StringUtils.hasText(callbackTimestampHeader) ? callbackTimestampHeader : "X-Kling-Timestamp";
        }
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
