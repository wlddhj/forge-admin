package com.forge.modules.screen.service;

import com.forge.modules.screen.entity.SysScreenRole;

import java.util.List;

public interface ScreenAccessService {

    /**
     * 查询大屏已授权的角色 ID 列表
     */
    List<Long> listRoleIdsByScreenId(Long screenId);

    /**
     * 设置大屏的授权角色（先删后插）
     */
    void setScreenRoles(Long screenId, List<Long> roleIds);

    /**
     * 查询大屏已授权的实体列表（用于内部调用）
     */
    List<SysScreenRole> listByScreenId(Long screenId);
}
