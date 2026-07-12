package com.forge.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.system.entity.SysTenantPackageMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户套餐-菜单关联 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysTenantPackageMenuMapper extends BaseMapper<SysTenantPackageMenu> {

    /**
     * 删除套餐的所有菜单关联
     *
     * @param packageId 套餐ID
     * @return 删除条数
     */
    int deleteByPackageId(@Param("packageId") Long packageId);

    /**
     * 批量插入套餐菜单关联
     *
     * @param list 关联列表
     * @return 插入条数
     */
    int batchInsert(@Param("list") List<SysTenantPackageMenu> list);

    /**
     * 查询套餐的菜单ID列表
     *
     * @param packageId 套餐ID
     * @return 菜单ID列表
     */
    @Select("SELECT menu_id FROM sys_tenant_package_menu WHERE tenant_package_id = #{packageId}")
    List<Long> selectMenuIdsByPackageId(@Param("packageId") Long packageId);
}
