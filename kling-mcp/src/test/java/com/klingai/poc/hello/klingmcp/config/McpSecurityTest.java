package com.klingai.poc.hello.klingmcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.klingai.poc.hello.klingmcp.generation.model.ImageTask;
import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;
import com.klingai.poc.hello.klingmcp.generation.model.VideoTask;
import com.klingai.poc.hello.klingmcp.generation.repository.ImageTaskRepository;
import com.klingai.poc.hello.klingmcp.generation.repository.VideoTaskRepository;

@SpringBootTest(properties = {
        "kling.mcp.public-base-url=http://localhost:8081",
        "kling.mcp.endpoint=/mcp",
        "kling.mcp.auth.issuer-uri=https://example.logto.app/oidc",
        "kling.mcp.auth.jwk-set-uri=https://example.logto.app/oidc/jwks",
        "kling.mcp.auth.audience=https://api.example.com/kling-mcp",
        "kling.mcp.auth.required-scope=kling:invoke",
        "kling.mcp.auth.trusted-issuers[0].issuer-uri=https://example.logto.app/oidc",
        "kling.mcp.auth.trusted-issuers[0].jwk-set-uri=https://example.logto.app/oidc/jwks",
        "kling.mcp.auth.trusted-issuers[1].issuer-uri=https://auth.example.com",
        "kling.mcp.auth.trusted-issuers[1].jwk-set-uri=https://auth.example.com/.well-known/jwks.json",
        "kling.mcp.api.callback-secret=test-callback-secret",
        "kling.mcp.repository.type=test",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@AutoConfigureMockMvc
class McpSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedMcpRequestReturnsProtectedResourceChallenge() throws Exception {
        mockMvc.perform(get("/mcp"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("resource_metadata=\"http://localhost:8081/.well-known/oauth-protected-resource/mcp\"")));
    }

    @Test
    void protectedResourceMetadataReturnsLogtoDiscoveryInfo() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("https://api.example.com/kling-mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("https://example.logto.app/oidc"))
                .andExpect(jsonPath("$.authorization_servers[1]").value("https://auth.example.com"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("kling:invoke"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"))
                .andExpect(jsonPath("$.resource_documentation").value("http://localhost:8081/mcp"));
    }

    @Test
    void protectedResourceMetadataEndpointPathReturnsSameResource() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("https://api.example.com/kling-mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("https://example.logto.app/oidc"));
    }

    @Test
    void mcpRequestWithoutRequiredScopeIsForbidden() throws Exception {
        mockMvc.perform(get("/mcp")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_other"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void callbackWithoutSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/kling/callbacks/video-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void imageCallbackWithoutSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/kling/callbacks/image-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void signedCallbackBypassesOauthAndReachesCallbackController() throws Exception {
        String body = """
                {
                  "eventId": "event-404",
                  "data": {
                    "taskId": "missing-provider-task",
                    "status": "succeeded"
                  }
                }
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/kling/callbacks/video-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Kling-Timestamp", timestamp)
                        .header("X-Kling-Signature", signature(timestamp, body))
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("Task not found."));
    }

    @Test
    void audienceValidatorAcceptsStringAudienceFromLogtoTokens() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "ES384")
                .claim("aud", "https://api.example.com/kling-mcp")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(SecurityConfig.audienceValidator("https://api.example.com/kling-mcp")
                .validate(jwt)
                .hasErrors()).isFalse();
    }

    private static String signature(String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-callback-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration
    static class RepositoryTestConfiguration {

        @Bean
        VideoTaskRepository videoTaskRepository() {
            return new VideoTaskRepository() {
                @Override
                public VideoTask save(VideoTask task) {
                    return task;
                }

                @Override
                public Optional<VideoTask> findByTaskId(String taskId) {
                    return Optional.empty();
                }

                @Override
                public Optional<VideoTask> findByProviderTaskId(String providerTaskId) {
                    return Optional.empty();
                }

                @Override
                public List<VideoTask> findByOwner(String ownerSubject, GenerationTaskStatus status, int limit, String cursor) {
                    return List.of();
                }

                @Override
                public boolean markCallbackEventProcessed(String eventKey) {
                    return true;
                }
            };
        }

        @Bean
        ImageTaskRepository imageTaskRepository() {
            return new ImageTaskRepository() {
                @Override
                public ImageTask save(ImageTask task) {
                    return task;
                }

                @Override
                public Optional<ImageTask> findByTaskId(String taskId) {
                    return Optional.empty();
                }

                @Override
                public Optional<ImageTask> findByProviderTaskId(String providerTaskId) {
                    return Optional.empty();
                }

                @Override
                public List<ImageTask> findByOwner(String ownerSubject, GenerationTaskStatus status, int limit, String cursor) {
                    return List.of();
                }

                @Override
                public boolean markCallbackEventProcessed(String eventKey) {
                    return true;
                }
            };
        }
    }
}
