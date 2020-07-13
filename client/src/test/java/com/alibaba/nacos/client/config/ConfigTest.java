/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.client.config;

import java.util.Date;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;

import org.junit.Ignore;
import org.junit.Test;

/**
 * @author nkorange
 */
public class ConfigTest {

    @Test
    @Ignore
    public void testServiceList() throws Exception {

        Properties properties = new Properties();
        //properties.put(PropertyKeyConst.SERVER_ADDR, "30.225.20.171:28848,30.225.20.171:38848");
        properties.put(PropertyKeyConst.SERVER_ADDR, "30.225.20.171:28848");
        //properties.put(PropertyKeyConst.NAMESPACE, "t1");

        ConfigService configService = NacosFactory.createConfigService(properties);

        final ExecutorService executorService = Executors.newSingleThreadExecutor();

        final String dataIdTest="configKeyPOC";
       // configService.publishConfig(dataIdTest2, "DEFAULT","pppp");

        System.out.println("初始化配置：dataId= :"+dataIdTest+", value =InitValue");

        configService.publishConfig(dataIdTest, "DEFAULT","InitValue");

        String config =  configService.getConfig(dataIdTest, "DEFAULT", 3000L);


        System.out.println("获取初始化的配置值,dataId= "+dataIdTest+",value="+config);

        System.out.println("开始监听配置变更：dataId="+dataIdTest);

        configService.addListener(dataIdTest, "DEFAULT", new Listener() {
            @Override
            public Executor getExecutor() {
                return executorService;
            }
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("监听到变更：dataId="+dataIdTest+"的新值变更为 :"+configInfo);
            }
        });

        System.out.println("修改发布新值： 'changevalue1'");

        configService.publishConfig(dataIdTest, "DEFAULT","changevalue1");
        System.out.println("修改发布新值成功：publish change success....");

        Thread.sleep(4000L);

        System.out.println("取消监听变更 ");

        configService.removeListener(dataIdTest, "DEFAULT", new Listener() {
            @Override
            public Executor getExecutor() {
                return executorService;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
               // System.out.println("[Unlistener ]recieve configinfo change to :"+configInfo);
            }
        });

        System.out.println("取消监听后发布变更：changevalue2");

        configService.publishConfig(dataIdTest, "DEFAULT","changevalue2");

        //Thread.sleep(5000L);


        String configAfterUnlisten = configService.getConfig(dataIdTest, "DEFAULT", 3000L);

        System.out.println("获取最新变更："+configAfterUnlisten);


        configService.addListener("configKeyPOC2", "DEFAULT", new Listener() {
            @Override
            public Executor getExecutor() {
                return executorService;
            }
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("监听到变更：dataId="+"configKeyPOC2"+"的新值变更为 :"+configInfo);
            }
        });

        System.out.println("开始监听配置变更：dataId=configKeyPOC2");

        configService.addListener("configKeyPOC3", "DEFAULT", new Listener() {
            @Override
            public Executor getExecutor() {
                return executorService;
            }
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("监听到变更：dataId="+"configKeyPOC3"+"的新值变更为 :"+configInfo);
            }
        });
        System.out.println("开始监听配置变更：dataId=configKeyPOC3");


        Thread.sleep(1000000L);

    }

}
