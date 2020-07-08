package com.alibaba.nacos.api.naming;



/**
 * @author liuzunfei
 * @version $Id: ConfigGrpcClient.java, v 0.1 2020年07月08日 4:15 PM liuzunfei Exp $
 */
public enum NamingSuscribeType {

    /**
     * Default
     */
    NONE,
    /**
     * UDP wait
     */
    UDP,
    /**
     * GRPC wait
     */
    GRPC
}
