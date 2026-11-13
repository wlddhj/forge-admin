package com.forge.modules.plugin.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.UserContext;
import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.ScopeDefinition;
import com.forge.modules.plugin.entity.SysPluginScope;
import com.forge.modules.plugin.license.PluginLicenseService;
import com.forge.modules.plugin.mapper.SysPluginScopeMapper;
import com.forge.modules.plugin.state.PluginCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件数据 scope 检查器：有效 scope = 插件声明集读类默认项 ∪ 管理端勾选授予
 * ∪ 当前有效 License 内置（02 功能设计⑥的三路合并）。
 *
 * <p>未命中抛 SCOPE_DENIED；授予/回收/授权变更时缓存逐键 evict。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginScopeChecker {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SysPluginScopeMapper scopeMapper;

    private final PluginLicenseService licenseService;

    private final PluginCacheService cacheService;

    private final List<PluginDescriptor> descriptors;

    /** 写操作前置检查：未授予即拒绝（fail-closed） */
    public void check(String pluginCode, String scope) {
        Long tenantId = currentTenantId();
        Set<String> effective = effectiveScopes(pluginCode, tenantId);
        if (!effective.contains(scope)) {
            throw new BusinessException(ResultCode.SCOPE_DENIED,
                    "插件[" + pluginCode + "]未被授予数据权限 " + scope + "，操作已拒绝");
        }
    }

    /** 插件在租户的有效 scope 全集（管理端界面/合并口径共用；Redis TTL 300s，变更方 evict） */
    public Set<String> effectiveScopes(String pluginCode, Long tenantId) {
        String cached = cacheService.getScope(tenantId, pluginCode);
        if (cached != null) {
            try {
                return JSON.readValue(cached, new TypeReference<Set<String>>() {
                });
            } catch (Exception e) {
                log.warn("租户 {} 插件 {} scope 缓存损坏，回源数据库", tenantId, pluginCode, e);
            }
        }
        Set<String> result = new HashSet<>();
        // 1) 声明集读类默认项
        descriptorOf(pluginCode).declaredScopes().stream()
                .filter(ScopeDefinition::grantedByDefault)
                .map(ScopeDefinition::scope)
                .forEach(result::add);
        // 2) 管理端勾选授予
        result.addAll(grantedByAdmin(tenantId, pluginCode));
        // 3) 有效 License 内置
        result.addAll(licenseService.getActiveLicenseScopes(tenantId, pluginCode, LocalDateTime.now()));
        // 越界防御：合并结果裁剪回声明集（License 导入已校验，此处双保险）
        Set<String> declared = descriptorOf(pluginCode).declaredScopes().stream()
                .map(ScopeDefinition::scope).collect(Collectors.toSet());
        result.retainAll(declared);
        cacheService.putScope(tenantId, pluginCode, JSON.writeValueAsString(result));
        return result;
    }

    /** 管理端勾选授予集（查表，授予/回收频率低；License 三路合并的高频读走 effectiveScopes 由调用方缓存） */
    public Set<String> grantedByAdmin(Long tenantId, String pluginCode) {
        return scopeMapper.selectList(Wrappers.<SysPluginScope>lambdaQuery()
                        .eq(SysPluginScope::getTenantId, tenantId)
                        .eq(SysPluginScope::getPluginCode, pluginCode))
                .stream()
                .map(SysPluginScope::getScope)
                .collect(Collectors.toSet());
    }

    /** 授予 scope（须 ∈ 插件声明集且非读类默认项） */
    public void grant(Long tenantId, String pluginCode, String scope) {
        Set<String> declared = descriptorOf(pluginCode).declaredScopes().stream()
                .map(ScopeDefinition::scope).collect(Collectors.toSet());
        if (!declared.contains(scope)) {
            throw new BusinessException(ResultCode.SCOPE_NOT_DECLARED,
                    "scope " + scope + " 未在插件[" + pluginCode + "]声明集内");
        }
        boolean exists = scopeMapper.selectCount(Wrappers.<SysPluginScope>lambdaQuery()
                .eq(SysPluginScope::getTenantId, tenantId)
                .eq(SysPluginScope::getPluginCode, pluginCode)
                .eq(SysPluginScope::getScope, scope)) > 0;
        if (!exists) {
            SysPluginScope record = new SysPluginScope();
            record.setTenantId(tenantId);
            record.setPluginCode(pluginCode);
            record.setScope(scope);
            record.setGrantTime(LocalDateTime.now());
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            scopeMapper.insert(record);
        }
        cacheService.evictScope(tenantId, pluginCode);
    }

    /** 回收 scope */
    public void revoke(Long tenantId, String pluginCode, String scope) {
        scopeMapper.delete(Wrappers.<SysPluginScope>lambdaQuery()
                .eq(SysPluginScope::getTenantId, tenantId)
                .eq(SysPluginScope::getPluginCode, pluginCode)
                .eq(SysPluginScope::getScope, scope));
        cacheService.evictScope(tenantId, pluginCode);
    }

    private PluginDescriptor descriptorOf(String pluginCode) {
        return descriptors.stream()
                .filter(d -> d.pluginCode().equals(pluginCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PLUGIN_NOT_FOUND,
                        "插件不存在或未注册: " + pluginCode));
    }

    private Long currentTenantId() {
        UserContext context = UserContext.get();
        if (context == null || context.getTenantId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "数据门面调用缺少租户上下文");
        }
        return context.getTenantId();
    }
}
