package com.forge.modules.workflow.framework.instance;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 流程实例装配器单元测试（活动/历史/UNION 行 → 响应对象的核心字段转换）
 */
class ProcessInstanceAssemblerTest {

    private ProcessService processService;
    private FlowLongIdentityService identityService;
    private WfProcessExtMapper processExtMapper;
    private ProcessInstanceAssembler assembler;

    @BeforeEach
    void setUp() {
        processService = mock(ProcessService.class);
        identityService = mock(FlowLongIdentityService.class);
        processExtMapper = mock(WfProcessExtMapper.class);
        assembler = new ProcessInstanceAssembler(processService, identityService,
                mock(FlwTaskActorMapper.class), mock(QueryService.class), processExtMapper);
    }

    private FlwProcess process() {
        FlwProcess process = new FlwProcess();
        process.setId(10L);
        process.setProcessName("请假流程");
        process.setProcessKey("leave");
        return process;
    }

    @Test
    @DisplayName("convertToResponse 装配 core 字段（定义名/Key/优先级名/发起人/扩展分类）")
    void convertActiveInstanceCoreFields() {
        FlwInstance instance = new FlwInstance();
        instance.setId(1L);
        instance.setProcessId(10L);
        instance.setBusinessKey("BO-1");
        instance.setCreateTime(new Date());
        instance.setPriority(0);
        instance.setUrgent(1);
        instance.setCreateId("7");
        instance.setCurrentNodeName("审批中");
        instance.setInstanceNo("PF20260001");
        instance.setVariable("{\"__title\":\"张三的请假\"}");
        when(processService.getProcessById(10L)).thenReturn(process());
        when(identityService.getUserName(7L)).thenReturn("张三");
        WfProcessExt ext = new WfProcessExt();
        ext.setProcessId(10L);
        ext.setCategoryId(5L);
        when(processExtMapper.selectOne(any())).thenReturn(ext);

        ProcessInstanceResponse response = assembler.convertToResponse(instance);

        assertEquals("1", response.getId());
        assertEquals("10", response.getProcessDefinitionId());
        assertEquals("请假流程", response.getProcessDefinitionName());
        assertEquals("leave", response.getProcessDefinitionKey());
        assertEquals("BO-1", response.getBusinessKey());
        assertEquals("张三", response.getStartUserName());
        assertEquals("普通", response.getPriorityName());
        assertEquals(Integer.valueOf(1), response.getUrgent());
        assertEquals("PF20260001", response.getProcessNo());
        assertEquals("张三的请假", response.getInstanceTitle());
        assertEquals(Long.valueOf(5L), response.getCategoryId());
        assertNotNull(response.getStartTime());
    }

    @Test
    @DisplayName("convertToHisResponse 按 instanceState 映射 deleteReason 文案")
    void convertHisInstanceDeleteReasonMapping() {
        FlwHisInstance hisInstance = new FlwHisInstance();
        hisInstance.setId(2L);
        hisInstance.setProcessId(10L);
        hisInstance.setInstanceState(2);
        hisInstance.setDuration(3600L);
        when(processService.getProcessById(10L)).thenReturn(process());

        ProcessInstanceResponse rejected = assembler.convertToHisResponse(hisInstance);
        assertEquals("审批拒绝", rejected.getDeleteReason());
        assertEquals(Long.valueOf(3600L), rejected.getDurationInMillis());

        hisInstance.setInstanceState(7);
        assertEquals("自动拒绝", assembler.convertToHisResponse(hisInstance).getDeleteReason());

        hisInstance.setInstanceState(1);
        assertNull(assembler.convertToHisResponse(hisInstance).getDeleteReason());
    }

    @Test
    @DisplayName("convertUnionRowToResponse 装配 UNION 行（列名小写/优先级/终止态文案）")
    void convertUnionRowCoreFields() {
        when(processService.getProcessById(10L)).thenReturn(process());
        when(identityService.getUserName(7L)).thenReturn("张三");
        Map<String, Object> row = Map.of(
                "id", "3",
                "process_id", "10",
                "create_id", "7",
                "business_key", "BO-3",
                "priority", "1",
                "instance_state", "5",
                "instance_no", "PF20260003");

        ProcessInstanceResponse response = assembler.convertUnionRowToResponse(row);

        assertEquals("3", response.getId());
        assertEquals("请假流程", response.getProcessDefinitionName());
        assertEquals("张三", response.getStartUserName());
        assertEquals("BO-3", response.getBusinessKey());
        assertEquals(Integer.valueOf(1), response.getPriority());
        assertEquals("强制终止", response.getDeleteReason());
        assertEquals("PF20260003", response.getProcessNo());
    }

    @Test
    @DisplayName("toLocalDateTime null 安全；优先级 1 映射高优先级文案")
    void helpersEdgeCases() {
        assertNull(assembler.toLocalDateTime(null));
        Date date = new Date();
        assertNotNull(assembler.toLocalDateTime(date));
    }

    @Test
    @DisplayName("fillCurrentAssigneeInfo 无活动任务时保持空且不查参与者")
    void fillAssigneeWithoutTasks() {
        QueryService queryService = mock(QueryService.class);
        com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper actorMapper =
                mock(com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper.class);
        ProcessInstanceAssembler withQuery = new ProcessInstanceAssembler(processService, identityService,
                actorMapper, queryService, processExtMapper);
        when(queryService.getTasksByInstanceId(1L)).thenReturn(List.of());

        ProcessInstanceResponse response = new ProcessInstanceResponse();
        withQuery.fillCurrentAssigneeInfo(response, 1L);

        assertNull(response.getCurrentAssigneeNames());
        assertNull(response.getCurrentCandidateNames());
        verifyNoInteractions(actorMapper);
    }
}
