package com.forge.modules.ai.mapper;

import com.forge.modules.ai.entity.AiApiUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Map;

@Mapper
public interface AiApiUsageMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<AiApiUsage> {

    /**
     * 用量累加 upsert（uk_user_model_date 命中即累加，否则插入当天首条）。
     * 租户模式下 tenant_id 由 TenantLineInnerInterceptor 注入（表已含列）。
     */
    int upsertIncrement(@Param("userId") long userId,
                        @Param("modelId") long modelId,
                        @Param("usageDate") LocalDate usageDate,
                        @Param("success") int success,
                        @Param("failure") int failure,
                        @Param("tokensIn") int tokensIn,
                        @Param("tokensOut") int tokensOut,
                        @Param("responseTimeMs") int responseTimeMs);

    /**
     * 汇总统计:请求/成败/Token 合计与总响应时长(avg 由调用侧除以总数求得);返回 Map(MyBatis 不支持 Optional 结果映射)
     */
    Map<String, Object> selectSummary(@Param("userId") Long userId,
                                                @Param("modelId") Long modelId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
}
