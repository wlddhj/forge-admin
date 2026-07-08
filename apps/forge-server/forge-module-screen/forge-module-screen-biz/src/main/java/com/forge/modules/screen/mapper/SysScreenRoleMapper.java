package com.forge.modules.screen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.screen.entity.SysScreenRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysScreenRoleMapper extends BaseMapper<SysScreenRole> {

    /**
     * 查询大屏已授权的角色 ID 列表
     */
    List<Long> selectRoleIdsByScreenId(@Param("screenId") Long screenId);

    /**
     * 查询大屏已授权的 screenId 列表（按角色过滤）
     */
    List<Long> selectScreenIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除大屏的所有授权角色
     */
    int deleteByScreenId(@Param("screenId") Long screenId);

    /**
     * 批量插入授权关系
     */
    int batchInsert(@Param("list") List<SysScreenRole> list);
}
