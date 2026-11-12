package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 生成会话标题：占位判定/清洗/前缀拼接/静默失败/越权拒绝/乐观锁条件更新
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

    @BeforeAll
    static void initTableInfo() {
        // MP 3.5.18：纯单测下 LambdaUpdateWrapper 渲染（getSqlSegment）需要实体 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiConversation.class);
    }

    @BeforeEach
    void setUp() {
        chatService = new AiChatServiceImpl(llmClient, modelResolver, conversationMapper,
                messageMapper, modelConfigMapper, usageRecorder);
        securityHelper = mockStatic(SecurityHelper.class);
        securityHelper.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
        // 乐观锁条件更新缺省命中 1 行（个别用例覆写为 0 模拟并发输家）
        lenient().when(conversationMapper.update(isNull(), any())).thenReturn(1);
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
    @DisplayName("占位标题生成成功：清洗装饰符号并条件更新落库返回")
    void 占位标题生成成功() {
        AiConversation conversation = conversation("与 GLM-4-Flash 的对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("  「报表功能怎么用」。\n"));

        String title = chatService.generateTitle(100L, "帮我看看报表功能怎么用");

        assertThat(title).isEqualTo("报表功能怎么用");
        ArgumentCaptor<LambdaUpdateWrapper<AiConversation>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(conversationMapper).update(isNull(), captor.capture());
        // 条件更新：WHERE 命中会话行且旧值=占位标题，SET 新标题
        // （先渲染 sqlSegment 使条件参数登记进 paramNameValuePairs，set 参数本就即时登记）
        assertThat(captor.getValue().getSqlSegment()).contains("title");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains("报表功能怎么用", "与 GLM-4-Flash 的对话", 100L);
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
        verify(conversationMapper, never()).update(any(), any());
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
        verify(conversationMapper, never()).update(any(), any());
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
        verify(conversationMapper, never()).update(any(), any());
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
    @DisplayName("空标题（未传 title 创建的会话）视为占位：正常生成，条件更新用 IS NULL")
    void 空标题视为占位() {
        AiConversation conversation = conversation(null, 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("空白标题会话"));

        assertThat(chatService.generateTitle(100L, "内容")).isEqualTo("空白标题会话");
        // SQL NULL 不能用 = 匹配：空标题须走 title IS NULL 条件
        ArgumentCaptor<LambdaUpdateWrapper<AiConversation>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(conversationMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("title IS NULL");
    }

    @Test
    @DisplayName("乐观锁输家：生成期间标题被手动改名/并发生成，条件更新 0 行命中放弃覆盖")
    void 并发改名放弃覆盖() {
        AiConversation conversation = conversation("新对话", 1L);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        when(modelResolver.resolve(any())).thenReturn(model);
        when(llmClient.chat(any(), eq(model))).thenReturn(llmOk("AI 起的标题"));
        // 模拟 LLM 生成期间另一端已改标题：WHERE title='新对话' 不再命中
        when(conversationMapper.update(isNull(), any())).thenReturn(0);

        String title = chatService.generateTitle(100L, "内容");

        assertThat(title).isNull();
        // 条件更新守的是旧占位值（不是无条件按 id 覆盖）；渲染后断言条件参数
        ArgumentCaptor<LambdaUpdateWrapper<AiConversation>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(conversationMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("title =");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains("新对话", "AI 起的标题", 100L);
    }
}
