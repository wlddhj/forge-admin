package com.forge.admin.modules.workflow.framework.candidate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 候选人策略调度器
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
}