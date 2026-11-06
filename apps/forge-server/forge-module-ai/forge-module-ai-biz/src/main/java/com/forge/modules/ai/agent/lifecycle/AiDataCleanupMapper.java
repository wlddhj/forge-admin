package com.forge.modules.ai.agent.lifecycle;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 运行数据生命周期清理 Mapper。
 *
 * 全部为物理删除（生命周期清理目的即释放空间）：run/todo 实体带 @TableLogic，
 * 不可用 MP 的 deleteByIds（那是逻辑删除），故自定义 DELETE。
 * run/todo 的选 id 不带 deleted 条件——逻辑删除的记录同样超期物理清理。
 */
@Mapper
public interface AiDataCleanupMapper {

    /** 每批选 id 上限；XML 经 OGNL 按名引用本常量（改名须同步 AiDataCleanupMapper.xml，AiMapperXmlGuardTest 守护） */
    int BATCH_LIMIT = 500;

    // ---------- 分批选 id（每批 ≤ BATCH_LIMIT，含保护条件） ----------

    List<Long> selectExpiredRunIds(@Param("cutoff") LocalDateTime cutoff);

    List<Long> selectExpiredStepIds(@Param("cutoff") LocalDateTime cutoff);

    List<Long> selectOrphanStepIds();

    List<Long> selectExpiredTodoIds(@Param("cutoff") LocalDateTime cutoff);

    List<Long> selectOrphanTodoIds();

    List<Long> selectExpiredCheckpointIds(@Param("cutoff") LocalDateTime cutoff);

    List<Long> selectOrphanCheckpointIds();

    // ---------- 对话数据（ai_chat_conversation / ai_chat_message，物理删，会话 deleted 列一并无视） ----------

    List<Long> selectExpiredConversationIds(@Param("cutoff") LocalDateTime cutoff);

    List<Long> selectExpiredMessageIds(@Param("cutoff") LocalDateTime cutoff);

    List<Long> selectOrphanMessageIds();

    // ---------- 物理删除 ----------

    int deleteRunByIds(@Param("ids") List<Long> ids);

    int deleteStepByIds(@Param("ids") List<Long> ids);

    int deleteTodoByIds(@Param("ids") List<Long> ids);

    int deleteCheckpointByIds(@Param("ids") List<Long> ids);

    int deleteConversationByIds(@Param("ids") List<Long> ids);

    int deleteMessageByIds(@Param("ids") List<Long> ids);
}
