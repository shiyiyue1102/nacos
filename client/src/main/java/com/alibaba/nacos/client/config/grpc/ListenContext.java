/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.config.grpc;

/**
 * @author liuzunfei
 * @version $Id: ListenContext.java, v 0.1 2020年07月10日 1:23 PM liuzunfei Exp $
 */
public  class ListenContext{


    String dataId;
    String group;

    /**
     * Getter method for property <tt>dataId</tt>.
     *
     * @return property value of dataId
     */
    public String getDataId() {
        return dataId;
    }

    /**
     * Setter method for property <tt>dataId</tt>.
     *
     * @param dataId value to be assigned to property dataId
     */
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    /**
     * Getter method for property <tt>group</tt>.
     *
     * @return property value of group
     */
    public String getGroup() {
        return group;
    }

    /**
     * Setter method for property <tt>group</tt>.
     *
     * @param group value to be assigned to property group
     */
    public void setGroup(String group) {
        this.group = group;
    }
}
