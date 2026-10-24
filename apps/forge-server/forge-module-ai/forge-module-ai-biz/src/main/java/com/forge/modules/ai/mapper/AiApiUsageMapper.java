package com.forge.modules.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface AiApiUsageMapper {

    /**
     * 用量累加 upsert（uk_user_model_date 命即累加）。
     * avg_response_time 增量加权：SET 左到右求值，先于 request_count 自增引用旧值。
     * 租户模式下 tenant_id 由 TenantLineInnerInterceptor 注入（表已含列）。
     */
    @Insert("INSERT INTO ai_api_usage (user_id, model_id, usage_date, request_count, success_count, failure_count, "
            + "total_input_tokens, total_output_tokens, avg_response_time) VALUES "
            + "(#{userId}, #{modelId}, #{usageDate}, 1, #{success}, #{failure}, #{tokensIn}, #{tokensOut}, #{responseTimeMs}) "
            + "ON DUPLICATE KEY UPDATE "
            + "avg_response_time = (avg_response_time * request_count + VALUES(avg_response_time)) / (request_count + 1), "
            + "request_count = request_count + 1, "
            + "success_count = success_count + VALUES(success_count), "
            + "failure_count = failure_count + VALUES(failure_count), "
            + "total_input_tokens = total_input_tokens + VALUES(total_input_tokens), "
            + "total_output_tokens = total_output_tokens + VALUES(total_output_tokens)")
    int upsertIncrement(@Param("userId") long userId,
                        @Param("modelId") long modelId,
                        @Param("usageDate") LocalDate usageDate,
                        @Param("success") int success,
                        @Param("failure") int failure,
                        @Param("tokensIn") int tokensIn,
                        @Param("tokensOut") int tokensOut,
                        @Param("responseTimeMs") int responseTimeMs);
}
