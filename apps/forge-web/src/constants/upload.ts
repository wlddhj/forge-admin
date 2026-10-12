/**
 * 上传限制统一常量（与后端 forge.security.upload 配置对齐）
 */

/** 附件大小上限（MB），对应后端 forge.security.upload.max-size */
export const FILE_MAX_SIZE_MB = 10

/** 图片大小上限（MB），头像/品牌 Logo 等场景 */
export const IMAGE_MAX_SIZE_MB = 2

/** 图片类型 accept */
export const IMAGE_ACCEPT = 'image/*'

/** AI 文档支持的扩展名 */
export const AI_DOCUMENT_EXTENSIONS = ['pdf', 'docx', 'doc', 'txt']

/** AI 文档大小上限（MB） */
export const AI_DOCUMENT_MAX_SIZE_MB = 10
