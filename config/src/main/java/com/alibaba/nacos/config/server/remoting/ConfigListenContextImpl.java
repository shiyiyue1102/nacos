/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.config.server.remoting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * @author liuzunfei
 * @version $Id: ConfigListenContextImpl.java, v 0.1 2020年07月08日 3:50 PM liuzunfei Exp $
 */
@Component
public class ConfigListenContextImpl implements ConfigListenContext{

    /**
     * listeneClients, Map<String(configKey), Set<String(ClientId)>>
     */
    private Map<String, Set<String>>  listenClients=new HashMap<String,Set<String>>();

    @Override
    public void registerListenClient(String dataId,String group, String clientId) {
        String configKey=group+"_"+dataId;
        if (!listenClients.containsKey(configKey)){
            listenClients.put(configKey,new HashSet<>());
        }
        listenClients.get(configKey).add(clientId);
    }

    @Override
    public void unregisterListenClient(String dataId,String group, String clientId) {
        String configKey=group+"_"+dataId;
        if (listenClients.containsKey(configKey)){
            listenClients.get(configKey).remove(clientId);
        }
    }

    @Override
    public Set<String> getRegisterClient(String dataId,String group) {
        String configKey=group+"_"+dataId;
        return listenClients.get(configKey);
    }
}
