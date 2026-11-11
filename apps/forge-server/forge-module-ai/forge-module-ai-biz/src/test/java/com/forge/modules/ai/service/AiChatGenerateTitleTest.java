package com.forge.modules.ai.service;

import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiConversation;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiConversationMapper;
import com.forge.modules.ai.mapper.AiMessageMapper;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import com.forge.modules.ai.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 生成会话标题：占位判定/清洗/前缀拼接/静默失败/越权拒绝
 */
@ExtendWith(MockitoExtension.class)
class AiChatGenerateTitleTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private AiModelResolver modelResolver;
    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;
    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private AiUsageRecorder usageRecorder;

    private AiChatServiceImpl chatService;

    private MockedStatic<SecurityHelper> securityHelper;

    private final AiModelConfig model = modelConfig();

    @BeforeEach
    void setUp() {
        chatService = new AiChatServiceImpl(llmClient, modelResolver, conversationMapper,
                messageMapper, modelConfigMapper, usageRecorder);
        securityHelper = mockStatic(SecurityHelper.class);
        securityHelper.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        securityHelper.close();
    }

    private AiModelConfig modelConfig() {
        AiModelConfig config = new AiModelConfig();
        config.setId(9L);
        config.setModelName("GLM-4-Flash");
        config.setModelCode("glm-4-flash");
        config.setStatus(1);
        return config;
    }

    private AiConversation conversation(String title, Long userId) {
        AiConversation conversation = new AiConversation();
        conversation.setId(100L);
        conversation.setConversationId("uuid-100");
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setStatus(1);
        return conversation;
    }

    private ChatResponse llmOk(String content) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setContent(content);
        return response;
    }

    @Test
    @DisplayName("占位标题生成成功：清洗装饰符号并落库返回")
    void 占位标题生成成功() {
        AiConversation conversation = conversation("与 GLM-4-Flash 的对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("  「报表功能怎么用」。\n"));

        String title = chatService.generateTitle(100L, "帮我看看报表功能怎么用");

        assertThat(title).isEqualTo("报表功能怎么用");
        ArgumentCaptor<AiConversation> captor = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("报表功能怎么用");
    }

    @Test
    @DisplayName("生成请求携带低温与截断后的用户消息")
    void 生成请求参数正确() {
        AiConversation conversation = conversation("新对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("长文本提问"));

        String longContent = "x".repeat(600);
        chatService.generateTitle(100L, longContent);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).chat(captor.capture(), eq(model));
        assertThat(captor.getValue().getTemperature()).isEqualTo(0.3);
        // 用户消息截断 500 字符后拼入提示词
        assertThat(captor.getValue().getContent()).endsWith("x".repeat(500));
    }

    @Test
    @DisplayName("Agent 占位标题：新标题保留 Agent： 前缀")
    void agent前缀保留() {
        AiConversation conversation = conversation("Agent：数据分析师", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("销售数据分析"));

        String title = chatService.generateTitle(100L, "帮我分析上个月的销售数据");

        assertThat(title).isEqualTo("Agent：销售数据分析");
    }

    @Test
    @DisplayName("非占位标题（用户已改名或已生成过）：拒绝生成且不落库")
    void 非占位标题拒绝() {
        AiConversation conversation = conversation("我手动起的名字", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);

        String title = chatService.generateTitle(100L, "内容");

        assertThat(title).isNull();
        verify(llmClient, never()).chat(any(), any());
        verify(conversationMapper, never()).updateById(any(AiConversation.class));
    }

    @Test
    @DisplayName("非本人会话：静默拒绝")
    void 非本人会话拒绝() {
        when(conversationMapper.selectById(100L)).thenReturn(conversation("新对话", 2L));

        assertThat(chatService.generateTitle(100L, "内容")).isNull();
        verify(llmClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("LLM 失败：静默返回 null 不落库")
    void llm失败静默() {
        AiConversation conversation = conversation("新对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        ChatResponse failed = new ChatResponse();
        failed.setSuccess(false);
        failed.setErrorMessage("超时");
        when(llmClient.chat(any(), eq(model))).thenReturn(failed);

        assertThat(chatService.generateTitle(100L, "内容")).isNull();
        verify(conversationMapper, never()).updateById(any(AiConversation.class));
    }

    @Test
    @DisplayName("无可用模型：静默返回 null")
    void 无模型静默() {
        AiConversation conversation = conversation("新对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(null);

        assertThat(chatService.generateTitle(100L, "内容")).isNull();
        verify(llmClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("生成内容清洗后为空（仅装饰符号）：静默返回 null")
    void 清洗后为空静默() {
        AiConversation conversation = conversation("新对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("「」"));

        assertThat(chatService.generateTitle(100L, "内容")).isNull();
        verify(conversationMapper, never()).updateById(any(AiConversation.class));
    }

    @Test
    @DisplayName("超长生成结果截断到 30 字符")
    void 超长截断() {
        AiConversation conversation = conversation("新对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("标".repeat(45)));

        String title = chatService.generateTitle(100L, "内容");

        assertThat(title).hasSize(30);
    }

    @Test
    @DisplayName("标题内部的书名号/引号属于正文：不剥；整体包裹的引号才剥")
    void 只剥整体包裹符号() {
        when(conversationMapper.selectById(100L)).thenAnswer(inv -> conversation("新对话", 1L));
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(
                llmOk("《三体》读后感"), llmOk("《三体》和《流浪地球》"), llmOk("\"如何学习C#\""));

        assertThat(chatService.generateTitle(100L, "内容")).isEqualTo("《三体》读后感");
        assertThat(chatService.generateTitle(100L, "内容")).isEqualTo("《三体》和《流浪地球》");
        assertThat(chatService.generateTitle(100L, "内容")).isEqualTo("如何学习C#");
    }

    @Test
    @DisplayName("成功与失败的调用都记入用量")
    void 调用记入用量() {
        when(conversationMapper.selectById(100L)).thenReturn(conversation("新对话", 1L));
        when(modelResolver.resolve(any())).thenReturn(model);
        ChatResponse ok = llmOk("用量统计");
        ok.setInputTokens(40);
        ok.setOutputTokens(6);
        when(llmClient.chat(any(), eq(model))).thenReturn(ok);

        chatService.generateTitle(100L, "内容");

        verify(usageRecorder).record(1L, 9L, true, 40, 6, 0);
    }

    @Test
    @DisplayName("空标题（未传 title 创建的会话）视为占位：正常生成")
    void 空标题视为占位() {
        AiConversation conversation = conversation(null, 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("空白标题会话"));

        assertThat(chatService.generateTitle(100L, "内容")).isEqualTo("空白标题会话");
    }
}
