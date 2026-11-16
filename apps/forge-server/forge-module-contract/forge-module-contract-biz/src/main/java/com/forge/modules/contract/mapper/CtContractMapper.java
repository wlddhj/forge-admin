package com.forge.modules.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.contract.entity.CtContract;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同记录 Mapper
 *
 * @author forge-admin
 */
@Mapper
public interface CtContractMapper extends BaseMapper<CtContract> {
}
