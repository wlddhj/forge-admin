package com.forge.framework.plugin.license;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * License 在线签发器（与 tools/license-cli 的 IssueLicense 同规则：canonical JSON
 * —— 对象 key 字典序、紧凑无空白——SHA256withRSA 签名，Base64 承载）。
 *
 * <p>仅在配置 forge.plugin.license.private-key 时装配；产物与 CLI 完全互通
 * （验签端对原始字节验签，两端不依赖同一套 JSON 库）。
 *
 * @author forge-admin
 */
public class LicenseIssuer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PrivateKey privateKey;

    public LicenseIssuer(String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64.trim());
            this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("插件 License 签发私钥解析失败", e);
        }
    }

    /**
     * 签发生成 license 文件内容（{"payload":"<Base64>","signature":"<Base64>"}）。
     */
    public String issue(LicensePayload payload) {
        String canonical = canonicalize(payload);
        byte[] payloadBytes = canonical.getBytes(StandardCharsets.UTF_8);
        String signature;
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(payloadBytes);
            signature = Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("License 签名失败", e);
        }
        String payloadBase64 = Base64.getEncoder().encodeToString(payloadBytes);
        return "{\"payload\":\"" + payloadBase64 + "\",\"signature\":\"" + signature + "\"}";
    }

    /** canonical JSON：对象（含嵌套 extra）递归字典序，紧凑输出 */
    private String canonicalize(LicensePayload payload) {
        Map<String, Object> sorted = new TreeMap<>();
        sorted.put("licenseKey", payload.licenseKey());
        sorted.put("pluginCodes", payload.pluginCodes());
        sorted.put("tenantCode", payload.tenantCode());
        sorted.put("licensee", payload.licensee());
        sorted.put("issuedAt", payload.issuedAt());
        sorted.put("notBefore", payload.notBefore());
        sorted.put("notAfter", payload.notAfter());
        sorted.put("scopes", payload.scopes());
        if (payload.extra() != null && !payload.extra().isEmpty()) {
            sorted.put("extra", sortDeep(payload.extra()));
        }
        return JSON.writeValueAsString(sorted);
    }

    private Object sortDeep(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), sortDeep(v)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sortDeep).toList();
        }
        return value;
    }
}
