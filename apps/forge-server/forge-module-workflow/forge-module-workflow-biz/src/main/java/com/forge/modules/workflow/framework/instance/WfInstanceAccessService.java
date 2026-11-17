package com.forge.modules.workflow.framework.instance;

import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.framework.WorkflowPermissionSupport;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程实例参与人判定（业务单据视图的统一权限闸口）。
 * <p>
 * 参与人 = 发起人 ∨ 当前/历史任务 actor（用户、其角色、其部门三类 actor 均算，
 * 历史侧天然含抄送人与发起节点；FlwHisTaskActor 继承 FlwTaskActor 统一处理）∨ 工作流管理员。
 * 活动实例与历史实例两侧都查。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class WfInstanceAccessService {

    private final QueryService queryService;
    private final FlowLongIdentityService identityService;

    /**
     * 判定用户是否实例参与人
     */
    public boolean isParticipant(Long instanceId, Long userId) {
        if (instanceId == null || userId == null) {
            return false;
        }
        if (WorkflowPermissionSupport.hasWorkflowAdminRole()) {
            return true;
        }

        // 发起人（活动表优先，结束实例落历史表）
        FlwInstance instance = queryService.getInstance(instanceId);
        if (instance != null) {
            if (isUser(instance.getCreateId(), userId)) {
                return true;
            }
        } else {
            FlwHisInstance hisInstance = queryService.getHistInstance(instanceId);
            if (hisInstance != null && isUser(hisInstance.getCreateId(), userId)) {
                return true;
            }
        }

        // 当前/历史任务 actor：用户本人 + 角色/部门候选组展开
        Set<String> actorIds = resolveUserActorIds(userId);
        if (actorIds.isEmpty()) {
            return false;
        }
        if (matchActor(queryService.getActiveTaskActorsByInstanceId(instanceId)
                .orElseGet(List::of), actorIds)) {
            return true;
        }
        return matchActor(queryService.getHisTaskActorsByInstanceId(instanceId)
                .orElseGet(List::of), actorIds);
    }

    private boolean matchActor(List<? extends FlwTaskActor> actors, Set<String> actorIds) {
        return actors.stream()
                .filter(a -> a.getActorId() != null)
                .anyMatch(a -> actorIds.contains(a.getActorId()));
    }

    /**
     * 用户在 actor 体系中的全部标识：本人 ID + 拥有角色 ID + 所属部门 ID
     * （角色取 UserContext 免 DB 查询，部门查用户主表——与待办候选组展开同口径）
     */
    private Set<String> resolveUserActorIds(Long userId) {
        Set<String> ids = new HashSet<>();
        ids.add(String.valueOf(userId));
        UserContext context = UserContext.get();
        if (context != null && context.getRoles() != null) {
            context.getRoles().forEach(role -> {
                if (role.getRoleId() != null) {
                    ids.add(String.valueOf(role.getRoleId()));
                }
            });
        }
        Long deptId = identityService.getUserDeptId(userId);
        if (deptId != null) {
            ids.add(String.valueOf(deptId));
        }
        return ids;
    }

    private boolean isUser(String createId, Long userId) {
        return String.valueOf(userId).equals(createId);
    }
}
