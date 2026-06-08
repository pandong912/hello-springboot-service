package com.klingai.poc.hello.klingmcp;

public interface KlingApiClient {

    KlingCreateVideoResult createVideo(
            String localTaskId,
            VideoContracts.CreateVideoRequest request,
            OwnerIdentity owner,
            String callbackUrl);

    boolean cancelVideo(String providerTaskId);
}
