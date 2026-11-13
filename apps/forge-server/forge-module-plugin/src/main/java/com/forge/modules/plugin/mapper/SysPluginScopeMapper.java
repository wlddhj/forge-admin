package com.forge.modules.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.plugin.entity.SysPluginScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 插件数据 scope 授权 Mapper
 *
 * @author forge-admin
 */
@Mapper
public interface SysPluginScopeMapper extends BaseMapper<SysPluginScope> {
}
