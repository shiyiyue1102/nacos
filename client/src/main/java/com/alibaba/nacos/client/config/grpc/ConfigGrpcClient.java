/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.config.grpc;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.connection.grpc.BaseGrpcClient;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
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

    private int port = 28849;


    private ListenContextCallBack  listenContextCallBack;


    public void registerListenContextCallBack(ListenContextCallBack  listenContextCallBack){
        this.listenContextCallBack=listenContextCallBack;
    }


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

        String server =nextServer();
        LOGGER.info("[GRPC ]init config listen stream.......,server list:"+server );

        this.channel = ManagedChannelBuilder.forAddress(server, port)
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

        relistenKeyIfNecessary();
    }

    void  relistenKeyIfNecessary(){
        if (listenContextCallBack!=null&& !CollectionUtils.isEmpty(listenContextCallBack.getAllListenContext())){

            System.out.println("[GRPC ]init listen context ......");
            LOGGER.info("[GRPC ]init listen context ......" );
            listenContextCallBack.getAllListenContext().forEach(new Consumer<ListenContext>() {
                @Override
                public void accept(ListenContext listenContext) {
                    try {
                        addListener(listenContext.dataId,listenContext.group);
                    } catch (NacosException e) {
                        LOGGER.info("[GRPC ]fail to relisten......." );
                        e.printStackTrace();
                    }
                }
            });
        }
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


    public GrpcResponse  getConfig(String dataId, String group) throws NacosException {
        GrpcRequest request = GrpcRequest.newBuilder()
            .setModule("config")
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("GET_CONFIG")
            .setAgent(UtilAndComs.VERSION)
            .putParams("group", group)
            .putParams("dataId", dataId)
            .build();
        return grpcServiceBlockingStub.request(request);

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
            LOGGER.error("[GRPC] config error", t);
            rebuildClient();
        }

        @Override
        public void onCompleted() {
            LOGGER.info("[GRPC] config connection closed.");
            rebuildClient();
        }
    }



    private String nextServer() {
        serverListManager.refreshCurrentServerAddr();
        String server = serverListManager.getCurrentServerAddr();
        if (server.contains("http")) {
            server = server.split(":")[1].replaceAll("//","");
        }else{
            server = server.split(":")[0];
        }
        return server;
    }
}


