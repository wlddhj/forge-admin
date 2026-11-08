package com.forge.common.json;

import cn.hutool.core.date.DateUtil;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JacksonStdImpl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * LocalDate 反序列化器
 */
@JacksonStdImpl
public class DateDeserializer extends ValueDeserializer<Date> {

    public static final DateDeserializer INSTANCE = new DateDeserializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext context) {
        String dateStr = parser.getValueAsString();
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        return DateUtil.parse(dateStr, FORMATTER);
    }
}
