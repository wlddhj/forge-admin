package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.entity.AiConversation;
import com.forge.modules.ai.entity.AiMessage;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 会话资源归属校验单元测试（安全堵口第一批 T3）：
 * 非管理员的读/写/删/流式上下文/save-ai 均限本人会话，404 语义不泄露存在性。
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceOwnershipTest {

    private static final Long OWNER = 1L;
    private static final Long OUTSIDER = 9L;
    private static final Long CONV_ID = 100L;

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

    private AiChatServiceImpl service;
    private MockedStatic<SecurityHelper> securityHelper;

    @BeforeAll
    static void initTableInfo() {
        // MP 3.5.17：纯单测下 LambdaQueryWrapper 需要实体 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiConversation.class);
        TableInfoHelper.initTableInfo(assistant, AiMessage.class);
    }

    @BeforeEach
    void setUp() {
        service = new AiChatServiceImpl(llmClient, modelResolver, conversationMapper,
                messageMapper, modelConfigMapper, usageRecorder);
        securityHelper = mockStatic(SecurityHelper.class);
        securityHelper.when(SecurityHelper::getCurrentUserId).thenReturn(OUTSIDER);
        securityHelper.when(SecurityHelper::isAdmin).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        securityHelper.close();
    }

    private AiConversation ownedConversation() {
        AiConversation conversation = new AiConversation();
        conversation.setId(CONV_ID);
        conversation.setConversationId("conv-uuid-100");
        conversation.setUserId(OWNER);
        conversation.setTitle("他人的会话");
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation);
        return conversation;
    }

    @Test
    @DisplayName("非管理员读他人会话被拒（404，不泄露存在性）")
    void getConversation_shouldDenyOutsider() {
        ownedConversation();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getConversation(CONV_ID));
        assertEquals(404, ex.getCode());
        verify(messageMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("非管理员读他人会话消息列表被拒（404）")
    void getMessages_shouldDenyOutsider() {
        ownedConversation();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMessages(CONV_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("非管理员删除他人会话被拒（404），不产生任何删除")
    void deleteConversation_shouldDenyOutsider() {
        ownedConversation();
        assertThrows(BusinessException.class, () -> service.deleteConversation(CONV_ID));
        verify(conversationMapper, never()).deleteById(any(Long.class));
        verify(messageMapper, never()).delete(any());
    }

    @Test
    @DisplayName("非管理员向他人会话 save-ai 伪造 assistant 消息被拒（404）")
    void saveAiMessage_shouldDenyOutsider() {
        ownedConversation();
        assertThrows(BusinessException.class, () -> service.saveAiMessage(CONV_ID, "伪造回复", "推理"));
        verify(messageMapper, never()).insert(any(AiMessage.class));
    }

    @Test
    @DisplayName("非管理员 sendMessage 指定他人会话被拒（404），不落消息不调 LLM")
    void sendMessage_shouldDenyOutsider() {
        ownedConversation();
        ChatRequest request = new ChatRequest();
        request.setConversationId(CONV_ID);
        request.setContent("hello");
        assertThrows(BusinessException.class, () -> service.sendMessage(request));
        verify(messageMapper, never()).insert(any(AiMessage.class));
        verify(llmClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("本人会话正常读取")
    void getConversation_shouldAllowOwner() {
        AiConversation conversation = ownedConversation();
        conversation.setUserId(OUTSIDER);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        var response = service.getConversation(CONV_ID);
        assertEquals(CONV_ID, response.getId());
    }

    @Test
    @DisplayName("管理员豁免可读任意会话")
    void getConversation_shouldAllowAdmin() {
        ownedConversation();
        securityHelper.when(SecurityHelper::isAdmin).thenReturn(true);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        var response = service.getConversation(CONV_ID);
        assertTrue(response != null);
    }

    @Test
    @DisplayName("会话不存在与无权同样 404（不可区分）")
    void getConversation_notFound() {
        when(conversationMapper.selectById(CONV_ID)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getConversation(CONV_ID));
        assertEquals(404, ex.getCode());
    }
}
