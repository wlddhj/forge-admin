package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI路由决策记录实体（网关 AI 路由每次决策落一条审计）
 *
 * @author forge-admin
 */
@Data
@TableName("wf_ai_route_record")
public class WfAiRouteRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 流程实例ID
     */
    private Long processInstanceId;

    /**
     * 网关节点Key
     */
    private String gatewayNodeKey;

    /**
     * 网关节点名称
     */
    private String gatewayNodeName;

    /**
     * 网关类型（4条件/9包容/23路由）
     */
    private Integer gatewayType;

    /**
     * 候选分支JSON [{nodeKey,nodeName}]
     */
    private String candidateBranches;

    /**
     * 选中分支nodeKey，逗号分隔
     */
    private String selectedBranches;

    /**
     * 置信度（0-100）
     */
    private Integer confidence;

    /**
     * AI决策理由
     */
    private String reasoning;

    /**
     * 原始AI响应
     */
    private String rawResponse;

    /**
     * 执行状态（SUCCESS/FALLBACK/LOW_CONFIDENCE/TIMEOUT/ERROR）
     */
    private String status;

    /**
     * AI模型提供商
     */
    private String provider;

    /**
     * AI模型名称
     */
    private String modelName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
