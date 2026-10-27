package com.forge.modules.workflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 流程实例合并查询 Mapper（运行中 + 历史 UNION ALL，SQL 分页）
 *
 * 替代 queryAllInstances 的"两侧全量捞取 + 内存排序 + 内存分页"实现。
 *
 * @author forge-admin
 */
@Mapper
public interface WfInstanceQueryMapper {

    /**
     * 合并查询全部实例（运行中 state 视为 0，历史侧带真实 instance_state/end_time）
     *
     * 筛选条件下推到两侧子查询；排序：priority 优先时先 priority desc 再 create_time desc，否则仅 create_time desc。
     */
    @Select("""
            <script>
            SELECT * FROM (
              SELECT i.id, i.tenant_id, i.create_id, i.create_by, i.create_time, i.process_id,
                     i.priority, i.urgent, i.instance_no, i.business_key, i.current_node_name, i.current_node_key,
                     0 AS instance_state, NULL AS end_time
              FROM flw_instance i
              <if test="processName != null and processName != ''">
                JOIN flw_process p ON p.id = i.process_id
              </if>
              WHERE 1=1
              <if test="startUserId != null"> AND i.create_id = #{startUserId} </if>
              <if test="processDefinitionId != null"> AND i.process_id = #{processDefinitionId} </if>
              <if test="processName != null and processName != ''"> AND p.process_name LIKE CONCAT('%', #{processName}, '%') </if>
              <if test="businessKey != null and businessKey != ''"> AND i.business_key = #{businessKey} </if>
              <if test="priority != null"> AND i.priority = #{priority} </if>
              UNION ALL
              SELECT h.id, h.tenant_id, h.create_id, h.create_by, h.create_time, h.process_id,
                     h.priority, h.urgent, h.instance_no, h.business_key, h.current_node_name, h.current_node_key,
                     h.instance_state, h.end_time
              FROM flw_his_instance h
              <if test="processName != null and processName != ''">
                JOIN flw_process p2 ON p2.id = h.process_id
              </if>
              WHERE h.instance_state &gt; 0
              <if test="startUserId != null"> AND h.create_id = #{startUserId} </if>
              <if test="processDefinitionId != null"> AND h.process_id = #{processDefinitionId} </if>
              <if test="processName != null and processName != ''"> AND p2.process_name LIKE CONCAT('%', #{processName}, '%') </if>
              <if test="businessKey != null and businessKey != ''"> AND h.business_key = #{businessKey} </if>
              <if test="priority != null"> AND h.priority = #{priority} </if>
            ) merged
            <choose>
              <when test="sortByPriority">
                ORDER BY merged.priority DESC, merged.create_time DESC
              </when>
              <otherwise>
                ORDER BY merged.create_time DESC
              </otherwise>
            </choose>
            </script>
            """)
    List<Map<String, Object>> selectUnionPage(Page<Map<String, Object>> page,
                                               @Param("startUserId") String startUserId,
                                               @Param("processDefinitionId") Long processDefinitionId,
                                               @Param("processName") String processName,
                                               @Param("businessKey") String businessKey,
                                               @Param("priority") Integer priority,
                                               @Param("sortByPriority") boolean sortByPriority);
}
