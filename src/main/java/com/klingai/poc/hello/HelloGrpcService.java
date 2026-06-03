package com.klingai.poc.hello;

import com.klingai.poc.hello.grpc.HelloRpcGrpc;
import com.klingai.poc.hello.grpc.SayHelloRequest;
import com.klingai.poc.hello.grpc.SayHelloResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class HelloGrpcService extends HelloRpcGrpc.HelloRpcImplBase {

    @Override
    public void sayHello(SayHelloRequest request, StreamObserver<SayHelloResponse> responseObserver) {
        var name = request.getName().isBlank() ? "grpc client" : request.getName();
        var response = SayHelloResponse.newBuilder()
                .setMessage("hello %s from grpc on eks".formatted(name))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
