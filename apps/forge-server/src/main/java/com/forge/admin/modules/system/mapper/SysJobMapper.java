package com.forge.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.admin.modules.system.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务Mapper
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
}
