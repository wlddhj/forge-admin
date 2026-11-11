package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.CreateConversationRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.ConversationResponse;
import com.forge.modules.ai.dto.response.MessageResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI对话服务接口
 */
public interface AiChatService {

    /**
     * 创建新对话
     */
    ConversationResponse createConversation(CreateConversationRequest request);

    /**
     * 分页获取对话列表
     */
    Page<ConversationResponse> pageConversationList(Integer pageNum, Integer pageSize, Long modelId);

    /**
     * 获取对话详情
     */
    ConversationResponse getConversation(Long conversationId);

    /**
     * 获取对话消息列表
     */
    List<MessageResponse> getMessages(Long conversationId);

    /**
     * 更新对话标题
     */
    void updateTitle(Long conversationId, String title);

    /**
     * AI 生成会话标题（仅占位标题会生成：新对话/与 xx 的对话/Agent：xx）。
     * 返回新标题；无需生成或失败时返回 null（静默，不抛错）。
     */
    String generateTitle(Long conversationId, String content);

    /**
     * 删除对话
     */
    void deleteConversation(Long conversationId);

    /**
     * 发送消息
     */
    MessageResponse sendMessage(ChatRequest request);

    /**
     * 发送聊天消息（旧接口）
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式聊天（返回SSE数据流）
     */
    Flux<String> chatStream(ChatRequest request);

    /**
     * 准备流式聊天（同步保存用户消息）
     */
    void prepareStreamChat(ChatRequest request);

    /**
     * 获取流式响应（纯 Flux，不需要 Security Context）
     */
    Flux<String> getStreamResponse(ChatRequest request);

    /**
     * 保存 AI 回复消息（含思考内容，思考模型 reasoning_content 可为空）
     */
    void saveAiMessage(Long conversationId, String content, String reasoningContent);

    /**
     * 获取对话列表（旧接口）
     */
    List<ConversationResponse> getConversationList();
}