package com.forge.modules.screen.executor;

import com.forge.modules.screen.config.ScreenProperties;
import com.forge.modules.screen.safety.SqlSafetyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HttpDataSourceExecutor} 单元测试（TDD）。
 *
 * <p>核心覆盖 SSRF 防护三个边界：
 * <ul>
 *   <li>host 不在白名单 → 立即抛 {@link SqlSafetyException}（"不在白名单"）</li>
 *   <li>{@code requireHttps=true} 且使用 http 协议 → 立即抛（"HTTPS"）</li>
 *   <li>host 在白名单 → 通过 SSRF 闸门（实际请求失败可忽略，仅校验闸门逻辑）</li>
 * </ul>
 *
 * <p>另含两条 SSRF 子串绕过专项用例，确保 host 比较为精确等值，而非子串包含：
 * <ul>
 *   <li>{@code evil.com} 子串包含攻击 → 拒绝</li>
 *   <li>白名单大小写不敏感：{@code LOCALHOST} → 通过 SSRF 闸门</li>
 * </ul>
 *
 * <p>构造函数注入 {@code null} RestClient：实现必须延迟构造，
 * 仅在真正发起请求时才使用 RestClient，使 SSRF 闸门在 mock 环境下也可独立验证。
 *
 * @author standadmin
 */
class HttpDataSourceExecutorTest {

    HttpDataSourceExecutor executor;
    ScreenProperties props;

    @BeforeEach
    void setup() {
        props = ScreenProperties.defaults();
        // 第二参数 null：强制执行器在 SSRF 闸门阶段不依赖 RestClient
        executor = new HttpDataSourceExecutor(props, null);
    }

    /**
     * 外部域名（evil.example.com）不在白名单 → SSRF 闸门立即拒绝。
     */
    @Test
    void rejects_external_url_not_in_allowlist() {
        Map<String, Object> config = Map.of(
            "method", "GET",
            "url", "https://evil.example.com/api/leak"
        );
        assertThatThrownBy(() -> executor.execute(config, Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("不在白名单");
    }

    /**
     * 子串包含攻击：host 形如 {@code evil.com.127.0.0.1} 不应被
     * "包含 127.0.0.1 子串"放行 —— 必须精确等值匹配。
     */
    @Test
    void rejects_substring_bypass_attempt() {
        Map<String, Object> config = Map.of(
            "method", "GET",
            // 攻击者构造 host：表面包含 "127.0.0.1" 子串，但其实是恶意域名
            "url", "http://127.0.0.1.evil.com/leak"
        );
        assertThatThrownBy(() -> executor.execute(config, Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("不在白名单");
    }

    /**
     * {@code requireHttps=true} 时 http 协议被拒绝。
     */
    @Test
    void rejects_https_violation_when_required() {
        props.setRequireHttps(true);
        Map<String, Object> config = Map.of(
            "method", "GET",
            "url", "http://localhost/api"
        );
        assertThatThrownBy(() -> executor.execute(config, Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("HTTPS");
    }

    /**
     * 白名单 host 大小写不敏感：{@code LOCALHOST} 视同 {@code localhost}。
     */
    @Test
    void allowlist_match_is_case_insensitive() {
        Map<String, Object> config = Map.of(
            "method", "GET",
            "url", "http://LOCALHOST:8181/api/system/user/list"
        );
        // host 大写 → 经 SSRF 闸门通过即可（实际请求失败可忽略）
        assertThatCode(() -> {
            try {
                executor.execute(config, Map.of());
            } catch (SqlSafetyException e) {
                // 不应抛"不在白名单"
                if (e.getMessage().contains("不在白名单")) {
                    throw e;
                }
                // 其他错误（如连接拒绝、HTTPS 强制）忽略
            }
        }).doesNotThrowAnyException();
    }

    /**
     * 正向用例：host 在白名单（小写），SSRF 闸门通过；
     * 由于未注入 RestClient，实际请求阶段会抛 IllegalStateException 等运行异常，
     * 但只要不是"不在白名单"即视为 SSRF 闸门通过。
     */
    @Test
    void accepts_url_in_allowlist() {
        Map<String, Object> config = Map.of(
            "method", "GET",
            "url", "http://localhost:8181/api/system/user/list"
        );
        assertThatCode(() -> {
            try {
                executor.execute(config, Map.of());
            } catch (SqlSafetyException e) {
                // 应该不抛"不在白名单"
                if (e.getMessage().contains("不在白名单")) {
                    throw e;
                }
                // 其他错误（实际请求失败 / RestClient null 等）忽略
            }
        }).doesNotThrowAnyException();
    }
}
