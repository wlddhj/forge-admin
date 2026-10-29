package com.forge.modules.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.demo.config.ProjectWorkflowProperties;
import com.forge.modules.demo.dto.ProjectResponse;
import com.forge.modules.demo.dto.ProjectSubmitRequest;
import com.forge.modules.demo.entity.BizProject;
import com.forge.modules.demo.mapper.BizProjectMapper;
import com.forge.modules.demo.service.ProjectApprovalService;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.modules.workflow.service.WfProcessDefinitionService;
import com.forge.modules.workflow.service.WfProcessInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 项目立项服务实现 —— 业务模块接入工作流（形态A）参考实现。
 * 对接步骤说明：docs-base/modules/workflow/06.business-integration-guide.md
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectApprovalServiceImpl implements ProjectApprovalService {

    /**
     * businessKey 前缀：事件回写侧按此前缀识别本业务的实例
     */
    public static final String BUSINESS_KEY_PREFIX = "project:";

    private final BizProjectMapper projectMapper;
    private final WfProcessDefinitionService processDefinitionService;
    private final WfProcessInstanceService processInstanceService;
    private final ProjectWorkflowProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitProject(ProjectSubmitRequest request) {
        // ① 业务落库（createBy/createTime 由 MetaObjectHandler 自动填充，发起人=当前用户）
        BizProject project = new BizProject();
        project.setProjectName(request.getProjectName());
        project.setTotalAmount(request.getTotalAmount());
        project.setBizUser(request.getBizUser());
        project.setDeptName(request.getDeptName());
        project.setRemark(request.getRemark());
        project.setApproveStatus(BizProject.STATUS_APPROVING);
        projectMapper.insert(project);

        // ② 按 processKey 解析最新部署版本（重新部署自动跟随，无硬编码 definitionId）
        String definitionId = processDefinitionService.getLatestDefinitionIdByKey(properties.getProjectProcessKey());

        // ③ 发起流程：businessKey 是业务↔流程的唯一关联键，variables 覆盖分支条件引用的字段
        String businessKey = BUSINESS_KEY_PREFIX + project.getId();
        ProcessStartRequest startRequest = new ProcessStartRequest();
        startRequest.setProcessDefinitionId(definitionId);
        startRequest.setBusinessKey(businessKey);
        startRequest.setComment("提交立项");
        Map<String, Object> variables = new HashMap<>();
        variables.put("projectName", request.getProjectName());
        if (request.getTotalAmount() != null) {
            variables.put("totalAmount", request.getTotalAmount());
        }
        if (request.getBizUser() != null) {
            variables.put("bizUser", request.getBizUser());
        }
        if (request.getDeptName() != null) {
            variables.put("deptName", request.getDeptName());
        }
        if (request.getRemark() != null) {
            variables.put("remark", request.getRemark());
        }
        startRequest.setVariables(variables);
        processInstanceService.startProcess(startRequest);

        // ④ startProcess 返回 void：按 businessKey 查回在途实例，回写 process_instance_id 与流程编号
        ProcessInstanceResponse activeInstance = processInstanceService.getInstancesByBusinessKey(businessKey).stream()
                .filter(i -> i.getEndTime() == null)
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "流程已发起但未查到在途实例，请检查实例状态"));
        BizProject update = new BizProject();
        update.setId(project.getId());
        update.setProcessInstanceId(Long.parseLong(activeInstance.getId()));
        update.setProcessNo(activeInstance.getProcessNo());
        projectMapper.updateById(update);

        log.info("项目立项已提交: projectId={}, businessKey={}, instanceId={}, processNo={}",
                project.getId(), businessKey, activeInstance.getId(), activeInstance.getProcessNo());
        return project.getId();
    }

    @Override
    public void cancelProject(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "立项记录不存在");
        }
        if (!BizProject.STATUS_APPROVING.equals(project.getApproveStatus())) {
            throw new BusinessException(400, "仅审批中的立项可以撤销");
        }
        Long currentUserId = SecurityHelper.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(project.getCreateBy())) {
            throw new BusinessException(403, "只有发起人可以撤销");
        }
        if (project.getProcessInstanceId() == null) {
            throw new BusinessException(400, "该记录未关联流程实例，无法撤销");
        }
        // 委托平台撤销；审批状态由 revokeComplete 事件回写为 CANCELED，本方法不改状态（事件驱动）
        processInstanceService.cancelProcess(String.valueOf(project.getProcessInstanceId()));
        log.info("项目立项撤销已委托: projectId={}, instanceId={}", id, project.getProcessInstanceId());
    }

    @Override
    public Page<ProjectResponse> pageProjects(int pageNum, int pageSize, String projectName, String approveStatus) {
        Page<BizProject> page = projectMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BizProject>()
                        .like(projectName != null && !projectName.isBlank(), BizProject::getProjectName, projectName)
                        .eq(approveStatus != null && !approveStatus.isBlank(), BizProject::getApproveStatus, approveStatus)
                        .orderByDesc(BizProject::getId));
        Page<ProjectResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(ProjectResponse::from).toList());
        return result;
    }

    @Override
    public ProjectResponse getProject(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "立项记录不存在");
        }
        return ProjectResponse.from(project);
    }
}
