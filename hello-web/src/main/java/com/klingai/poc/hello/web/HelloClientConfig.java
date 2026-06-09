package com.klingai.poc.hello.web;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.klingai.poc.hello.dubbo.HelloDubboService;
import com.klingai.poc.hello.grpc.HelloRpcGrpc;

@Configuration
@EnableConfigurationProperties(HelloWebProperties.class)
public class HelloClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel helloGrpcChannel(HelloWebProperties properties) {
        return ManagedChannelBuilder.forTarget(properties.getGrpcTarget())
                .usePlaintext()
                .build();
    }

    @Bean
    HelloRpcGrpc.HelloRpcBlockingStub helloRpcBlockingStub(ManagedChannel helloGrpcChannel) {
        return HelloRpcGrpc.newBlockingStub(helloGrpcChannel);
    }

    @Bean
    ReferenceConfig<HelloDubboService> helloDubboReferenceConfig(HelloWebProperties properties) {
        var referenceConfig = new ReferenceConfig<HelloDubboService>();
        referenceConfig.setInterface(HelloDubboService.class);
        referenceConfig.setUrl(properties.getDubboUrl());
        referenceConfig.setCheck(false);
        referenceConfig.setTimeout(3000);
        DubboBootstrap.getInstance().reference(referenceConfig);
        return referenceConfig;
    }

    @Bean
    HelloDubboService helloDubboService(ReferenceConfig<HelloDubboService> referenceConfig) {
        return referenceConfig.get();
    }
}
