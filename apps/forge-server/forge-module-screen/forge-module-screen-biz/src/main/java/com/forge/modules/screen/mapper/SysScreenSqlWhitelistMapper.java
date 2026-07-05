package com.forge.modules.screen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.screen.entity.SysScreenSqlWhitelist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 大屏 SQL 白名单 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysScreenSqlWhitelistMapper extends BaseMapper<SysScreenSqlWhitelist> {

    /**
     * 根据 Schema 与表名查找启用的白名单配置
     *
     * @param schemaName Schema 名称
     * @param tableName  表名
     * @return 启用的白名单配置；若不存在或未启用则返回 null
     */
    SysScreenSqlWhitelist findByTable(@Param("schemaName") String schemaName,
                                      @Param("tableName") String tableName);
}
