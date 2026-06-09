package com.klingai.poc.hello.klingfeignclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class KlingImageRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOmniImageRequestWithSnakeCaseFieldNames() throws Exception {
        KlingImageRequest request = KlingImageRequest.of(
                "a blue robot cat flying in the sky",
                "kling-v3-omni",
                "https://example.com/cat.png",
                1,
                "1:1",
                "http://localhost:8081/api/kling/callbacks/image-generation",
                "local-task-1");

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.path("model_name").asText()).isEqualTo("kling-v3-omni");
        assertThat(json.path("prompt").asText()).isEqualTo("a blue robot cat flying in the sky");
        assertThat(json.path("image_list").get(0).path("image").asText()).isEqualTo("https://example.com/cat.png");
        assertThat(json.path("n").asInt()).isEqualTo(1);
        assertThat(json.path("aspect_ratio").asText()).isEqualTo("1:1");
        assertThat(json.path("callback_url").asText())
                .isEqualTo("http://localhost:8081/api/kling/callbacks/image-generation");
        assertThat(json.path("external_task_id").asText()).isEqualTo("local-task-1");
        assertThat(json.has("modelName")).isFalse();
        assertThat(json.has("aspectRatio")).isFalse();
        assertThat(json.has("referenceImageUrl")).isFalse();
    }

    @Test
    void usesDefaultModelAndOmitsEmptyReferenceImage() {
        KlingImageRequest request = KlingImageRequest.of(
                "a blue robot cat flying in the sky",
                null,
                "",
                null,
                null,
                null,
                "local-task-1");

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.path("model_name").asText()).isEqualTo("kling-v3-omni");
        assertThat(json.has("image_list")).isFalse();
        assertThat(json.has("n")).isFalse();
        assertThat(json.has("aspect_ratio")).isFalse();
    }
}
