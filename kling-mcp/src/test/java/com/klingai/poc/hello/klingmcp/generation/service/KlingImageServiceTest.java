package com.klingai.poc.hello.klingmcp.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;
import com.klingai.poc.hello.klingmcp.generation.api.KlingApiClient;
import com.klingai.poc.hello.klingmcp.generation.api.KlingCreateImageResult;
import com.klingai.poc.hello.klingmcp.generation.api.KlingCreateVideoResult;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;
import com.klingai.poc.hello.klingmcp.generation.model.ImageTask;
import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;
import com.klingai.poc.hello.klingmcp.generation.model.VideoContracts;
import com.klingai.poc.hello.klingmcp.generation.repository.ImageTaskRepository;

class KlingImageServiceTest {

    private final FakeImageTaskRepository repository = new FakeImageTaskRepository();
    private final FakeKlingApiClient imageApiClient = new FakeKlingApiClient();
    private final KlingImageService service = new KlingImageService(
            repository,
            imageApiClient,
            properties(),
            new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createImageBindsTaskToCurrentJwtOwner() {
        authenticate("user-1");

        ImageContracts.ImageTaskResponse created = service.createImage(new ImageContracts.CreateImageRequest(
                "a panda watercolor portrait",
                "kling-image-v1",
                "1:1",
                null,
                123L,
                1,
                "watercolor",
                null,
                "idem-1"));

        assertThat(created.ok()).isTrue();
        assertThat(created.providerTaskId()).isEqualTo("provider-image-1");
        assertThat(created.status()).isEqualTo(GenerationTaskStatus.SUBMITTED);

        authenticate("user-2");
        ImageContracts.ImageTaskResponse hidden = service.getImageTask(
                new ImageContracts.GetImageTaskRequest(created.taskId()));

        assertThat(hidden.ok()).isFalse();
        assertThat(hidden.error().code()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void callbackUpdatesTaskAndDoesNotAllowTerminalStatusToRegress() {
        authenticate("user-1");
        ImageContracts.ImageTaskResponse created = service.createImage(new ImageContracts.CreateImageRequest(
                "a cinematic mountain scene",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        ImageContracts.CallbackResponse completed = service.applyCallback("""
                {
                  "eventId": "image-event-1",
                  "data": {
                    "taskId": "provider-image-1",
                    "status": "succeeded",
                    "progress": 100,
                    "result": {
                      "imageUrls": ["https://cdn.example.com/image.png"]
                    }
                  }
                }
                """);

        assertThat(completed.ok()).isTrue();
        assertThat(completed.status()).isEqualTo(GenerationTaskStatus.SUCCEEDED);

        ImageContracts.CallbackResponse duplicate = service.applyCallback("""
                {
                  "eventId": "image-event-1",
                  "data": {
                    "taskId": "provider-image-1",
                    "status": "running",
                    "progress": 20
                  }
                }
                """);

        assertThat(duplicate.ok()).isTrue();
        assertThat(duplicate.message()).contains("Duplicate");

        ImageContracts.CallbackResponse stale = service.applyCallback("""
                {
                  "eventId": "image-event-2",
                  "data": {
                    "taskId": "provider-image-1",
                    "status": "running",
                    "progress": 20
                  }
                }
                """);

        assertThat(stale.ok()).isTrue();
        assertThat(stale.status()).isEqualTo(GenerationTaskStatus.SUCCEEDED);

        ImageContracts.ImageTaskResponse fetched = service.getImageTask(
                new ImageContracts.GetImageTaskRequest(created.taskId()));
        assertThat(fetched.status()).isEqualTo(GenerationTaskStatus.SUCCEEDED);
        assertThat(fetched.result().imageUrls()).containsExactly("https://cdn.example.com/image.png");
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
                        "access-key",
                        "secret-key",
                        "/v1/videos/generations",
                        "/v1/videos/generations/{providerTaskId}/cancel",
                        "/api/kling/callbacks/video-generation",
                        "/v1/images/generations",
                        "/v1/images/generations/{providerTaskId}/cancel",
                        "/api/kling/callbacks/image-generation",
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
            throw new UnsupportedOperationException("Video generation is not used by image service tests.");
        }

        @Override
        public boolean cancelVideo(String providerTaskId) {
            throw new UnsupportedOperationException("Video cancellation is not used by image service tests.");
        }

        @Override
        public KlingCreateImageResult createImage(
                String localTaskId,
                ImageContracts.CreateImageRequest request,
                OwnerIdentity owner,
                String callbackUrl) {
            return new KlingCreateImageResult("provider-image-1", GenerationTaskStatus.SUBMITTED, 0, Map.of());
        }

        @Override
        public boolean cancelImage(String providerTaskId) {
            return true;
        }
    }

    private static class FakeImageTaskRepository implements ImageTaskRepository {

        private final Map<String, ImageTask> tasksById = new ConcurrentHashMap<>();
        private final Map<String, String> taskIdsByProviderTaskId = new ConcurrentHashMap<>();
        private final Set<String> processedCallbackEvents = ConcurrentHashMap.newKeySet();

        @Override
        public ImageTask save(ImageTask task) {
            tasksById.put(task.taskId(), task);
            if (hasText(task.providerTaskId())) {
                taskIdsByProviderTaskId.put(task.providerTaskId(), task.taskId());
            }
            return task;
        }

        @Override
        public Optional<ImageTask> findByTaskId(String taskId) {
            return hasText(taskId) ? Optional.ofNullable(tasksById.get(taskId)) : Optional.empty();
        }

        @Override
        public Optional<ImageTask> findByProviderTaskId(String providerTaskId) {
            if (!hasText(providerTaskId)) {
                return Optional.empty();
            }
            String taskId = taskIdsByProviderTaskId.get(providerTaskId);
            return findByTaskId(taskId);
        }

        @Override
        public List<ImageTask> findByOwner(String ownerSubject, GenerationTaskStatus status, int limit, String cursor) {
            return tasksById.values().stream()
                    .filter(task -> task.ownerSubject().equals(ownerSubject))
                    .filter(task -> status == null || task.status() == status)
                    .filter(task -> !hasText(cursor) || task.updatedAt().toString().compareTo(cursor) < 0)
                    .sorted(Comparator.comparing(ImageTask::updatedAt).reversed())
                    .limit(Math.clamp(limit, 1, 100))
                    .toList();
        }

        @Override
        public boolean markCallbackEventProcessed(String eventKey) {
            return processedCallbackEvents.add(eventKey);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
