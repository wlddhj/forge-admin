package com.forge.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.workflow.entity.WfCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程分类 Mapper
 *
 * @author forge
 */
@Mapper
public interface WfCategoryMapper extends BaseMapper<WfCategory> {
}
