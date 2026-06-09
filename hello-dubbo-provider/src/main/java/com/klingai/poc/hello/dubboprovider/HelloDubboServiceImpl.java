package com.klingai.poc.hello.dubboprovider;

import org.apache.dubbo.config.annotation.DubboService;

import com.klingai.poc.hello.dubbo.HelloDubboService;

@DubboService
public class HelloDubboServiceImpl implements HelloDubboService {

    @Override
    public String sayHello(String name) {
        var resolvedName = name == null || name.isBlank() ? "dubbo client" : name;
        return "hello %s from dubbo triple on eks".formatted(resolvedName);
    }
}
