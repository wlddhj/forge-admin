package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.client.PythonAiClient;
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

    private final PythonAiClient pythonAiClient;
    private final AiModelConfigMapper modelConfigMapper;

    @Override
    public ModelListResponse getAvailableModels() {
        // 从Python服务获取可用模型
        ModelListResponse response = pythonAiClient.getAvailableModels();
        if (response != null && response.getModels() != null) {
            // 更新本地缓存状态
            response.getModels().forEach(model -> {
                AiModelConfig config = modelConfigMapper.selectByModelName(model.getModelName());
                if (config != null) {
                    model.setId(config.getId());
                    model.setStatus(config.getStatus());
                }
            });
        }
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
        ModelListResponse response = pythonAiClient.getAvailableModels();
        if (response != null && response.getModels() != null) {
            response.getModels().forEach(model -> {
                AiModelConfig existing = modelConfigMapper.selectByModelName(model.getModelName());
                if (existing == null) {
                    // 新模型，插入记录
                    AiModelConfig config = new AiModelConfig();
                    config.setModelName(model.getModelName());
                    config.setModelCode(model.getModelName());
                    config.setProvider(model.getProvider());
                    config.setStatus(1);
                    config.setIsDefault(0);
                    config.setMaxTokens(4096);
                    config.setTemperature(new BigDecimal("0.7"));
                    if (model.getPricingInput() != null) {
                        config.setInputPrice(new BigDecimal(model.getPricingInput().toString()));
                    }
                    if (model.getPricingOutput() != null) {
                        config.setOutputPrice(new BigDecimal(model.getPricingOutput().toString()));
                    }
                    modelConfigMapper.insert(config);
                }
            });
        }
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
        // 调用 Python 服务检查模型状态
        boolean available = pythonAiClient.checkModelAvailable(config.getModelName());
        config.setStatus(available ? 1 : 2);
        modelConfigMapper.updateById(config);
        return config;
    }

    @Override
    public List<AiModelConfig> refreshAllModelStatus() {
        List<AiModelConfig> configs = getAllModelConfigs();
        List<AiModelConfig> updatedConfigs = new ArrayList<>();
        for (AiModelConfig config : configs) {
            try {
                boolean available = pythonAiClient.checkModelAvailable(config.getModelName());
                config.setStatus(available ? 1 : 2);
                modelConfigMapper.updateById(config);
                updatedConfigs.add(config);
            } catch (Exception e) {
                log.warn("检查模型 {} 状态失败: {}", config.getModelName(), e.getMessage());
                config.setStatus(2);
                modelConfigMapper.updateById(config);
                updatedConfigs.add(config);
            }
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