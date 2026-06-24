package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;

/**
 * 任务候选人自动分配处理器
 *
 * 注意：不再实现 TaskListener 接口，由 CompositeTaskListener 调用
 * 使用 @Service 注解，可以被依赖注入
 *
 * @author forge-admin
 */
@Slf4j
@Service
public class BpmTaskCandidateListener {

    private final BpmTaskCandidateInvoker candidateInvoker;
    private final FlowLongIdentityService identityService;

    public BpmTaskCandidateListener(BpmTaskCandidateInvoker candidateInvoker,
                                    FlowLongIdentityService identityService) {
        this.candidateInvoker = candidateInvoker;
        this.identityService = identityService;
    }

    /**
     * 处理任务事件
     * 由 CompositeTaskListener 调用
     */
    public boolean notify(TaskEventType eventType, Supplier<FlwTask> supplier,
                          List<FlwTaskActor> taskActors, NodeModel nodeModel, FlowCreator flowCreator) {
        // 只处理任务创建事件
        if (eventType != TaskEventType.create) {
            return false;
        }

        FlwTask task = supplier.get();
        if (task == null) {
            return false;
        }

        return assignCandidates(task, taskActors, nodeModel, flowCreator);
    }

    /**
     * 处理任务候选人分配
     */
    public boolean assignCandidates(FlwTask task, List<FlwTaskActor> taskActors,
                                    NodeModel nodeModel, FlowCreator flowCreator) {
        Integer strategyCode = null;
        String param = null;

        // 1. 从 NodeModel 扩展属性获取候选人策略
        if (nodeModel != null) {
            Map<String, Object> extendConfig = nodeModel.getExtendConfig();
            if (extendConfig != null) {
                Object strategyObj = extendConfig.get("candidateStrategy");
                if (strategyObj != null) {
                    strategyCode = ((Number) strategyObj).intValue();
                }
                Object paramObj = extendConfig.get("candidateParam");
                if (paramObj != null) {
                    param = paramObj.toString();
                }
            }
        }

        // 2. 从任务变量获取候选人策略（备用）
        if (strategyCode == null || param == null) {
            String variableJson = task.getVariable();
            if (variableJson != null && !variableJson.isEmpty()) {
                try {
                    // 简单解析 JSON 获取候选人策略
                    Map<String, Object> variables = parseVariables(variableJson);
                    if (strategyCode == null) {
                        Object strategyObj = variables.get("candidateStrategy");
                        if (strategyObj != null) {
                            strategyCode = ((Number) strategyObj).intValue();
                        }
                    }
                    if (param == null) {
                        Object paramObj = variables.get("candidateParam");
                        if (paramObj != null) {
                            param = paramObj.toString();
                        }
                    }
                    // 尝试从任务定义 Key 对应的变量获取
                    String taskKey = task.getTaskKey();
                    if (strategyCode == null) {
                        Object taskStrategyObj = variables.get(taskKey + "_candidateStrategy");
                        if (taskStrategyObj != null) {
                            strategyCode = ((Number) taskStrategyObj).intValue();
                        }
                    }
                    if (param == null) {
                        Object taskParamObj = variables.get(taskKey + "_candidateParam");
                        if (taskParamObj != null) {
                            param = taskParamObj.toString();
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析任务变量失败: {}", e.getMessage());
                }
            }
        }

        if (strategyCode == null) {
            log.debug("任务 {} 未配置候选人策略", task.getId());
            return false;
        }

        // 构建任务上下文
        BpmTaskCandidateStrategy.TaskContext taskContext = createTaskContext(task, flowCreator);

        Set<Long> userIds = candidateInvoker.calculateUsers(strategyCode, param, taskContext);
        if (userIds.isEmpty()) {
            log.warn("任务 {} 候选人计算结果为空, strategy={}, param={}",
                    task.getId(), strategyCode, param);
            return false;
        }

        // 添加候选人到 taskActors 列表
        List<FlwTaskActor> actors = candidateInvoker.convertToTaskActors(userIds, identityService);
        taskActors.addAll(actors);

        if (userIds.size() == 1) {
            log.info("任务 {} 单候选人: strategy={}, candidate={}",
                    task.getId(), strategyCode, userIds.iterator().next());
        } else {
            log.info("任务 {} 多候选人: strategy={}, candidates={}",
                    task.getId(), strategyCode, userIds);
        }

        return true;
    }

    /**
     * 创建任务上下文
     */
    private BpmTaskCandidateStrategy.TaskContext createTaskContext(FlwTask task, FlowCreator flowCreator) {
        return new BpmTaskCandidateStrategy.TaskContext() {
            @Override
            public Long getTaskId() {
                return task.getId();
            }

            @Override
            public String getTaskKey() {
                return task.getTaskKey();
            }

            @Override
            public String getTaskName() {
                return task.getTaskName();
            }

            @Override
            public Long getInstanceId() {
                return task.getInstanceId();
            }

            @Override
            public Long getProcessId() {
                // TODO: 从流程实例获取流程定义ID
                return null;
            }

            @Override
            public Long getStartUserId() {
                // 从 FlowCreator 获取发起人ID
                if (flowCreator != null && flowCreator.getCreateId() != null) {
                    try {
                        return Long.parseLong(flowCreator.getCreateId());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
            }

            @Override
            public Map<String, Object> getVariables() {
                String variableJson = task.getVariable();
                if (variableJson != null && !variableJson.isEmpty()) {
                    return parseVariables(variableJson);
                }
                return Collections.emptyMap();
            }

            @Override
            public String getBusinessKey() {
                // TODO: 从流程实例获取业务Key
                return null;
            }
        };
    }

    /**
     * 简单解析 JSON 变量
     */
    private Map<String, Object> parseVariables(String variableJson) {
        try {
            // 使用简单的 JSON 解析（避免依赖 ObjectMapper）
            // 实际项目中应该使用 ObjectMapper
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(variableJson, Map.class);
        } catch (Exception e) {
            log.warn("解析变量JSON失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}