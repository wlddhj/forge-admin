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
import java.util.UUID;
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
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setTitle(request.getTitle());
        conversation.setStatus(1);
        conversation.setTotalMessages(0);
        conversation.setTotalTokens(0);
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
        wrapper.eq(AiMessage::getConversationId, conversation.getConversationId())
                .orderByAsc(AiMessage::getCreateTime);
        List<AiMessage> messages = messageMapper.selectList(wrapper);
        response.setMessages(messages.stream().map(this::toMessageResponse).collect(Collectors.toList()));

        return response;
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            // 删除消息
            LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiMessage::getConversationId, conversation.getConversationId());
            messageMapper.delete(wrapper);

            // 删除对话
            conversationMapper.deleteById(conversationId);
        }
    }

    private void saveUserMessage(ChatRequest request) {
        String conversationIdStr;
        if (request.getConversationId() == null) {
            // 创建新对话
            AiConversation conversation = new AiConversation();
            conversation.setConversationId(UUID.randomUUID().toString());
            conversation.setTitle(request.getContent().substring(0, Math.min(50, request.getContent().length())));
            conversation.setStatus(1);
            conversation.setTotalMessages(0);
            conversation.setTotalTokens(0);
            conversationMapper.insert(conversation);
            request.setConversationId(conversation.getId());
            conversationIdStr = conversation.getConversationId();
        } else {
            AiConversation conversation = conversationMapper.selectById(request.getConversationId());
            conversationIdStr = conversation != null ? conversation.getConversationId() : UUID.randomUUID().toString();
        }

        AiMessage message = new AiMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversationIdStr);
        message.setRole("user");
        message.setContent(request.getContent());
        message.setStatus(1);
        messageMapper.insert(message);

        // 更新对话消息数
        updateConversationStats(conversationIdStr);
    }

    private void saveAssistantMessage(ChatResponse response) {
        AiConversation conversation = conversationMapper.selectById(response.getConversationId());
        if (conversation == null) {
            return;
        }

        AiMessage message = new AiMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversation.getConversationId());
        message.setRole("assistant");
        message.setContent(response.getContent());
        message.setInputTokens(response.getInputTokens());
        message.setOutputTokens(response.getOutputTokens());
        message.setStatus(1);
        messageMapper.insert(message);
        response.setMessageId(message.getId());

        // 更新对话统计
        updateConversationStats(conversation.getConversationId());
    }

    private void updateConversationStats(String conversationId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId);
        Long messageCount = messageMapper.selectCount(wrapper);

        AiConversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>().eq(AiConversation::getConversationId, conversationId)
        );
        if (conversation != null) {
            conversation.setTotalMessages(messageCount.intValue());
            conversationMapper.updateById(conversation);
        }
    }

    private ConversationResponse toConversationResponse(AiConversation conversation) {
        ConversationResponse response = new ConversationResponse();
        response.setId(conversation.getId());
        response.setTitle(conversation.getTitle());
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