package com.forge.modules.workflow.im;

/**
 * 单用户单渠道发送结果
 *
 * @param success     是否成功（errcode=0）
 * @param error       失败原因（渠道错误码+描述 或 异常摘要）
 * @param invalidUser 平台提示无效的用户ID（可空；消息可能未送达）
 * @author forge-admin
 */
public record ImSendResult(boolean success, String error, String invalidUser) {

    public static ImSendResult ok() {
        return new ImSendResult(true, null, null);
    }

    public static ImSendResult fail(String error) {
        return new ImSendResult(false, error, null);
    }
}
