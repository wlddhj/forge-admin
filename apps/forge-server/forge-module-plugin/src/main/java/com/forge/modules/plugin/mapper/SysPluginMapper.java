package com.forge.modules.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.plugin.entity.SysPlugin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 插件注册表 Mapper（通用 CRUD 走 MP；自定义 SQL 落 mapper XML）
 *
 * @author forge-admin
 */
@Mapper
public interface SysPluginMapper extends BaseMapper<SysPlugin> {
}
