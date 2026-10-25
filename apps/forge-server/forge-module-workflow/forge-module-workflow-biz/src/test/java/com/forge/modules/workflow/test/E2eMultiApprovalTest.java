package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.core.enums.PerformType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 端到端：或签 / 会签 / 票签 三种多人审批方式
 */
class E2eMultiApprovalTest extends WorkflowIntegrationTestBase {

    private String modelJson(int examineMode, Integer passWeight, String weights) {
        // weights 形如 [{"id":"2","name":"审批人乙","weight":60}] —— 仅票签带 weight
        return """
                {"name":"多人审批流程","key":"e2e_multi",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":%d%s%s,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"%s},{"id":"3","name":"审批人丙"%s}]
                   }}}
                """.formatted(examineMode,
                passWeight != null ? ",\"passWeight\":" + passWeight : "",
                "",
                weights != null ? ",\"weight\":" + weights.split(",")[0] : "",
                weights != null ? ",\"weight\":" + weights.split(",")[1] : "");
    }

    @Test
    @DisplayName("或签：一人通过即推进结束")
    void orSign_oneApprovalCompletes() {
        Long processId = deploy(modelJson(3, null, null));
        FlwHisInstance his = startAndApprove(processId, "2");
        assertEquals(InstanceState.complete.getValue(), his.getInstanceState(), "或签一人通过流程应完成");
    }

    @Test
    @DisplayName("会签：首人通过后仍活动，全员通过才结束")
    void countersign_allMustApprove() {
        Long processId = deploy(modelJson(2, null, null));
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"), (java.util.Map<String, Object>) null).orElseThrow();
        Long instanceId = instance.getId();

        List<FlwTask> tasks = activeTasks(instanceId);
        assertEquals(2, tasks.size(), "会签应生成两条任务（每人一条）");

        FlwTask first = tasks.get(0);
        String firstApprover = actorsOf(first.getId()).get(0).getActorId();
        flowLongEngine.executeTask(first.getId(), user(Long.parseLong(firstApprover), userName(Long.parseLong(firstApprover))), null);
        assertNotNull(queryService.getInstance(instanceId), "会签首人通过后流程仍在运行");

        FlwTask second = activeTasks(instanceId).get(0);
        String secondApprover = actorsOf(second.getId()).get(0).getActorId();
        flowLongEngine.executeTask(second.getId(), user(Long.parseLong(secondApprover), userName(Long.parseLong(secondApprover))), null);

        FlwHisInstance his = queryService.getHistInstance(instanceId);
        assertEquals(InstanceState.complete.getValue(), his.getInstanceState(), "会签全员通过流程完成");
    }

    @Test
    @DisplayName("票签：权重 60 达到门槛 50 即通过并关闭其余投票")
    void voteSign_weightThreshold() {
        Long processId = deploy(modelJson(4, 50, "60,40"));
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"), (java.util.Map<String, Object>) null).orElseThrow();
        Long instanceId = instance.getId();

        List<FlwTask> tasks = activeTasks(instanceId);
        assertEquals(2, tasks.size(), "票签每人一条任务");

        // 权重 60 的用户（id=2）通过 → 已投 60 ≥ 门槛 50 → 直接结束并关闭另一票
        FlwTask weighted = tasks.stream()
                .filter(t -> actorsOf(t.getId()).stream().anyMatch(a -> "2".equals(a.getActorId())))
                .findFirst().orElseThrow();
        flowLongEngine.executeTask(weighted.getId(), user(2L, "审批人乙"), null);

        FlwHisInstance his = queryService.getHistInstance(instanceId);
        assertEquals(InstanceState.complete.getValue(), his.getInstanceState(), "权重达标即通过");
        assertTrue(activeTasks(instanceId).isEmpty(), "其余投票任务被关闭");
    }

    /** 发起并由指定用户审批通过（单任务场景） */
    private FlwHisInstance startAndApprove(Long processId, String approverId) {
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"), (java.util.Map<String, Object>) null).orElseThrow();
        FlwTask task = activeTasks(instance.getId()).get(0);
        flowLongEngine.executeTask(task.getId(), user(Long.parseLong(approverId), userName(Long.parseLong(approverId))), null);
        return queryService.getHistInstance(instance.getId());
    }
}
