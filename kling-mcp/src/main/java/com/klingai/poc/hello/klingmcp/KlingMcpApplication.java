package com.klingai.poc.hello.klingmcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KlingMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(KlingMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider klingToolCallbacks(DateTools dateTools, KlingVideoTools klingVideoTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTools, klingVideoTools)
                .build();
    }
}
