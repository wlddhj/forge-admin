package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.mapper.WfInstanceQueryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开租户 E2E：UNION 分页（运行中+历史）在 TenantLine 插件（链首）下
 * count 与记录数一致——验证租户改写先于分页的顺序正确性。
 */
class TenantOnUnionPageTest extends TenantEnabledTestBase {

    @Autowired
    private WfInstanceQueryMapper instanceQueryMapper;

    @Test
    @DisplayName("开租户下 UNION 分页 total 与行数一致（TenantLine 先于 Pagination）")
    void unionPageTotalShouldMatchRecordsUnderTenant() {
        // 租户 1（Base @BeforeEach）下造运行中 + 已结束实例各一
        String model = """
                {"name":"开租户分页流程","key":"tenant_page",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long processId = deploy(model);
        flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"), (Map<String, Object>) null);
        var finished = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (Map<String, Object>) null).orElseThrow();
        FlwTask task = activeTasks(finished.getId()).get(0);
        flowLongEngine.executeTask(task.getId(), user(2L, "审批人乙"), null);

        Page<Map<String, Object>> page = new Page<>(1, 10);
        List<Map<String, Object>> rows = instanceQueryMapper.selectUnionPage(
                page, null, null, null, null, null, null, false);

        assertTrue(page.getTotal() >= 2, "运行中+历史两侧均在租户 1 计数内");
        assertEquals(rows.size(), (int) Math.min(page.getTotal(), 10),
                "total 与本页行数口径一致（租户改写先于分页 count）");
    }
}
