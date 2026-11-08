package com.forge.common.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JacksonStdImpl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * LocalDate 反序列化器
 */
@JacksonStdImpl
public class LocalDateDeserializer extends ValueDeserializer<LocalDate> {

    public static final LocalDateDeserializer INSTANCE = new LocalDateDeserializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
        String dateStr = parser.getValueAsString();
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateStr, FORMATTER);
    }
}
