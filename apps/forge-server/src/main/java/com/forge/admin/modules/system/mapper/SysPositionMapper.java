package com.forge.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.DataPermission;
import com.forge.admin.modules.system.entity.SysPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysPositionMapper extends BaseMapper<SysPosition> {

    /**
     * 分页查询岗位列表（带数据权限过滤）
     *
     * @param page 分页参数
     * @param positionName 岗位名称（模糊查询）
     * @param positionCode 岗位编码
     * @param status 状态
     * @param deptId 部门ID
     * @return 岗位分页列表
     */
    @DataPermission(userAlias = "p", deptAlias = "d")
    IPage<SysPosition> selectPositionPageWithPermission(
            Page<SysPosition> page,
            @Param("positionName") String positionName,
            @Param("positionCode") String positionCode,
            @Param("status") Integer status,
            @Param("deptId") Long deptId
    );

    /**
     * 查询岗位列表（带数据权限过滤）
     *
     * @param positionName 岗位名称（模糊查询）
     * @param positionCode 岗位编码
     * @param status 状态
     * @param deptId 部门ID
     * @return 岗位列表
     */
    @DataPermission(userAlias = "p", deptAlias = "d")
    List<SysPosition> selectPositionListWithPermission(
            @Param("positionName") String positionName,
            @Param("positionCode") String positionCode,
            @Param("status") Integer status,
            @Param("deptId") Long deptId
    );
}
