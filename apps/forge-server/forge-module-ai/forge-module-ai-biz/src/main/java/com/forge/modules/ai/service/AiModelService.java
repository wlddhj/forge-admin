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
     * 获取默认模型配置
     */
    AiModelConfig getDefaultModel();

    /**
     * 更新模型状态
     */
    void updateModelStatus(Long id, Integer status);

    /**
     * 设置默认模型
     */
    void setDefaultModel(Long id);

    /**
     * 刷新模型缓存
     */
    void refreshModelCache();

    /**
     * 更新模型配置
     */
    void updateModelConfig(Long id, AiModelConfig config);

    /**
     * 刷新单个模型状态
     */
    AiModelConfig refreshModelStatus(Long id);

    /**
     * 刷新所有模型状态
     */
    List<AiModelConfig> refreshAllModelStatus();

    /**
     * 新增模型配置
     */
    void addModelConfig(AiModelConfig config);

    /**
     * 删除模型配置
     */
    void deleteModelConfig(Long id);
}