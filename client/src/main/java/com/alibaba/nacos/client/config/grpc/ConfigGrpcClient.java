/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.config.grpc;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.connection.grpc.BaseGrpcClient;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.grpc.GrpcMetadata;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;
import com.alibaba.nacos.common.grpc.GrpcServiceGrpc;
import com.alibaba.nacos.common.grpc.GrpcStreamServiceGrpc;
import com.alibaba.nacos.common.utils.UuidUtils;

import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;

/**
 * @author liuzunfei
 * @version $Id: ConfigGrpcClient.java, v 0.1 2020年07月08日 4:15 PM liuzunfei Exp $
 */
public class ConfigGrpcClient extends BaseGrpcClient {


    private static final Logger LOGGER = LogUtils.logger(ConfigGrpcClient.class);

    private ServerListManager serverListManager;

    private AbstractStreamMessageHandler abstractStreamMessageHandler;

    private int port = 18849;

    public ConfigGrpcClient(ServerListManager serverListManager){

        super(UuidUtils.generateUuid());

        LOGGER.info("client initializing...." );

        this.serverListManager=serverListManager;
        buildClient();

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.config.grpc.worker");
                t.setDaemon(true);
                return t;
            }
        });

        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                sendBeat();
            }
        }, 5000, 5000, TimeUnit.MILLISECONDS);


        LOGGER.info("[ConfigGrpcClient ] started successfully......." );

    }

    public void initResponseHandler(AbstractStreamMessageHandler abstractStreamMessageHandler){
        this.abstractStreamMessageHandler=abstractStreamMessageHandler;
    }

    private void buildClient() {

        this.channel = ManagedChannelBuilder.forAddress(nextServer(), this.port)
            .usePlaintext(true)
            .build();

        GrpcMetadata grpcMetadata = GrpcMetadata.newBuilder().putLabels("type", "config").build();

        grpcStreamServiceStub = GrpcStreamServiceGrpc.newStub(channel);

        grpcServiceBlockingStub = GrpcServiceGrpc.newBlockingStub(channel);

        GrpcRequest request = GrpcRequest.newBuilder()
            .setModule("config")
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("registerClient")
            .setMetadata(grpcMetadata)
            .build();

        LOGGER.info("[GRPC ]init config listen stream......." );

        grpcStreamServiceStub.streamRequest(request, new ConfigStreamServer());
    }



    public void removeListener(String dataId, String group) throws NacosException {
        GrpcRequest request = GrpcRequest.newBuilder()
            .setModule("config")
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("REMOVE_LISTENER")
            .setAgent(UtilAndComs.VERSION)
            .putParams("group", group)
            .putParams("dataId", dataId)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), getMessage(response));
        }
    }

    public void addListener(String dataId, String group) throws NacosException {
        GrpcRequest request = GrpcRequest.newBuilder()
            .setModule("config")
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("ADD_LISTENER")
            .setAgent(UtilAndComs.VERSION)
            .putParams("group", group)
            .putParams("dataId", dataId)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), getMessage(response));
        }
    }


    private String getMessage(GrpcResponse response) {
        String message;
        try {
            message = response.getMessage().getValue().toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            message = e.getMessage();
        }
        return message;
    }


    private void rebuildClient() {
        buildClient();
    }

    private class ConfigStreamServer implements StreamObserver<GrpcResponse> {

        @Override
        public void onNext(GrpcResponse value) {
            LOGGER.info("[GRPC] receive config data: " + value.toString());
            String message = value.getMessage().getValue().toStringUtf8();
            JSONObject json = JSON.parseObject(message.trim());
            LOGGER.info("[GRPC] receive config data: " + json);
            abstractStreamMessageHandler.onResponse(json);
        }


        @Override
        public void onError(Throwable t) {
            LOGGER.error("[GRPC] error", t);
            rebuildClient();
        }

        @Override
        public void onCompleted() {
            LOGGER.info("[GRPC] connection closed.");
            rebuildClient();
        }
    }



    private String nextServer() {
        serverListManager.refreshCurrentServerAddr();
        String server = serverListManager.getCurrentServerAddr();
        if (server.contains(":")) {
            server = server.split(":")[0];
        }
        return server;
    }
}
