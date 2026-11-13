package com.forge.framework.plugin.license;

import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.PrivateKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * License 验签矩阵测试：有效 / 篡改 / 错钥 / 格式错误 / 缺字段 / 载荷解析。
 * 私钥在测试内动态生成（canonical payload 手工构造，与签发 CLI 同规则）。
 *
 * @author forge-admin
 */
@DisplayName("License 验签矩阵")
class LicenseVerifierTest {

    private static KeyPair keyPair;

    private static KeyPair otherKeyPair;

    private static LicenseVerifier verifier;

    @BeforeAll
    static void init() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
        verifier = new LicenseVerifier(Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("有效 License：验签通过并解析载荷")
    void verifyValid() {
        String license = buildLicense(keyPair.getPrivate(), validPayload());
        LicensePayload payload = verifier.verify(license);
        assertThat(payload.licenseKey()).isEqualTo("LIC-TEST-0001");
        assertThat(payload.pluginCodes()).containsExactly("demo");
        assertThat(payload.tenantCode()).isEqualTo("tenant-a");
        assertThat(payload.scopes()).containsExactly("user:write");
    }

    @Test
    @DisplayName("篡改载荷：签名校验失败")
    void tamperedPayload() {
        String license = buildLicense(keyPair.getPrivate(), validPayload());
        // 正确篡改：解码 Base64 → 改明文 → 重新编码（不重签）
        String tampered = tamperLicenseKey(license, "LIC-HACK-9999");
        assertThatThrownBy(() -> verifier.verify(tampered))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.LICENSE_INVALID.getCode()))
                .hasMessageContaining("签名校验失败");
    }

    @Test
    @DisplayName("非签发方私钥（错钥）：签名校验失败")
    void wrongPrivateKey() {
        String license = buildLicense(otherKeyPair.getPrivate(), validPayload());
        assertThatThrownBy(() -> verifier.verify(license))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名校验失败");
    }

    @Test
    @DisplayName("格式错误：非 JSON / 缺 payload / 缺 signature")
    void malformedFile() {
        assertThatThrownBy(() -> verifier.verify("not-a-json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式错误");

        assertThatThrownBy(() -> verifier.verify("{\"payload\":\"" + b64(validPayload()) + "\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 signature");

        assertThatThrownBy(() -> verifier.verify("{\"signature\":\"" + signatureOf(b64(validPayload())) + "\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 payload");
    }

    @Test
    @DisplayName("Base64 非法：解码失败")
    void illegalBase64() {
        String license = "{\"payload\":\"!!!not-base64!!!\",\"signature\":\"AAAA\"}";
        assertThatThrownBy(() -> verifier.verify(license))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Base64 解码失败");
    }

    // ===== 构造工具（canonical payload 与签发 CLI 同规则：key 字典序紧凑）=====

    private static String validPayload() {
        return "{\"issuedAt\":\"2026-11-13T00:00:00+08:00\",\"licenseKey\":\"LIC-TEST-0001\","
                + "\"licensee\":\"测试科技有限公司\",\"notAfter\":\"2027-11-13T00:00:00+08:00\","
                + "\"notBefore\":\"2026-11-13T00:00:00+08:00\",\"pluginCodes\":[\"demo\"],"
                + "\"scopes\":[\"user:write\"],\"tenantCode\":\"tenant-a\"}";
    }

    private static String b64(String canonicalJson) {
        return Base64.getEncoder().encodeToString(canonicalJson.getBytes(StandardCharsets.UTF_8));
    }

    private static String signatureOf(String payloadBase64) {
        try {
            return signatureOf(keyPair.getPrivate(), Base64.getDecoder().decode(payloadBase64));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 解码 payload → 替换 licenseKey 明文 → 重新编码（保持旧签名=篡改语义） */
    private static String tamperLicenseKey(String license, String newLicenseKey) {
        int p = license.indexOf("\"payload\":\"") + 11;
        int q = license.indexOf('"', p);
        String payloadBase64 = license.substring(p, q);
        String json = new String(Base64.getDecoder().decode(payloadBase64), StandardCharsets.UTF_8);
        String tamperedJson = json.replace("LIC-TEST-0001", newLicenseKey);
        String tamperedBase64 = Base64.getEncoder().encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));
        return license.substring(0, p) + tamperedBase64 + license.substring(q);
    }

    private static String signatureOf(PrivateKey privateKey, byte[] payloadBytes) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(payloadBytes);
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private static String buildLicense(PrivateKey privateKey, String canonicalPayload) {
        String payloadBase64 = b64(canonicalPayload);
        try {
            String signature = signatureOf(privateKey, canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return buildLicenseRaw(payloadBase64, signature);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String buildLicenseRaw(String payloadBase64, String signature) {
        return "{\"payload\":\"" + payloadBase64 + "\",\"signature\":\"" + signature + "\"}";
    }
}
