package com.forge.modules.ai.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 编排配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "forge.ai.agent")
public class AgentProperties {

    /**
     * HTTP 工具允许访问的域名白名单（空=禁用 HTTP 工具）
     */
    private List<String> allowedHosts = List.of();

    /**
     * SQL 查询工具是否启用
     */
    private boolean sqlToolEnabled = true;

    /**
     * SQL 工具返回行数上限
     */
    private int sqlMaxRows = 200;

    /**
     * HTTP/工具输出截断长度
     */
    private int toolOutputLimit = 4000;

    /**
     * SQL 工具结果字符预算（超预算裁剪行集并标注 truncated/omittedRows）
     */
    private int sqlResultCharBudget = 6000;

    /**
     * 团队图专家并行度（expert_pool 并发子图上限）
     */
    private int expertParallelism = 2;

    /**
     * 团队图检查点落库节流间隔（每 N 个节点写一次，中断/终态强制落）
     */
    private int checkpointIntervalTeam = 2;

    /**
     * 步骤事件批量落库阈值
     */
    private int stepBatchSize = 8;

    /**
     * Agent 专用线程池（工具并行/专家子图/failover 竞速/SSE 图执行）
     */
    private Executor executor = new Executor();

    /**
     * 决策提示词预算
     */
    private Prompt prompt = new Prompt();

    @Data
    public static class Executor {
        private int core = 8;
        private int max = 16;
        private int queue = 200;
    }

    @Data
    public static class Prompt {
        /** 工具历史段总预算（字符） */
        private int historyBudget = 6000;
        /** 最近 N 条工具记录保留完整结果 */
        private int historyRecentCount = 2;
        /** 最近记录单条结果上限（字符） */
        private int historyRecentLimit = 2000;
        /** 更早记录单条结果上限（字符） */
        private int historyOldLimit = 200;
        /** 专家汇报段总预算（字符） */
        private int reportsBudget = 6000;
        /** 单条专家汇报上限（字符） */
        private int reportsPerLimit = 1500;
        /** 注入提示词的表清单最大表数（按任务相关性优先） */
        private int maxTables = 15;
        /** 多轮记忆段预算（字符） */
        private int memoryBudget = 3000;
        /** 多轮记忆回溯 run 数 */
        private int memoryRuns = 2;
    }
}
