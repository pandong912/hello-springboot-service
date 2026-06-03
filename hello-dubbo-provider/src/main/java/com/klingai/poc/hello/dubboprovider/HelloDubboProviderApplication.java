package com.klingai.poc.hello.dubboprovider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableDubbo
@SpringBootApplication
public class HelloDubboProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloDubboProviderApplication.class, args);
    }
}
