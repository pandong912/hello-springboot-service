package com.klingai.poc.hello.klingmcpauthgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.klingai.poc.hello.klingmcpauthgateway.config.GatewayProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class KlingMcpAuthGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(KlingMcpAuthGatewayApplication.class, args);
    }
}
