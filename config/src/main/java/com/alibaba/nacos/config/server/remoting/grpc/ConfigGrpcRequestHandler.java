/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.config.server.remoting.grpc;

import java.io.File;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import javax.annotation.PostConstruct;

import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.grpc.GrpcMetadata;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;
import com.alibaba.nacos.common.grpc.GrpcResponse.Builder;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.remoting.ConfigCommonParams;
import com.alibaba.nacos.config.server.remoting.ConfigListenContext;
import com.alibaba.nacos.config.server.remoting.ConfigPushService;
import com.alibaba.nacos.config.server.remoting.ConfigRemotingActions;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.DiskUtil;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.remoting.grpc.impl.GrpcRequestHandler;
import com.alibaba.nacos.core.remoting.grpc.impl.RequestServiceGrpcImpl;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import static com.alibaba.nacos.config.server.utils.LogUtil.pullLog;
import static com.alibaba.nacos.core.utils.SystemUtils.STANDALONE_MODE;

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
    private PersistService persistService;

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

        System.out.println("handler  request :"+request);

        String action = request.getAction();
        switch (action) {
            case ConfigRemotingActions.ADD_LISTENER:
                return listen(request);
            case ConfigRemotingActions.GET_CONFIG:
                GrpcResponse content=null;
                try {
                     content = getContent(request);
                    System.out.println("get content result :"+content);

                }catch(Exception e){
                    System.out.println("get content fail :"+e.getMessage());
                    e.printStackTrace();
                }
                return content;
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
        System.out.println("receive listen requst:"+request);
        String clientId = request.getClientId();
        String group = request.getParamsOrDefault(ConfigCommonParams.GROUP,"DEFAULT");
        String dataId = request.getParamsOrDefault(ConfigCommonParams.DATA_ID,"");
        if (StringUtils.isNotBlank(clientId)&&StringUtils.isNotBlank(dataId)){
            configListenContext.registerListenClient(dataId,group,clientId);
        }

        GrpcResponse response = GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
        return response;
    }


    private GrpcResponse unlisten(GrpcRequest request){
        String clientId = request.getClientId();
        String group = request.getParamsOrDefault(ConfigCommonParams.GROUP,"DEFAULT");
        String dataId = request.getParamsOrDefault(ConfigCommonParams.DATA_ID,"");
        if (StringUtils.isNotBlank(clientId)&&StringUtils.isNotBlank(dataId)){
            configListenContext.unregisterListenClient(dataId,group,clientId);
        }

        GrpcResponse response = GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
        return response;
    }



    /**
     * serverside get config, refer to ConfigServletInner#doGetConfig
     * @param request
     * @return
     * @throws UnsupportedEncodingException
     */
    private GrpcResponse getContent(GrpcRequest request) {



        String clientId = request.getParamsOrDefault(ConfigCommonParams.CLIENT_ID,"");
        String group = request.getParamsOrDefault(ConfigCommonParams.GROUP,"DEFAULT");
        String dataId = request.getParamsOrDefault(ConfigCommonParams.DATA_ID,"");

        GrpcMetadata metadata = request.getMetadata();
        String autoTag = metadata.getLabelsMap().get("Vipserver-Tag");
        String tag = metadata.getLabelsMap().get("tag");
        String tenant = metadata.getLabelsMap().get("tenant");
        String clientIp = metadata.getLabelsMap().get("clientIp");

        String requestIpApp = metadata.getLabelsMap().get("requestIpApp");

        String groupKey = GroupKey2.getKey(dataId, group);
        int lockResult = tryConfigReadLock(groupKey);

        String content=null;
        boolean isBeta = false;
        if (lockResult > 0) {
            try {
                String md5 = Constants.NULL;
                long lastModified = 0L;
                CacheItem cacheItem = ConfigService.getContentCache(groupKey);
                if (cacheItem != null) {
                    if (cacheItem.isBeta()) {
                        if (cacheItem.getIps4Beta().contains(clientIp)) {
                            isBeta = true;
                        }
                    }
                    String configType = cacheItem.getType();
                }
                File file = null;
                ConfigInfoBase configInfoBase = null;
                PrintWriter out = null;
                if (isBeta) {
                    md5 = cacheItem.getMd54Beta();
                    lastModified = cacheItem.getLastModifiedTs4Beta();
                    if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                        configInfoBase = persistService.findConfigInfo4Beta(dataId, group, tenant);
                    } else {
                        file = DiskUtil.targetBetaFile(dataId, group, tenant);
                    }
                    // TODO response.setHeader("isBeta", "true");
                } else {
                    if (org.apache.commons.lang3.StringUtils.isBlank(tag)) {
                        if (isUseTag(cacheItem, autoTag)) {
                            if (cacheItem != null) {
                                if (cacheItem.tagMd5 != null) {
                                    md5 = cacheItem.tagMd5.get(autoTag);
                                }
                                if (cacheItem.tagLastModifiedTs != null) {
                                    lastModified = cacheItem.tagLastModifiedTs.get(autoTag);
                                }
                            }
                            if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                                configInfoBase = persistService.findConfigInfo4Tag(dataId, group, tenant, autoTag);
                            } else {
                                file = DiskUtil.targetTagFile(dataId, group, tenant, autoTag);
                            }

                            // response.setHeader("Vipserver-Tag",
                             //   URLEncoder.encode(autoTag, StandardCharsets.UTF_8.displayName()));
                        } else {
                            md5 = cacheItem.getMd5();
                            lastModified = cacheItem.getLastModifiedTs();
                            if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                                configInfoBase = persistService.findConfigInfo(dataId, group, tenant);
                            } else {
                                file = DiskUtil.targetFile(dataId, group, tenant);
                            }
                            if (configInfoBase == null && fileNotExist(file)) {
                                // FIXME CacheItem
                                // 不存在了无法简单的计算推送delayed，这里简单的记做-1
                                //ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                                //    ConfigTraceService.PULL_EVENT_NOTFOUND, -1, clientIp);

                            }
                        }
                    } else {
                        if (cacheItem != null) {
                            if (cacheItem.tagMd5 != null) {
                                md5 = cacheItem.tagMd5.get(tag);
                            }
                            if (cacheItem.tagLastModifiedTs != null) {
                                Long lm = cacheItem.tagLastModifiedTs.get(tag);
                                if (lm != null) {
                                    lastModified = lm;
                                }
                            }
                        }
                        if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                            configInfoBase = persistService.findConfigInfo4Tag(dataId, group, tenant, tag);
                        } else {
                            file = DiskUtil.targetTagFile(dataId, group, tenant, tag);
                        }
                        if (configInfoBase == null && fileNotExist(file)) {
                            // FIXME CacheItem
                            // 不存在了无法简单的计算推送delayed，这里简单的记做-1
                            //ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                            //    ConfigTraceService.PULL_EVENT_NOTFOUND,
                            //    -1, clientIp);

                            // pullLog.info("[client-get] clientIp={}, {},
                            // no data",
                            // new Object[]{clientIp, groupKey});


                        }
                    }
                }


                content=configInfoBase.getContent();
                final long delayed = System.currentTimeMillis() - lastModified;

                // TODO distinguish pull-get && push-get
                // 否则无法直接把delayed作为推送延时的依据，因为主动get请求的delayed值都很大
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, lastModified,
                    ConfigTraceService.PULL_EVENT_OK, delayed,
                    clientIp);

            } finally {
                releaseConfigReadLock(groupKey);
            }
        } else if (lockResult == 0) {

            // FIXME CacheItem 不存在了无法简单的计算推送delayed，这里简单的记做-1
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                ConfigTraceService.PULL_EVENT_NOTFOUND, -1, clientIp);

        } else {
            pullLog.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
        }

        Builder builder = GrpcResponse.newBuilder().setClientId(clientId).setMetadata(metadata);
        if (content!=null){
            try {
                builder.setMessage(Any.newBuilder().setValue(ByteString.copyFrom(content.getBytes("UTF-8"))));
                builder.setCode(ResponseCode.OK);
            } catch (UnsupportedEncodingException e) {
                builder.setCode(ResponseCode.ERROR_UNKNOWN);
                e.printStackTrace();
            }
        }

        return builder.build();


    }

    private static boolean isUseTag(CacheItem cacheItem, String tag) {
        if (cacheItem != null && cacheItem.tagMd5 != null && cacheItem.tagMd5.size() > 0) {
            return org.apache.commons.lang3.StringUtils.isNotBlank(tag) && cacheItem.tagMd5.containsKey(tag);
        }
        return false;
    }


    private static final int TRY_GET_LOCK_TIMES = 9;


    private static boolean fileNotExist(File file) {
        return file == null || !file.exists();
    }

    private static int tryConfigReadLock(String groupKey) {
        /**
         *  默认加锁失败
         */
        int lockResult = -1;
        /**
         *  尝试加锁，最多10次
         */
        for (int i = TRY_GET_LOCK_TIMES; i >= 0; --i) {
            lockResult = ConfigService.tryReadLock(groupKey);
            /**
             *  数据不存在
             */
            if (0 == lockResult) {
                break;
            }

            /**
             *  success
             */
            if (lockResult > 0) {
                break;
            }
            /**
             *  retry
             */
            if (i > 0) {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                }
            }
        }

        return lockResult;
    }


    private static void releaseConfigReadLock(String groupKey) {
        ConfigService.releaseReadLock(groupKey);
    }

    
    private GrpcResponse publishConfig(GrpcRequest request) {
        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }


    private GrpcResponse removeConfig(GrpcRequest request) {
        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }







}
