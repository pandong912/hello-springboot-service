package com.klingai.poc.hello.klingfeignclient;

import java.util.List;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author pandong <pandong@kuaishou.com>
 * Created on 2026-06-08
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KlingImageRequest(
        @JsonProperty("model_name") String modelName,
        @JsonProperty("prompt") String prompt,
        @JsonProperty("image_list") List<ImageReference> imageList,
        @JsonProperty("resolution") String resolution,
        @JsonProperty("n") Integer count,
        @JsonProperty("aspect_ratio") String aspectRatio,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("external_task_id") String externalTaskId) {

    private static final String DEFAULT_MODEL_NAME = "kling-v3-omni";

    public static KlingImageRequest of(
            String prompt,
            String modelName,
            String referenceImageUrl,
            Integer count,
            String aspectRatio,
            String callbackUrl,
            String externalTaskId) {
        return new KlingImageRequest(
                StringUtils.hasText(modelName) ? modelName : DEFAULT_MODEL_NAME,
                prompt,
                referenceImages(referenceImageUrl),
                null,
                count,
                aspectRatio,
                callbackUrl,
                externalTaskId);
    }

    private static List<ImageReference> referenceImages(String referenceImageUrl) {
        if (!StringUtils.hasText(referenceImageUrl)) {
            return null;
        }
        return List.of(new ImageReference(referenceImageUrl));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImageReference(@JsonProperty("image") String image) {
    }
}
