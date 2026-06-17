package com.forge.modules.ai.service;

import com.forge.modules.ai.dto.response.ModelListResponse;
import com.forge.modules.ai.entity.AiModelConfig;

import java.util.List;

/**
 * AI模型服务接口
 */
public interface AiModelService {

    /**
     * 获取可用模型列表
     */
    ModelListResponse getAvailableModels();

    /**
     * 获取所有模型配置
     */
    List<AiModelConfig> getAllModelConfigs();

    /**
     * 获取模型配置详情
     */
    AiModelConfig getModelConfig(Long id);

    /**
     * 更新模型状态
     */
    void updateModelStatus(Long id, Integer status);

    /**
     * 刷新模型缓存
     */
    void refreshModelCache();
}