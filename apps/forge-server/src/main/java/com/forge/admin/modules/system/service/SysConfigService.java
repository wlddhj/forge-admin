package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.system.dto.config.ConfigQueryRequest;
import com.forge.admin.modules.system.dto.config.ConfigRequest;
import com.forge.admin.modules.system.dto.config.ConfigResponse;

import java.util.List;

public interface SysConfigService {
    Page<ConfigResponse> pageConfigs(ConfigQueryRequest request);
    ConfigResponse getConfigDetail(Long id);
    String getConfigValueByKey(String configKey);
    void addConfig(ConfigRequest request);
    void updateConfig(ConfigRequest request);
    void deleteConfigs(List<Long> ids);
}
