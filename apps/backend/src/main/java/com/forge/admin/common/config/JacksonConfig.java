package com.forge.admin.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.forge.admin.common.json.LongSerializer;
import com.forge.admin.common.json.TimestampLocalDateTimeDeserializer;
import com.forge.admin.common.json.TimestampLocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Jackson 配置
 * 参考 shi9-boot 配置，解决 JSON 序列化问题
 */
@Slf4j
@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonConfig {

    /**
     * 创建自定义的 ObjectMapper，覆盖默认配置
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // 创建 SimpleModule 对象
        SimpleModule simpleModule = new SimpleModule();
        simpleModule
                // Long 类型序列化规则，数值超过 2^53-1，在 JS 会出现精度丢失问题，因此 Long 自动序列化为字符串类型
                .addSerializer(Long.class, LongSerializer.INSTANCE)
                .addSerializer(Long.TYPE, LongSerializer.INSTANCE)
                // LocalDate 序列化/反序列化
                .addSerializer(LocalDate.class, LocalDateSerializer.INSTANCE)
                .addDeserializer(LocalDate.class, LocalDateDeserializer.INSTANCE)
                // LocalTime 序列化/反序列化
                .addSerializer(LocalTime.class, LocalTimeSerializer.INSTANCE)
                .addDeserializer(LocalTime.class, LocalTimeDeserializer.INSTANCE)
                // LocalDateTime 序列化/反序列化，使用秒级时间戳
                .addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE)
                .addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);

        // 创建 ObjectMapper 并注册模块
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(simpleModule);

        // 其他配置
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        log.info("[Jackson] ObjectMapper 初始化成功，已注册自定义序列化器");
        return objectMapper;
    }

    /**
     * 初始化其他 ObjectMapper 实例（如 Redis 使用的）
     */
    @Bean
    public Object jacksonInitializer(List<ObjectMapper> objectMappers) {
        // 创建 SimpleModule 对象
        SimpleModule simpleModule = new SimpleModule();
        simpleModule
                .addSerializer(Long.class, LongSerializer.INSTANCE)
                .addSerializer(Long.TYPE, LongSerializer.INSTANCE)
                .addSerializer(LocalDate.class, LocalDateSerializer.INSTANCE)
                .addDeserializer(LocalDate.class, LocalDateDeserializer.INSTANCE)
                .addSerializer(LocalTime.class, LocalTimeSerializer.INSTANCE)
                .addDeserializer(LocalTime.class, LocalTimeDeserializer.INSTANCE)
                .addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE)
                .addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);

        // 注册到所有 ObjectMapper（排除已注册的）
        int count = 0;
        for (ObjectMapper objectMapper : objectMappers) {
            if (!hasModule(objectMapper, simpleModule)) {
                objectMapper.registerModule(simpleModule);
                count++;
            }
        }

        log.info("[Jackson] 初始化成功，已注册自定义序列化器到 {} 个 ObjectMapper", count);
        return new Object();
    }

    private boolean hasModule(ObjectMapper objectMapper, SimpleModule module) {
        return objectMapper.getRegisteredModuleIds().contains(module.getTypeId());
    }

}
