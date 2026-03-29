package com.forge.admin.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;

import java.io.IOException;

/**
 * Long 类型序列化器
 * 解决 Long 类型在 JS 中精度丢失的问题
 * 将 Long 自动序列化为字符串类型
 */
@JacksonStdImpl
public class LongSerializer extends JsonSerializer<Long> {

    public static final LongSerializer INSTANCE = new LongSerializer();

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.toString());
        }
    }
}

