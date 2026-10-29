package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.exception.FlowLongException;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwProcessMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.workflow.framework.diagram.FlowLongDiagramGenerator;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfCategoryMapper;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * getLatestDefinitionIdByKey 按 key 解析最新流程定义的门面规则单测
 */
class WfProcessDefinitionKeyLookupTest {

    private ProcessService processService;
    private WfProcessDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        processService = mock(ProcessService.class);
        service = new WfProcessDefinitionServiceImpl(
                processService,
                mock(QueryService.class),
                mock(FlwProcessMapper.class),
                mock(FlwInstanceMapper.class),
                mock(FlowLongDiagramGenerator.class),
                mock(WfProcessExtMapper.class),
                mock(WfCategoryMapper.class),
                mock(FlowLongIdentityService.class),
                mock(com.aizuda.bpm.engine.FlowLongEngine.class),
                mock(ObjectMapper.class));
    }

    @Test
    @DisplayName("命中：返回最新版本流程定义ID字符串")
    void returnsLatestDefinitionId() {
        FlwProcess process = new FlwProcess();
        process.setId(9527L);
        when(processService.getProcessByKey(anyString(), anyString())).thenReturn(process);

        assertEquals("9527", service.getLatestDefinitionIdByKey("project_approval"));
    }

    @Test
    @DisplayName("未部署：引擎 FlowLongException 转译为 404 带排查指引")
    void missingKeyTranslatesTo404() {
        when(processService.getProcessByKey(anyString(), anyString()))
                .thenThrow(new FlowLongException("process key [x] does not exist"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getLatestDefinitionIdByKey("x"));
        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("空 key：400 拒绝")
    void blankKeyRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getLatestDefinitionIdByKey(" "));
        assertEquals(400, e.getCode());
    }
}
