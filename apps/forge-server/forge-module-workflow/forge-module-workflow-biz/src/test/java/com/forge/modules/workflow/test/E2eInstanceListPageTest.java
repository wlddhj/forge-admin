package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.mapper.WfInstanceQueryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 端到端：queryAllInstances 的 UNION ALL SQL 分页行为
 */
class E2eInstanceListPageTest extends WorkflowIntegrationTestBase {

    @Autowired
    private WfInstanceQueryMapper instanceQueryMapper;

    @Test
    @DisplayName("UNION 分页：页大小生效且行含运行中/历史两侧字段")
    void unionPage_pagingWorks() {
        // 制造 1 个运行中实例 + 1 个已结束实例
        String model = """
                {"name":"分页流程","key":"e2e_page",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long processId = deploy(model);

        // 运行中
        flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"), (java.util.Map<String, Object>) null);
        // 结束（或签一人通过即完）
        var finished = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"), (java.util.Map<String, Object>) null).orElseThrow();
        FlwTask task = activeTasks(finished.getId()).get(0);
        flowLongEngine.executeTask(task.getId(), user(2L, "审批人乙"), null);

        Page<Map<String, Object>> page1 = new Page<>(1, 1);
        List<Map<String, Object>> rows1 = instanceQueryMapper.selectUnionPage(page1, null, null, null, null, null, null, false);
        assertEquals(1, rows1.size(), "页大小 1 只返回 1 行");
        assertTrue(page1.getTotal() >= 2, "总数应含运行中+历史两侧");

        Page<Map<String, Object>> page2 = new Page<>(2, 1);
        List<Map<String, Object>> rows2 = instanceQueryMapper.selectUnionPage(page2, null, null, null, null, null, null, false);
        assertEquals(1, rows2.size(), "第二页仍有数据");
        assertNotEquals(rows1.get(0).get("id"), rows2.get(0).get("id"), "两页不重复");

        // 筛选下推：发起人过滤
        Page<Map<String, Object>> filtered = new Page<>(1, 10);
        List<Map<String, Object>> byUser = instanceQueryMapper.selectUnionPage(filtered, "1", null, null, null, null, null, false);
        assertEquals(filtered.getTotal(), page1.getTotal(), "发起人=1 覆盖全部种子实例");
        assertTrue(byUser.stream().allMatch(r -> "1".equals(r.get("create_id"))));
    }
}
