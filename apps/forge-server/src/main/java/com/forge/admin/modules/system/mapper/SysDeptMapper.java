package com.forge.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.admin.modules.system.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 部门 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 检查是否存在子部门
     */
    int hasChildren(@Param("deptId") Long deptId);

    /**
     * 检查部门下是否存在用户
     */
    int hasUsers(@Param("deptId") Long deptId);
}
