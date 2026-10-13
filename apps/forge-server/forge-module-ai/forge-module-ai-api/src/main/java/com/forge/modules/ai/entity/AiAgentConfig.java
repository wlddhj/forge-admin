package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.forge.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Agent 配置实体（编排图结构代码定义，配置存库）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_config")
public class AiAgentConfig extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentCode;

    private String agentName;

    private String graphKey;

    private String description;

    private String systemPrompt;

    private String userPromptTemplate;

    /** JSON：{"节点名":{"modelId":1}|{"modelType":"text"}} */
    private String modelBindings;

    private Integer maxSteps;

    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;
}
