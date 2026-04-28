package com.forge.admin.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.admin.modules.auth.entity.SysSocialUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社交账号绑定 Mapper
 */
@Mapper
public interface SysSocialUserMapper extends BaseMapper<SysSocialUser> {
}
