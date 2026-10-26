package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.service.AiModelResolver;
import com.forge.modules.workflow.entity.WfAiRouteRecord;
import com.forge.modules.workflow.mapper.WfAiRouteRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * H2 端到端：网关 AI 智能路由
 * 四链路：AI 优先于 SpEL / AI 失败回退 SpEL / 双失败回退默认分支 / 包容网关 AI 多选
 */
class E2eAiRoutingTest extends WorkflowIntegrationTestBase {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private AiModelResolver aiModelResolver;

    @Autowired
    private WfAiRouteRecordMapper routeRecordMapper;

    @AfterEach
    void resetLlmStubs() {
        // 共享 Spring mock，避免桩残留影响同上下文的其他测试类
        reset(llmClient, aiModelResolver);
    }

    private void stubModelResolved() {
        AiModelConfig modelConfig = new AiModelConfig();
        modelConfig.setProvider("deepseek");
        when(aiModelResolver.resolve(any())).thenReturn(modelConfig);
    }

    private void stubLlmContent(String content) {
        stubModelResolved();
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setContent(content);
        response.setModelName("test-model");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(response);
    }

    private void stubLlmFailure() {
        stubModelResolved();
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage("上游不可用");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(response);
    }

    /**
     * 条件网关 + AI 路由模型：分支1(高额 amount>10000→审批人丙)、分支2(低额 amount<100→审批人乙)、默认(→发起人甲)
     */
    private String conditionModel(String processKey) {
        return """
                {"name":"AI路由条件分支流程","key":"%s",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"AI网关","nodeKey":"ai_gw","type":4,"callAi":"1",
                     "extendConfig":{"aiRouting":{"prompt":"金额大于1万走高额审批"}},
                     "conditionNodes":[
                       {"nodeName":"高额","nodeKey":"ai_high","type":3,"priorityLevel":1,
                        "conditionList":[[{"label":"金额","field":"amount","operator":">","value":"10000"}]],
                        "childNode":{"nodeName":"高额审批","nodeKey":"ap_high","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}},
                       {"nodeName":"低额","nodeKey":"ai_low","type":3,"priorityLevel":2,
                        "conditionList":[[{"label":"金额","field":"amount","operator":"<","value":"100"}]],
                        "childNode":{"nodeName":"低额审批","nodeKey":"ap_low","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}},
                       {"nodeName":"其他","nodeKey":"ai_default","type":3,"priorityLevel":3,"conditionList":[],
                        "childNode":{"nodeName":"默认审批","nodeKey":"ap_def","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"1","name":"发起人甲"}]}}
                     ]}}}
                """.formatted(processKey);
    }

