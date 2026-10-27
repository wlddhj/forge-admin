package com.forge.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务回调投递记录Mapper
 *
 * @author forge-admin
 */
@Mapper
public interface WfCallbackRecordMapper extends BaseMapper<WfCallbackRecord> {
}
