package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.ai.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    /**
     * 仅用于重名校验（读取 id/名称即可）：@Select 自动映射不走 @TableField typeHandler，
     * 返回实体的 apiKey 为库中原始值（可能是密文），不得用于回写或模型构建
     */
    @Select("SELECT * FROM ai_model_config WHERE model_name = #{modelName} AND deleted = 0")
    AiModelConfig selectByModelName(String modelName);

    @Select("SELECT * FROM ai_model_config WHERE model_code = #{modelCode} AND deleted = 0")
    AiModelConfig selectByModelCode(String modelCode);

    /**
     * 原始库值（不走解密），供存量加密迁移判断 ENCv1 前缀
     */
    @Select("SELECT id, api_key FROM ai_model_config WHERE api_key IS NOT NULL AND api_key <> ''")
    List<Map<String, Object>> selectRawApiKeys();
}