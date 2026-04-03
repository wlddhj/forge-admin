package com.forge.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.admin.modules.system.entity.SysUserPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户岗位关联 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysUserPositionMapper extends BaseMapper<SysUserPosition> {

    /**
     * 删除用户岗位关联
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 批量插入用户岗位
     */
    int batchInsert(@Param("list") List<SysUserPosition> list);

    /**
     * 根据用户ID查询岗位ID列表
     */
    List<Long> selectPositionIdsByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询岗位名称列表
     */
    List<String> selectPositionNamesByUserId(@Param("userId") Long userId);
}
