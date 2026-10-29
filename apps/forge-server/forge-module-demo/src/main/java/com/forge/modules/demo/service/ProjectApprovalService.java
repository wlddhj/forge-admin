package com.forge.modules.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.demo.dto.ProjectResponse;
import com.forge.modules.demo.dto.ProjectSubmitRequest;

/**
 * 项目立项服务（工作流对接示例）
 *
 * @author forge-admin
 */
public interface ProjectApprovalService {

    /**
     * 提交立项：业务落库 + 发起审批流程（businessKey=project:{id}）
     *
     * @param request 提交请求
     * @return 业务ID
     */
    Long submitProject(ProjectSubmitRequest request);

    /**
     * 发起人撤销：委托平台撤销流程实例，状态由 revokeComplete 事件回写 CANCELED
     *
     * @param id 业务ID
     */
    void cancelProject(Long id);

    /**
     * 分页查询立项记录
     *
     * @param pageNum       页码
     * @param pageSize      页大小
     * @param projectName   项目名称模糊
     * @param approveStatus 审批状态
     * @return 分页结果
     */
    Page<ProjectResponse> pageProjects(int pageNum, int pageSize, String projectName, String approveStatus);

    /**
     * 查询详情
     *
     * @param id 业务ID
     * @return 详情
     */
    ProjectResponse getProject(Long id);
}
