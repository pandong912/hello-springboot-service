package com.klingai.poc.hello.klingfeignclient;

@FunctionalInterface
public interface KlingAccessTokenGenerator {

    String generateToken();
}
