package com.forge.common.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JacksonStdImpl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalTime 序列化器
 */
@JacksonStdImpl
public class LocalTimeSerializer extends ValueSerializer<LocalTime> {

    public static final LocalTimeSerializer INSTANCE = new LocalTimeSerializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void serialize(LocalTime value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
        } else {
            String formatted = value.format(FORMATTER);
            gen.writeString(formatted);
        }
    }
}
