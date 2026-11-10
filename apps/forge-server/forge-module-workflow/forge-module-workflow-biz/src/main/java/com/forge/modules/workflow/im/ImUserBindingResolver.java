package com.forge.modules.workflow.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.system.auth.entity.SysSocialUser;
import com.forge.modules.system.auth.mapper.SysSocialUserMapper;
import com.forge.modules.workflow.im.dingtalk.DingTalkChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统用户 → 企业 userid 解析器（sys_social_user 复用，零 DDL）
 * <p>
 * 绑定语义：source=wework 行 openId=企微 userid；source=dingtalk_corp 行 openId=钉钉 userid
 * （管理端手动录入）；source=dingtalk 行 unionId 非空时经钉钉 API 转换（DingTalkChannel 内缓存）。
 * 未解析到绑定的用户不在返回 Map 中，由 Router 计数跳过。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ImUserBindingResolver {

    private final SysSocialUserMapper socialUserMapper;
    private final DingTalkChannel dingTalkChannel;

    public ImUserBindingResolver(SysSocialUserMapper socialUserMapper, DingTalkChannel dingTalkChannel) {
        this.socialUserMapper = socialUserMapper;
        this.dingTalkChannel = dingTalkChannel;
    }

    /**
     * 解析一批系统用户在指定渠道的企业 userid
     *
     * @param channel 渠道标识（wework / dingtalk）
     * @param userIds 系统用户ID集合
     * @return userId → corpUserid（仅包含成功解析的）
     */
    public Map<Long, String> resolve(String channel, Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }
        return switch (channel) {
            case "wework" -> resolveWework(userIds);
            case "dingtalk" -> resolveDingtalk(userIds);
            default -> Map.of();
        };
    }

    /**
     * 企微：wework 绑定行 openId 即 userid
     */
    private Map<Long, String> resolveWework(Collection<Long> userIds) {
        List<SysSocialUser> rows = selectBySources(userIds, List.of("wework"));
        Map<Long, String> result = new HashMap<>();
        for (SysSocialUser row : rows) {
            if (StringUtils.hasText(row.getOpenId())) {
                result.put(row.getUserId(), row.getOpenId());
            }
        }
        return result;
    }

    /**
     * 钉钉：dingtalk_corp 直读优先；dingtalk 绑定行走 unionId 转换
     */
    private Map<Long, String> resolveDingtalk(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        List<SysSocialUser> rows = selectBySources(userIds, List.of("dingtalk_corp", "dingtalk"));
        for (SysSocialUser row : rows) {
            if (!StringUtils.hasText(row.getOpenId())) {
                continue;
            }
            if ("dingtalk_corp".equals(row.getSource())) {
                result.put(row.getUserId(), row.getOpenId());
            }
        }
        for (SysSocialUser row : rows) {
            if ("dingtalk_corp".equals(row.getSource()) || result.containsKey(row.getUserId())) {
                continue;
            }
            String userid = dingTalkChannel.resolveUseridByUnionId(row.getUnionId());
            if (StringUtils.hasText(userid)) {
                result.put(row.getUserId(), userid);
            }
        }
        return result;
    }

    private List<SysSocialUser> selectBySources(Collection<Long> userIds, List<String> sources) {
        return socialUserMapper.selectList(new LambdaQueryWrapper<SysSocialUser>()
                .in(SysSocialUser::getUserId, userIds)
                .in(SysSocialUser::getSource, sources)
                .eq(SysSocialUser::getStatus, 1));
    }
}
