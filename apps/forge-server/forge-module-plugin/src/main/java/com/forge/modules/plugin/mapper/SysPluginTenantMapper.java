package com.forge.modules.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.plugin.entity.SysPluginTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户插件启停 Mapper
 *
 * @author forge-admin
 */
@Mapper
public interface SysPluginTenantMapper extends BaseMapper<SysPluginTenant> {
}
