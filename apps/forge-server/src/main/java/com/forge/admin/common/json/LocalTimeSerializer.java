package com.forge.admin.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalTime 序列化器
 */
@JacksonStdImpl
public class LocalTimeSerializer extends JsonSerializer<LocalTime> {

    public static final LocalTimeSerializer INSTANCE = new LocalTimeSerializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void serialize(LocalTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            String formatted = value.format(FORMATTER);
            gen.writeString(formatted);
        }
    }
}
