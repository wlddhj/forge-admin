package com.forge.modules.workflow.framework.interceptor;

import com.aizuda.bpm.engine.TaskCreateInterceptor;
import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.core.FlowLongContext;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 任务创建拦截器 - 权限检查
 * 在任务创建前后执行自定义逻辑
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class PermissionTaskCreateInterceptor implements TaskCreateInterceptor {

    @Override
    public void before(FlowLongContext flowLongContext, Execution execution) {
        // 通过执行对象获取节点模型
        FlwTask currentTask = execution.getFlwTask();
        ProcessModel processModel = execution.getProcessModel();

        if (currentTask == null || processModel == null) {
            return;
        }

        NodeModel nodeModel = processModel.getNode(currentTask.getTaskKey());
        if (nodeModel == null) {
            return;
        }

        log.debug("任务创建前检查: nodeKey={}, nodeName={}",
                nodeModel.getNodeKey(), nodeModel.getNodeName());

        // 检查节点是否需要特殊权限
        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        if (extendConfig != null) {
            checkNodePermissions(extendConfig, execution);
        }
    }

    @Override
    public void after(FlowLongContext flowLongContext, Execution execution) {
        // 获取刚创建的任务
        List<FlwTask> tasks = execution.getFlwTasks();
        if (tasks != null && !tasks.isEmpty()) {
            for (FlwTask task : tasks) {
                log.info("任务创建完成: taskId={}, taskName={}, taskKey={}",
                        task.getId(), task.getTaskName(), task.getTaskKey());
            }
        }
    }

    /**
     * 检查节点权限配置
     */
    private void checkNodePermissions(Map<String, Object> extendConfig, Execution execution) {
        // 检查是否需要权限验证
        Object requirePermission = extendConfig.get("requirePermission");
        if (requirePermission != null && Boolean.TRUE.equals(requirePermission)) {
            String permission = (String) extendConfig.get("permission");
            String userId = execution.getFlowCreator() != null
                    ? execution.getFlowCreator().getCreateId() : null;

            log.debug("节点需要权限验证: permission={}, userId={}", permission, userId);

            // TODO: 实现实际的权限验证逻辑
            // 可以集成 Spring Security 的权限检查
            // if (!hasPermission(userId, permission)) {
            //     throw new FlowLongException("No permission to execute this node");
            // }
        }

        // 检查是否限制发起人类型
        Object restrictInitiatorType = extendConfig.get("restrictInitiatorType");
        if (restrictInitiatorType != null) {
            // 例如：只能由特定部门的用户发起
            log.debug("节点限制发起人类型: {}", restrictInitiatorType);
        }

        // 检查是否需要前置条件
        Object preCondition = extendConfig.get("preCondition");
        if (preCondition != null) {
            // 例如：前置任务必须完成
            log.debug("节点前置条件: {}", preCondition);
        }
    }
}