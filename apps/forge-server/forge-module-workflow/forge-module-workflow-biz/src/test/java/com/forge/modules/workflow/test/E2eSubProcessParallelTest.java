package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 端到端：并行分支 + 子流程 组合（原 MySQL 自增环境撞号缺陷链路）
 *
 * 雪花 id 生成器（FlowLongSnowflakeIdGenerator）使任务与归档 id 全局唯一，
 * 该组合在 id 显式生成策略下应完整回流。
 */
class E2eSubProcessParallelTest extends WorkflowIntegrationTestBase {

    @Test
    @DisplayName("并行分支内嵌子流程：子流程回流后与并行分支汇聚结束")
    void parallelWithSubProcess_mergesAndCompletes() {
        // 子流程：发起人→子审批(乙)
        String subModel = """
                {"name":"并行子流程","key":"e2e_par_sub_inner",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"子审批","nodeKey":"ap_sub","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long subProcessId = deploy(subModel);

        // 主流程：并行分支(分支1=审批A / 分支2=子流程节点)
        String mainModel = """
                {"name":"并行子流程主流程","key":"e2e_par_sub_main",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"并行分支","nodeKey":"pb1","type":8,
                     "parallelNodes":[
                       {"nodeName":"分支1","nodeKey":"p1","type":3,"priorityLevel":1,"conditionList":[],
                        "childNode":{"nodeName":"审批A","nodeKey":"ap_a","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}},
                       {"nodeName":"分支2","nodeKey":"p2","type":3,"priorityLevel":2,"conditionList":[],
                        "childNode":{"nodeName":"子流程节点","nodeKey":"cp1","type":5,
                          "callProcess":"%d:并行子流程"}}
                     ]}}}
                """.formatted(subProcessId);
        Long mainProcessId = deploy(mainModel);

        var parent = flowLongEngine.startInstanceById(mainProcessId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();
        Long parentId = parent.getId();

        // 并行分支1任务 + 子流程实例的子审批任务同时存在
        FlwTask taskA = activeTasks(parentId).stream()
                .filter(t -> "审批A".equals(t.getTaskName())).findFirst().orElseThrow();
        var subInstances = queryService.getSubProcessByInstanceId(parentId).orElseThrow();
        assertEquals(1, subInstances.size());
        FlwTask subTask = activeTasks(subInstances.get(0).getId()).get(0);
        assertEquals("子审批", subTask.getTaskName());

        // 子审批先归档（原 MySQL 自增环境在此处撞 flw_his_task 主键）→ 回流；
        // 引擎并行汇聚挂在分支末任务完成路径：分支 A 须最后完成以触发汇聚
        flowLongEngine.executeTask(subTask.getId(), user(2L, "审批人乙"), null);
        assertNotNull(queryService.getInstance(parentId), "分支 A 未完成前父流程仍在运行");

        // 分支 A 最后通过 → 并行汇聚 → 父流程结束
        flowLongEngine.executeTask(taskA.getId(), user(2L, "审批人乙"), null);

        FlwHisInstance his = queryService.getHistInstance(parentId);
        assertNotNull(his, "并行+子流程汇聚后父流程应结束");
        assertEquals(InstanceState.complete.getValue(), his.getInstanceState(), "组合链路完整回流成功");
        assertTrue(activeTasks(parentId).isEmpty());
    }
}
