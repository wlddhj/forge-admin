package com.forge.modules.system.quartz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.system.quartz.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行日志 Mapper
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
}
