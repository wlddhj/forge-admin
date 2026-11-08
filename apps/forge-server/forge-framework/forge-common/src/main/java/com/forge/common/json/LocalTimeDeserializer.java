package com.forge.common.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JacksonStdImpl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalTime 反序列化器
 */
@JacksonStdImpl
public class LocalTimeDeserializer extends ValueDeserializer<LocalTime> {

    public static final LocalTimeDeserializer INSTANCE = new LocalTimeDeserializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) {
        String timeStr = parser.getValueAsString();
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        return LocalTime.parse(timeStr, FORMATTER);
    }
}
