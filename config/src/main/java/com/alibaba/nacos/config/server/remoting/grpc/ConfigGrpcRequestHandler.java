/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.config.server.remoting.grpc;

import javax.annotation.PostConstruct;

import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;
import com.alibaba.nacos.config.server.remoting.ConfigCommonParams;
import com.alibaba.nacos.config.server.remoting.ConfigListenContext;
import com.alibaba.nacos.config.server.remoting.ConfigPushService;
import com.alibaba.nacos.config.server.remoting.ConfigRemotingActions;
import com.alibaba.nacos.core.remoting.grpc.impl.GrpcRequestHandler;
import com.alibaba.nacos.core.remoting.grpc.impl.RequestServiceGrpcImpl;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 *
 * gRPCRequestHandler implements in config domain.
 * @author liuzunfei
 * @version $Id: ConfigGrpcRequestHandler.java, v 0.1 2020年07月08日 3:18 PM liuzunfei Exp $
 */
@Service
public class ConfigGrpcRequestHandler implements GrpcRequestHandler  {


    @Autowired
    private RequestServiceGrpcImpl requestServiceGrpc;

    @Autowired
    private ConfigListenContext configListenContext;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    private ConfigPushService pushService;

    @PostConstruct
    public void init() {
        requestServiceGrpc.registerHandler("config", this);
    }

    @Override
    public GrpcResponse handle(GrpcRequest request) throws NacosException {

        String action = request.getAction();
        switch (action) {
            case ConfigRemotingActions.ADD_LISTENER:
                return listen(request);
            case ConfigRemotingActions.GET_CONFIG:
                return unlisten(request);
            case ConfigRemotingActions.PUBLISH_CONFIG:
                return publishConfig(request);
            case ConfigRemotingActions.REMOVE_CONFIG:
                return removeConfig(request);
            case ConfigRemotingActions.REMOVE_LISTENER:
                return unlisten(request);
            default:
                return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
        }
    }


    private GrpcResponse listen(GrpcRequest request){
        String clientId = request.getParamsOrDefault(ConfigCommonParams.CLIENT_ID,"");
        String group = request.getParamsOrDefault(ConfigCommonParams.GROUP,"DEFAULT");
        String dataId = request.getParamsOrDefault(ConfigCommonParams.DATA_ID,"");
        if (StringUtils.isNotBlank(clientId)&&StringUtils.isNotBlank(dataId)){
            configListenContext.registerListenClient(dataId,group,clientId);
        }

        GrpcResponse response = GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
        return response;
    }


    private GrpcResponse unlisten(GrpcRequest request){
        String clientId = request.getParamsOrDefault(ConfigCommonParams.CLIENT_ID,"");
        String group = request.getParamsOrDefault(ConfigCommonParams.GROUP,"DEFAULT");
        String dataId = request.getParamsOrDefault(ConfigCommonParams.DATA_ID,"");
        if (StringUtils.isNotBlank(clientId)&&StringUtils.isNotBlank(dataId)){
            configListenContext.unregisterListenClient(dataId,group,clientId);
        }

        GrpcResponse response = GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
        return response;
    }


    private GrpcResponse publishConfig(GrpcRequest request) {
        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }


    private GrpcResponse removeConfig(GrpcRequest request) {
        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }







}
