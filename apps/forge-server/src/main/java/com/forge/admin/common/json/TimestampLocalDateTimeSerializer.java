package com.forge.admin.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * LocalDateTime 时间戳序列化器
 * 使用时间戳格式序列化 LocalDateTime
 */
@JacksonStdImpl
public class TimestampLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

    public static final TimestampLocalDateTimeSerializer INSTANCE = new TimestampLocalDateTimeSerializer();

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // 使用时间戳格式序列化（秒）
        long timestamp = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000;
        gen.writeNumber(timestamp);
    }
}
