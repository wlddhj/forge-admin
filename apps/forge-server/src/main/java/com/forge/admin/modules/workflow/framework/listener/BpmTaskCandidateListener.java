package com.forge.admin.modules.workflow.framework.listener;

import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 任务候选人自动分配监听器
 * 在任务创建时根据候选人策略自动设置候选用户
 * 既可作为 TaskListener 在 BPMN 中配置，也可作为全局事件监听器
 *
 * @author forge-admin
 */
@Slf4j
@Component("bpmTaskCandidateListener")
public class BpmTaskCandidateListener implements TaskListener {

    private final BpmTaskCandidateInvoker candidateInvoker;
    private RepositoryService repositoryService;

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    public BpmTaskCandidateListener(BpmTaskCandidateInvoker candidateInvoker) {
        this.candidateInvoker = candidateInvoker;
    }

    @Autowired
    @Lazy
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        // 只处理任务创建事件
        if (!EVENTNAME_CREATE.equals(delegateTask.getEventName())) {
            return;
        }

        assignCandidates(delegateTask);
    }

    /**
     * 处理任务候选人分配（可被全局事件监听器调用）
     */
    public void assignCandidates(DelegateTask delegateTask) {
        Integer strategyCode = null;
        String param = null;

        // 1. 从 BPMN 模型的扩展属性获取（flowable:candidateStrategy/candidateParam）
        String processDefinitionId = delegateTask.getProcessDefinitionId();
        String taskDefKey = delegateTask.getTaskDefinitionKey();
        try {
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            FlowElement flowElement = bpmnModel.getFlowElement(taskDefKey);
            if (flowElement instanceof UserTask) {
                UserTask userTask = (UserTask) flowElement;
                String strategyAttr = userTask.getAttributeValue(FLOWABLE_NS, "candidateStrategy");
                if (strategyAttr != null && !strategyAttr.isEmpty()) {
                    strategyCode = Integer.parseInt(strategyAttr);
                }
                param = userTask.getAttributeValue(FLOWABLE_NS, "candidateParam");
            }
        } catch (Exception e) {
            log.warn("从BPMN模型读取候选人策略失败: {}", e.getMessage());
        }

        // 2. 从流程变量中获取候选人策略和参数（备用）
        if (strategyCode == null) {
            strategyCode = (Integer) delegateTask.getVariable("candidateStrategy");
        }
        if (param == null) {
            param = (String) delegateTask.getVariable("candidateParam");
        }

        // 3. 尝试从任务本地变量获取（针对特定任务的策略）
        if (strategyCode == null) {
            Object localStrategy = delegateTask.getVariableLocal("candidateStrategy");
            if (localStrategy != null) {
                strategyCode = (Integer) localStrategy;
            }
        }
        if (param == null) {
            Object localParam = delegateTask.getVariableLocal("candidateParam");
            if (localParam != null) {
                param = (String) localParam;
            }
        }

        // 4. 尝试从任务名称属性获取（BPMN XML 中配置的扩展属性）
        if (strategyCode == null) {
            Object processStrategy = delegateTask.getVariable(taskDefKey + "_candidateStrategy");
            if (processStrategy != null) {
                strategyCode = (Integer) processStrategy;
            }
        }
        if (param == null) {
            Object processParam = delegateTask.getVariable(taskDefKey + "_candidateParam");
            if (processParam != null) {
                param = (String) processParam;
            }
        }

        if (strategyCode == null) {
            log.debug("任务 {} 未配置候选人策略", delegateTask.getId());
            return;
        }

        Set<Long> userIds = candidateInvoker.calculateUsers(strategyCode, param, delegateTask);
        if (userIds.isEmpty()) {
            log.warn("任务 {} 候选人计算结果为空, strategy={}, param={}",
                    delegateTask.getId(), strategyCode, param);
            return;
        }

        // 设置候选用户
        for (Long userId : userIds) {
            delegateTask.addCandidateUser(String.valueOf(userId));
        }

        log.info("任务 {} 自动分配候选人: strategy={}, param={}, users={}",
                delegateTask.getId(), strategyCode, param, userIds);
    }
}