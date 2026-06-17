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

import java.util.List;
import java.util.stream.Collectors;

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
        wrapper.orderByAsc(AiModelConfig::getSortOrder);
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
    public void refreshModelCache() {
        ModelListResponse response = pythonAiClient.getAvailableModels();
        if (response != null && response.getModels() != null) {
            response.getModels().forEach(model -> {
                AiModelConfig existing = modelConfigMapper.selectByModelName(model.getModelName());
                if (existing == null) {
                    // 新模型，插入记录
                    AiModelConfig config = new AiModelConfig();
                    config.setModelName(model.getModelName());
                    config.setDisplayName(model.getDisplayName());
                    config.setProvider(model.getProvider());
                    config.setDescription(model.getDescription());
                    config.setContextLength(model.getContextLength());
                    config.setMaxTemperature(model.getMaxTemperature());
                    config.setSupportsVision(model.getSupportsVision());
                    config.setSupportsFunctionCall(model.getSupportsFunctionCall());
                    config.setPricingInput(model.getPricingInput());
                    config.setPricingOutput(model.getPricingOutput());
                    config.setStatus(1);
                    modelConfigMapper.insert(config);
                }
            });
        }
    }
}