package com.forge.modules.workflow.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * IM 渠道配置服务（按租户维度）
 * <p>
 * 访问模型：全部数据方法 {@code @TenantIgnore} + 显式 tenant_id 条件——推送链路运行在
 * 异步线程，显式过滤保证确定性，不依赖 MyBatis 租户拦截器。租户来源 = 事件携带租户或
 * 线程上下文（{@link #resolveTenantId()} 可能返回 null），无租户来源时渠道一律视为
 * 不可用（fail-closed 不发送），不再兜底默认租户，防止跨租户串扰。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImChannelConfigService {

    static final Set<String> SUPPORTED_CHANNELS = Set.of("wework", "dingtalk");

    private final WfImChannelConfigMapper configMapper;

    /**
     * 推送链路租户解析：返回当前线程租户上下文（可能 null——事件未携带且线程无上下文时），
     * 调用方须对 null 判定 fail-closed（该渠道视为不可用，不发送）
     */
    public Long resolveTenantId() {
        return TenantContextHolder.getTenantId();
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
     * 渠道在该租户下是否可推送（行存在 + 启用 + 凭据完整）；
     * 无租户来源（tenantId=null）直接视为不可用——fail-closed 不发送
     */
    @TenantIgnore
    public boolean runnable(Long tenantId, String channel) {
        if (tenantId == null) {
            return false;
        }
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
     * upsert：(tenantId, channel) 唯一，重复保存更新已有行；secret 空串语义 = 保留原值。
     * 并发首次保存由 uk_tenant_channel 兜底：撞唯一键方重查存量转更新，幂等收敛为单行
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
            try {
                configMapper.insert(entity);
                log.info("新增IM渠道配置: tenantId={}, channel={}", request.getTenantId(), request.getChannel());
                return;
            } catch (DuplicateKeyException e) {
                // 并发保存双双判空、双双 insert：输家撞 uk_tenant_channel，
                // 重查赢家落库的存量行转更新（幂等收敛双行为单行）
                existing = configOf(request.getTenantId(), request.getChannel());
                if (existing == null) {
                    // 唯一键冲突却查无此行：非常规异常（如连接故障），按原语义抛出
                    throw e;
                }
                log.info("并发新增撞唯一索引，转更新已有行: tenantId={}, channel={}",
                        request.getTenantId(), request.getChannel());
            }
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

    /**
     * 逻辑删除墓碑惯用法：deleted 置本行 id 而非 1——
     * uk_tenant_channel(tenant_id, channel, deleted) 下同键允许多条墓碑共存，
     * "删除-重建-再删除"不撞唯一索引（配合升级脚本 upgrade-20261112）
     */
    @TenantIgnore
    public void deleteConfig(Long id) {
        configMapper.update(null, new LambdaUpdateWrapper<WfImChannelConfig>()
                .eq(WfImChannelConfig::getId, id)
                .set(WfImChannelConfig::getDeleted, id));
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
