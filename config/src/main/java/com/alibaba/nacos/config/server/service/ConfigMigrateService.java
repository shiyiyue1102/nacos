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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConfigCompatibleConfig;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigMigratePersistService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.alibaba.nacos.config.server.utils.PropertyUtil.CONFIG_MIGRATE_FLAG;
import static com.alibaba.nacos.config.server.utils.PropertyUtil.GRAY_MIGRATE_FLAG;

/**
 * Handle namespace compatibility migration for config and gray config.
 *
 * @author shiyiyue
 */
@Service
public class ConfigMigrateService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMigrateService.class);
    
    private static final String NAMESPACE_MIGRATE_SRC_USER = "nacos_namespace_migrate";
    
    private final String namespacePublic = "public";
    
    /**
     * The Config info gray persist service.
     */
    ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    /**
     * The Config info persist service.
     */
    ConfigInfoPersistService configInfoPersistService;
    
    /**
     * The Config migrate persist service.
     */
    ConfigMigratePersistService configMigratePersistService;
    
    /**
     * The Namespace persist service.
     */
    NamespacePersistService namespacePersistService;
    
    /**
     * Instantiates a new Config migrate service.
     *
     * @param configInfoGrayPersistService the config info gray persist service
     * @param configMigratePersistService  the config migrate persist service
     * @param namespacePersistService      the namespace persist service
     * @param configInfoPersistService     the config info persist service
     */
    public ConfigMigrateService(ConfigInfoGrayPersistService configInfoGrayPersistService,
            ConfigMigratePersistService configMigratePersistService, NamespacePersistService namespacePersistService,
             ConfigInfoPersistService configInfoPersistService) {
        this.configInfoGrayPersistService = configInfoGrayPersistService;
        this.configMigratePersistService = configMigratePersistService;
        this.namespacePersistService = namespacePersistService;
        this.configInfoPersistService = configInfoPersistService;
    }
    
    /**
     * Run startup migration checks for namespace compatibility.
     *
     * @throws Exception the exception
     */
    @PostConstruct
    public void migrate() throws Exception {
        if (ConfigCompatibleConfig.getInstance().isNamespaceCompatibleMode()) {
            doCheckNamespaceMigrate();
        }
    }
    /**
     * Check changed config gray migrate state.
     *
     * @param changedConfigInfoGrayWrapper the changed config info gray wrapper
     */
    public void checkChangedConfigGrayMigrateState(ConfigInfoGrayWrapper changedConfigInfoGrayWrapper) {
        String tenant = changedConfigInfoGrayWrapper.getTenant();
        if (!ConfigCompatibleConfig.getInstance().isNamespaceCompatibleMode()) {
            return;
        }
        if (!StringUtils.equals(tenant, namespacePublic) && StringUtils.isNotBlank(tenant)) {
            return;
        }
        String targetTenant = StringUtils.EMPTY;
        if (StringUtils.isBlank(tenant)) {
            targetTenant = namespacePublic;
        }
        ConfigInfoGrayWrapper targetConfigInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                changedConfigInfoGrayWrapper.getDataId(), changedConfigInfoGrayWrapper.getGroup(), targetTenant,
                changedConfigInfoGrayWrapper.getGrayName());
        try {
            GRAY_MIGRATE_FLAG.set(true);
            if (StringUtils.equals(changedConfigInfoGrayWrapper.getSrcUser(), NAMESPACE_MIGRATE_SRC_USER)) {
                if (targetConfigInfoGrayWrapper == null) {
                    configInfoGrayPersistService.removeConfigInfoGray(changedConfigInfoGrayWrapper.getDataId(),
                            changedConfigInfoGrayWrapper.getGroup(), tenant, changedConfigInfoGrayWrapper.getGrayName(),
                            null, NAMESPACE_MIGRATE_SRC_USER);
                } else if (!targetConfigInfoGrayWrapper.getMd5().equals(changedConfigInfoGrayWrapper.getMd5())
                        || !targetConfigInfoGrayWrapper.getGrayRule()
                        .equals(changedConfigInfoGrayWrapper.getGrayRule())) {
                    if (targetConfigInfoGrayWrapper.getLastModified() >= changedConfigInfoGrayWrapper.getLastModified()
                            || !StringUtils.equals(targetConfigInfoGrayWrapper.getSrcUser(),
                            NAMESPACE_MIGRATE_SRC_USER)) {
                        targetConfigInfoGrayWrapper.setTenant(tenant);
                        configInfoGrayPersistService.updateConfigInfo4Gray(targetConfigInfoGrayWrapper,
                                targetConfigInfoGrayWrapper.getGrayName(), targetConfigInfoGrayWrapper.getGrayRule(),
                                null, NAMESPACE_MIGRATE_SRC_USER);
                    }
                }
            } else {
                if (targetConfigInfoGrayWrapper == null) {
                    changedConfigInfoGrayWrapper.setTenant(targetTenant);
                    configInfoGrayPersistService.addConfigInfo4Gray(changedConfigInfoGrayWrapper,
                            changedConfigInfoGrayWrapper.getGrayName(), changedConfigInfoGrayWrapper.getGrayRule(),
                            null, NAMESPACE_MIGRATE_SRC_USER);
                } else if (!targetConfigInfoGrayWrapper.getMd5().equals(changedConfigInfoGrayWrapper.getMd5())
                        || !targetConfigInfoGrayWrapper.getGrayRule()
                        .equals(changedConfigInfoGrayWrapper.getGrayRule())) {
                    if (targetConfigInfoGrayWrapper.getLastModified() >= changedConfigInfoGrayWrapper.getLastModified()
                            && !StringUtils.equals(targetConfigInfoGrayWrapper.getSrcUser(),
                            NAMESPACE_MIGRATE_SRC_USER)) {
                        targetConfigInfoGrayWrapper.setTenant(tenant);
                        configInfoGrayPersistService.updateConfigInfo4Gray(targetConfigInfoGrayWrapper,
                                targetConfigInfoGrayWrapper.getGrayName(), targetConfigInfoGrayWrapper.getGrayRule(),
                                null, NAMESPACE_MIGRATE_SRC_USER);
                    } else if (targetConfigInfoGrayWrapper.getLastModified() < changedConfigInfoGrayWrapper.getLastModified()) {
                        changedConfigInfoGrayWrapper.setTenant(targetTenant);
                        configInfoGrayPersistService.updateConfigInfo4Gray(changedConfigInfoGrayWrapper,
                                changedConfigInfoGrayWrapper.getGrayName(), changedConfigInfoGrayWrapper.getGrayRule(),
                                null, NAMESPACE_MIGRATE_SRC_USER);
                    }
                }
            }
        } finally {
            GRAY_MIGRATE_FLAG.set(false);
        }
    }
    
    /**
     * Check changed config migrate state.
     *
     * @param changedConfigInfoStateWrapper the config info state wrapper
     */
    public void checkChangedConfigMigrateState(ConfigInfoStateWrapper changedConfigInfoStateWrapper) {
        String tenant = changedConfigInfoStateWrapper.getTenant();
        if (!ConfigCompatibleConfig.getInstance().isNamespaceCompatibleMode()) {
            return;
        }
        if (!StringUtils.equals(tenant, namespacePublic) && StringUtils.isNotBlank(tenant)) {
            return;
        }
        String targetTenant = StringUtils.EMPTY;
        if (StringUtils.isBlank(tenant)) {
            targetTenant = namespacePublic;
        }
        ConfigAllInfo changedConfigAllInfo = configInfoPersistService.findConfigAllInfo(
                changedConfigInfoStateWrapper.getDataId(), changedConfigInfoStateWrapper.getGroup(), tenant);
        ConfigAllInfo targetConfigAllInfo = configInfoPersistService.findConfigAllInfo(
                changedConfigInfoStateWrapper.getDataId(), changedConfigInfoStateWrapper.getGroup(), targetTenant);
        try {
            CONFIG_MIGRATE_FLAG.set(true);
            if (NAMESPACE_MIGRATE_SRC_USER.equals(changedConfigAllInfo.getCreateUser())) {
                if (targetConfigAllInfo == null) {
                    configInfoPersistService.removeConfigInfo(changedConfigAllInfo.getDataId(),
                            changedConfigAllInfo.getGroup(), tenant, null, NAMESPACE_MIGRATE_SRC_USER);
                } else if (!targetConfigAllInfo.getMd5().equals(changedConfigAllInfo.getMd5())) {
                    if (targetConfigAllInfo.getModifyTime() >= changedConfigAllInfo.getModifyTime()
                            || !StringUtils.equals(targetConfigAllInfo.getCreateUser(), NAMESPACE_MIGRATE_SRC_USER)) {
                        targetConfigAllInfo.setTenant(tenant);
                        configInfoPersistService.updateConfigInfo(targetConfigAllInfo, null, NAMESPACE_MIGRATE_SRC_USER,
                                null);
                    }
                }
            } else {
                if (targetConfigAllInfo == null) {
                    changedConfigAllInfo.setTenant(targetTenant);
                    configInfoPersistService.addConfigInfo(null, NAMESPACE_MIGRATE_SRC_USER, changedConfigAllInfo,
                            null);
                } else if (!targetConfigAllInfo.getMd5().equals(changedConfigAllInfo.getMd5())) {
                    if (targetConfigAllInfo.getModifyTime() >= changedConfigAllInfo.getModifyTime()
                            && !StringUtils.equals(targetConfigAllInfo.getCreateUser(), NAMESPACE_MIGRATE_SRC_USER)) {
                        targetConfigAllInfo.setTenant(tenant);
                        configInfoPersistService.updateConfigInfo(targetConfigAllInfo, null, NAMESPACE_MIGRATE_SRC_USER,
                                null);
                    } else if (targetConfigAllInfo.getModifyTime() < changedConfigAllInfo.getModifyTime()) {
                        changedConfigAllInfo.setTenant(targetTenant);
                        configInfoPersistService.updateConfigInfo(changedConfigAllInfo, null,
                                NAMESPACE_MIGRATE_SRC_USER, null);
                    }
                }
            }
        } finally {
            CONFIG_MIGRATE_FLAG.set(false);
        }
        
    }
    
    /**
     * Check deleted config gray migrate state.
     *
     * @param deletedConfigInfoGrayStateWrapper the deleted config info gray state wrapper
     */
    public void checkDeletedConfigGrayMigrateState(ConfigInfoStateWrapper deletedConfigInfoGrayStateWrapper) {
        if (deletedConfigInfoGrayStateWrapper == null) {
            return;
        }
        String tenant = deletedConfigInfoGrayStateWrapper.getTenant();
        if (!ConfigCompatibleConfig.getInstance().isNamespaceCompatibleMode()) {
            return;
        }
        if (!StringUtils.equals(tenant, namespacePublic) && StringUtils.isNotBlank(tenant)) {
            return;
        }
        String targetTenant = StringUtils.EMPTY;
        if (StringUtils.isBlank(tenant)) {
            targetTenant = namespacePublic;
        }
        ConfigInfoStateWrapper targetConfigInfoGrayStateWrapper = configInfoGrayPersistService.findConfigInfo4GrayState(
                deletedConfigInfoGrayStateWrapper.getDataId(), deletedConfigInfoGrayStateWrapper.getGroup(),
                targetTenant, deletedConfigInfoGrayStateWrapper.getGrayName());
        if (targetConfigInfoGrayStateWrapper == null) {
            return;
        }
        
        try {
            GRAY_MIGRATE_FLAG.set(true);
            if (targetConfigInfoGrayStateWrapper.getLastModified()
                    <= deletedConfigInfoGrayStateWrapper.getLastModified()) {
                configInfoGrayPersistService.removeConfigInfoGray(deletedConfigInfoGrayStateWrapper.getDataId(),
                        deletedConfigInfoGrayStateWrapper.getGroup(), targetTenant,
                        deletedConfigInfoGrayStateWrapper.getGrayName(), null, NAMESPACE_MIGRATE_SRC_USER);
            }
        } finally {
            GRAY_MIGRATE_FLAG.set(false);
        }
    }
    
    /**
     * Check deleted config migrate state.
     *
     * @param deletedConfigInfoStateWrapper the deleted config info state wrapper
     */
    public void checkDeletedConfigMigrateState(ConfigInfoStateWrapper deletedConfigInfoStateWrapper) {
        String tenant = deletedConfigInfoStateWrapper.getTenant();
        if (!ConfigCompatibleConfig.getInstance().isNamespaceCompatibleMode()) {
            return;
        }
        if (!StringUtils.equals(tenant, namespacePublic) && StringUtils.isNotBlank(tenant)) {
            return;
        }
        String targetTenant = StringUtils.EMPTY;
        if (StringUtils.isBlank(tenant)) {
            targetTenant = namespacePublic;
        }
        ConfigInfoStateWrapper targetConfigInfoStateWrapper = configInfoPersistService.findConfigInfoState(
                deletedConfigInfoStateWrapper.getDataId(), deletedConfigInfoStateWrapper.getGroup(), targetTenant);
        if (targetConfigInfoStateWrapper == null) {
            return;
        }
        try {
            CONFIG_MIGRATE_FLAG.set(true);
            if (targetConfigInfoStateWrapper.getLastModified() <= deletedConfigInfoStateWrapper.getLastModified()) {
                configInfoPersistService.removeConfigInfo(deletedConfigInfoStateWrapper.getDataId(),
                        deletedConfigInfoStateWrapper.getGroup(), targetTenant, null, NAMESPACE_MIGRATE_SRC_USER);
            }
        } finally {
            CONFIG_MIGRATE_FLAG.set(false);
        }
        
    }
    
    private void doCheckNamespaceMigrate() throws Exception {
        final long startTime = System.currentTimeMillis();
        int maxNamespaceMigrateRetryTimes = EnvUtil.getProperty("nacos.namespace.migrate.retry.times", Integer.class,
                3);
        namespaceMigratePreCheck(maxNamespaceMigrateRetryTimes);
        int batchSize = EnvUtil.getProperty("nacos.namespace.migrate.batch.size", Integer.class, 100);
        long startId = -1;
        List<Long> batchIds = new ArrayList<>();
        int totalInsertNums = 0;
        LOGGER.info("[migrate] start migrate config namespace");
        do {
            int retryTimes = 0;
            boolean migrateSuccess = false;
            while (retryTimes <= maxNamespaceMigrateRetryTimes) {
                try {
                    batchIds = configMigratePersistService.getMigrateConfigInsertIdList(startId, batchSize);
                    if (!batchIds.isEmpty()) {
                        configMigratePersistService.migrateConfigInsertByIds(batchIds, NAMESPACE_MIGRATE_SRC_USER);
                        startId = batchIds.get(batchIds.size() - 1);
                    }
                    migrateSuccess = true;
                    break;
                } catch (Exception e) {
                    LOGGER.error("[migrate] config_info namespace migrate insert failed, retry times={}, error={}", retryTimes,
                            e.getMessage());
                }
                retryTimes++;
                Thread.sleep(1000L);
            }
            if (!migrateSuccess) {
                LOGGER.error("[migrate] config_info namespace migrate insert failed");
                throw new Exception("[migrate] config_info namespace migrate insert failed");
            } else {
                totalInsertNums += batchIds.size();
            }
            LOGGER.info("[migrate] migrating config namespace from empty to public, finished:" + totalInsertNums);
        } while (batchIds.size() == batchSize);
        
        long startEmptyId = -1;
        int totalUpdateFromEmptyNums = 0;
        List<ConfigInfo> batchConfigInfosFromEmpty = new ArrayList<>();
        do {
            int retryTimes = 0;
            boolean migrateSuccess = false;
            while (retryTimes <= maxNamespaceMigrateRetryTimes) {
                try {
                    batchConfigInfosFromEmpty = configMigratePersistService
                            .getMigrateConfigUpdateList(startEmptyId, batchSize, StringUtils.EMPTY, namespacePublic, NAMESPACE_MIGRATE_SRC_USER);
                    if (!batchConfigInfosFromEmpty.isEmpty()) {
                        for (ConfigInfo configInfo : batchConfigInfosFromEmpty) {
                            configMigratePersistService.syncConfig(configInfo.getDataId(), configInfo.getGroup(),
                                    StringUtils.EMPTY, namespacePublic, NAMESPACE_MIGRATE_SRC_USER);
                        }
                        startEmptyId = batchConfigInfosFromEmpty.get(batchConfigInfosFromEmpty.size() - 1)
                                .getId();
                    }
                    migrateSuccess = true;
                    break;
                } catch (Exception e) {
                    LOGGER.error("[migrate] config_info namespace migrate update from empty failed, retry times={}, error={}", retryTimes,
                            e.getMessage());
                }
                retryTimes++;
                Thread.sleep(1000L);
            }
            if (!migrateSuccess) {
                LOGGER.error("[migrate] config_info namespace migrate update from empty failed, skipped");
                if (!batchConfigInfosFromEmpty.isEmpty()) {
                    startEmptyId = batchConfigInfosFromEmpty.get(batchConfigInfosFromEmpty.size() - 1).getId();
                }
            } else {
                totalUpdateFromEmptyNums += batchConfigInfosFromEmpty.size();
            }
            LOGGER.info("[migrate] syncing config namespace from empty to public, finished:" + totalUpdateFromEmptyNums);
        } while (batchConfigInfosFromEmpty.size() == batchSize);
        
        long startPublicId = -1;
        int totalUpdateFromPublicNums = 0;
        List<ConfigInfo> batchConfigInfosFromPublic = new ArrayList<>();
        do {
            int retryTimes = 0;
            boolean migrateSuccess = false;
            while (retryTimes <= maxNamespaceMigrateRetryTimes) {
                try {
                    batchConfigInfosFromPublic = configMigratePersistService
                            .getMigrateConfigUpdateList(startPublicId, batchSize, namespacePublic, StringUtils.EMPTY,
                                    NAMESPACE_MIGRATE_SRC_USER);
                    if (!batchConfigInfosFromPublic.isEmpty()) {
                        for (ConfigInfo configInfo : batchConfigInfosFromPublic) {
                            configMigratePersistService.syncConfig(configInfo.getDataId(), configInfo.getGroup(),
                                    namespacePublic, StringUtils.EMPTY, NAMESPACE_MIGRATE_SRC_USER);
                        }
                        startPublicId = batchConfigInfosFromPublic.get(batchConfigInfosFromPublic.size() - 1)
                                .getId();
                    }
                    migrateSuccess = true;
                    break;
                } catch (Exception e) {
                    LOGGER.error("[migrate] config_info namespace migrate update from public failed, retry times={}, error={}", retryTimes,
                            e.getMessage());
                }
                retryTimes++;
                Thread.sleep(1000L);
            }
            if (!migrateSuccess) {
                LOGGER.error("[migrate] config_info namespace migrate update from public failed, skipped");
                if (!batchConfigInfosFromPublic.isEmpty()) {
                    startPublicId = batchConfigInfosFromPublic.get(batchConfigInfosFromPublic.size() - 1).getId();
                }
            } else {
                totalUpdateFromPublicNums += batchConfigInfosFromPublic.size();
            }
            LOGGER.info("[migrate] syncing config namespace from public to empty, finished:" + totalUpdateFromPublicNums);
        } while (batchConfigInfosFromPublic.size() == batchSize);
        
        long startGrayId = -1;
        int totalInsertGrayNum = 0;
        do {
            int retryTimes = 0;
            boolean migrateSuccess = false;
            while (retryTimes <= maxNamespaceMigrateRetryTimes) {
                try {
                    batchIds = configMigratePersistService.getMigrateConfigGrayInsertIdList(startGrayId, batchSize);
                    if (!batchIds.isEmpty()) {
                        configMigratePersistService.migrateConfigGrayInsertByIds(batchIds, NAMESPACE_MIGRATE_SRC_USER);
                        startGrayId = batchIds.get(batchIds.size() - 1);
                    }
                    migrateSuccess = true;
                    break;
                } catch (Exception e) {
                    LOGGER.error("[migrate] config_info gray namespace migrate insert failed, retry times={}, error={}", retryTimes,
                            e.getMessage());
                }
                retryTimes++;
                Thread.sleep(1000L);
            }
            if (!migrateSuccess) {
                LOGGER.error("[migrate] config_info_gray namespace migrate insert failed");
                throw new Exception("[migrate] config_info_gray namespace migrate insert failed");
            } else {
                totalInsertGrayNum += batchIds.size();
            }
            LOGGER.info("[migrate] migrating config gray namespace from empty to public, finished:" + totalInsertGrayNum);
        } while (batchIds.size() == batchSize);
        
        long startGrayEmptyId = -1;
        int totalUpdateGrayFromEmptyNum = 0;
        List<ConfigInfoGrayWrapper> batchConfigInfoGraysFromEmpty = new ArrayList<>();
        do {
            int retryTimes = 0;
            boolean migrateSuccess = false;
            while (retryTimes <= maxNamespaceMigrateRetryTimes) {
                try {
                    batchConfigInfoGraysFromEmpty = configMigratePersistService
                            .getMigrateConfigGrayUpdateList(startGrayEmptyId, batchSize, StringUtils.EMPTY,
                                    namespacePublic, NAMESPACE_MIGRATE_SRC_USER);
                    if (!batchConfigInfoGraysFromEmpty.isEmpty()) {
                        for (ConfigInfoGrayWrapper configInfoGrayWrapper : batchConfigInfoGraysFromEmpty) {
                            configMigratePersistService.syncConfigGray(configInfoGrayWrapper.getDataId(),
                                    configInfoGrayWrapper.getGroup(), StringUtils.EMPTY,
                                    configInfoGrayWrapper.getGrayName(), namespacePublic, NAMESPACE_MIGRATE_SRC_USER);
                        }
                        startGrayEmptyId = batchConfigInfoGraysFromEmpty.get(batchConfigInfoGraysFromEmpty.size() - 1)
                                .getId();
                    }
                    migrateSuccess = true;
                    break;
                } catch (Exception e) {
                    LOGGER.error("[migrate] config_info_gray namespace migrate update from empty failed, retry times={}, error={}",
                            retryTimes, e.getMessage());
                }
                retryTimes++;
                Thread.sleep(1000L);
            }
            if (!migrateSuccess) {
                LOGGER.error("[migrate] config_info_gray namespace migrate update from empty failed, skipped");
                if (!batchConfigInfoGraysFromEmpty.isEmpty()) {
                    startGrayEmptyId = batchConfigInfoGraysFromEmpty.get(batchConfigInfoGraysFromEmpty.size() - 1)
                            .getId();
                }
            } else {
                totalUpdateGrayFromEmptyNum += batchConfigInfoGraysFromEmpty.size();
            }
            LOGGER.info("[migrate] syncing config gray namespace from empty to public, finished:" + totalUpdateGrayFromEmptyNum);
        } while (batchConfigInfoGraysFromEmpty.size() == batchSize);
        
        long startGrayPublicId = -1;
        int totalUpdateGrayFromPublicNum = 0;
        List<ConfigInfoGrayWrapper> batchConfigInfoGraysFromPublic = new ArrayList<>();
        do {
            int retryTimes = 0;
            boolean migrateSuccess = false;
            while (retryTimes <= maxNamespaceMigrateRetryTimes) {
                try {
                    batchConfigInfoGraysFromPublic = configMigratePersistService
                            .getMigrateConfigGrayUpdateList(startGrayPublicId, batchSize, namespacePublic,
                                    StringUtils.EMPTY, NAMESPACE_MIGRATE_SRC_USER);
                    if (!batchConfigInfoGraysFromPublic.isEmpty()) {
                        for (ConfigInfoGrayWrapper configInfoGrayWrapper : batchConfigInfoGraysFromPublic) {
                            configMigratePersistService.syncConfigGray(configInfoGrayWrapper.getDataId(),
                                    configInfoGrayWrapper.getGroup(), namespacePublic,
                                    configInfoGrayWrapper.getGrayName(), StringUtils.EMPTY, NAMESPACE_MIGRATE_SRC_USER);
                        }
                        startGrayPublicId = batchConfigInfoGraysFromPublic.get(batchConfigInfoGraysFromPublic.size() - 1)
                                .getId();
                    }
                    migrateSuccess = true;
                    break;
                } catch (Exception e) {
                    LOGGER.error("[migrate] config_info_gray namespace migrate update from public failed, retry times={}, error={}",
                            retryTimes, e.getMessage());
                }
                retryTimes++;
                Thread.sleep(1000L);
            }
            if (!migrateSuccess) {
                LOGGER.error("[migrate] config_info_gray namespace migrate update from public failed, skipped");
                if (!batchConfigInfoGraysFromPublic.isEmpty()) {
                    startGrayPublicId = batchConfigInfoGraysFromPublic.get(batchConfigInfoGraysFromPublic.size() - 1)
                            .getId();
                }
            } else {
                totalUpdateGrayFromPublicNum += batchConfigInfoGraysFromPublic.size();
            }
            LOGGER.info("[migrate] syncing config gray namespace from public to empty, finished:" + totalUpdateGrayFromPublicNum);
        } while (batchConfigInfoGraysFromPublic.size() == batchSize);
        LOGGER.info("[migrate] finish migrate config namespace" + "total time taken: "
                + (System.currentTimeMillis() - startTime) + " ms");
    }
    
    private void namespaceMigratePreCheck(int maxRetryTimes) throws Exception {
        int retryTimes = 0;
        boolean checkSuccess = false;
        while (retryTimes <= maxRetryTimes) {
            try {
                int conflictCount = configMigratePersistService.configInfoConflictCount(NAMESPACE_MIGRATE_SRC_USER);
                if (conflictCount > 0) {
                    LOGGER.error("[migrate] config_info conflict count=" + conflictCount);
                } else {
                    checkSuccess = true;
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("[migrate] namespace migrate pre check failed, retry times={}, error={}", retryTimes,
                        e.getMessage());
            }
            retryTimes++;
            Thread.sleep(1000L);
        }
        if (!checkSuccess) {
            throw new Exception("[migrate] config_info namespace migrate pre check failed");
        }
        
        retryTimes = 0;
        checkSuccess = false;
        while (retryTimes <= maxRetryTimes) {
            try {
                int conflictCount = configMigratePersistService.configInfoGrayConflictCount(NAMESPACE_MIGRATE_SRC_USER);
                if (conflictCount > 0) {
                    LOGGER.error("[migrate] config_info_gray conflict count=" + conflictCount);
                } else {
                    checkSuccess = true;
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("[migrate] namespace migrate pre check failed, retry times={}, error={}", retryTimes,
                        e.getMessage());
            }
            retryTimes++;
            Thread.sleep(1000L);
        }
        if (!checkSuccess) {
            throw new Exception("[migrate] config_gray namespace migrate pre check failed");
        }
    }
    
    /**
     * Namespace migrate gray.
     *
     * @param dataId   the data id
     * @param group    the group
     * @param tenant   the tenant
     * @param grayName the gray name
     */
    public void namespaceMigrateGray(String dataId, String group, String tenant, String grayName) {
        try {
            GRAY_MIGRATE_FLAG.set(true);
            if (StringUtils.isBlank(tenant)) {
                configMigratePersistService.syncConfigGray(dataId, group, tenant, grayName, namespacePublic,
                        NAMESPACE_MIGRATE_SRC_USER);
            } else if (StringUtils.equals(tenant, namespacePublic)) {
                configMigratePersistService.syncConfigGray(dataId, group, tenant, grayName, "",
                        NAMESPACE_MIGRATE_SRC_USER);
            }
        } catch (Exception e) {
            LOGGER.error("[migrate] namespace migrate gray failed", e);
        } finally {
            GRAY_MIGRATE_FLAG.set(false);
        }
    }
    
    /**
     * Namespace migrate.
     *
     * @param dataId the data id
     * @param group  the group
     * @param tenant the tenant
     */
    public void namespaceMigrate(String dataId, String group, String tenant) {
        try {
            CONFIG_MIGRATE_FLAG.set(true);
            if (StringUtils.isBlank(tenant)) {
                configMigratePersistService.syncConfig(dataId, group, tenant, namespacePublic, NAMESPACE_MIGRATE_SRC_USER);
            } else if (StringUtils.equals(tenant, namespacePublic)) {
                configMigratePersistService.syncConfig(dataId, group, tenant, "", NAMESPACE_MIGRATE_SRC_USER);
            }
        } catch (Exception e) {
            LOGGER.error("[migrate] namespace migrate failed", e);
        } finally {
            CONFIG_MIGRATE_FLAG.set(false);
        }
    }
    
    /**
     * Publish config migrate.
     *
     * @param configFormOrigin  the config form origin
     * @param configRequestInfo the config request info
     * @param encryptedDataKey  the encrypted data key
     * @throws NacosException the nacos exception
     */
    public void publishConfigMigrate(ConfigForm configFormOrigin, ConfigRequestInfo configRequestInfo,
            String encryptedDataKey) throws NacosException {
        ConfigForm configForm = configFormOrigin.clone();
        if (!StringUtils.equals(configForm.getNamespaceId(), namespacePublic) || !ConfigCompatibleConfig.getInstance()
                .isNamespaceCompatibleMode()) {
            return;
        }
        configForm.setNamespaceId(StringUtils.EMPTY);
        configForm.setSrcUser(NAMESPACE_MIGRATE_SRC_USER);
        Map<String, Object> configAdvanceInfo = getConfigAdvanceInfo(configForm);
        ParamUtils.checkParam(configAdvanceInfo);
        configForm.setEncryptedDataKey(encryptedDataKey);
        ConfigInfo configInfo = new ConfigInfo(configForm.getDataId(), configForm.getGroup(),
                configForm.getNamespaceId(), configForm.getAppName(), configForm.getContent());
        //set old md5
        if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
            configInfo.setMd5(configRequestInfo.getCasMd5());
        }
        configInfo.setType(configForm.getType());
        configInfo.setEncryptedDataKey(encryptedDataKey);
        
        ConfigOperateResult configOperateResult;
        
        try {
            CONFIG_MIGRATE_FLAG.set(true);
            if (StringUtils.isNotBlank(configRequestInfo.getCasMd5())) {
                configOperateResult = configInfoPersistService.insertOrUpdateCas(configRequestInfo.getSrcIp(),
                        configForm.getSrcUser(), configInfo, configAdvanceInfo);
                if (!configOperateResult.isSuccess()) {
                    LOGGER.warn(
                            "[cas-publish-config-fail] srcIp = {}, dataId= {}, casMd5 = {}, msg = server md5 may have changed.",
                            configRequestInfo.getSrcIp(), configForm.getDataId(), configRequestInfo.getCasMd5());
                    throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.RESOURCE_CONFLICT,
                            "Cas publish fail, server md5 may have changed.");
                }
            } else {
                if (configRequestInfo.getUpdateForExist()) {
                    configInfoPersistService.insertOrUpdate(configRequestInfo.getSrcIp(), configForm.getSrcUser(),
                            configInfo, configAdvanceInfo);
                } else {
                    try {
                        configInfoPersistService.addConfigInfo(configRequestInfo.getSrcIp(), configForm.getSrcUser(),
                                configInfo, configAdvanceInfo);
                    } catch (DataIntegrityViolationException ive) {
                        LOGGER.warn(
                                "[publish-config-failed] config already exists. dataId: {}, group: {}, namespaceId: {}",
                                configForm.getDataId(), configForm.getGroup(), configForm.getNamespaceId());
                        throw new ConfigAlreadyExistsException(
                                String.format("config already exist, dataId: %s, group: %s, namespaceId: %s",
                                        configForm.getDataId(), configForm.getGroup(), configForm.getNamespaceId()));
                    }
                }
            }
        } finally {
            CONFIG_MIGRATE_FLAG.set(false);
        }
    }
    
    /**
     * Update config metadata migrate.
     *
     * @param dataId      the data id
     * @param group       the group
     * @param namespaceId the namespace id
     * @param configTags  the config tags
     * @param description the description
     * @throws NacosException the nacos exception
     */
    public void updateConfigMetadataMigrate(final String dataId,
            final String group, final String namespaceId, final String configTags, final String description)
            throws NacosException {
        if (!StringUtils.equals(namespaceId, namespacePublic) || !ConfigCompatibleConfig.getInstance()
                .isNamespaceCompatibleMode()) {
            return;
        }
        ConfigOperateResult configOperateResult;
        configOperateResult = configInfoPersistService.updateConfigInfoMetadata(dataId, group, StringUtils.EMPTY, configTags, description);
        if (!configOperateResult.isSuccess()) {
            LOGGER.warn("[update-config-metadata-fail] dataId: {}, group: {}, namespaceId: {}",
                    dataId, group, namespaceId);
            throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.RESOURCE_CONFLICT,
                    "update metadata fail.");
        }
    }
    
    /**
     * Remove config info migrate.
     *
     * @param dataId  the data id
     * @param group   the group
     * @param tenant  the tenant
     * @param srcIp   the src ip
     * @param srcUser the src user
     */
    public void removeConfigInfoMigrate(String dataId, String group, String tenant, String srcIp, String srcUser) {
        if (!StringUtils.equals(tenant, namespacePublic) || !ConfigCompatibleConfig.getInstance()
                .isNamespaceCompatibleMode()) {
            return;
        }
        try {
            CONFIG_MIGRATE_FLAG.set(true);
            configInfoPersistService.removeConfigInfo(dataId, group, "", srcIp, NAMESPACE_MIGRATE_SRC_USER);
        } finally {
            CONFIG_MIGRATE_FLAG.set(false);
        }
    }
    
    public Map<String, Object> getConfigAdvanceInfo(ConfigForm configForm) {
        Map<String, Object> configAdvanceInfo = new HashMap<>(10);
        MapUtil.putIfValNoNull(configAdvanceInfo, "config_tags", configForm.getConfigTags());
        MapUtil.putIfValNoNull(configAdvanceInfo, "desc", configForm.getDesc());
        MapUtil.putIfValNoNull(configAdvanceInfo, "use", configForm.getUse());
        MapUtil.putIfValNoNull(configAdvanceInfo, "effect", configForm.getEffect());
        MapUtil.putIfValNoNull(configAdvanceInfo, "type", configForm.getType());
        MapUtil.putIfValNoNull(configAdvanceInfo, "schema", configForm.getSchema());
        return configAdvanceInfo;
    }
    
}
