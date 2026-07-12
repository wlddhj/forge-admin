package com.forge.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.system.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /**
     * 根据租户标识查询租户ID
     *
     * @param code 租户标识
     * @return 租户ID，未找到返回 null
     */
    @Select("SELECT id FROM sys_tenant WHERE code = #{code} AND deleted = 0 LIMIT 1")
    Long selectIdByCode(@Param("code") String code);
}