    @Test
    @DisplayName("AI 优先：SpEL 本会命中低额分支，AI 坚持高额 → 走高额并落 SUCCESS 审计")
    void aiRouting_takesPriorityOverSpel() {
        Long processId = deploy(conditionModel("e2e_ai_pri"));
        // amount=50 时 SpEL 命中"低额"，AI 返回"高额"分支 nodeKey → 必须走 AI 的选择
        stubLlmContent("{\"nodeKeys\": [\"ai_high\"], \"confidence\": 93, \"reasoning\": \"客户等级高\"}");

        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                Map.of("amount", 50)).orElseThrow();
        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size());
        assertEquals("高额审批", tasks.get(0).getTaskName());
        assertEquals("3", actorsOf(tasks.get(0).getId()).get(0).getActorId());

        // 审计：SUCCESS，选中 ai_high
        List<WfAiRouteRecord> records = routeRecordMapper.selectList(
                new LambdaQueryWrapper<WfAiRouteRecord>().eq(WfAiRouteRecord::getProcessInstanceId, instance.getId()));
        assertEquals(1, records.size());
        assertEquals("SUCCESS", records.get(0).getStatus());
        assertEquals("ai_high", records.get(0).getSelectedBranches());
        assertEquals("AI网关", records.get(0).getGatewayNodeName());
        assertNotNull(records.get(0).getCandidateBranches());

        flowLongEngine.executeTask(tasks.get(0).getId(), user(3L, "审批人丙"), null);
        assertEquals(InstanceState.complete.getValue(), queryService.getHistInstance(instance.getId()).getInstanceState());
    }

    @Test
    @DisplayName("AI 失败回退 SpEL：LLM 返回失败 → 走条件命中的低额分支，审计 ERROR")
    void aiRouting_fallsBackToSpel() {
        Long processId = deploy(conditionModel("e2e_ai_fb1"));
        stubLlmFailure();

        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                Map.of("amount", 50)).orElseThrow();
        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size());
        assertEquals("低额审批", tasks.get(0).getTaskName());

        List<WfAiRouteRecord> records = routeRecordMapper.selectList(
                new LambdaQueryWrapper<WfAiRouteRecord>().eq(WfAiRouteRecord::getProcessInstanceId, instance.getId()));
        assertEquals(1, records.size());
        assertEquals("ERROR", records.get(0).getStatus());
    }

    @Test
    @DisplayName("AI 与 SpEL 均不中：回退默认分支，审计 FALLBACK")
    void aiRouting_fallsBackToDefaultBranch() {
        // 两分支均不可能命中 amount=50，AI 返回幻觉 nodeKey
        //（注意 aiRouting 不可为空对象：引擎部署时以 NON_EMPTY 序列化重写模型，空配置会被整键剔除）
        String model = """
                {"name":"AI路由默认分支流程","key":"e2e_ai_fb2",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"AI网关","nodeKey":"ai_gw2","type":4,"callAi":"1",
                     "extendConfig":{"aiRouting":{"confidenceThreshold":80}},
                     "conditionNodes":[
                       {"nodeName":"高额","nodeKey":"b1","type":3,"priorityLevel":1,
                        "conditionList":[[{"label":"金额","field":"amount","operator":">","value":"10000"}]],
                        "childNode":{"nodeName":"高额审批","nodeKey":"ap_h","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}},
                       {"nodeName":"负数","nodeKey":"b2","type":3,"priorityLevel":2,
                        "conditionList":[[{"label":"金额","field":"amount","operator":"<","value":"0"}]],
                        "childNode":{"nodeName":"负数审批","nodeKey":"ap_n","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}},
                       {"nodeName":"其他","nodeKey":"b_def","type":3,"priorityLevel":3,"conditionList":[],
                        "childNode":{"nodeName":"默认审批","nodeKey":"ap_d","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"1","name":"发起人甲"}]}}
                     ]}}}
                """;
        Long processId = deploy(model);
        stubLlmContent("{\"nodeKeys\": [\"flk_ghost\"], \"confidence\": 99, \"reasoning\": \"幻觉分支\"}");

        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                Map.of("amount", 50)).orElseThrow();
        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size());
        assertEquals("默认审批", tasks.get(0).getTaskName());

        List<WfAiRouteRecord> records = routeRecordMapper.selectList(
                new LambdaQueryWrapper<WfAiRouteRecord>().eq(WfAiRouteRecord::getProcessInstanceId, instance.getId()));
        assertEquals(1, records.size());
        assertEquals("FALLBACK", records.get(0).getStatus());
    }

    @Test
    @DisplayName("包容网关 AI 多选：条件互斥的两分支被 AI 同时激活，全完成流程结束")
    void aiRouting_inclusiveMultiSelect() {
        String model = """
                {"name":"AI路由包容分支流程","key":"e2e_ai_inc",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"AI包容网关","nodeKey":"ai_gw3","type":9,"callAi":"1",
                     "extendConfig":{"aiRouting":{"prompt":"重要单据并行审查"}},
                     "inclusiveNodes":[
                       {"nodeName":"甲审","nodeKey":"inc_a","type":3,"priorityLevel":1,
                        "conditionList":[[{"label":"金额","field":"amount","operator":">","value":"100"}]],
                        "childNode":{"nodeName":"包容审批A","nodeKey":"inc_ap_a","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}},
                       {"nodeName":"乙审","nodeKey":"inc_b","type":3,"priorityLevel":2,
                        "conditionList":[[{"label":"金额","field":"amount","operator":"<","value":"0"}]],
                        "childNode":{"nodeName":"包容审批B","nodeKey":"inc_ap_b","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}},
                       {"nodeName":"其他","nodeKey":"inc_def","type":3,"priorityLevel":3,"conditionList":[],
                        "childNode":{"nodeName":"包容默认审批","nodeKey":"inc_ap_d","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"1","name":"发起人甲"}]}}
                     ]}}}
                """;
        Long processId = deploy(model);
        // SpEL 只会激活 inc_a（amount=500），AI 同时选 inc_a + inc_b → 两任务并起
        stubLlmContent("{\"nodeKeys\": [\"inc_a\", \"inc_b\"], \"confidence\": 90, \"reasoning\": \"并行复核\"}");

        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                Map.of("amount", 500)).orElseThrow();
        Long instanceId = instance.getId();

        List<FlwTask> tasks = activeTasks(instanceId);
        assertEquals(2, tasks.size(), "AI 应同时激活两个包容分支");

        List<WfAiRouteRecord> records = routeRecordMapper.selectList(
                new LambdaQueryWrapper<WfAiRouteRecord>().eq(WfAiRouteRecord::getProcessInstanceId, instanceId));
        assertEquals(1, records.size());
        assertEquals("SUCCESS", records.get(0).getStatus());
        assertEquals("inc_a,inc_b", records.get(0).getSelectedBranches());

        for (FlwTask task : tasks) {
            flowLongEngine.executeTask(task.getId(),
                    "包容审批A".equals(task.getTaskName()) ? user(2L, "审批人乙") : user(3L, "审批人丙"), null);
        }
        assertEquals(InstanceState.complete.getValue(), queryService.getHistInstance(instanceId).getInstanceState());
    }

    @Test
    @DisplayName("未启用 AI 的网关零影响：无 callAi 时不调 LLM，纯 SpEL 路由")
    void gatewayWithoutAi_untouched() {
        String model = """
                {"name":"普通条件分支流程","key":"e2e_ai_none",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"条件网关","nodeKey":"gw_plain","type":4,
                     "conditionNodes":[
                       {"nodeName":"大于","nodeKey":"p1","type":3,"priorityLevel":1,
                        "conditionList":[[{"label":"金额","field":"amount","operator":">","value":"100"}]],
                        "childNode":{"nodeName":"大于审批","nodeKey":"ap_p1","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}},
                       {"nodeName":"其他","nodeKey":"p2","type":3,"priorityLevel":2,"conditionList":[],
                        "childNode":{"nodeName":"默认审批","nodeKey":"ap_p2","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"1","name":"发起人甲"}]}}
                     ]}}}
                """;
        Long processId = deploy(model);

        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                Map.of("amount", 999)).orElseThrow();
        List<FlwTask> tasks = activeTasks(instance.getId());
        assertEquals(1, tasks.size());
        assertEquals("大于审批", tasks.get(0).getTaskName());

        verifyNoInteractions(llmClient);
        assertEquals(0, routeRecordMapper.selectCount(
                new LambdaQueryWrapper<WfAiRouteRecord>().eq(WfAiRouteRecord::getProcessInstanceId, instance.getId())).intValue());
    }
}
