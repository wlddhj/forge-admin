package com.forge.modules.system.constant;

/**
 * sys_attachment.biz_type 业务类型常量（小写下划线风格）。
 * 全仓写入/查询该列一律引用本类，禁止散落字符串字面量；
 * 存量旧值（APP_AVATAR / brand-logo）由 upgrade SQL 归一，新值以本类为准。
 * avatar / brand_logo 为永久文件（无 bizId 语义），不参与孤儿回收；
 * contract / wf_comment / notice 为「上传后回绑」型，超期未绑定由回收任务清理；
 * contract_template 由模板保存时绑定 bizId；ai_document 经 ai_document.attachment_id 关联并级联删除。
 */
public final class AttachmentBizTypes {

    /** 个人头像（管理端 /system/attachment/avatar 与 App 端共用） */
    public static final String AVATAR = "avatar";

    /** 品牌 Logo */
    public static final String BRAND_LOGO = "brand_logo";

    /** AI 知识库文档（经 ai_document.attachment_id 关联，bizId 不落值） */
    public static final String AI_DOCUMENT = "ai_document";

    /** 合同定稿 PDF（bizId=合同ID） */
    public static final String CONTRACT = "contract";

    /** 合同模板编辑器图片（模板保存时绑定 bizId=模板ID） */
    public static final String CONTRACT_TEMPLATE = "contract_template";

    /** 审批评论附件（bizId=评论ID，评论创建后回绑） */
    public static final String WF_COMMENT = "wf_comment";

    /** 公告附件（bizId=公告ID，公告创建后回绑） */
    public static final String NOTICE = "notice";

    private AttachmentBizTypes() {
    }
}
