package com.forge.modules.ai.service;

import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.CreateConversationRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.ConversationResponse;

import java.util.List;

/**
 * AI对话服务接口
 */
public interface AiChatService {

    /**
     * 发送聊天消息
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 创建新对话
     */
    ConversationResponse createConversation(CreateConversationRequest request);

    /**
     * 获取对话列表
     */
    List<ConversationResponse> getConversationList();

    /**
     * 获取对话详情
     */
    ConversationResponse getConversation(Long conversationId);

    /**
     * 删除对话
     */
    void deleteConversation(Long conversationId);
}