package com.forge.modules.workflow.framework.instance;

import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.mapper.WfInstanceQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 流程实例查询服务单元测试（状态路由 + 名称模糊条件 + 标题转义下推）
 */
class WfInstanceQueryServiceTest {

    private FlwInstanceMapper instanceMapper;
    private FlwHisInstanceMapper hisInstanceMapper;
    private WfInstanceQueryMapper instanceQueryMapper;
    private ProcessInstanceAssembler assembler;
    private WfInstanceQueryService queryService;

    @BeforeEach
    void setUp() {
        instanceMapper = mock(FlwInstanceMapper.class);
        hisInstanceMapper = mock(FlwHisInstanceMapper.class);
        instanceQueryMapper = mock(WfInstanceQueryMapper.class);
        assembler = mock(ProcessInstanceAssembler.class);
        queryService = new WfInstanceQueryService(
                instanceMapper, hisInstanceMapper, instanceQueryMapper, assembler);
    }

    private ProcessInstanceQueryRequest request(String status) {
        ProcessInstanceQueryRequest request = new ProcessInstanceQueryRequest();
        request.setStatus(status);
        request.setPageNum(1);
        request.setPageSize(10);
        return request;
    }

    @Test
    @DisplayName("status=running 走运行中查询并委托装配器转换")
    void runningStatusQueriesActiveInstances() {
        FlwInstance instance = new FlwInstance();
        instance.setId(1L);
        Page<FlwInstance> page = new Page<>(1, 10);
        page.setRecords(List.of(instance));
        when(instanceMapper.selectPage(any(), any())).thenReturn(page);
        when(assembler.convertToResponse(instance)).thenReturn(new ProcessInstanceResponse());

        Page<ProcessInstanceResponse> result = queryService.queryInstances(request("running"), 7L);

        assertEquals(1, result.getRecords().size());
        verify(instanceMapper).selectPage(any(), any());
        verifyNoInteractions(hisInstanceMapper, instanceQueryMapper);
        verify(assembler).convertToResponse(instance);
    }

    @Test
    @DisplayName("status=finished 走历史查询并委托装配器转换")
    void finishedStatusQueriesHisInstances() {
        com.aizuda.bpm.engine.entity.FlwHisInstance hisInstance = new com.aizuda.bpm.engine.entity.FlwHisInstance();
        hisInstance.setId(2L);
        Page<com.aizuda.bpm.engine.entity.FlwHisInstance> page = new Page<>(1, 10);
        page.setRecords(List.of(hisInstance));
        when(hisInstanceMapper.selectPage(any(), any())).thenReturn(page);
        when(assembler.convertToHisResponse(hisInstance)).thenReturn(new ProcessInstanceResponse());

        Page<ProcessInstanceResponse> result = queryService.queryInstances(request("finished"), null);

        assertEquals(1, result.getRecords().size());
        verify(hisInstanceMapper).selectPage(any(), any());
        verifyNoInteractions(instanceMapper, instanceQueryMapper);
        verify(assembler).convertToHisResponse(hisInstance);
    }

    @Test
    @DisplayName("未指定状态走 UNION 合并查询，标题经 escapeLike 转义后下推")
    void defaultStatusRoutesToUnionQueryWithTitleEscape() {
        when(instanceQueryMapper.selectUnionPage(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of());
        when(assembler.convertUnionRowToResponse(any())).thenReturn(new ProcessInstanceResponse());

        ProcessInstanceQueryRequest req = request(null);
        req.setTitle("a_b%c");
        Page<ProcessInstanceResponse> result = queryService.queryInstances(req, 3L);

        assertNotNull(result);
        // title 关键字按 | 转义（% _ 字面匹配），发起人转字符串下推
        verify(instanceQueryMapper).selectUnionPage(any(), eq("3"), isNull(), isNull(), isNull(),
                isNull(), eq("a|_b|%c"), eq(false));
        verifyNoInteractions(instanceMapper, hisInstanceMapper);
    }

    @Test
    @DisplayName("processName 模糊条件生成 %name% 下推（兼容 processDefinitionName 兜底）")
    void processNameLikeBuiltFromRequest() {
        when(instanceQueryMapper.selectUnionPage(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of());
        when(assembler.convertUnionRowToResponse(any())).thenReturn(new ProcessInstanceResponse());

        ProcessInstanceQueryRequest req = request(null);
        req.setProcessName(" 报销 ");
        queryService.queryInstances(req, null);

        verify(instanceQueryMapper).selectUnionPage(any(), isNull(), isNull(), eq("%报销%"), isNull(),
                isNull(), isNull(), eq(false));

        // processName 缺省时回退 processDefinitionName
        ProcessInstanceQueryRequest req2 = request(null);
        req2.setProcessDefinitionName("请假");
        queryService.queryInstances(req2, null);
        verify(instanceQueryMapper).selectUnionPage(any(), isNull(), isNull(), eq("%请假%"), isNull(),
                isNull(), isNull(), eq(false));
    }

    @Test
    @DisplayName("UNION 行经装配器逐行转换，总数沿用分页参数")
    void unionRowsDelegatedToAssembler() {
        when(instanceQueryMapper.selectUnionPage(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(Map.of("id", "1")));
        when(assembler.convertUnionRowToResponse(any())).thenReturn(new ProcessInstanceResponse());

        Page<ProcessInstanceResponse> result = queryService.queryInstances(request(null), null);

        assertEquals(1, result.getRecords().size());
        verify(assembler).convertUnionRowToResponse(any());
    }
}
