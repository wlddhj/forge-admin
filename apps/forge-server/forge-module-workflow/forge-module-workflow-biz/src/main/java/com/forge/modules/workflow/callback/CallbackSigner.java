package com.forge.modules.workflow.callback;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 回调签名器：HMAC-SHA256(secret, timestamp + "." + body)
 * <p>
 * 接收方复算方式：Base64(HmacSHA256(secret, timestamp + "." + rawBody))
 *
 * @author forge-admin
 */
public final class CallbackSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private CallbackSigner() {
    }

    /**
     * 计算签名（Base64）；secret 为空返回 null（不签名）
     */
    public static String sign(String secret, String timestamp, String body) {
        if (secret == null || secret.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    /**
     * 验签（常量时间比较防时序侧信道）
     */
    public static boolean verify(String secret, String timestamp, String body, String signature) {
        String expected = sign(secret, timestamp, body);
        if (expected == null || signature == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}
