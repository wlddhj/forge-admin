package com.forge.modules.workflow.dto.instance;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实例 AI 决策记录（分支路由 + 审批决策两组）
 *
 * @author forge-admin
 */
@Data
public class AiDecisionResponse {

    /**
     * AI 路由决策记录（wf_ai_route_record，时间倒序）
     */
    private List<Routing> routings;

    /**
     * AI 审批决策记录（wf_ai_approval_record，时间倒序）
     */
    private List<Approval> approvals;

    @Data
    public static class Routing {

        private String gatewayNodeKey;

        private String gatewayNodeName;

        /**
         * 网关类型原始值（4条件/9包容/23路由）
         */
        private Integer gatewayType;

        /**
         * 网关类型中文名
         */
        private String gatewayTypeName;

        /**
         * 选中分支 nodeKey 原始值（逗号分隔存储，拆为列表）
         */
        private List<String> selectedBranches;

        /**
         * 选中分支名（由 candidate_branches 解析；解析不到回退 nodeKey）
         */
        private List<String> selectedBranchNames;

        private Integer confidence;

        private String reasoning;

        private String rawResponse;

        /**
         * SUCCESS / FALLBACK / LOW_CONFIDENCE / TIMEOUT / ERROR
         */
        private String status;

        private LocalDateTime createTime;
    }

    @Data
    public static class Approval {

        private Long taskId;

        private String taskName;

        /**
         * APPROVE / REJECT / MANUAL
         */
        private String decision;

        private Integer confidence;

        private String reasoning;

        /**
         * SUCCESS / FAILURE / LOW_CONFIDENCE / TIMEOUT / ERROR
         */
        private String status;

        private String provider;

        private String modelName;

        private LocalDateTime createTime;
    }
}
