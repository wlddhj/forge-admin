package com.forge.modules.ai.agent.lifecycle;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    int BATCH_LIMIT = 500;

    // ---------- 分批选 id（每批 ≤500，含保护条件） ----------

    @Select("SELECT id FROM ai_agent_run WHERE status IN ('SUCCEEDED','FAILED','CANCELLED') " +
            "AND create_time < #{cutoff} LIMIT " + BATCH_LIMIT)
    List<Long> selectExpiredRunIds(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT id FROM ai_agent_run_step WHERE create_time < #{cutoff} LIMIT " + BATCH_LIMIT)
    List<Long> selectExpiredStepIds(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT s.id FROM ai_agent_run_step s LEFT JOIN ai_agent_run r ON s.run_id = r.id " +
            "WHERE r.id IS NULL LIMIT " + BATCH_LIMIT)
    List<Long> selectOrphanStepIds();

    @Select("SELECT id FROM ai_agent_todo WHERE status <> 'PENDING' AND create_time < #{cutoff} LIMIT " + BATCH_LIMIT)
    List<Long> selectExpiredTodoIds(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT t.id FROM ai_agent_todo t LEFT JOIN ai_agent_run r ON t.run_id = r.id " +
            "WHERE r.id IS NULL LIMIT " + BATCH_LIMIT)
    List<Long> selectOrphanTodoIds();

    @Select("SELECT id FROM ai_agent_checkpoint WHERE create_time < #{cutoff} LIMIT " + BATCH_LIMIT)
    List<Long> selectExpiredCheckpointIds(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT c.id FROM ai_agent_checkpoint c LEFT JOIN ai_agent_run r ON c.run_id = r.id " +
            "WHERE r.id IS NULL LIMIT " + BATCH_LIMIT)
    List<Long> selectOrphanCheckpointIds();

    // ---------- 物理删除 ----------

    @Delete("<script>DELETE FROM ai_agent_run WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteRunByIds(@Param("ids") List<Long> ids);

    @Delete("<script>DELETE FROM ai_agent_run_step WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteStepByIds(@Param("ids") List<Long> ids);

    @Delete("<script>DELETE FROM ai_agent_todo WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteTodoByIds(@Param("ids") List<Long> ids);

    @Delete("<script>DELETE FROM ai_agent_checkpoint WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteCheckpointByIds(@Param("ids") List<Long> ids);
}
