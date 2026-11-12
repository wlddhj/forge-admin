package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.service.WfTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * H2 端到端：催办节流行锁（开租户模式，验证 FOR UPDATE 当前读穿越租户拦截器）。
 * <p>
 * 修复前：并发催办各自在任务列表快照上判节流（互不可见）→ 双双通过并整体覆写 variable，
 * 节流失效且丢更新。修复后：节流判定前 FOR UPDATE 锁行，锁内读最新 variable，
 * 行锁持有至事务提交——并发请求串行化，仅首个成功，其余被节流拒绝。
 */
class E2eUrgeRowLockTest extends TenantEnabledTestBase {

    @Autowired
    private WfTaskService wfTaskService;

    @Autowired
    private WfApprovalCommentMapper approvalCommentMapper;

    private FlwInstance startUrgableInstance() {
        String model = """
                {"name":"催办行锁流程","key":"e2e_urge_lock",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long processId = deploy(model);
        return flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (Map<String, Object>) null).orElseThrow();
    }

    private long urgeCommentCount(Long instanceId) {
        return approvalCommentMapper.selectCount(new LambdaQueryWrapper<WfApprovalComment>()
                .eq(WfApprovalComment::getProcessInstanceId, instanceId)
                .eq(WfApprovalComment::getActionType, ApprovalActionTypeEnum.URGE.getCode()));
    }

    @Test
    @DisplayName("催办节流：首次成功写标记，窗口内再次催办被拒（FOR UPDATE 在租户拦截器下可执行）")
    void urge_onceThenThrottled() {
        FlwInstance instance = startUrgableInstance();

        try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
            security.when(SecurityHelper::getCurrentUserId).thenReturn(1L);

            wfTaskService.urgeInstance(String.valueOf(instance.getId()), "尽快处理");

            FlwTask task = activeTasks(instance.getId()).get(0);
            assertTrue(task.variableMap().containsKey("lastUrgeTime"), "节流标记已写任务 variable");
            assertEquals(1, urgeCommentCount(instance.getId()));

            BusinessException throttled = assertThrows(BusinessException.class,
                    () -> wfTaskService.urgeInstance(String.valueOf(instance.getId()), "再催一次"));
            assertTrue(throttled.getMessage().contains("催办过于频繁"), "窗口内重复催办被拒");
        }
        assertEquals(1, urgeCommentCount(instance.getId()), "窗口内重复催办不再留痕");
    }

    @Test
    @DisplayName("并发催办双穿透防护：多线程同时催办仅一个成功，其余被节流，留痕仅一条")
    void urge_concurrentOnlyOneWins() throws Exception {
        FlwInstance instance = startUrgableInstance();
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    // 静态 mock 与租户上下文均为 ThreadLocal 语义：工作线程内自建自清
                    TenantContextHolder.setTenantId(1L);
                    try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
                        security.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
                        startGate.await();
                        try {
                            wfTaskService.urgeInstance(String.valueOf(instance.getId()), null);
                            return true;
                        } catch (BusinessException e) {
                            if (!e.getMessage().contains("催办过于频繁")) {
                                throw e;
                            }
                            return false;
                        }
                    } finally {
                        TenantContextHolder.clear();
                    }
                }));
            }
            startGate.countDown();
            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertEquals(1, succeeded, "并发催办仅一个请求成功，其余被行锁内节流拒绝");
            assertEquals(1, urgeCommentCount(instance.getId()), "催办留痕仅一条");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("锁内 variable 合并写回：既有任务变量在催办后不丢失")
    void urge_keepsExistingTaskVariable() {
        FlwInstance instance = startUrgableInstance();
        // 任务 variable 预置业务键（等价引擎流转写入），催办标记须合并非覆写
        FlwTask task = activeTasks(instance.getId()).get(0);
        FlwTask update = new FlwTask();
        update.setId(task.getId());
        update.setVariable("{\"bizFlag\":\"keep-me\"}");
        flowLongEngine.taskService().updateTaskById(update, user(1L, "发起人甲"));

        try (MockedStatic<SecurityHelper> security = mockStatic(SecurityHelper.class)) {
            security.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            wfTaskService.urgeInstance(String.valueOf(instance.getId()), null);
        }

        Map<String, Object> variable = activeTasks(instance.getId()).get(0).variableMap();
        assertEquals("keep-me", variable.get("bizFlag"), "既有变量键保留");
        assertTrue(variable.containsKey("lastUrgeTime"), "节流标记写入");
    }
}
