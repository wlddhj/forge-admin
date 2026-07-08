package com.forge.modules.screen.service.impl;

import com.forge.modules.screen.entity.SysScreenRole;
import com.forge.modules.screen.mapper.SysScreenRoleMapper;
import com.forge.modules.screen.service.ScreenAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScreenAccessServiceImpl implements ScreenAccessService {

    private final SysScreenRoleMapper screenRoleMapper;

    @Override
    public List<Long> listRoleIdsByScreenId(Long screenId) {
        return screenRoleMapper.selectRoleIdsByScreenId(screenId);
    }

    @Override
    public List<SysScreenRole> listByScreenId(Long screenId) {
        return listRoleIdsByScreenId(screenId).stream().map(roleId -> {
            SysScreenRole sr = new SysScreenRole();
            sr.setScreenId(screenId);
            sr.setRoleId(roleId);
            return sr;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void setScreenRoles(Long screenId, List<Long> roleIds) {
        screenRoleMapper.deleteByScreenId(screenId);
        if (roleIds == null || roleIds.isEmpty()) return;
        List<SysScreenRole> list = roleIds.stream().distinct().map(roleId -> {
            SysScreenRole sr = new SysScreenRole();
            sr.setScreenId(screenId);
            sr.setRoleId(roleId);
            return sr;
        }).collect(Collectors.toList());
        screenRoleMapper.batchInsert(list);
    }
}
