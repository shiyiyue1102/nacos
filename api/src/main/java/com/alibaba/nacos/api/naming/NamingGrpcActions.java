package com.alibaba.nacos.api.naming;

/**
 * @author liuzunfei
 * @version $Id: ConfigGrpcClient.java, v 0.1 2020年07月08日 4:15 PM liuzunfei Exp $
 */
public class NamingGrpcActions {

    public static final String SUBSCRIBE_SERVICE = "subscribeService";

    public static final String UNSUBSCRIBE_SERVICE = "unsubscribeService";

    public static final String REGISTER_INSTANCE = "registerInstance";

    public static final String DEREGISTER_INSTANCE = "deregisterInstance";

    public static final String UPDATE_INSTANCE = "updateInstance";

    public static final String QUERY_SERVICE = "queryService";

    public static final String CREATE_SERVICE = "createService";

    public static final String DELETE_SERVICE = "deleteService";

    public static final String UPDATE_SERVICE = "updateService";

    public static final String QUERY_LIST = "queryList";

    public static final String SERVER_HEALTHY = "serverHealthy";

    public static final String GET_SERVICE_LIST = "getServiceList";
}
