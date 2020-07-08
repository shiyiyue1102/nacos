/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.config.grpc;

import com.alibaba.fastjson.JSONObject;

/**
 * @author liuzunfei
 * @version $Id: AbstractStreamMessageHandler.java, v 0.1 2020年07月08日 5:23 PM liuzunfei Exp $
 */
public interface AbstractStreamMessageHandler {


    void onResponse(JSONObject response );
}
