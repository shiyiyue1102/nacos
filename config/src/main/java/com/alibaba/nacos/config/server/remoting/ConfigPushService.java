/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.config.server.remoting;

import java.io.IOException;
import java.util.Set;

import com.alibaba.nacos.config.server.utils.JSONUtils;
import com.alibaba.nacos.core.remoting.ConnectionManager;
import com.alibaba.nacos.core.remoting.PushManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 *
 *
 * @author liuzunfei
 * @version $Id: ConfigPushService.java, v 0.1 2020年07月08日 3:29 PM liuzunfei Exp $
 */
@Component
public class ConfigPushService  {


    @Autowired
    private ConfigListenContext configListenContext;

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private PushManager pushManager;


    public void notifyConfigChange(String dataId,String group){

        Set<String> registerClients = configListenContext.getRegisterClient(dataId, group);
        if (!CollectionUtils.isEmpty(registerClients)){
            for (String clientId:registerClients){

                ConfigChangeReply reply = new ConfigChangeReply(dataId, group);
                try {
                    byte[] bytes = JSONUtils.serializeObject(reply).getBytes("UTF-8");
                    pushManager.pushChange(clientId,dataId,bytes);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class ConfigChangeReply{
        String group;
        String dataId;
        String changeType;
        ConfigChangeReply(String dataId,String group){
            this.group=group;
            this.dataId=dataId;
        }
    }

}
