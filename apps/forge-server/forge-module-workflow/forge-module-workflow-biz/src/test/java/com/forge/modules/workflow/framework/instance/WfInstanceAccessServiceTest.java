package com.forge.modules.workflow.framework.instance;

import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwHisTaskActor;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实例参与人判定单元测试（发起人/actor 展开/角色/部门/admin）
 */
class WfInstanceAccessServiceTest {

    private QueryService queryService;
    private FlowLongIdentityService identityService;
    private WfInstanceAccessService accessService;

    @BeforeEach
    void setUp() {
        queryService = mock(QueryService.class);
        identityService = mock(FlowLongIdentityService.class);
        accessService = new WfInstanceAccessService(queryService, identityService);
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private FlwInstance instance(String createId) {
        FlwInstance instance = new FlwInstance();
        instance.setId(100L);
        instance.setCreateId(createId);
        return instance;
    }

    private FlwTaskActor actor(String actorId, int actorType) {
        FlwTaskActor actor = new FlwTaskActor();
        actor.setTaskId(1L);
        actor.setActorId(actorId);
        actor.setActorType(actorType);
        return actor;
    }

    @Test
    @DisplayName("发起人（活动实例 createId）判定参与")
    void initiatorOfActiveInstance() {
        when(queryService.getInstance(100L)).thenReturn(instance("42"));
        assertTrue(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("历史实例发起人判定参与（活动表无行）")
    void initiatorOfFinishedInstance() {
        when(queryService.getInstance(100L)).thenReturn(null);
        FlwHisInstance hisInstance = new FlwHisInstance();
        hisInstance.setId(100L);
        hisInstance.setCreateId("42");
        when(queryService.getHistInstance(100L)).thenReturn(hisInstance);
        assertTrue(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("当前任务 actor=用户本人判定参与")
    void activeTaskActorUser() {
        when(queryService.getInstance(100L)).thenReturn(instance("1"));
        when(queryService.getActiveTaskActorsByInstanceId(100L))
                .thenReturn(Optional.of(List.of(actor("42", 0))));
        assertTrue(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("当前任务 actor=用户角色（候选组）判定参与")
    void activeTaskActorRole() {
        when(queryService.getInstance(100L)).thenReturn(instance("1"));
        when(identityService.getUserDeptId(42L)).thenReturn(null);
        when(queryService.getActiveTaskActorsByInstanceId(100L))
                .thenReturn(Optional.of(List.of(actor("9", 1))));

        UserContext context = new UserContext();
        UserContext.DataScopeRoleInfo role = new UserContext.DataScopeRoleInfo();
        role.setRoleId(9L);
        context.setRoles(List.of(role));
        UserContext.set(context);

        assertTrue(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("当前任务 actor=用户部门（候选组）判定参与")
    void activeTaskActorDepartment() {
        when(queryService.getInstance(100L)).thenReturn(instance("1"));
        when(identityService.getUserDeptId(42L)).thenReturn(7L);
        when(queryService.getActiveTaskActorsByInstanceId(100L))
                .thenReturn(Optional.of(List.of(actor("7", 2))));
        assertTrue(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("历史任务 actor（含抄送人）判定参与")
    void hisTaskActorCc() {
        when(queryService.getInstance(100L)).thenReturn(instance("1"));
        FlwHisTaskActor ccActor = new FlwHisTaskActor();
        ccActor.setTaskId(2L);
        ccActor.setActorId("42");
        when(queryService.getActiveTaskActorsByInstanceId(100L)).thenReturn(Optional.empty());
        when(queryService.getHisTaskActorsByInstanceId(100L)).thenReturn(Optional.of(List.of(ccActor)));
        assertTrue(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("无任何交集判定不参与")
    void notParticipant() {
        when(queryService.getInstance(100L)).thenReturn(instance("1"));
        when(identityService.getUserDeptId(42L)).thenReturn(7L);
        when(queryService.getActiveTaskActorsByInstanceId(100L))
                .thenReturn(Optional.of(List.of(actor("999", 0))));
        FlwHisTaskActor hisActor = new FlwHisTaskActor();
        hisActor.setTaskId(3L);
        hisActor.setActorId("888");
        when(queryService.getHisTaskActorsByInstanceId(100L))
                .thenReturn(Optional.of(List.of(hisActor)));
        assertFalse(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("工作流管理员豁免（不查任何数据）")
    void workflowAdminAlwaysParticipant() {
        UserContext context = new UserContext();
        UserContext.DataScopeRoleInfo role = new UserContext.DataScopeRoleInfo();
        role.setRoleCode("workflow_admin");
        context.setRoles(List.of(role));
        UserContext.set(context);
        assertTrue(accessService.isParticipant(100L, 42L));
    }

    @Test
    @DisplayName("实例/用户任一为空直接 false")
    void nullArguments() {
        assertFalse(accessService.isParticipant(null, 42L));
        assertFalse(accessService.isParticipant(100L, null));
    }
}
