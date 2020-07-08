package com.alibaba.nacos.api.naming.listener;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

/**
 * @author liuzunfei
 * @version $Id: ConfigGrpcClient.java, v 0.1 2020年07月08日 4:15 PM liuzunfei Exp $
 */
public class ServerPushEvent implements Event {

    private ServiceInfo serviceInfo;

    public ServerPushEvent(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    public void setServiceInfo(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }
}
