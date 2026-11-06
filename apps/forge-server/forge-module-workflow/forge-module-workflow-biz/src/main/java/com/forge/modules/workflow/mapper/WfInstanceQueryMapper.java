package com.forge.modules.workflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
    List<Map<String, Object>> selectUnionPage(Page<Map<String, Object>> page,
                                               @Param("startUserId") String startUserId,
                                               @Param("processDefinitionId") Long processDefinitionId,
                                               @Param("processName") String processName,
                                               @Param("businessKey") String businessKey,
                                               @Param("priority") Integer priority,
                                               @Param("title") String title,
                                               @Param("sortByPriority") boolean sortByPriority);
}
