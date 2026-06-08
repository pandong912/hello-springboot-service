package com.klingai.poc.hello.klingmcp.generation.api;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;
import com.klingai.poc.hello.klingmcp.generation.model.VideoContracts;

public interface KlingApiClient {

    KlingCreateVideoResult createVideo(
            String localTaskId,
            VideoContracts.CreateVideoRequest request,
            OwnerIdentity owner,
            String callbackUrl);

    boolean cancelVideo(String providerTaskId);

    KlingCreateImageResult createImage(
            String localTaskId,
            ImageContracts.CreateImageRequest request,
            OwnerIdentity owner,
            String callbackUrl);

    boolean cancelImage(String providerTaskId);
}
