package com.forge.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.workflow.entity.WfAiApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI审批记录Mapper
 *
 * @author forge-admin
 */
@Mapper
public interface WfAiApprovalRecordMapper extends BaseMapper<WfAiApprovalRecord> {
}