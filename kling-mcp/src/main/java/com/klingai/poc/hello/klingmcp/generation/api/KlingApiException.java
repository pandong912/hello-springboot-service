package com.klingai.poc.hello.klingmcp.generation.api;

import lombok.Getter;

@Getter
public class KlingApiException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public KlingApiException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }
}
