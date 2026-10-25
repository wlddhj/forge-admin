package com.forge.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.workflow.entity.WfEventAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流事件审计 Mapper
 *
 * @author forge-admin
 */
@Mapper
public interface WfEventAuditMapper extends BaseMapper<WfEventAudit> {
}
