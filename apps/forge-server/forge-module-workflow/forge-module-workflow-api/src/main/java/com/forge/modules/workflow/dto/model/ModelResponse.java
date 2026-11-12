package com.forge.modules.workflow.dto.model;

import com.forge.modules.workflow.entity.WfCategory;
import com.forge.modules.workflow.entity.WfForm;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.TransPojo;

import java.time.LocalDateTime;

/**
 * 模型响应
 *
 * @author forge-admin
 */
@Data
public class ModelResponse implements TransPojo {

    /**
     * 模型ID
     */
    private String id;

    /**
     * 模型名称
     */
    private String name;

    /**
     * 模型标识（流程定义Key）
     */
    private String key;

    /**
     * 分类ID
     */
    @Trans(type = TransType.SIMPLE, target = WfCategory.class, fields = {"categoryCode", "categoryName"}, refs = {"category", "categoryName"})
    private Long categoryId;

    /**
     * 分类编码
     */
    private String category;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 描述
     */
    private String description;

    /**
     * 版本号
     */
    private String version;

    /**
     * 扩展信息（JSON）
     */
    private String metaInfo;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;

    /**
     * 是否已部署
     */
    private boolean deployed;

    /**
     * 表单类型(10流程表单 20业务表单)
     */
    private Integer formType;

    /**
     * 关联表单ID
     */
    @Trans(type = TransType.SIMPLE, target = WfForm.class, fields = "name", ref = "formName")
    private Long formId;
    private String formName;

    /**
     * 自动抄送策略
     */
    private Integer autoCopyStrategy;

    /**
     * 自动抄送参数
     */
    private String autoCopyParam;

    /** 发起范围 0全员 1指定人员 2均不可发起 */
    private Integer useScope;

    /** 发起范围人员名单JSON（用户ID数组） */
    private String useScopeUsers;

    /**
     * 标题规则（${} 模板，空=不生成标题）
     */
    private String titleRule;

    /**
     * FlowLong 流程模型 JSON 内容（仅详情接口返回）
     */
    private String modelJson;
}