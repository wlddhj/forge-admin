package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.client.ModelHealthProbe;
import com.forge.modules.ai.client.OpenAiChatModelFactory;
import com.forge.modules.ai.dto.response.ModelListResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import com.forge.modules.ai.service.AiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI模型服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final ModelHealthProbe modelHealthProbe;
    private final OpenAiChatModelFactory modelFactory;
    private final AiModelConfigMapper modelConfigMapper;

    @Override
    public ModelListResponse getAvailableModels() {
        List<AiModelConfig> configs = getAllModelConfigs();
        ModelListResponse response = new ModelListResponse();
        response.setAvailable(!configs.isEmpty());
        List<ModelListResponse.ModelConfigResponse> models = new ArrayList<>();
        for (AiModelConfig config : configs) {
            ModelListResponse.ModelConfigResponse model = new ModelListResponse.ModelConfigResponse();
            model.setId(config.getId());
            model.setModelName(config.getModelName());
            model.setDisplayName(config.getModelName());
            model.setProvider(config.getProvider());
            model.setDescription(config.getRemark());
            model.setContextLength(config.getContextWindow());
            model.setPricingInput(config.getInputPrice() != null ? config.getInputPrice().doubleValue() : null);
            model.setPricingOutput(config.getOutputPrice() != null ? config.getOutputPrice().doubleValue() : null);
            model.setStatus(config.getStatus());
            models.add(model);
        }
        response.setModels(models);
        return response;
    }

    @Override
    public List<AiModelConfig> getAllModelConfigs() {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AiModelConfig::getId);
        return modelConfigMapper.selectList(wrapper);
    }

    @Override
    public AiModelConfig getModelConfig(Long id) {
        return modelConfigMapper.selectById(id);
    }

    @Override
    public AiModelConfig getDefaultModel() {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getIsDefault, 1);
        wrapper.eq(AiModelConfig::getStatus, 1);
        wrapper.last("LIMIT 1");
        return modelConfigMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public void updateModelStatus(Long id, Integer status) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config != null) {
            config.setStatus(status);
            modelConfigMapper.updateById(config);
        }
    }

    @Override
    @Transactional
    public void setDefaultModel(Long id) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("模型配置不存在");
        }

        // 先取消当前默认模型
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getIsDefault, 1);
        List<AiModelConfig> defaultModels = modelConfigMapper.selectList(wrapper);
        for (AiModelConfig defaultModel : defaultModels) {
            defaultModel.setIsDefault(0);
            modelConfigMapper.updateById(defaultModel);
        }

        // 设置新的默认模型
        config.setIsDefault(1);
        config.setStatus(1); // 同时启用
        modelConfigMapper.updateById(config);
    }

    @Override
    public void refreshModelCache() {
        // 模型目录已由 ai_model_config 表维护，刷新动作即重建实例缓存并探测全部模型状态
        refreshAllModelStatus();
    }

    @Override
    @Transactional
    public void updateModelConfig(Long id, AiModelConfig config) {
        AiModelConfig existing = modelConfigMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("模型配置不存在");
        }
        // 只更新允许修改的字段
        if (config.getApiEndpoint() != null) {
            existing.setApiEndpoint(config.getApiEndpoint());
        }
        if (config.getApiKey() != null) {
            existing.setApiKey(config.getApiKey());
        }
        if (config.getMaxTokens() != null) {
            existing.setMaxTokens(config.getMaxTokens());
        }
        if (config.getTemperature() != null) {
            existing.setTemperature(config.getTemperature());
        }
        modelConfigMapper.updateById(existing);
    }

    @Override
    public AiModelConfig refreshModelStatus(Long id) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("模型配置不存在");
        }
        modelFactory.evict(config.getId());
        boolean available = modelHealthProbe.probe(config);
        config.setStatus(available ? 1 : 2);
        modelConfigMapper.updateById(config);
        return config;
    }

    @Override
    public List<AiModelConfig> refreshAllModelStatus() {
        List<AiModelConfig> configs = getAllModelConfigs();
        List<AiModelConfig> updatedConfigs = new ArrayList<>();
        for (AiModelConfig config : configs) {
            modelFactory.evict(config.getId());
            try {
                boolean available = modelHealthProbe.probe(config);
                config.setStatus(available ? 1 : 2);
            } catch (Exception e) {
                log.warn("检查模型 {} 状态失败: {}", config.getModelName(), e.getMessage());
                config.setStatus(2);
            }
            modelConfigMapper.updateById(config);
            updatedConfigs.add(config);
        }
        return updatedConfigs;
    }

    @Override
    @Transactional
    public void addModelConfig(AiModelConfig config) {
        // 检查模型名称是否已存在
        AiModelConfig existing = modelConfigMapper.selectByModelName(config.getModelName());
        if (existing != null) {
            throw new RuntimeException("模型名称已存在");
        }
        config.setStatus(1);
        config.setIsDefault(0);
        if (config.getMaxTokens() == null) {
            config.setMaxTokens(4096);
        }
        if (config.getTemperature() == null) {
            config.setTemperature(new BigDecimal("0.7"));
        }
        modelConfigMapper.insert(config);
    }

    @Override
    @Transactional
    public void deleteModelConfig(Long id) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("模型配置不存在");
        }
        if (config.getIsDefault() == 1) {
            throw new RuntimeException("不能删除默认模型");
        }
        modelConfigMapper.deleteById(id);
    }
}