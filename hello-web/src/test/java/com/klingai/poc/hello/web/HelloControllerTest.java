package com.klingai.poc.hello.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.klingai.poc.hello.dubbo.HelloDubboService;
import com.klingai.poc.hello.grpc.HelloRpcGrpc;
import com.klingai.poc.hello.grpc.SayHelloRequest;
import com.klingai.poc.hello.grpc.SayHelloResponse;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HelloRpcGrpc.HelloRpcBlockingStub grpcClient;

    @MockBean
    private HelloDubboService dubboClient;

    @Test
    void helloReturnsExpectedMessage() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello from spring boot web on eks"));
    }

    @Test
    void aggregateReturnsAllProtocolResponses() throws Exception {
        when(grpcClient.sayHello(ArgumentMatchers.any(SayHelloRequest.class)))
                .thenReturn(SayHelloResponse.newBuilder().setMessage("hello EKS from grpc on eks").build());
        when(dubboClient.sayHello("EKS")).thenReturn("hello EKS from dubbo triple on eks");

        mockMvc.perform(get("/hello/aggregate").param("name", "EKS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.web").value("hello from spring boot web on eks"))
                .andExpect(jsonPath("$.grpc").value("hello EKS from grpc on eks"))
                .andExpect(jsonPath("$.dubbo").value("hello EKS from dubbo triple on eks"));
    }
}
