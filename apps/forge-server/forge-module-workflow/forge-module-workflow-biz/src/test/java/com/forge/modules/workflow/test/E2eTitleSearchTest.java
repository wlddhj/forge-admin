package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.workflow.dto.task.TaskQueryRequest;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.forge.modules.workflow.mapper.WfInstanceQueryMapper;
import com.forge.modules.workflow.service.WfTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forge.framework.security.utils.SecurityHelper;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * H2 端到端：流程标题（实例变量 __title）的搜索过滤——实例 UNION 侧 + 待办 EXISTS 侧
 */
class E2eTitleSearchTest extends WorkflowIntegrationTestBase {

    @Autowired
    private WfInstanceQueryMapper instanceQueryMapper;

    @Autowired
    private WfTaskService taskService;

    @Test
    @DisplayName("标题搜索：实例列表与待办列表按 __title 命中，无标题实例不混入")
    void titleSearch_filtersInstanceAndTodoLists() {
        String model = """
                {"name":"标题搜索流程","key":"e2e_title",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long processId = deploy(model);

        // 一例带标题（等价 startProcess 注入 __title），一例无标题
        flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                Map.of("__title", "甲的智慧园区立项"));
        flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null);

        // 实例 UNION 侧：标题关键字只命中带标题实例
        Page<Map<String, Object>> page = new Page<>(1, 10);
        List<Map<String, Object>> hit = instanceQueryMapper.selectUnionPage(
                page, null, null, null, null, null, "智慧园区", false);
        assertEquals(1, hit.size(), "标题关键字仅命中 1 例");
        Page<Map<String, Object>> all = new Page<>(1, 10);
        instanceQueryMapper.selectUnionPage(all, null, null, null, null, null, null, false);
        assertTrue(all.getTotal() >= 2, "无过滤时本用例两例都在");

        // 待办 EXISTS 侧：审批人乙按标题过滤只剩 1 条待办（mockStatic 须指向声明类 SecurityHelper）
        try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
            security.when(SecurityHelper::getCurrentUserId).thenReturn(2L);

            TaskQueryRequest request = new TaskQueryRequest();
            request.setPageNum(1);
            request.setPageSize(10);
            request.setTitle("智慧园区");
            assertEquals(1, taskService.getTodoTasks(request).getRecords().size(),
                    "待办按标题过滤命中 1 条");

            // 通配符按字面匹配：输入 % 不误匹配全部
            TaskQueryRequest literal = new TaskQueryRequest();
            literal.setPageNum(1);
            literal.setPageSize(10);
            literal.setTitle("%");
            assertEquals(0, taskService.getTodoTasks(literal).getRecords().size(),
                    "通配符字面匹配，不命中任何标题");
        }
    }

    @Test
    @DisplayName("已办标题搜索：流程仍在运行（实例在活动表）时已办任务同样命中")
    void titleSearch_doneTasksOfRunningInstance() {
        String model = """
                {"name":"标题已办流程","key":"e2e_title_done",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"一审","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}],
                     "childNode":{"nodeName":"二审","nodeKey":"ap2","type":1,"setType":1,"examineMode":3,
                       "nodeAssigneeList":[{"id":"3","name":"审批人丙"}]}}}}
                """;
        Long processId = deploy(model);
        FlwInstance instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                Map.of("__title", "在途已办标题")).orElseThrow();
        // 办结一审：任务进 his，实例仍在运行（活动表）
        FlwTask first = activeTasks(instance.getId()).get(0);
        flowLongEngine.executeTask(first.getId(), user(2L, "审批人乙"), null);

        try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
            security.when(SecurityHelper::getCurrentUserId).thenReturn(2L);
            TaskQueryRequest request = new TaskQueryRequest();
            request.setPageNum(1);
            request.setPageSize(10);
            request.setTitle("在途已办");
            assertEquals(1, taskService.getDoneTasks(request).getRecords().size(),
                    "运行中实例的已办任务按标题命中（活动表+历史表并集）");
        }
    }

    @Test
    @DisplayName("流程名称搜索：待办/已办按流程名称命中（此前后端从未消费该参数的存量缺口）")
    void processNameSearch_todoAndDone() {
        String model = """
                {"name":"名称搜索专用流程","key":"e2e_name_search",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long processId = deploy(model);
        FlwInstance instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (java.util.Map<String, Object>) null).orElseThrow();

        try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
            security.when(SecurityHelper::getCurrentUserId).thenReturn(2L);

            // 待办按流程名称
            TaskQueryRequest todo = new TaskQueryRequest();
            todo.setPageNum(1);
            todo.setPageSize(10);
            todo.setProcessDefinitionName("名称搜索专用");
            assertEquals(1, taskService.getTodoTasks(todo).getRecords().size(), "待办按流程名称命中");

            // 办结后（实例结束、任务进历史）已办按流程名称
            FlwTask task = activeTasks(instance.getId()).get(0);
            flowLongEngine.executeTask(task.getId(), user(2L, "审批人乙"), null);
            TaskQueryRequest done = new TaskQueryRequest();
            done.setPageNum(1);
            done.setPageSize(10);
            done.setProcessDefinitionName("名称搜索专用");
            assertEquals(1, taskService.getDoneTasks(done).getRecords().size(), "已办按流程名称命中");

            // 未命中名称快速空页
            TaskQueryRequest miss = new TaskQueryRequest();
            miss.setPageNum(1);
            miss.setPageSize(10);
            miss.setProcessDefinitionName("不存在的流程");
            assertEquals(0, taskService.getTodoTasks(miss).getRecords().size());
        }
    }
}
