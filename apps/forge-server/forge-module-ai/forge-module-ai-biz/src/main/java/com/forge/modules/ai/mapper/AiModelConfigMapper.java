package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.ai.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    @Select("SELECT * FROM ai_model_config WHERE model_name = #{modelName} AND deleted = 0")
    AiModelConfig selectByModelName(String modelName);
}