package com.forge.framework.plugin.license;

import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 离线 License 验签器（SHA256withRSA，公钥可经配置覆盖）。
 *
 * <p>文件格式（零歧义承载，验签对原始字节，不做 JSON 重序列化）：
 * <pre>{@code
 * { "payload": "<Base64(canonical JSON)>", "signature": "<Base64(RSA2048-SHA256 签名)>" }
 * }</pre>
 *
 * <p>威胁模型：防客户侧篡改载荷与越权使用；防不住持有私钥者（私钥离线保管）。
 *
 * @author forge-admin
 */
public class LicenseVerifier {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 公钥 Base64（可为 null：装配期允许缺失，使用期 fail-fast，见 {@link #publicKey()}） */
    private final String publicKeyBase64;

    /** 惰性解析的公钥（License 导入低频，无需并发优化） */
    private volatile PublicKey publicKey;

    public LicenseVerifier(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64 == null ? null : publicKeyBase64.trim().isEmpty() ? null : publicKeyBase64.trim();
    }

    /**
     * 惰性解析公钥：装配期允许缺失（避免炸掉依赖方 Spring 上下文），
     * 实际验签时才 fail-fast。
     */
    private PublicKey publicKey() {
        PublicKey key = publicKey;
        if (key != null) {
            return key;
        }
        synchronized (this) {
            if (publicKey != null) {
                return publicKey;
            }
            if (publicKeyBase64 == null) {
                throw new BusinessException(ResultCode.LICENSE_INVALID,
                        "插件 License 验签公钥未配置（forge.plugin.license.public-key 或 classpath:plugin-license-public.key）");
            }
            try {
                byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
                publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            } catch (Exception e) {
                throw new BusinessException(ResultCode.LICENSE_INVALID, "插件 License 公钥解析失败: " + e.getMessage());
            }
            return publicKey;
        }
    }

    /**
     * 验签并解析 License 文件内容。
     *
     * @throws BusinessException LICENSE_INVALID（格式错误或签名校验失败）
     */
    public LicensePayload verify(String licenseFileContent) {
        String payloadBase64;
        String signatureBase64;
        try {
            JsonNode root = JSON.readTree(licenseFileContent);
            payloadBase64 = requiredText(root, "payload");
            signatureBase64 = requiredText(root, "signature");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 文件格式错误，应为含 payload 与 signature 的 JSON");
        }

        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = Base64.getDecoder().decode(payloadBase64);
            signature = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 内容 Base64 解码失败");
        }

        if (!verifySignature(payloadBytes, signature)) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 签名校验失败，文件可能被篡改");
        }

        try {
            return JSON.readValue(payloadBytes, LicensePayload.class);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 载荷解析失败：" + e.getMessage());
        }
    }

    /** 验签当前公钥是否与私钥签出的签名匹配 */
    boolean verifySignature(byte[] payloadBytes, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey());
            verifier.update(payloadBytes);
            return verifier.verify(signature);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 验签执行异常：" + e.getMessage());
        }
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 文件缺少 " + field + " 字段");
        }
        return node.asText();
    }
}
