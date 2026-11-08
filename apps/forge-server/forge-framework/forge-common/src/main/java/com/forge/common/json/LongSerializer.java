package com.forge.common.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JacksonStdImpl;


/**
 * Long 类型序列化器
 * 解决 Long 类型在 JS 中精度丢失的问题
 * 将 Long 自动序列化为字符串类型
 */
@JacksonStdImpl
public class LongSerializer extends ValueSerializer<Long> {

    public static final LongSerializer INSTANCE = new LongSerializer();

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.toString());
        }
    }
}

