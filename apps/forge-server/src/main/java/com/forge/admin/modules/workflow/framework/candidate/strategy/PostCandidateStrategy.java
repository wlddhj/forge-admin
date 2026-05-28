package com.forge.admin.modules.workflow.framework.candidate.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.admin.modules.system.entity.SysUserPosition;
import com.forge.admin.modules.system.mapper.SysUserPositionMapper;
import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.admin.modules.workflow.framework.candidate.CandidateStrategyEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位候选人策略
 * 根据岗位ID查询关联的用户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCandidateStrategy implements BpmTaskCandidateStrategy {

    private final SysUserPositionMapper sysUserPositionMapper;

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.POST.getCode();
    }

    @Override
    public String getDescription() {
        return "指定岗位";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            Set<Long> positionIds = Arrays.stream(param.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
            if (positionIds.isEmpty()) {
                return Collections.emptySet();
            }
            return sysUserPositionMapper.selectList(
                    new LambdaQueryWrapper<SysUserPosition>()
                            .in(SysUserPosition::getPositionId, positionIds)
            ).stream().map(SysUserPosition::getUserId).collect(Collectors.toSet());
        } catch (NumberFormatException e) {
            log.warn("岗位候选人参数解析失败: param={}", param, e);
            return Collections.emptySet();
        }
    }
}
