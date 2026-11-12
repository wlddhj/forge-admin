package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.ai.agent.kernel.AgentGraphRunner;
import com.forge.modules.ai.agent.kernel.AgentRunEventBridge;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.entity.AiDocument;
import com.forge.modules.ai.mapper.AiAgentRunMapper;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMapper;
import com.forge.modules.ai.mapper.AiAgentTodoMapper;
import com.forge.modules.ai.mapper.AiDocumentMapper;
import com.forge.modules.ai.service.impl.AiAgentRunServiceImpl;
import com.forge.modules.ai.service.impl.AiDocumentServiceImpl;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.framework.web.websocket.NotificationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 文档与 Agent 运行记录归属校验单元测试（安全堵口第一批 T4/T5）。
 */
@ExtendWith(MockitoExtension.class)
class AiResourceOwnershipTest {

    private static final Long OWNER = 1L;
    private static final Long OUTSIDER = 9L;
    private static final Long DOC_ID = 50L;
    private static final Long RUN_ID = 60L;

    @Mock
    private AiDocumentMapper documentMapper;
    @Mock
    private SysAttachmentService attachmentService;
    @Mock
    private DocumentParser documentParser;
    @Mock
    private DocumentSummarizer documentSummarizer;
    @Mock
    private AiModelResolver modelResolver;
    @Mock
    private com.forge.modules.ai.service.AiModelService modelService;

    @Mock
    private AiAgentRunMapper runMapper;
    @Mock
    private AiAgentRunStepMapper runStepMapper;
    @Mock
    private AiAgentTodoMapper todoMapper;
    @Mock
    private AiAgentTeamMapper teamMapper;
    @Mock
    private AgentGraphRunner graphRunner;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private AgentRunEventBridge agentRunEventBridge;

    private AiDocumentServiceImpl documentService;
    private AiAgentRunServiceImpl runService;
    private MockedStatic<SecurityHelper> securityHelper;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiAgentRun.class);
        TableInfoHelper.initTableInfo(assistant, AiAgentRunStep.class);
    }

    @BeforeEach
    void setUp() {
        documentService = new AiDocumentServiceImpl(documentParser, documentSummarizer, modelResolver,
                documentMapper, attachmentService, modelService);
        runService = new AiAgentRunServiceImpl(runMapper, runStepMapper, todoMapper, teamMapper,
                graphRunner, notificationService, aiChatService, agentRunEventBridge);
        securityHelper = mockStatic(SecurityHelper.class);
        securityHelper.when(SecurityHelper::getCurrentUserId).thenReturn(OUTSIDER);
        securityHelper.when(SecurityHelper::isAdmin).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        securityHelper.close();
    }

    private AiDocument ownedDocument() {
        AiDocument document = new AiDocument();
        document.setId(DOC_ID);
        document.setUserId(OWNER);
        document.setFileName("他人文档.pdf");
        when(documentMapper.selectById(DOC_ID)).thenReturn(document);
        return document;
    }

    @Test
    @DisplayName("非管理员读他人文档详情被拒（404）")
    void getDocument_shouldDenyOutsider() {
        ownedDocument();
        BusinessException ex = assertThrows(BusinessException.class, () -> documentService.getDocument(DOC_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("非管理员删除他人文档被拒（404），不产生任何删除")
    void deleteDocument_shouldDenyOutsider() {
        ownedDocument();
        assertThrows(BusinessException.class, () -> documentService.deleteDocument(DOC_ID));
        verify(documentMapper, never()).deleteById(any(Long.class));
        verify(attachmentService, never()).deleteAttachments(any());
    }

    @Test
    @DisplayName("非管理员摘要他人文档被拒（404）")
    void generateSummary_shouldDenyOutsider() {
        ownedDocument();
        assertThrows(BusinessException.class, () -> documentService.generateSummary(DOC_ID));
        verify(documentSummarizer, never()).summarize(any(), any(), any(), any());
    }

    @Test
    @DisplayName("summarize 回写他人文档摘要被拒（404）")
    void summarize_shouldDenyOutsiderWriteback() {
        ownedDocument();
        DocumentSummaryRequest request = new DocumentSummaryRequest();
        request.setDocumentId(DOC_ID);
        request.setText("文本");
        request.setStyle("brief");
        request.setMaxLength(500);
        DocumentResponse response = new DocumentResponse();
        response.setStatus(1);
        response.setSummary("摘要");
        when(modelResolver.resolve(any())).thenReturn(new com.forge.modules.ai.entity.AiModelConfig());
        when(documentSummarizer.summarize(any(), any(), any(), any())).thenReturn(response);

        assertThrows(BusinessException.class, () -> documentService.summarize(request));
        verify(documentMapper, never()).updateById(any(AiDocument.class));
    }

    @Test
    @DisplayName("管理员豁免可读任意文档")
    void getDocument_shouldAllowAdmin() {
        ownedDocument();
        securityHelper.when(SecurityHelper::isAdmin).thenReturn(true);
        DocumentResponse response = documentService.getDocument(DOC_ID);
        assertEquals(DOC_ID, response.getId());
    }

    private AiAgentRun ownedRun() {
        AiAgentRun run = new AiAgentRun();
        run.setId(RUN_ID);
        run.setUserId(OWNER);
        when(runMapper.selectById(RUN_ID)).thenReturn(run);
        return run;
    }

    @Test
    @DisplayName("非管理员读他人运行记录被拒（404）")
    void getRun_shouldDenyOutsider() {
        ownedRun();
        BusinessException ex = assertThrows(BusinessException.class, () -> runService.getRun(RUN_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("非管理员读他人运行步骤（含工具入出参）被拒（404）")
    void getSteps_shouldDenyOutsider() {
        ownedRun();
        assertThrows(BusinessException.class, () -> runService.getSteps(RUN_ID));
        verify(runStepMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("pageRuns 非管理员追加 userId 过滤（本人可见）")
    void pageRuns_shouldFilterByOwnerForNonAdmin() {
        when(runMapper.selectPage(any(Page.class), any())).thenReturn(new Page<AiAgentRun>(1, 10));
        runService.pageRuns(null, null, null, null, 1, 10);
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(runMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertEquals(true, sql != null && sql.contains("user_id"));
    }

    @Test
    @DisplayName("管理员豁免：运行记录与步骤可读")
    void run_shouldAllowAdmin() {
        ownedRun();
        securityHelper.when(SecurityHelper::isAdmin).thenReturn(true);
        when(runStepMapper.selectList(any())).thenReturn(List.of());
        assertEquals(RUN_ID, runService.getRun(RUN_ID).getId());
        assertEquals(0, runService.getSteps(RUN_ID).size());
    }
}
