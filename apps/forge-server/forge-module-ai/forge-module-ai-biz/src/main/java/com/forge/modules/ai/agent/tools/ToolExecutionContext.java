package com.forge.modules.ai.agent.tools;

/**
 * 工具执行上下文：发起者身份(会话用户/定时任务),供工具做数据可见性决策。
 * userId 为空或 <=0 视为无用户上下文(定时任务/系统触发),工具按全量语义执行。
 */
public record ToolExecutionContext(Long userId) {

    /** 定时任务/系统触发的无用户上下文 */
    public static final ToolExecutionContext UNRESTRICTED = new ToolExecutionContext(null);

    public static ToolExecutionContext of(Long userId) {
        return userId == null || userId <= 0 ? UNRESTRICTED : new ToolExecutionContext(userId);
    }

    /** 是否限定为指定用户的数据可见范围 */
    public boolean limitedToUser(Long targetUserId) {
        return userId != null && userId > 0 && !userId.equals(targetUserId);
    }

    public boolean hasUser() {
        return userId != null && userId > 0;
    }
}
