package com.forge.modules.workflow.spi;

import com.forge.modules.workflow.dto.bizview.WfBizViewData;

/**
 * 业务单据视图 SPI（formType=20 业务表单的视图提供方）。
 * <p>
 * 业务模块实现本接口并注册为 Spring Bean 即完成接入：workflow 侧按
 * businessKey 前缀（{bizType}:{bizId} 约定）路由到 {@link #bizType()} 匹配的
 * Provider，在「实例参与人」（发起人/办理人/抄送人/工作流管理员）鉴权下
 * 经代理端点透出单据只读视图，审批人无需业务模块查看权限。
 * <p>
 * 实现约束：实现类须做业务归属校验（租户由请求上下文经 MyBatis-Plus 租户插件
 * 自然过滤）；单据不存在或不可展示时返回 {@code null}，由 workflow 侧降级为
 * 实例变量快照兜底。
 *
 * @author forge-admin
 */
public interface WfBizViewProvider {

    /**
     * 业务类型标识（businessKey 前缀，如 "contract"、"project"）
     */
    String bizType();

    /**
     * 渲染单据只读视图数据
     *
     * @param bizId businessKey 冒号后的业务ID（原始字符串，由实现方解析）
     * @return 视图数据；单据不存在或不可展示返回 null
     */
    WfBizViewData getBizView(String bizId);

    /**
     * 单据预览字节流（如合同 PDF 实时渲染）；无预览能力返回 null
     */
    default byte[] getBizPreview(String bizId) {
        return null;
    }

    /**
     * 预览内容的 Content-Type（默认 application/pdf）
     */
    default String previewContentType() {
        return "application/pdf";
    }
}
