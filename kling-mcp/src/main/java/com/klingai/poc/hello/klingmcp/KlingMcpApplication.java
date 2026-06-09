package com.klingai.poc.hello.klingmcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import com.klingai.poc.hello.klingfeignclient.KlingFeignClient;
import com.klingai.poc.hello.klingmcp.tools.DateTools;
import com.klingai.poc.hello.klingmcp.tools.KlingImageTools;
import com.klingai.poc.hello.klingmcp.tools.KlingVideoTools;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = KlingFeignClient.class)
public class KlingMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(KlingMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider klingToolCallbacks(
            DateTools dateTools,
            KlingVideoTools klingVideoTools,
            KlingImageTools klingImageTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTools, klingVideoTools, klingImageTools)
                .build();
    }
}
