package com.klingai.poc.hello.klingmcp.video.service;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;
import com.klingai.poc.hello.klingmcp.video.api.KlingApiClient;
import com.klingai.poc.hello.klingmcp.video.api.KlingCreateVideoResult;
import com.klingai.poc.hello.klingmcp.video.model.VideoContracts;
import com.klingai.poc.hello.klingmcp.video.model.VideoTaskStatus;
import com.klingai.poc.hello.klingmcp.video.repository.InMemoryVideoTaskRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class KlingVideoServiceTest {

    private final InMemoryVideoTaskRepository repository = new InMemoryVideoTaskRepository();
    private final FakeKlingApiClient klingApiClient = new FakeKlingApiClient();
    private final KlingVideoService service = new KlingVideoService(
            repository,
            klingApiClient,
            properties(),
            new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createVideoBindsTaskToCurrentJwtOwner() {
        authenticate("user-1");

        VideoContracts.VideoTaskResponse created = service.createVideo(new VideoContracts.CreateVideoRequest(
                "a panda playing guitar",
                "kling-v1",
                5,
                "16:9",
                null,
                123L,
                null,
                "idem-1"));

        assertThat(created.ok()).isTrue();
        assertThat(created.providerTaskId()).isEqualTo("provider-1");
        assertThat(created.status()).isEqualTo(VideoTaskStatus.SUBMITTED);

        authenticate("user-2");
        VideoContracts.VideoTaskResponse hidden = service.getVideoTask(
                new VideoContracts.GetVideoTaskRequest(created.taskId()));

        assertThat(hidden.ok()).isFalse();
        assertThat(hidden.error().code()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void callbackUpdatesTaskAndDoesNotAllowTerminalStatusToRegress() {
        authenticate("user-1");
        VideoContracts.VideoTaskResponse created = service.createVideo(new VideoContracts.CreateVideoRequest(
                "a cinematic ocean shot",
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        VideoContracts.CallbackResponse completed = service.applyCallback("""
                {
                  "eventId": "event-1",
                  "data": {
                    "taskId": "provider-1",
                    "status": "succeeded",
                    "progress": 100,
                    "result": {
                      "videoUrl": "https://cdn.example.com/video.mp4",
                      "coverUrl": "https://cdn.example.com/cover.jpg"
                    }
                  }
                }
                """);

        assertThat(completed.ok()).isTrue();
        assertThat(completed.status()).isEqualTo(VideoTaskStatus.SUCCEEDED);

        VideoContracts.CallbackResponse duplicate = service.applyCallback("""
                {
                  "eventId": "event-1",
                  "data": {
                    "taskId": "provider-1",
                    "status": "running",
                    "progress": 20
                  }
                }
                """);

        assertThat(duplicate.ok()).isTrue();
        assertThat(duplicate.message()).contains("Duplicate");

        VideoContracts.CallbackResponse stale = service.applyCallback("""
                {
                  "eventId": "event-2",
                  "data": {
                    "taskId": "provider-1",
                    "status": "running",
                    "progress": 20
                  }
                }
                """);

        assertThat(stale.ok()).isTrue();
        assertThat(stale.status()).isEqualTo(VideoTaskStatus.SUCCEEDED);

        VideoContracts.VideoTaskResponse fetched = service.getVideoTask(
                new VideoContracts.GetVideoTaskRequest(created.taskId()));
        assertThat(fetched.status()).isEqualTo(VideoTaskStatus.SUCCEEDED);
        assertThat(fetched.result().videoUrl()).isEqualTo("https://cdn.example.com/video.mp4");
    }

    private static void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("token-" + subject)
                .header("alg", "RS256")
                .subject(subject)
                .issuer("https://auth.example.com")
                .audience(List.of("https://api.example.com/kling-mcp"))
                .issuedAt(Instant.parse("2026-06-08T00:00:00Z"))
                .expiresAt(Instant.parse("2026-06-08T01:00:00Z"))
                .claims(claims -> {
                    claims.put("client_id", "agent-client");
                    claims.put("organization_id", "org-1");
                })
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_kling:invoke"))));
    }

    private static KlingMcpProperties properties() {
        return new KlingMcpProperties(
                "http://localhost:8081",
                "/mcp",
                new KlingMcpProperties.Auth(
                        "https://auth.example.com",
                        "https://auth.example.com/jwks",
                        "https://api.example.com/kling-mcp",
                        "kling:invoke",
                        List.of(new KlingMcpProperties.TrustedIssuer(
                                "https://auth.example.com",
                                "https://auth.example.com/jwks"))),
                new KlingMcpProperties.Api(
                        "https://kling.example.com",
                        "api-key",
                        "Authorization",
                        "/v1/videos/generations",
                        "/v1/videos/generations/{providerTaskId}/cancel",
                        "/api/kling/callbacks/video-generation",
                        "callback-secret",
                        "X-Kling-Signature",
                        "X-Kling-Timestamp"));
    }

    private static class FakeKlingApiClient implements KlingApiClient {

        @Override
        public KlingCreateVideoResult createVideo(
                String localTaskId,
                VideoContracts.CreateVideoRequest request,
                OwnerIdentity owner,
                String callbackUrl) {
            return new KlingCreateVideoResult("provider-1", VideoTaskStatus.SUBMITTED, 0, Map.of());
        }

        @Override
        public boolean cancelVideo(String providerTaskId) {
            return true;
        }
    }
}
