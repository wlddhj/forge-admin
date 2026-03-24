package com.forge.admin.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalTime 反序列化器
 */
@JacksonStdImpl
public class LocalTimeDeserializer extends JsonDeserializer<LocalTime> {

    public static final LocalTimeDeserializer INSTANCE = new LocalTimeDeserializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String timeStr = parser.getValueAsString();
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        return LocalTime.parse(timeStr, FORMATTER);
    }
}
