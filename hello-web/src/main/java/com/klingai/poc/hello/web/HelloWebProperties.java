package com.klingai.poc.hello.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hello")
public class HelloWebProperties {

    private String grpcTarget = "hello-grpc.hello.svc.cluster.local:9090";
    private String dubboUrl = "tri://hello-dubbo.hello.svc.cluster.local:50051";

    public String getGrpcTarget() {
        return grpcTarget;
    }

    public void setGrpcTarget(String grpcTarget) {
        this.grpcTarget = grpcTarget;
    }

    public String getDubboUrl() {
        return dubboUrl;
    }

    public void setDubboUrl(String dubboUrl) {
        this.dubboUrl = dubboUrl;
    }
}
