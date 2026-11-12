package com.forge.modules.workflow.framework;

import com.forge.common.utils.UserContext;

/**
 * 工作流权限支撑（上帝类拆分第四批抽取）：
 * hasWorkflowAdminRole 原三份逐字副本（WfProcessInstanceServiceImpl/WfTaskServiceImpl/WfUrgeService）收敛于此。
 *
 * @author forge-admin
 */
public final class WorkflowPermissionSupport {

    private WorkflowPermissionSupport() {
    }

    /**
     * 当前用户是否工作流管理员（admin/workflow_admin 角色或平台超管）
     */
    public static boolean hasWorkflowAdminRole() {
        UserContext context = UserContext.get();
        if (context == null) {
            return false;
        }
        if (context.isAdmin()) {
            return true;
        }
        if (context.getRoles() == null) {
            return false;
        }
        return context.getRoles().stream()
                .map(role -> role.getRoleCode() == null ? "" : role.getRoleCode().toLowerCase())
                .anyMatch(code -> "admin".equals(code) || "workflow_admin".equals(code));
    }
}
