package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.ai.client.LlmClient;
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
import com.forge.modules.ai.service.AiModelResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI对话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final LlmClient llmClient;
    private final AiModelResolver modelResolver;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final com.forge.modules.ai.service.AiUsageRecorder usageRecorder;

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
        // 会话按用户隔离：非管理员仅见本人会话
        if (!SecurityHelper.isAdmin()) {
            wrapper.eq(AiConversation::getUserId, SecurityHelper.getCurrentUserId());
        }
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
        AiConversation conversation = requireOwnedConversation(conversationId);
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
        AiConversation conversation = requireOwnedConversation(conversationId);

        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversation.getConversationId())
                .orderByAsc(AiMessage::getCreateTime);
        List<AiMessage> messages = messageMapper.selectList(wrapper);

        return messages.stream().map(this::toMessageResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateTitle(Long conversationId, String title) {
        AiConversation conversation = requireOwnedConversation(conversationId);
        conversation.setTitle(title);
        conversationMapper.updateById(conversation);
    }

    /** 标题生成提示词：短中文标题，直接输出文本，禁止装饰符号 */
    private static final String TITLE_PROMPT_TEMPLATE = "你是会话标题生成器。请根据用户消息生成一个简短的中文对话标题。"
            + "要求：直接输出标题文本，不要任何解释或前后缀；不超过20个字；概括用户核心意图；"
            + "不要使用引号、书名号、emoji、序号，也不要以句号结尾。\n\n用户消息：";

    /** 标题生成输入截断长度（字符） */
    private static final int TITLE_INPUT_MAX_LEN = 500;

    /** 生成标题兜底截断长度（字符） */
    private static final int TITLE_MAX_LEN = 30;

    /** 模型常用来包裹标题的成对符号（两串下标一一对应） */
    private static final String TITLE_WRAP_OPENERS = "\"'“‘「『《";
    private static final String TITLE_WRAP_CLOSERS = "\"'”’」』》";

    @Override
    public String generateTitle(Long conversationId, String content) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null
                || !Objects.equals(conversation.getUserId(), SecurityHelper.getCurrentUserId())) {
            return null;
        }
        String currentTitle = conversation.getTitle();
        // 仅占位标题生成：天然保证「只生成一次」且不覆盖用户手动改过的标题。
        // 注：Agent 前缀即视为占位（用户手改成 Agent 前缀名的场景极罕见，覆盖一次可接受）
        if (!isPlaceholderTitle(currentTitle)) {
            return null;
        }
        String input = content.length() > TITLE_INPUT_MAX_LEN ? content.substring(0, TITLE_INPUT_MAX_LEN) : content;
        ChatRequest titleRequest = new ChatRequest();
        titleRequest.setModelId(conversation.getModelId());
        titleRequest.setContent(TITLE_PROMPT_TEMPLATE + input);
        titleRequest.setTemperature(0.3);
        AiModelConfig modelConfig = modelResolver.resolve(titleRequest);
        if (modelConfig == null) {
            log.warn("会话标题生成跳过：无可用启用模型，conversationId={}", conversationId);
            return null;
        }
        ChatResponse response = llmClient.chat(titleRequest, modelConfig);
        recordUsage(modelConfig, response);
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            log.warn("会话标题生成失败：conversationId={}，原因={}", conversationId,
                    response == null ? "响应为空" : response.getErrorMessage());
            return null;
        }
        String cleaned = cleanGeneratedTitle(response.getContent());
        if (cleaned == null) {
            log.warn("会话标题生成结果清洗后为空：conversationId={}", conversationId);
            return null;
        }
        boolean agentConversation = currentTitle != null
                && (currentTitle.trim().startsWith("Agent：") || currentTitle.trim().startsWith("Agent:"));
        String newTitle = agentConversation ? "Agent：" + cleaned : cleaned;
        conversation.setTitle(newTitle);
        conversationMapper.updateById(conversation);
        return newTitle;
    }

    /** 占位标题判定：空 / 新对话 / 与 xx 的对话 / Agent 前缀 */
    private boolean isPlaceholderTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String trimmed = title.trim();
        return "新对话".equals(trimmed)
                || (trimmed.startsWith("与 ") && trimmed.endsWith(" 的对话"))
                || trimmed.startsWith("Agent：")
                || trimmed.startsWith("Agent:");
    }

    /** 清洗生成结果：折叠空白、去结尾句号、剥整体包裹的成对符号、截断；清洗后为空返回 null */
    private String cleanGeneratedTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("\\s+", " ").trim();
        // 只剥「整体成对包裹」的符号：标题内部的书名号/引号属于正文（如「《三体》读后感」），不能动
        String previous;
        do {
            previous = cleaned;
            cleaned = cleaned.replaceAll("[。.\\s]+$", "");
            if (cleaned.length() >= 2) {
                int pair = TITLE_WRAP_OPENERS.indexOf(cleaned.charAt(0));
                int last = cleaned.length() - 1;
                if (pair >= 0 && cleaned.indexOf(TITLE_WRAP_CLOSERS.charAt(pair), 1) == last) {
                    cleaned = cleaned.substring(1, last).trim();
                }
            }
        } while (!cleaned.equals(previous));
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > TITLE_MAX_LEN) {
            // 截断点落在代理对中间时回退一位，避免留下半个字符
            int end = Character.isHighSurrogate(cleaned.charAt(TITLE_MAX_LEN - 1)) ? TITLE_MAX_LEN - 1 : TITLE_MAX_LEN;
            cleaned = cleaned.substring(0, end);
        }
        return cleaned;
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        AiConversation conversation = requireOwnedConversation(conversationId);
        // 删除消息
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversation.getConversationId());
        messageMapper.delete(wrapper);

        // 删除对话
        conversationMapper.deleteById(conversationId);
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
            // 指定会话发送：校验归属（不存在/他人会话一律 404，不再静默生成孤儿会话 ID）
            conversation = requireOwnedConversation(conversationId);
            conversationIdStr = conversation.getConversationId();
        }

        // 保存用户消息
        AiMessage userMessage = new AiMessage();
        userMessage.setMessageId(UUID.randomUUID().toString());
        userMessage.setConversationId(conversationIdStr);
        userMessage.setRole("user");
        userMessage.setContent(request.getContent());
        userMessage.setStatus(1);
        messageMapper.insert(userMessage);

        // 调用 LLM
        AiModelConfig modelConfig = modelResolver.resolve(request);
        ChatResponse chatResponse = modelConfig != null
                ? llmClient.chat(request, modelConfig)
                : noModelResponse();
        recordUsage(modelConfig, chatResponse);

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

        // 调用 LLM
        AiModelConfig modelConfig = modelResolver.resolve(request);
        ChatResponse response = modelConfig != null
                ? llmClient.chat(request, modelConfig)
                : noModelResponse();
        recordUsage(modelConfig, response);

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
            AiConversation conversation = requireOwnedConversation(request.getConversationId());
            if (conversation.getModelId() != null) {
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
        // 直连上游 LLM 流式接口（不需要 Security Context）
        AiModelConfig modelConfig = modelResolver.resolve(request);
        if (modelConfig == null) {
            return Flux.just("{\"error\":true,\"message\":\"没有可用的模型配置，请先在模型管理中启用模型\"}");
        }
        return llmClient.stream(request, modelConfig);
    }

    /** 非流式调用记账(成功/失败均记;失败无 Token 只记请求与失败次数) */
    private void recordUsage(AiModelConfig modelConfig, ChatResponse response) {
        if (modelConfig == null || response == null) {
            return;
        }
        Long userId = SecurityHelper.getCurrentUserId();
        usageRecorder.record(userId == null ? 0L : userId, modelConfig.getId(),
                Boolean.TRUE.equals(response.getSuccess()),
                response.getInputTokens() == null ? 0 : response.getInputTokens(),
                response.getOutputTokens() == null ? 0 : response.getOutputTokens(),
                response.getResponseTime() == null ? 0 : response.getResponseTime().intValue());
    }

    private ChatResponse noModelResponse() {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage("没有可用的模型配置，请先在模型管理中启用模型");
        return response;
    }

    @Override
    @Transactional
    public void saveAiMessage(Long conversationId, String content, String reasoningContent) {
        // 前端回传落库同样受归属约束：禁止向他人会话伪造 assistant 消息
        AiConversation conversation = requireOwnedConversation(conversationId);

        AiMessage message = new AiMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversation.getConversationId());
        message.setRole("assistant");
        message.setContent(content);
        message.setReasoningContent(reasoningContent);
        message.setStatus(1);
        messageMapper.insert(message);

        // 更新对话统计
        updateConversationStats(conversation.getConversationId());
    }

    @Override
    public List<ConversationResponse> getConversationList() {
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        // 遗留全量接口同样按用户隔离：非管理员仅返回本人会话
        if (!SecurityHelper.isAdmin()) {
            wrapper.eq(AiConversation::getUserId, SecurityHelper.getCurrentUserId());
        }
        wrapper.orderByDesc(AiConversation::getCreateTime);
        List<AiConversation> conversations = conversationMapper.selectList(wrapper);
        return conversations.stream().map(this::toConversationResponse).collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    /**
     * 会话归属校验：非管理员仅可操作本人会话；不存在/无权统一按 404 拒绝（不泄露存在性）。
     */
    private AiConversation requireOwnedConversation(Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !isConversationOwner(conversation)) {
            throw new BusinessException(404, "会话不存在");
        }
        return conversation;
    }

    private boolean isConversationOwner(AiConversation conversation) {
        return SecurityHelper.isAdmin()
                || Objects.equals(conversation.getUserId(), SecurityHelper.getCurrentUserId());
    }

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
            AiConversation conversation = requireOwnedConversation(request.getConversationId());
            conversationIdStr = conversation.getConversationId();
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
        response.setReasoningContent(message.getReasoningContent());
        response.setInputTokens(message.getInputTokens());
        response.setOutputTokens(message.getOutputTokens());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}