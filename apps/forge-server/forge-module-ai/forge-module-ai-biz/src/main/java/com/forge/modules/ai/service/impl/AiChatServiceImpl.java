package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.ai.client.PythonAiClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.CreateConversationRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.ConversationResponse;
import com.forge.modules.ai.dto.response.MessageResponse;
import com.forge.modules.ai.entity.AiConversation;
import com.forge.modules.ai.entity.AiMessage;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import com.forge.modules.ai.mapper.AiConversationMapper;
import com.forge.modules.ai.mapper.AiMessageMapper;
import com.forge.modules.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

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
    private final AiModelConfigMapper modelConfigMapper;

    @Override
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request) {
        AiConversation conversation = new AiConversation();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setUserId(SecurityHelper.getCurrentUserId());
        conversation.setTitle(request.getTitle());
        conversation.setModelId(request.getModelId());
        conversation.setStatus(1);
        conversation.setTotalMessages(0);
        conversation.setTotalTokens(0);
        conversationMapper.insert(conversation);

        return toConversationResponse(conversation);
    }

    @Override
    public Page<ConversationResponse> pageConversationList(Integer pageNum, Integer pageSize, Long modelId) {
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(modelId != null, AiConversation::getModelId, modelId)
               .orderByDesc(AiConversation::getUpdateTime);

        Page<AiConversation> page = conversationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ConversationResponse> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setRecords(page.getRecords().stream().map(this::toConversationResponse).collect(Collectors.toList()));
        resultPage.setTotal(page.getTotal());
        return resultPage;
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
        response.setMessages(messages.stream().map(this::toInnerMessageResponse).collect(Collectors.toList()));

        return response;
    }

    @Override
    public List<MessageResponse> getMessages(Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversation.getConversationId())
                .orderByAsc(AiMessage::getCreateTime);
        List<AiMessage> messages = messageMapper.selectList(wrapper);

        return messages.stream().map(this::toMessageResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateTitle(Long conversationId, String title) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setTitle(title);
            conversationMapper.updateById(conversation);
        }
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

    @Override
    @Transactional
    public MessageResponse sendMessage(ChatRequest request) {
        // 获取或创建对话
        Long conversationId = request.getConversationId();
        AiConversation conversation;
        String conversationIdStr;

        if (conversationId == null) {
            // 创建新对话
            conversation = new AiConversation();
            conversationIdStr = UUID.randomUUID().toString();
            conversation.setConversationId(conversationIdStr);
            conversation.setUserId(SecurityHelper.getCurrentUserId());
            conversation.setTitle(request.getContent().substring(0, Math.min(50, request.getContent().length())));
            conversation.setModelId(request.getModelId());
            conversation.setStatus(1);
            conversation.setTotalMessages(0);
            conversation.setTotalTokens(0);
            conversationMapper.insert(conversation);
            conversationId = conversation.getId();
        } else {
            conversation = conversationMapper.selectById(conversationId);
            conversationIdStr = conversation != null ? conversation.getConversationId() : UUID.randomUUID().toString();
        }

        // 保存用户消息
        AiMessage userMessage = new AiMessage();
        userMessage.setMessageId(UUID.randomUUID().toString());
        userMessage.setConversationId(conversationIdStr);
        userMessage.setRole("user");
        userMessage.setContent(request.getContent());
        userMessage.setStatus(1);
        messageMapper.insert(userMessage);

        // 调用 Python 服务
        ChatResponse chatResponse = pythonAiClient.chat(request);

        // 保存 AI 响应
        MessageResponse aiMessageResponse = new MessageResponse();
        if (chatResponse != null && chatResponse.getSuccess()) {
            AiMessage aiMessage = new AiMessage();
            aiMessage.setMessageId(UUID.randomUUID().toString());
            aiMessage.setConversationId(conversationIdStr);
            aiMessage.setRole("assistant");
            aiMessage.setContent(chatResponse.getContent());
            aiMessage.setInputTokens(chatResponse.getInputTokens());
            aiMessage.setOutputTokens(chatResponse.getOutputTokens());
            aiMessage.setStatus(1);
            messageMapper.insert(aiMessage);

            // 更新对话统计
            updateConversationStats(conversationIdStr);

            // 返回 AI 消息
            aiMessageResponse.setId(aiMessage.getId());
            aiMessageResponse.setConversationId(conversationId);
            aiMessageResponse.setRole("assistant");
            aiMessageResponse.setContent(chatResponse.getContent());
            aiMessageResponse.setInputTokens(chatResponse.getInputTokens());
            aiMessageResponse.setOutputTokens(chatResponse.getOutputTokens());
            aiMessageResponse.setCreateTime(aiMessage.getCreateTime());
        } else {
            aiMessageResponse.setId(null);
            aiMessageResponse.setConversationId(conversationId);
            aiMessageResponse.setRole("assistant");
            aiMessageResponse.setContent(chatResponse != null ? chatResponse.getErrorMessage() : "请求失败");
            aiMessageResponse.setCreateTime(null);
        }

        return aiMessageResponse;
    }

    // ========== 旧接口实现 ==========

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
    public Flux<String> chatStream(ChatRequest request) {
        prepareStreamChat(request);
        return getStreamResponse(request);
    }

    @Override
    public void prepareStreamChat(ChatRequest request) {
        if (request.getConversationId() != null && request.getModelName() == null) {
            AiConversation conversation = conversationMapper.selectById(request.getConversationId());
            if (conversation != null && conversation.getModelId() != null) {
                AiModelConfig modelConfig = modelConfigMapper.selectById(conversation.getModelId());
                if (modelConfig != null) {
                    request.setModelName(modelConfig.getModelName());
                }
                // 获取历史消息作为上下文
                LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AiMessage::getConversationId, conversation.getConversationId())
                       .orderByAsc(AiMessage::getCreateTime)
                       .last("LIMIT 20");
                List<AiMessage> history = messageMapper.selectList(wrapper);
                List<ChatRequest.MessageItem> messages = history.stream()
                    .map(m -> {
                        ChatRequest.MessageItem item = new ChatRequest.MessageItem();
                        item.setRole(m.getRole());
                        item.setContent(m.getContent());
                        return item;
                    })
                    .collect(Collectors.toList());
                request.setMessages(messages);
            }
        }
        saveUserMessage(request);
    }

    @Override
    public Flux<String> getStreamResponse(ChatRequest request) {
        // 调用Python服务流式接口（不需要 Security Context）
        return pythonAiClient.chatStreamFlux(request);
    }

    @Override
    @Transactional
    public void saveAiMessage(Long conversationId, String content) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }

        AiMessage message = new AiMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversation.getConversationId());
        message.setRole("assistant");
        message.setContent(content);
        message.setStatus(1);
        messageMapper.insert(message);

        // 更新对话统计
        updateConversationStats(conversation.getConversationId());
    }

    @Override
    public List<ConversationResponse> getConversationList() {
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiConversation::getCreateTime);
        List<AiConversation> conversations = conversationMapper.selectList(wrapper);
        return conversations.stream().map(this::toConversationResponse).collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    private void saveUserMessage(ChatRequest request) {
        String conversationIdStr;
        if (request.getConversationId() == null) {
            // 创建新对话
            AiConversation conversation = new AiConversation();
            conversation.setConversationId(UUID.randomUUID().toString());
            conversation.setUserId(SecurityHelper.getCurrentUserId());
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
        response.setModelId(conversation.getModelId());
        response.setCreateTime(conversation.getCreateTime());
        response.setUpdateTime(conversation.getUpdateTime());
        response.setMessages(Collections.emptyList());
        return response;
    }

    private ConversationResponse.MessageResponse toInnerMessageResponse(AiMessage message) {
        ConversationResponse.MessageResponse response = new ConversationResponse.MessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setInputTokens(message.getInputTokens());
        response.setOutputTokens(message.getOutputTokens());
        response.setCreateTime(message.getCreateTime());
        return response;
    }

    private MessageResponse toMessageResponse(AiMessage message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setInputTokens(message.getInputTokens());
        response.setOutputTokens(message.getOutputTokens());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}