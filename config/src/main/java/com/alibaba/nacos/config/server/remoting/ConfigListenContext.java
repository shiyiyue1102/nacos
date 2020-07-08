/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.config.server.remoting;

import java.util.Set;

/**
 * @author liuzunfei
 * @version $Id: ConfigListenContext.java, v 0.1 2020年07月08日 3:40 PM liuzunfei Exp $
 */
public interface ConfigListenContext {

    /**
     *
     * @param congifKey
     * @param clientId
     */
    void registerListenClient(String dataId,String group,String clientId);

    /**
     *
     * @param congifKey
     * @param clientId
     */
    void unregisterListenClient(String dataId,String group,String clientId);

    /**
     * get all listener clients for the specific configKey
     * @param configKey
     * @return
     */
    Set<String> getRegisterClient(String dataId,String group);

}


