/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.config.grpc;

import java.util.List;

/**
 * @author liuzunfei
 * @version $Id: ListenContextCallBack.java, v 0.1 2020年07月10日 1:14 PM liuzunfei Exp $
 */
public interface  ListenContextCallBack {

    /**
     * get all listen keys in current context, config ,naming etc..
     * @return
     */
    List<ListenContext> getAllListenContext();
}
