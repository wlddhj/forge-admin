package com.forge.modules.system.mapper;

import com.forge.modules.system.entity.KeySequence;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KeySequenceMapper extends BaseMapper<KeySequence> {

    default KeySequence findByKeyCategory(String keyCategory) {
        return selectOne(new LambdaQueryWrapper<KeySequence>()
                .eq(KeySequence::getKeyCategory, keyCategory));
    }
}
