package com.forge.modules.ai.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.service.AiAgentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAgentConfigServiceImpl implements AiAgentConfigService {

    private final AiAgentConfigMapper agentConfigMapper;

    @Override
    public List<AiAgentConfig> listEnabled() {
        return agentConfigMapper.selectList(new LambdaQueryWrapper<AiAgentConfig>()
                .eq(AiAgentConfig::getStatus, 1)
                .orderByAsc(AiAgentConfig::getId));
    }

    @Override
    public List<AiAgentConfig> listAll() {
        return agentConfigMapper.selectList(new LambdaQueryWrapper<AiAgentConfig>()
                .orderByAsc(AiAgentConfig::getId));
    }

    @Override
    public AiAgentConfig getById(Long id) {
        return agentConfigMapper.selectById(id);
    }

    @Override
    @Transactional
    public void update(Long id, AiAgentConfig config) {
        AiAgentConfig existing = agentConfigMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Agent 配置不存在");
        }
        if (config.getAgentName() != null) {
            existing.setAgentName(config.getAgentName());
        }
        if (config.getDescription() != null) {
            existing.setDescription(config.getDescription());
        }
        if (config.getSystemPrompt() != null) {
            existing.setSystemPrompt(config.getSystemPrompt());
        }
        if (config.getUserPromptTemplate() != null) {
            existing.setUserPromptTemplate(config.getUserPromptTemplate());
        }
        if (config.getModelBindings() != null) {
            existing.setModelBindings(config.getModelBindings());
        }
        if (config.getMaxSteps() != null) {
            if (config.getMaxSteps() < 1 || config.getMaxSteps() > 50) {
                throw new BusinessException("最大步数须在 1-50 之间");
            }
            existing.setMaxSteps(config.getMaxSteps());
        }
        if (config.getStatus() != null) {
            existing.setStatus(config.getStatus());
        }
        if (config.getRemark() != null) {
            existing.setRemark(config.getRemark());
        }
        agentConfigMapper.updateById(existing);
    }
}
