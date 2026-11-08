package com.forge.common.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JacksonStdImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * LocalDateTime 时间戳序列化器
 * 使用时间戳格式序列化 LocalDateTime
 */
@JacksonStdImpl
public class TimestampLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

    public static final TimestampLocalDateTimeSerializer INSTANCE = new TimestampLocalDateTimeSerializer();

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext serializers) {
        // 使用时间戳格式序列化（秒）
        long timestamp = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000;
        gen.writeNumber(timestamp);
    }
}
