package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 端到端：驳回策略（引擎层分派）与拿回
 */
class E2eRejectReclaimTest extends WorkflowIntegrationTestBase {

    /** 发起人→审批A(乙)→审批B(丙) 模型，B 节点可配驳回策略 */
    private String model(Integer rejectStrategy) {
        return """
                {"name":"驳回流程","key":"e2e_reject",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批A","nodeKey":"ap_a","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}],
                     "childNode":{"nodeName":"审批B","nodeKey":"ap_b","type":1,"setType":1,"examineMode":3%s,
                       "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}}}}
                """.formatted(rejectStrategy != null ? ",\"rejectStrategy\":" + rejectStrategy : "");
    }

    @Test
    @DisplayName("默认驳回（未配置策略）：B 驳回后流程终结为拒绝态")
    void rejectDefault_terminates() {
        Long processId = deploy(model(null));
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();
        FlwTask taskA = activeTasks(instance.getId()).get(0);
        flowLongEngine.executeTask(taskA.getId(), user(2L, "审批人乙"), null);

        FlwTask taskB = activeTasks(instance.getId()).get(0);
        flowLongEngine.executeRejectTask(taskB, null, user(3L, "审批人丙"), null, true);

        FlwHisInstance his = queryService.getHistInstance(instance.getId());
        assertEquals(InstanceState.reject.getValue(), his.getInstanceState(), "termination=true 应终结流程");
    }

    @Test
    @DisplayName("策略2（上一节点）：B 驳回后回到审批A")
    void rejectToPrevious_returnsToA() {
        Long processId = deploy(model(2));
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();
        FlwTask taskA = activeTasks(instance.getId()).get(0);
        flowLongEngine.executeTask(taskA.getId(), user(2L, "审批人乙"), null);

        FlwTask taskB = activeTasks(instance.getId()).get(0);
        // 引擎层策略分派：termination=false 时按节点 rejectStrategy=2 走上一节点
        flowLongEngine.executeRejectTask(taskB, null, user(3L, "审批人丙"), null, false);

        assertNotNull(queryService.getInstance(instance.getId()), "策略2 驳回后流程仍在运行");
        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size());
        assertEquals("审批A", tasks.get(0).getTaskName(), "应回到上一审批节点");
        assertEquals("2", actorsOf(tasks.get(0).getId()).get(0).getActorId());
    }

    @Test
    @DisplayName("策略1（发起人）：B 驳回后发起人待办重现")
    void rejectToInitiator_backToStart() {
        Long processId = deploy(model(1));
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();
        FlwTask taskA = activeTasks(instance.getId()).get(0);
        flowLongEngine.executeTask(taskA.getId(), user(2L, "审批人乙"), null);

        FlwTask taskB = activeTasks(instance.getId()).get(0);
        flowLongEngine.executeRejectTask(taskB, null, user(3L, "审批人丙"), null, false);

        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size());
        assertEquals("发起人", tasks.get(0).getTaskName(), "策略1 应回到发起人重新提交");
        assertEquals("1", actorsOf(tasks.get(0).getId()).get(0).getActorId());
    }

    @Test
    @DisplayName("拿回：A 提交后 B 未办理，A 拿回成功；B 的待办消失")
    void reclaim_afterSubmitBeforeNextApproval() {
        Long processId = deploy(model(null));
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();
        FlwTask taskA = activeTasks(instance.getId()).get(0);
        Long taskAId = taskA.getId();
        flowLongEngine.executeTask(taskAId, user(2L, "审批人乙"), null);

        // B 的任务存在且未办
        FlwTask taskB = activeTasks(instance.getId()).get(0);
        assertEquals("审批B", taskB.getTaskName());

        // A 的办理人（乙）拿回
        flowLongEngine.taskService().reclaimTask(taskAId, user(2L, "审批人乙"), null);

        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size(), "拿回后 B 待办消失、A 重新出现");
        assertEquals("审批A", tasks.get(0).getTaskName());
    }
}
