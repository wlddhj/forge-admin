package com.forge.modules.workflow.service.impl;

import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.workflow.dto.model.ModelRequest;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * createModel 落 modelJson 单元测试（修复前创建时该字段被静默丢弃）
 */
class WfModelServiceCreateTest {

    private WfProcessExtMapper processExtMapper;
    private WfModelServiceImpl service;
    private MockedStatic<SecurityHelper> securityHelper;

    @BeforeEach
    void setUp() {
        processExtMapper = mock(WfProcessExtMapper.class);
        service = new WfModelServiceImpl(
                mock(com.aizuda.bpm.engine.ProcessService.class),
                mock(com.aizuda.bpm.engine.QueryService.class),
                processExtMapper,
                mock(com.forge.modules.workflow.mapper.WfCategoryMapper.class),
                mock(com.forge.modules.workflow.identity.FlowLongIdentityService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        securityHelper = mockStatic(SecurityHelper.class);
        securityHelper.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        securityHelper.close();
    }

    private ModelRequest request(String modelJson) {
        ModelRequest request = new ModelRequest();
        request.setName("测试模型");
        request.setKey("t_create_" + System.nanoTime());
        request.setModelJson(modelJson);
        return request;
    }

    @Test
    @DisplayName("创建时携带 modelJson：草稿直接落模型内容")
    void createModel_persistsModelJson() {
        String json = "{\"name\":\"测试模型\",\"key\":\"t1\",\"nodeConfig\":{\"type\":0}}";
        service.createModel(request(json));

        ArgumentCaptor<WfProcessExt> captor = ArgumentCaptor.forClass(WfProcessExt.class);
        verify(processExtMapper).insert(captor.capture());
        assertEquals(json, captor.getValue().getModelJson(), "创建时应落 modelJson");
    }

    @Test
    @DisplayName("创建时不带 modelJson：字段保持 null（行为不变）")
    void createModel_blankJsonKeepsNull() {
        service.createModel(request(null));

        ArgumentCaptor<WfProcessExt> captor = ArgumentCaptor.forClass(WfProcessExt.class);
        verify(processExtMapper).insert(captor.capture());
        assertNull(captor.getValue().getModelJson());
    }

    @Test
    @DisplayName("key 重复仍被拦截（既有行为不受影响）")
    void createModel_duplicateKeyRejected() {
        when(processExtMapper.selectCount(any())).thenReturn(1L);
        assertThrows(com.forge.common.exception.BusinessException.class,
                () -> service.createModel(request("{}")));
        verify(processExtMapper, never()).insert(any(WfProcessExt.class));
    }
}
