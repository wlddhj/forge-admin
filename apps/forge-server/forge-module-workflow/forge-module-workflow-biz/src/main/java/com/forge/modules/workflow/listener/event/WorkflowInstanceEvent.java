package com.forge.modules.workflow.listener.event;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;

/**
 * 流程实例事件 - Spring 事件机制
 * 用于解耦流程实例生命周期事件的处理
 *
 * @author forge-admin
 */
@Getter
public class WorkflowInstanceEvent extends ApplicationEvent implements Serializable {

    /**
     * 事件类型
     */
    private final InstanceEventType eventType;

    /**
     * 流程实例（历史实例，包含完整信息）
     */
    private final FlwHisInstance instance;

    /**
     * 当前执行节点
     */
    private final NodeModel nodeModel;

    /**
     * 流程创建者/操作者
     */
    private final FlowCreator flowCreator;

    /**
     * 发布时刻的租户快照：AFTER_COMMIT 异步消费线程不再依赖（可能失真的）上下文，
     * 消费端以此显式传参/重建租户；关闸时为 null
     */
    private final Long tenantId;

    public WorkflowInstanceEvent(Object source, InstanceEventType eventType,
                                  FlwHisInstance instance, NodeModel nodeModel, FlowCreator flowCreator) {
        // 旧签名内聚烘焙：所有既有发布点自动携带发布时刻租户
        this(source, eventType, instance, nodeModel, flowCreator, TenantContextHolder.getTenantId());
    }

    public WorkflowInstanceEvent(Object source, InstanceEventType eventType,
                                  FlwHisInstance instance, NodeModel nodeModel, FlowCreator flowCreator,
                                  Long tenantId) {
        super(source);
        this.eventType = eventType;
        this.instance = instance;
        this.nodeModel = nodeModel;
        this.flowCreator = flowCreator;
        this.tenantId = tenantId;
    }

    /**
     * 获取流程实例ID
     */
    public Long getInstanceId() {
        return instance != null ? instance.getId() : null;
    }

    /**
     * 获取流程定义ID
     */
    public Long getProcessId() {
        return instance != null ? instance.getProcessId() : null;
    }

    /**
     * 获取业务Key
     */
    public String getBusinessKey() {
        return instance != null ? instance.getBusinessKey() : null;
    }

    /**
     * 获取操作者ID
     */
    public String getOperatorId() {
        return flowCreator != null ? flowCreator.getCreateId() : null;
    }

    /**
     * 获取操作者名称
     */
    public String getOperatorName() {
        return flowCreator != null ? flowCreator.getCreateBy() : null;
    }
}