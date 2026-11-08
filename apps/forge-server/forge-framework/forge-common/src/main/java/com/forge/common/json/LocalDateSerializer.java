package com.forge.common.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JacksonStdImpl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * LocalDate 序列化器
 */
@JacksonStdImpl
public class LocalDateSerializer extends ValueSerializer<LocalDate> {

    public static final LocalDateSerializer INSTANCE = new LocalDateSerializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void serialize(LocalDate value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
        } else {
            String formatted = value.format(FORMATTER);
            gen.writeString(formatted);
        }
    }
}
