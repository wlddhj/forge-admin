package com.forge.modules.workflow.framework;

/**
 * 工作流公共支撑工具（上帝类拆分第四批抽取的跨类公共件）
 *
 * @author forge-admin
 */
public final class WorkflowSupport {

    private WorkflowSupport() {
    }

    /**
     * LIKE 通配符转义（ESCape 字符 '|'，配合 SQL 侧 ESCAPE '|' 使用）。
     * null/空白返回 null（调用方按"无过滤条件"跳过拼接）。
     * 原两份逐字副本位于 WfProcessInstanceServiceImpl/WfTaskServiceImpl（拆分收敛）。
     */
    public static String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("|", "||")
                .replace("%", "|%")
                .replace("_", "|_");
    }
}
