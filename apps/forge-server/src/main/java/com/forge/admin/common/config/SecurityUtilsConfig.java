package com.forge.admin.common.config;

import com.forge.admin.common.utils.SecurityUtils;
import com.forge.admin.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * SecurityUtils 配置
 *
 * @author standadmin
 */
@Configuration
@RequiredArgsConstructor
public class SecurityUtilsConfig {

    private final SysUserService sysUserService;

    @PostConstruct
    public void init() {
        SecurityUtils.setSysUserService(sysUserService);
    }
}
