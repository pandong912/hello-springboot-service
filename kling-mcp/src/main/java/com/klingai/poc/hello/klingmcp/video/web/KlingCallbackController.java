package com.klingai.poc.hello.klingmcp.video.web;

import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;
import com.klingai.poc.hello.klingmcp.video.model.VideoContracts;
import com.klingai.poc.hello.klingmcp.video.service.KlingVideoService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KlingCallbackController {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final KlingVideoService videoService;
    private final KlingMcpProperties properties;

    @PostMapping(
            path = "${kling.mcp.api.callback-path:/api/kling/callbacks/video-generation}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<VideoContracts.CallbackResponse> receiveVideoGenerationCallback(
            @RequestBody String rawPayload,
            @RequestHeader(name = "${kling.mcp.api.callback-signature-header:X-Kling-Signature}", required = false) String signature,
            @RequestHeader(name = "${kling.mcp.api.callback-timestamp-header:X-Kling-Timestamp}", required = false) String timestamp) {
        if (!isValidSignature(rawPayload, signature, timestamp)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new VideoContracts.CallbackResponse(false, null, null, null, "Invalid callback signature."));
        }
        try {
            VideoContracts.CallbackResponse response = videoService.applyCallback(rawPayload);
            if (!response.ok()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            return ResponseEntity.ok(response);
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(new VideoContracts.CallbackResponse(false, null, null, null, ex.getMessage()));
        }
    }

    private boolean isValidSignature(String rawPayload, String signature, String timestamp) {
        String secret = properties.api().callbackSecret();
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(signature) || !StringUtils.hasText(timestamp)) {
            return false;
        }
        if (!isFresh(timestamp)) {
            return false;
        }
        String expected = hmacSha256(secret, timestamp + "." + rawPayload);
        String normalizedSignature = signature.startsWith("sha256=") ? signature.substring("sha256=".length()) : signature;
        return MessageDigestSupport.constantTimeEquals(expected, normalizedSignature);
    }

    private static boolean isFresh(String timestamp) {
        try {
            Instant callbackTime = Instant.ofEpochSecond(Long.parseLong(timestamp));
            Duration age = Duration.between(callbackTime, Instant.now()).abs();
            return age.compareTo(MAX_CLOCK_SKEW) <= 0;
        }
        catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    private static final class MessageDigestSupport {

        private MessageDigestSupport() {
        }

        static boolean constantTimeEquals(String expected, String actual) {
            if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
                return false;
            }
            byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
            return java.security.MessageDigest.isEqual(expectedBytes, actualBytes);
        }
    }
}
