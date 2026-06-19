package com.forge.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.modules.system.entity.SysUserPasswordHistory;

import java.util.List;

public interface SysUserPasswordHistoryService extends IService<SysUserPasswordHistory> {

    /**
     * 检查新密码是否与最近 N 次历史密码重复
     *
     * @param userId       用户ID
     * @param rawPassword  新密码明文
     * @param historySize  历史校验条数
     * @return true 表示重复，false 表示不重复
     */
    boolean isPasswordInHistory(Long userId, String rawPassword, int historySize);

    /**
     * 保存密码到历史表，并裁剪超过保留条数的旧记录
     *
     * @param userId      用户ID
     * @param passwordHash BCrypt 哈希
     * @param historySize 保留条数
     */
    void saveAndTrim(Long userId, String passwordHash, int historySize);

    /**
     * 获取用户最近 N 次密码历史
     */
    List<SysUserPasswordHistory> getRecentHistory(Long userId, int size);
}
