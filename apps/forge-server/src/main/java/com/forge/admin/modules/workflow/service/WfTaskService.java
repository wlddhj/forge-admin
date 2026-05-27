package com.forge.admin.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.workflow.dto.task.*;

import java.util.List;
import java.util.Map;

/**
 * 工作流任务管理服务接口
 *
 * @author forge-admin
 */
public interface WfTaskService {

    /**
     * 查询待办任务（当前用户待处理的任务）
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<TaskResponse> getTodoTasks(TaskQueryRequest request);

    /**
     * 查询可签收任务（候选任务）
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<TaskResponse> getClaimableTasks(TaskQueryRequest request);

    /**
     * 查询已办任务（当前用户已完成的任务）
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<TaskResponse> getDoneTasks(TaskQueryRequest request);

    /**
     * 根据任务ID获取任务详情
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    TaskResponse getTaskById(String taskId);

    /**
     * 签收任务
     *
     * @param taskId 任务ID
     */
    void claimTask(String taskId);

    /**
     * 取消签收任务
     *
     * @param taskId 任务ID
     */
    void unclaimTask(String taskId);

    /**
     * 完成任务（普通提交）
     *
     * @param taskId  任务ID
     * @param request 完成请求
     */
    void completeTask(String taskId, TaskCompleteRequest request);

    /**
     * 审批通过任务
     *
     * @param taskId  任务ID
     * @param request 完成请求
     */
    void approveTask(String taskId, TaskCompleteRequest request);

    /**
     * 审批驳回任务
     *
     * @param taskId  任务ID
     * @param request 完成请求
     */
    void rejectTask(String taskId, TaskCompleteRequest request);

    /**
     * 委派任务（将任务委派给其他人处理，处理后回到自己）
     *
     * @param taskId  任务ID
     * @param request 委派请求
     */
    void delegateTask(String taskId, TaskDelegateRequest request);

    /**
     * 转办任务（将任务转给其他人处理，不再回到自己）
     *
     * @param taskId  任务ID
     * @param request 转办请求
     */
    void transferTask(String taskId, TaskTransferRequest request);

    /**
     * 退回任务到指定节点
     *
     * @param taskId  任务ID
     * @param request 退回请求
     */
    void returnTask(String taskId, TaskReturnRequest request);

    /**
     * 获取可退回的节点列表
     *
     * @param taskId 任务ID
     * @return 可退回节点列表
     */
    List<Map<String, String>> getReturnNodes(String taskId);

    /**
     * 加签（在当前任务前后增加审批人）
     *
     * @param taskId  任务ID
     * @param request 加签请求
     */
    void signCreateTask(String taskId, TaskSignCreateRequest request);

    /**
     * 减签（移除加签的审批人）
     *
     * @param taskId  任务ID
     * @param request 减签请求
     */
    void signDeleteTask(String taskId, TaskSignDeleteRequest request);

    /**
     * 抄送任务
     *
     * @param taskId  任务ID
     * @param request 抄送请求
     */
    void copyTask(String taskId, TaskCopyRequest request);

    /**
     * 撤回任务（发起人撤回已提交的任务）
     *
     * @param taskId 任务ID
     */
    void withdrawTask(String taskId);

    /**
     * 获取子任务列表（用于减签场景）
     *
     * @param parentTaskId 父任务ID
     * @return 子任务列表
     */
    List<Map<String, String>> getChildTasks(String parentTaskId);
}
