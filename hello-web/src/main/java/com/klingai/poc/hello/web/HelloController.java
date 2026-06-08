package com.klingai.poc.hello.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.klingai.poc.hello.dubbo.HelloDubboService;
import com.klingai.poc.hello.grpc.HelloRpcGrpc;
import com.klingai.poc.hello.grpc.SayHelloRequest;

@RestController
public class HelloController {

    private final HelloRpcGrpc.HelloRpcBlockingStub grpcClient;
    private final HelloDubboService dubboClient;

    public HelloController(HelloRpcGrpc.HelloRpcBlockingStub grpcClient, HelloDubboService dubboClient) {
        this.grpcClient = grpcClient;
        this.dubboClient = dubboClient;
    }

    @GetMapping("/hello")
    public String hello() {
        return "hello from spring boot web on eks";
    }

    @GetMapping("/hello/grpc")
    public String helloGrpc(@RequestParam(name = "name", defaultValue = "EKS") String name) {
        var request = SayHelloRequest.newBuilder().setName(name).build();
        return grpcClient.sayHello(request).getMessage();
    }

    @GetMapping("/hello/dubbo")
    public String helloDubbo(@RequestParam(name = "name", defaultValue = "EKS") String name) {
        return dubboClient.sayHello(name);
    }

    @GetMapping("/hello/aggregate")
    public Map<String, String> helloAggregate(@RequestParam(name = "name", defaultValue = "EKS") String name) {
        return Map.of(
                "web", hello(),
                "grpc", helloGrpc(name),
                "dubbo", helloDubbo(name)
        );
    }
}
