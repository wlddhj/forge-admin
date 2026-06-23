package com.forge.modules.workflow.framework.candidate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 候选人策略调度器 - FlowLong 版本
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class BpmTaskCandidateInvoker {

    private final Map<Integer, BpmTaskCandidateStrategy> strategyMap;

    public BpmTaskCandidateInvoker(List<BpmTaskCandidateStrategy> strategies) {
        this.strategyMap = new HashMap<>();
        for (BpmTaskCandidateStrategy strategy : strategies) {
            strategyMap.put(strategy.getStrategy(), strategy);
            log.info("注册候选人策略: {} - {}", strategy.getStrategy(), strategy.getDescription());
        }
    }

    /**
     * 根据策略代码和参数计算候选人
     *
     * @param strategyCode 策略代码
     * @param param        策略参数
     * @return 候选人用户ID集合
     */
    public Set<Long> calculateUsers(Integer strategyCode, String param) {
        if (strategyCode == null) {
            return Collections.emptySet();
        }
        BpmTaskCandidateStrategy strategy = strategyMap.get(strategyCode);
        if (strategy == null) {
            log.warn("未找到候选人策略: {}", strategyCode);
            return Collections.emptySet();
        }
        return strategy.calculateUsers(param);
    }

    /**
     * 根据策略代码、参数和任务上下文计算候选人
     *
     * @param strategyCode 策略代码
     * @param param        策略参数
     * @param taskContext  任务上下文
     * @return 候选人用户ID集合
     */
    public Set<Long> calculateUsers(Integer strategyCode, String param, BpmTaskCandidateStrategy.TaskContext taskContext) {
        if (strategyCode == null) {
            return Collections.emptySet();
        }
        BpmTaskCandidateStrategy strategy = strategyMap.get(strategyCode);
        if (strategy == null) {
            log.warn("未找到候选人策略: {}", strategyCode);
            return Collections.emptySet();
        }
        return strategy.calculateUsers(param, taskContext);
    }

    /**
     * 获取所有已注册的策略
     *
     * @return 策略映射
     */
    public Map<Integer, BpmTaskCandidateStrategy> getStrategyMap() {
        return strategyMap;
    }

    /**
     * 获取所有策略列表（用于前端下拉选项）
     *
     * @return 策略列表
     */
    public List<BpmTaskCandidateStrategy> getStrategyList() {
        return new ArrayList<>(strategyMap.values());
    }

    /**
     * 将候选人用户ID集合转换为 FlowLong TaskActor 列表
     *
     * @param userIds      用户ID集合
     * @param identityService 身份服务
     * @return TaskActor 列表
     */
    public List<com.aizuda.bpm.engine.entity.FlwTaskActor> convertToTaskActors(Set<Long> userIds,
                                                                                com.forge.modules.workflow.identity.FlowLongIdentityService identityService) {
        return userIds.stream()
                .map(userId -> {
                    com.aizuda.bpm.engine.entity.FlwTaskActor actor = new com.aizuda.bpm.engine.entity.FlwTaskActor();
                    actor.setActorId(String.valueOf(userId));
                    actor.setActorName(identityService.getUserName(userId));
                    actor.setActorType(0); // 用户类型
                    return actor;
                })
                .collect(Collectors.toList());
    }
}