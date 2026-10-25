package com.forge.modules.workflow.framework.actor;

import com.aizuda.bpm.engine.TaskAccessStrategy;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 任务访问策略 - 集成 forge-admin 权限系统
 * 判断用户是否有权限访问（查看、处理）某任务
 *
 * 权限判断逻辑：
 * 1. 检查用户是否是任务参与者（候选人）
 * 2. 检查用户是否隶属任务的候选组（角色/部门类型 actor，FlowLong actor 模型语义：
 *    候选组任务签收 claimRole/claimDepartment 与组内直接办理均以此判定）
 * 3. 检查用户是否有管理员权限（可访问所有任务）
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ForgeTaskAccessStrategy implements TaskAccessStrategy {

    /**
     * 管理员角色列表
     */
    private static final String[] ADMIN_ROLES = {"admin", "workflow_admin"};

    private final FlowLongIdentityService identityService;

    public ForgeTaskAccessStrategy(FlowLongIdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * 判断用户是否可以访问任务
     *
     * @param userId    用户ID
     * @param taskActors 任务参与者列表
     * @return 如果允许访问，返回对应的参与者对象；否则返回 null
     */
    @Override
    public FlwTaskActor isAllowed(String userId, List<FlwTaskActor> taskActors) {
        if (userId == null || taskActors == null || taskActors.isEmpty()) {
            return null;
        }

        // 1. 检查是否是任务参与者（候选人）
        Optional<FlwTaskActor> actorOpt = taskActors.stream()
                .filter(actor -> Objects.equals(actor.getActorId(), userId))
                .findFirst();

        if (actorOpt.isPresent()) {
            log.debug("用户 {} 是任务候选人，允许访问", userId);
            return actorOpt.get();
        }

        // 2. 候选组隶属：角色/部门类型 actor 且用户隶属该组
        for (FlwTaskActor actor : taskActors) {
            if (ActorType.role.eq(actor.getActorType()) && belongsToRole(userId, actor.getActorId())) {
                log.debug("用户 {} 隶属候选角色 {}，允许访问", userId, actor.getActorId());
                return actor;
            }
            if (ActorType.department.eq(actor.getActorType()) && belongsToDepartment(userId, actor.getActorId())) {
                log.debug("用户 {} 隶属候选部门 {}，允许访问", userId, actor.getActorId());
                return actor;
            }
        }

        // 3. 检查是否有管理员权限
        UserContext userContext = UserContext.get();
        if (userContext != null && userContext.isAdmin()) {
            log.debug("管理员用户 {} 可访问任意任务", userId);
            // 返回第一个参与者作为占位符（管理员有权限，但不是候选人）
            return taskActors.get(0);
        }

        // 4. 检查用户角色（如果 UserContext 中有角色信息）
        if (hasWorkflowAdminRole(userContext)) {
            log.debug("工作流管理员用户 {} 可访问任意任务", userId);
            return taskActors.get(0);
        }

        log.debug("用户 {} 无权限访问该任务", userId);
        return null;
    }

    /**
     * 用户是否隶属指定角色（actorId 为角色ID）
     */
    private boolean belongsToRole(String userId, String roleId) {
        try {
            return identityService.getUserRoleIds(Long.parseLong(userId))
                    .contains(Long.parseLong(roleId));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 用户是否隶属指定部门（actorId 为部门ID）
     */
    private boolean belongsToDepartment(String userId, String deptId) {
        try {
            Long userDeptId = identityService.getUserDeptId(Long.parseLong(userId));
            return userDeptId != null && userDeptId.equals(Long.parseLong(deptId));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查是否是工作流管理员
     */
    private boolean hasWorkflowAdminRole(UserContext userContext) {
        if (userContext == null || userContext.getRoles() == null) {
            return false;
        }

        return userContext.getRoles().stream()
                .anyMatch(role -> {
                    if (role.getRoleCode() == null) {
                        return false;
                    }
                    for (String adminRole : ADMIN_ROLES) {
                        if (adminRole.equalsIgnoreCase(role.getRoleCode())) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    /**
     * 获取指定合法参与者对象
     * 用于确认用户是任务的合法处理人
     */
    @Override
    public FlwTaskActor getAllowedFlwTaskActor(Long taskId, com.aizuda.bpm.engine.core.FlowCreator flowCreator,
                                                List<FlwTaskActor> taskActors) {
        if (flowCreator == null || taskActors == null) {
            return null;
        }

        String userId = flowCreator.getCreateId();

        // 使用 isAllowed 方法检查权限
        FlwTaskActor allowedActor = isAllowed(userId, taskActors);

        if (allowedActor == null) {
            log.warn("用户 {} 无权限处理任务 {}", userId, taskId);
            throw new com.aizuda.bpm.engine.exception.FlowLongException("Not authorized to perform this task");
        }

        return allowedActor;
    }

    /**
     * 判断任务是否可以被认领
     *
     * @param userId    用户ID
     * @param taskActors 任务参与者列表
     * @return 是否可以认领
     */
    public boolean canClaim(String userId, List<FlwTaskActor> taskActors) {
        if (userId == null || taskActors == null || taskActors.isEmpty()) {
            return false;
        }

        // 参与者本人、候选组隶属者、管理员均可认领
        return isAllowed(userId, taskActors) != null;
    }

    /**
     * 判断用户是否可以查看任务（权限更宽松）
     *
     * @param userId    用户ID
     * @param taskActors 任务参与者列表
     * @return 是否可以查看
     */
    public boolean canView(String userId, List<FlwTaskActor> taskActors) {
        // 查看权限：候选人、候选组、管理员、发起人都可以查看
        FlwTaskActor allowed = isAllowed(userId, taskActors);
        return allowed != null;
    }
}
