package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.client.PythonAiClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.CreateConversationRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.ConversationResponse;
import com.forge.modules.ai.entity.AiConversation;
import com.forge.modules.ai.entity.AiMessage;
import com.forge.modules.ai.mapper.AiConversationMapper;
import com.forge.modules.ai.mapper.AiMessageMapper;
import com.forge.modules.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI对话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final PythonAiClient pythonAiClient;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    @Override
    public ChatResponse chat(ChatRequest request) {
        // 保存用户消息
        saveUserMessage(request);

        // 调用Python服务
        ChatResponse response = pythonAiClient.chat(request);

        // 保存AI响应消息
        if (response != null && response.getSuccess()) {
            saveAssistantMessage(response);
        }

        return response;
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request) {
        AiConversation conversation = new AiConversation();
        conversation.setTitle(request.getTitle());
        conversation.setModelName(request.getModelName());
        conversation.setType(request.getType());
        conversation.setDocumentId(request.getDocumentId());
        conversationMapper.insert(conversation);

        return toConversationResponse(conversation);
    }

    @Override
    public List<ConversationResponse> getConversationList() {
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiConversation::getCreateTime);
        List<AiConversation> conversations = conversationMapper.selectList(wrapper);
        return conversations.stream().map(this::toConversationResponse).collect(Collectors.toList());
    }

    @Override
    public ConversationResponse getConversation(Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return null;
        }
        ConversationResponse response = toConversationResponse(conversation);

        // 获取消息列表
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreateTime);
        List<AiMessage> messages = messageMapper.selectList(wrapper);
        response.setMessages(messages.stream().map(this::toMessageResponse).collect(Collectors.toList()));

        return response;
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        // 删除消息
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId);
        messageMapper.delete(wrapper);

        // 删除对话
        conversationMapper.deleteById(conversationId);
    }

    private void saveUserMessage(ChatRequest request) {
        if (request.getConversationId() == null) {
            // 创建新对话
            AiConversation conversation = new AiConversation();
            conversation.setTitle(request.getContent().substring(0, Math.min(50, request.getContent().length())));
            conversation.setModelName(request.getModelName());
            conversation.setType("chat");
            conversationMapper.insert(conversation);
            request.setConversationId(conversation.getId());
        }

        AiMessage message = new AiMessage();
        message.setConversationId(request.getConversationId());
        message.setRole("user");
        message.setContent(request.getContent());
        messageMapper.insert(message);
    }

    private void saveAssistantMessage(ChatResponse response) {
        AiMessage message = new AiMessage();
        message.setConversationId(response.getConversationId());
        message.setRole("assistant");
        message.setContent(response.getContent());
        message.setInputTokens(response.getInputTokens());
        message.setOutputTokens(response.getOutputTokens());
        messageMapper.insert(message);
        response.setMessageId(message.getId());
    }

    private ConversationResponse toConversationResponse(AiConversation conversation) {
        ConversationResponse response = new ConversationResponse();
        response.setId(conversation.getId());
        response.setTitle(conversation.getTitle());
        response.setModelName(conversation.getModelName());
        response.setType(conversation.getType());
        response.setDocumentId(conversation.getDocumentId());
        response.setCreateTime(conversation.getCreateTime());
        response.setUpdateTime(conversation.getUpdateTime());
        response.setMessages(Collections.emptyList());
        return response;
    }

    private ConversationResponse.MessageResponse toMessageResponse(AiMessage message) {
        ConversationResponse.MessageResponse response = new ConversationResponse.MessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setInputTokens(message.getInputTokens());
        response.setOutputTokens(message.getOutputTokens());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}