package com.forge.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.admin.modules.system.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
