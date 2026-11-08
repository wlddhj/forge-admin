package com.forge.framework.web.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forge.common.json.DateDeserializer;
import com.forge.common.json.DateSerializer;
import com.forge.common.json.LocalDateDeserializer;
import com.forge.common.json.LocalDateSerializer;
import com.forge.common.json.LocalTimeDeserializer;
import com.forge.common.json.LocalTimeSerializer;
import com.forge.common.json.LongSerializer;
import com.forge.common.json.TimestampLocalDateTimeDeserializer;
import com.forge.common.json.TimestampLocalDateTimeSerializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

/**
 * Jackson 配置（Jackson 3 / Boot 4）
 *
 * <p>Boot 4 自动装配 tools.jackson.databind.json.JsonMapper，本类经 JsonMapperBuilderCustomizer
 * 在其上追加项目序列化规则；Redis 缓存用的独立 Mapper 见 CacheConfig。
 */
@Configuration
public class JacksonConfig {

    /**
     * 项目统一序列化模块：
     * Long 超过 2^53-1 在 JS 精度丢失 → 序列化为字符串；
     * LocalDate/LocalTime 字符串格式化；LocalDateTime 秒级时间戳；Date 毫秒时间戳
     */
    static SimpleModule forgeSerializersModule() {
        SimpleModule simpleModule = new SimpleModule();
        simpleModule
                .addSerializer(Long.class, LongSerializer.INSTANCE)
                .addSerializer(Long.TYPE, LongSerializer.INSTANCE)
                .addSerializer(LocalDate.class, LocalDateSerializer.INSTANCE)
                .addDeserializer(LocalDate.class, LocalDateDeserializer.INSTANCE)
                .addSerializer(LocalTime.class, LocalTimeSerializer.INSTANCE)
                .addDeserializer(LocalTime.class, LocalTimeDeserializer.INSTANCE)
                .addSerializer(Date.class, DateSerializer.INSTANCE)
                .addDeserializer(Date.class, DateDeserializer.INSTANCE)
                .addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE)
                .addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);
        return simpleModule;
    }

    /**
     * 在自动装配的 JsonMapper 上追加项目序列化规则与容错配置
     */
    @Bean
    public JsonMapperBuilderCustomizer forgeJacksonCustomizer() {
        return builder -> builder
                .addModule(forgeSerializersModule())
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .disable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL));
    }
}
