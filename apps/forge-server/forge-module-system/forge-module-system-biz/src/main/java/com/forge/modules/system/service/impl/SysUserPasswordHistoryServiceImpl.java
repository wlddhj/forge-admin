package com.forge.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.modules.system.entity.SysUserPasswordHistory;
import com.forge.modules.system.mapper.SysUserPasswordHistoryMapper;
import com.forge.modules.system.service.SysUserPasswordHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserPasswordHistoryServiceImpl
        extends ServiceImpl<SysUserPasswordHistoryMapper, SysUserPasswordHistory>
        implements SysUserPasswordHistoryService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean isPasswordInHistory(Long userId, String rawPassword, int historySize) {
        List<SysUserPasswordHistory> recent = getRecentHistory(userId, historySize);
        return recent.stream().anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPassword()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAndTrim(Long userId, String passwordHash, int historySize) {
        SysUserPasswordHistory entity = new SysUserPasswordHistory();
        entity.setUserId(userId);
        entity.setPassword(passwordHash);
        entity.setCreateTime(LocalDateTime.now());
        save(entity);

        // 裁剪超过 historySize 条的旧记录
        long total = count(new LambdaQueryWrapper<SysUserPasswordHistory>()
                .eq(SysUserPasswordHistory::getUserId, userId));
        if (total > historySize) {
            List<SysUserPasswordHistory> all = list(new LambdaQueryWrapper<SysUserPasswordHistory>()
                    .eq(SysUserPasswordHistory::getUserId, userId)
                    .orderByDesc(SysUserPasswordHistory::getCreateTime));
            List<Long> toDelete = all.stream()
                    .skip(historySize)
                    .map(SysUserPasswordHistory::getId)
                    .toList();
            removeByIds(toDelete);
        }
    }

    @Override
    public List<SysUserPasswordHistory> getRecentHistory(Long userId, int size) {
        return list(new LambdaQueryWrapper<SysUserPasswordHistory>()
                .eq(SysUserPasswordHistory::getUserId, userId)
                .orderByDesc(SysUserPasswordHistory::getCreateTime)
                .last("LIMIT " + size));
    }
}
