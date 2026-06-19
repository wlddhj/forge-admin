package com.forge.modules.system.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "forge.security.login")
public class LoginPolicyProperties {
    /** 触发锁定的失败次数阈值 */
    private int maxFailCount = 5;
    /** 锁定时长（分钟） */
    private int lockMinutes = 15;
    /** 失败计数 Redis key 前缀 */
    private String failCountPrefix = "login_fail:";
    /** 锁定标记 Redis key 前缀 */
    private String lockPrefix = "login_lock:";
    /** 单点登录模式（true：新登录踢掉旧会话） */
    private boolean singleSession = true;
    /** 单点登录最大并发会话数（single-session=false 时生效） */
    private int maxConcurrentSessions = 3;
}
