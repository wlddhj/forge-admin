package com.forge.modules.workflow.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.UserContext;
import com.forge.framework.tenant.core.aop.TenantIgnore;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.dto.im.ImChannelConfigRequest;
import com.forge.modules.workflow.dto.im.ImChannelConfigResponse;
import com.forge.modules.workflow.entity.WfImChannelConfig;
import com.forge.modules.workflow.mapper.WfImChannelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * IM 渠道配置服务（按租户维度）
 * <p>
 * 访问模型：全部数据方法 {@code @TenantIgnore} + 显式 tenant_id 条件——推送链路运行在
 * 异步线程（无可靠租户上下文，见 workflowExecutor TTL 限制），显式过滤保证确定性，
 * 不依赖 MyBatis 租户拦截器。非租户模式（TENANT_ENABLE=false）统一视作租户 1。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImChannelConfigService {

    static final Set<String> SUPPORTED_CHANNELS = Set.of("wework", "dingtalk");

    /**
     * 非租户模式下的默认租户
     */
    static final long DEFAULT_TENANT_ID = 1L;

    private final WfImChannelConfigMapper configMapper;

    /**
     * 推送链路租户解析：上下文有值用之（异步线程继承，best-effort），缺失兜底租户 1
     */
    public Long resolveTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    /**
     * 运行时查询某租户某渠道配置（含禁用行——启停判断由渠道侧做完整性校验）
     */
    @TenantIgnore
    public WfImChannelConfig configOf(Long tenantId, String channel) {
        return configMapper.selectOne(new LambdaQueryWrapper<WfImChannelConfig>()
                .eq(WfImChannelConfig::getTenantId, tenantId)
                .eq(WfImChannelConfig::getChannel, channel));
    }

    /**
     * 渠道在该租户下是否可推送（行存在 + 启用 + 凭据完整）
     */
    @TenantIgnore
    public boolean runnable(Long tenantId, String channel) {
        WfImChannelConfig config = configOf(tenantId, channel);
        return config != null && config.getEnabled() != null && config.getEnabled() == 1
                && isCredentialComplete(config);
    }

    /**
     * 管理端列表（按租户）
     */
    @TenantIgnore
    public List<ImChannelConfigResponse> listByTenant(Long tenantId) {
        return configMapper.selectList(new LambdaQueryWrapper<WfImChannelConfig>()
                .eq(WfImChannelConfig::getTenantId, tenantId)
                .orderByAsc(WfImChannelConfig::getChannel))
                .stream().map(this::toResponse).toList();
    }

    /**
     * upsert：(tenantId, channel) 唯一，重复保存更新已有行；secret 空串语义 = 保留原值
     */
    @TenantIgnore
    public void upsert(ImChannelConfigRequest request) {
        validate(request);
        WfImChannelConfig existing = configOf(request.getTenantId(), request.getChannel());
        LocalDateTime now = LocalDateTime.now();
        String operator = UserContext.getCurrentUsername();
        String corpId = normalize(request.getCorpId());
        String appKey = normalize(request.getAppKey());
        String agentId = normalize(request.getAgentId());
        if (existing == null) {
            if (!StringUtils.hasText(request.getSecret())) {
                throw new BusinessException(400, "新建渠道配置时 secret 不能为空");
            }
            WfImChannelConfig entity = new WfImChannelConfig();
            entity.setTenantId(request.getTenantId());
            entity.setChannel(request.getChannel());
            entity.setEnabled(request.getEnabled());
            entity.setCorpId(corpId);
            entity.setAppKey(appKey);
            entity.setAgentId(agentId);
            entity.setSecret(normalize(request.getSecret()));
            entity.setRemark(request.getRemark());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setCreateBy(operator);
            entity.setUpdateBy(operator);
            entity.setDeleted(0);
            configMapper.insert(entity);
            log.info("新增IM渠道配置: tenantId={}, channel={}", request.getTenantId(), request.getChannel());
            return;
        }
        existing.setEnabled(request.getEnabled());
        existing.setCorpId(corpId);
        existing.setAppKey(appKey);
        existing.setAgentId(agentId);
        if (StringUtils.hasText(request.getSecret())) {
            existing.setSecret(normalize(request.getSecret()));
        }
        existing.setRemark(request.getRemark());
        existing.setUpdateTime(now);
        existing.setUpdateBy(operator);
        configMapper.updateById(existing);
        log.info("更新IM渠道配置: tenantId={}, channel={}", request.getTenantId(), request.getChannel());
    }

    /**
     * 空白归一：null/纯空白 → null，否则 trim
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @TenantIgnore
    public void deleteConfig(Long id) {
        configMapper.deleteById(id);
        log.info("删除IM渠道配置: id={}", id);
    }

    private ImChannelConfigResponse toResponse(WfImChannelConfig entity) {
        return ImChannelConfigResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .channel(entity.getChannel())
                .enabled(entity.getEnabled())
                .corpId(entity.getCorpId())
                .appKey(entity.getAppKey())
                .agentId(entity.getAgentId())
                .hasSecret(StringUtils.hasText(entity.getSecret()))
                .remark(entity.getRemark())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private void validate(ImChannelConfigRequest request) {
        if (!SUPPORTED_CHANNELS.contains(request.getChannel())) {
            throw new BusinessException(400, "不支持的渠道: " + request.getChannel());
        }
        if (!request.getAgentId().trim().matches("\\d+")) {
            throw new BusinessException(400, "agentId 必须为数字");
        }
        if ("wework".equals(request.getChannel()) && !StringUtils.hasText(request.getCorpId())) {
            throw new BusinessException(400, "企业微信渠道 corpId 不能为空");
        }
        if ("dingtalk".equals(request.getChannel()) && !StringUtils.hasText(request.getAppKey())) {
            throw new BusinessException(400, "钉钉渠道 appKey 不能为空");
        }
    }

    /**
     * 凭据完整性：两渠道均需 agentId+secret，企微需 corpId、钉钉需 appKey
     */
    private boolean isCredentialComplete(WfImChannelConfig config) {
        if (!StringUtils.hasText(config.getAgentId()) || !StringUtils.hasText(config.getSecret())) {
            return false;
        }
        return "wework".equals(config.getChannel())
                ? StringUtils.hasText(config.getCorpId())
                : StringUtils.hasText(config.getAppKey());
    }
}
