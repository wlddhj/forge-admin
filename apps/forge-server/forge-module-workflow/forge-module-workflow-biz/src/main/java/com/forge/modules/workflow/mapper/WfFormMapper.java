package com.forge.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.workflow.entity.WfForm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单管理 Mapper
 *
 * @author forge
 */
@Mapper
public interface WfFormMapper extends BaseMapper<WfForm> {
}
