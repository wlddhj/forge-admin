package com.forge.framework.plugin.license;

import com.forge.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在线签发器测试：签发↔验签闭环 / 篡改拒绝 / 私钥非法。
 *
 * @author forge-admin
 */
@DisplayName("License 在线签发器")
class LicenseIssuerTest {

    private static LicenseIssuer issuer;

    private static LicenseVerifier verifier;

    private static LicensePayload payload;

    @BeforeAll
    static void init() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        issuer = new LicenseIssuer(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        verifier = new LicenseVerifier(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        payload = new LicensePayload(
                "LIC-ISSUE-0001",
                List.of("demo"),
                "tenant-a",
                "测试主体",
                "2026-11-13T00:00:00+08:00",
                "2026-11-13T00:00:00+08:00",
                "2027-11-13T00:00:00+08:00",
                List.of("user:write"),
                Map.of("k", "v"));
    }

    @Test
    @DisplayName("签发↔验签闭环：canonical 序列化产物可被同钥公钥验签并还原")
    void issueThenVerify() {
        String license = issuer.issue(payload);
        LicensePayload parsed = verifier.verify(license);
        assertThat(parsed.licenseKey()).isEqualTo("LIC-ISSUE-0001");
        assertThat(parsed.pluginCodes()).containsExactly("demo");
        assertThat(parsed.tenantCode()).isEqualTo("tenant-a");
        assertThat(parsed.scopes()).containsExactly("user:write");
        assertThat(parsed.extra()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("篡改载荷：验签失败")
    void tamperedRejected() {
        String license = issuer.issue(payload);
        int p = license.indexOf("\"payload\":\"") + 11;
        int q = license.indexOf('"', p);
        String json = new String(Base64.getDecoder().decode(license.substring(p, q)),
                java.nio.charset.StandardCharsets.UTF_8);
        String tampered = json.replace("tenant-a", "tenant-b");
        String reEncoded = Base64.getEncoder().encodeToString(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> verifier.verify(license.substring(0, p) + reEncoded + license.substring(q)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名校验失败");
    }

    @Test
    @DisplayName("私钥非法：构造失败")
    void invalidPrivateKeyRejected() {
        assertThatThrownBy(() -> new LicenseIssuer("!!!not-base64!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("私钥解析失败");
    }

    @Test
    @DisplayName("无 scope/无 extra 的最小载荷：可签可验")
    void minimalPayload() {
        LicensePayload minimal = new LicensePayload(
                "LIC-MIN", List.of("p1"), "t1", "L", null, "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z", null, null);
        LicensePayload parsed = verifier.verify(issuer.issue(minimal));
        assertThat(parsed.scopes()).isNull();
        assertThat(parsed.extra()).isNull();
    }
}
