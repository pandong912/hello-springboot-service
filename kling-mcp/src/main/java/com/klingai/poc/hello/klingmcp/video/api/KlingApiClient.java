package com.klingai.poc.hello.klingmcp.video.api;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.video.model.VideoContracts;

public interface KlingApiClient {

    KlingCreateVideoResult createVideo(
            String localTaskId,
            VideoContracts.CreateVideoRequest request,
            OwnerIdentity owner,
            String callbackUrl);

    boolean cancelVideo(String providerTaskId);
}
