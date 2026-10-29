package com.forge.modules.demo.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.demo.config.ProjectWorkflowProperties;
import com.forge.modules.demo.dto.ProjectSubmitRequest;
import com.forge.modules.demo.entity.BizProject;
import com.forge.modules.demo.mapper.BizProjectMapper;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.modules.workflow.service.WfProcessDefinitionService;
import com.forge.modules.workflow.service.WfProcessInstanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目立项服务对接规则单测
 */
class ProjectApprovalServiceImplTest {

    private final BizProjectMapper projectMapper = mock(BizProjectMapper.class);
    private final WfProcessDefinitionService definitionService = mock(WfProcessDefinitionService.class);
    private final WfProcessInstanceService instanceService = mock(WfProcessInstanceService.class);
    private final ProjectWorkflowProperties properties = new ProjectWorkflowProperties();

    private final ProjectApprovalServiceImpl service =
            new ProjectApprovalServiceImpl(projectMapper, definitionService, instanceService, properties);

    private ProjectSubmitRequest request() {
        ProjectSubmitRequest request = new ProjectSubmitRequest();
        request.setProjectName("智慧园区一期");
        request.setTotalAmount(new BigDecimal("12000.50"));
        request.setBizUser("张三");
        request.setDeptName("研发部");
        request.setRemark("首期立项");
        return request;
    }

    private ProcessInstanceResponse instance(String id, boolean active) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(id);
        response.setEndTime(active ? null : java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        return response;
    }

    @Test
    @DisplayName("提交：落库+按key解析定义+发起流程+businessKey查回实例回写")
    void submit_startsProcessAndWritesBackInstanceId() {
        doAnswer(inv -> {
            ((BizProject) inv.getArgument(0)).setId(10L);
            return 1;
        }).when(projectMapper).insert(any(BizProject.class));
        when(definitionService.getLatestDefinitionIdByKey("project_approval")).thenReturn("123");
        when(instanceService.getInstancesByBusinessKey("project:10"))
                .thenReturn(List.of(instance("456", false), instance("789", true)));

        Long id = service.submitProject(request());

        assertEquals(10L, id);
        ArgumentCaptor<ProcessStartRequest> captor = ArgumentCaptor.forClass(ProcessStartRequest.class);
        verify(instanceService).startProcess(captor.capture());
        ProcessStartRequest start = captor.getValue();
        assertEquals("123", start.getProcessDefinitionId());
        assertEquals("project:10", start.getBusinessKey());
        assertEquals("智慧园区一期", start.getVariables().get("projectName"));
        assertEquals(new BigDecimal("12000.50"), start.getVariables().get("totalAmount"));
        assertEquals("张三", start.getVariables().get("bizUser"));

        ArgumentCaptor<BizProject> updateCaptor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(updateCaptor.capture());
        assertEquals(10L, updateCaptor.getValue().getId());
        assertEquals(789L, updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    @DisplayName("提交：发起后查无在途实例 → 500（事务回滚业务记录）")
    void submit_failsWhenNoActiveInstance() {
        doAnswer(inv -> {
            ((BizProject) inv.getArgument(0)).setId(11L);
            return 1;
        }).when(projectMapper).insert(any(BizProject.class));
        when(definitionService.getLatestDefinitionIdByKey(anyString())).thenReturn("123");
        when(instanceService.getInstancesByBusinessKey("project:11")).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.submitProject(request()));
        assertEquals(500, e.getCode());
    }

    @Test
    @DisplayName("撤销：发起人本人+审批中 → 委托平台撤销实例")
    void cancel_delegatesToInstanceService() {
        BizProject project = new BizProject();
        project.setId(5L);
        project.setCreateBy(1L);
        project.setApproveStatus(BizProject.STATUS_APPROVING);
        project.setProcessInstanceId(99L);
        when(projectMapper.selectById(5L)).thenReturn(project);

        try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
            security.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.cancelProject(5L);
        }
        verify(instanceService).cancelProcess("99");
    }

    @Test
    @DisplayName("撤销：非发起人 → 403 拒绝")
    void cancel_rejectsNonOwner() {
        BizProject project = new BizProject();
        project.setId(5L);
        project.setCreateBy(1L);
        project.setApproveStatus(BizProject.STATUS_APPROVING);
        project.setProcessInstanceId(99L);
        when(projectMapper.selectById(5L)).thenReturn(project);

        try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
            security.when(SecurityHelper::getCurrentUserId).thenReturn(2L);
            BusinessException e = assertThrows(BusinessException.class, () -> service.cancelProject(5L));
            assertEquals(403, e.getCode());
        }
        verify(instanceService, never()).cancelProcess(anyString());
    }

    @Test
    @DisplayName("撤销：非审批中 → 400 拒绝")
    void cancel_rejectsTerminalStatus() {
        BizProject project = new BizProject();
        project.setId(5L);
        project.setCreateBy(1L);
        project.setApproveStatus(BizProject.STATUS_APPROVED);
        when(projectMapper.selectById(5L)).thenReturn(project);

        BusinessException e = assertThrows(BusinessException.class, () -> service.cancelProject(5L));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("详情：不存在 → 404")
    void getProject_notFound() {
        when(projectMapper.selectById(404L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.getProject(404L));
        assertEquals(404, e.getCode());
    }
}
