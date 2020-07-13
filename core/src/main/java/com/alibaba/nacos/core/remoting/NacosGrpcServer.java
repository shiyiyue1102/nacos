package com.alibaba.nacos.core.remoting;

import java.io.IOException;
import java.util.Properties;

import javax.annotation.PostConstruct;

import com.alibaba.nacos.core.remoting.grpc.impl.RequestGrpcIntercepter;
import com.alibaba.nacos.core.remoting.grpc.impl.RequestServiceGrpcImpl;
import com.alibaba.nacos.core.remoting.grpc.impl.StreamGrpcIntercepter;
import com.alibaba.nacos.core.remoting.grpc.impl.StreamServiceGrpcImpl;
import com.alibaba.nacos.core.utils.Loggers;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NacosGrpcServer {

    private static final int DEFAULT_PORT = 18849;

    private Server server;

    @Autowired
    private RequestGrpcIntercepter requestGrpcIntercepter;

    @Autowired
    private StreamGrpcIntercepter streamGrpcIntercepter;

    @Autowired
    private RequestServiceGrpcImpl requestServiceGrpc;

    @Autowired
    private StreamServiceGrpcImpl streamServiceGrpc;

    @PostConstruct
    public void start() throws IOException {

        Loggers.GRPC.info("Nacos gRPC server starting...");

        Properties properties = System.getProperties();

        int serverPort=DEFAULT_PORT;
        String appointedPort=properties.getProperty("gRPCServerPort");
        if (NumberUtils.isDigits(appointedPort)){
            serverPort=NumberUtils.toInt(appointedPort);
        }

        server = ServerBuilder.forPort(serverPort)
            .addService(requestServiceGrpc)
            .addService(streamServiceGrpc)
            .build();
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {

                System.out.println("Stopping Nacos gRPC server...");
                NacosGrpcServer.this.stop();
                System.out.println("Nacos gRPC server stopped...");
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}
