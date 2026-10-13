package com.forge.modules.ai.service;

import com.forge.modules.ai.entity.AiAgentConfig;

import java.util.List;

/**
 * AI Agent 配置服务
 */
public interface AiAgentConfigService {

    List<AiAgentConfig> listEnabled();

    List<AiAgentConfig> listAll();

    AiAgentConfig getById(Long id);

    /**
     * 更新可配置项（名称/提示词/模型绑定/步数上限/状态）
     */
    void update(Long id, AiAgentConfig config);
}
