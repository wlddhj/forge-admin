package com.forge.modules.workflow.callback;

/**
 * 业务回调契约事件类型（稳定对外契约，与引擎内部枚举解耦）
 *
 * @author forge-admin
 */
public enum CallbackEventType {

    /**
     * 到达节点产生待办（引擎 TaskEventType.create/recreate）
     */
    taskCreated("任务创建"),

    /**
     * 任务办结（引擎 TaskEventType.complete 等）
     */
    taskCompleted("任务办结"),

    /**
     * 实例终态（通过/拒绝/撤销/终止/超时）
     */
    instanceFinished("实例终态");

    private final String label;

    CallbackEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
