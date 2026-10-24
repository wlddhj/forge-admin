package com.forge.modules.ai.mapper;

import com.forge.modules.ai.entity.AiApiUsage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface AiApiUsageMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<AiApiUsage> {

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

    /**
     * 汇总统计:请求/成败/Token 合计与总响应时长(avg 由调用侧除以总数求得)
     */
    @Select("<script>"
            + "SELECT COALESCE(SUM(request_count),0) AS totalRequest, "
            + "COALESCE(SUM(success_count),0) AS successCount, "
            + "COALESCE(SUM(failure_count),0) AS failureCount, "
            + "COALESCE(SUM(total_input_tokens),0) AS totalInputTokens, "
            + "COALESCE(SUM(total_output_tokens),0) AS totalOutputTokens, "
            + "COALESCE(SUM(avg_response_time * request_count),0) AS avgResponseMs "
            + "FROM ai_api_usage "
            + "WHERE 1=1 "
            + "<if test='userId != null'>AND user_id = #{userId} </if>"
            + "<if test='modelId != null'>AND model_id = #{modelId} </if>"
            + "<if test='startDate != null'>AND usage_date &gt;= #{startDate} </if>"
            + "<if test='endDate != null'>AND usage_date &lt;= #{endDate} </if>"
            + "</script>")
    Optional<Map<String, Object>> selectSummary(@Param("userId") Long userId,
                                                @Param("modelId") Long modelId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
}
