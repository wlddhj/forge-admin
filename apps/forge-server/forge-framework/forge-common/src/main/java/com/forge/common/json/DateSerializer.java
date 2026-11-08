package com.forge.common.json;

import cn.hutool.core.date.DateUtil;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JacksonStdImpl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * LocalDate 序列化器
 */
@JacksonStdImpl
public class DateSerializer extends ValueSerializer<Date> {

    public static final DateSerializer INSTANCE = new DateSerializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void serialize(Date value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
        } else {
//            String formatted = DateUtil.format(value, FORMATTER);
//            gen.writeString(formatted);
            gen.writeNumber(value.getTime());
        }
    }
}
