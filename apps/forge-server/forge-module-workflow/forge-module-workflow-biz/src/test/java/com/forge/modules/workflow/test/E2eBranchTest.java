package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 端到端：条件分支 / 并行分支 / 子流程
 */
class E2eBranchTest extends WorkflowIntegrationTestBase {

    @Test
    @DisplayName("条件分支：条件命中走对应分支")
    void conditionBranch_routesByCondition() {
        String model = """
                {"name":"条件分支流程","key":"e2e_cond",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"条件路由","nodeKey":"cb1","type":4,
                     "conditionNodes":[
                       {"nodeName":"大于100","nodeKey":"c1","type":3,"priorityLevel":1,
                        "conditionList":[[{"label":"金额","field":"amount","operator":">","value":"100"}]],
                        "childNode":{"nodeName":"高额审批","nodeKey":"ap_high","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}},
                       {"nodeName":"其他","nodeKey":"c2","type":3,"priorityLevel":2,"conditionList":[],
                        "childNode":{"nodeName":"普通审批","nodeKey":"ap_low","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}
                     ]}}}
                """;
        Long processId = deploy(model);

        // amount=500 → 走高额审批（审批人丙）
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                java.util.Map.of("amount", 500)).orElseThrow();
        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size());
        assertEquals("高额审批", tasks.get(0).getTaskName());
        assertEquals("3", actorsOf(tasks.get(0).getId()).get(0).getActorId());

        flowLongEngine.executeTask(tasks.get(0).getId(), user(3L, "审批人丙"), null);
        assertEquals(InstanceState.complete.getValue(), queryService.getHistInstance(instance.getId()).getInstanceState());
    }

    @Test
    @DisplayName("并行分支：两分支同时生成任务，全完成才汇聚结束")
    void parallelBranch_concurrentTasks() {
        String model = """
                {"name":"并行分支流程","key":"e2e_par",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"并行分支","nodeKey":"pb1","type":8,
                     "parallelNodes":[
                       {"nodeName":"分支1","nodeKey":"p1","type":3,"priorityLevel":1,"conditionList":[],
                        "childNode":{"nodeName":"审批A","nodeKey":"ap_a","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}},
                       {"nodeName":"分支2","nodeKey":"p2","type":3,"priorityLevel":2,"conditionList":[],
                        "childNode":{"nodeName":"审批B","nodeKey":"ap_b","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}}
                     ]}}}
                """;
        Long processId = deploy(model);
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();
        Long instanceId = instance.getId();

        List<FlwTask> tasks = activeTasks(instanceId);
        assertEquals(2, tasks.size(), "并行分支应同时生成两条任务");

        // 审批A 通过 → 审批B 仍在
        FlwTask taskA = tasks.stream().filter(t -> "审批A".equals(t.getTaskName())).findFirst().orElseThrow();
        flowLongEngine.executeTask(taskA.getId(), user(2L, "审批人乙"), null);
        assertEquals(1, activeTasks(instanceId).size(), "另一分支任务仍在等待");
        assertNotNull(queryService.getInstance(instanceId), "并行未全部完成流程不结束");

        FlwTask taskB = activeTasks(instanceId).get(0);
        flowLongEngine.executeTask(taskB.getId(), user(3L, "审批人丙"), null);
        assertEquals(InstanceState.complete.getValue(), queryService.getHistInstance(instanceId).getInstanceState());
    }

    @Test
    @DisplayName("单一子流程：进入自动发起子实例，子流程结束回父流程继续")
    void subProcess_returnsToParent() throws Exception {
        // 子流程：发起人→子审批(乙)
        String subModel = """
                {"name":"子流程","key":"e2e_sub_inner",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"子审批","nodeKey":"ap_sub","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long subProcessId = deploy(subModel);

        // 主流程：发起人→子流程节点→主审批(丙)
        String mainModel = """
                {"name":"主流程","key":"e2e_sub_main",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"子流程节点","nodeKey":"cp1","type":5,
                     "callProcess":"%d:子流程",
                     "childNode":{"nodeName":"主审批","nodeKey":"ap_main","type":1,"setType":1,"examineMode":3,
                       "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}}}}}
                """.formatted(subProcessId);
        Long mainProcessId = deploy(mainModel);

        var parent = flowLongEngine.startInstanceById(mainProcessId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();
        Long parentId = parent.getId();

        // 子实例创建且任务为子流程的"子审批"
        var subInstances = queryService.getSubProcessByInstanceId(parentId).orElseThrow();
        assertEquals(1, subInstances.size(), "应创建 1 个子流程实例");
        Long subInstanceId = subInstances.get(0).getId();

        List<FlwTask> subTasks = activeTasks(subInstanceId);
        assertEquals(1, subTasks.size());
        assertEquals("子审批", subTasks.get(0).getTaskName());

        // 子流程审批完成 → 父流程回到主审批
        flowLongEngine.executeTask(subTasks.get(0).getId(), user(2L, "审批人乙"), null);

        List<FlwTask> parentTasks = activeTasks(parentId);
        assertEquals(1, parentTasks.size(), "子流程结束后父流程应继续到主审批");
        assertEquals("主审批", parentTasks.get(0).getTaskName());

        // 主审批完成 → 父流程结束
        flowLongEngine.executeTask(parentTasks.get(0).getId(), user(3L, "审批人丙"), null);
        FlwHisInstance his = queryService.getHistInstance(parentId);
        assertEquals(InstanceState.complete.getValue(), his.getInstanceState(), "单一子流程全链路回流成功");
    }
}
