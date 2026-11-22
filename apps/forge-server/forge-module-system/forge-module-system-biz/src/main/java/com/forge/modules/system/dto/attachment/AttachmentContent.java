package com.forge.modules.system.dto.attachment;

import java.io.InputStream;

/**
 * 附件流式内容载体：openStream 打开即验证成功后的返回值，
 * 流由调用方（响应写入链）消费并关闭，读取异常已在打开阶段抛出
 */
public record AttachmentContent(InputStream stream, String fileName, String contentType, Long fileSize) {
}
