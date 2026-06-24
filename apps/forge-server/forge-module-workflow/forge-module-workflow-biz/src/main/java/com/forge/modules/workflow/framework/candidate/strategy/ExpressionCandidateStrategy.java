package com.forge.modules.workflow.framework.candidate.strategy;

import cn.hutool.core.util.StrUtil;
import com.forge.modules.system.entity.SysDept;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysDeptMapper;
import com.forge.modules.system.mapper.SysUserMapper;
import com.forge.modules.workflow.entity.WfProcessExpression;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.framework.candidate.CandidateStrategyEnum;
import com.forge.modules.workflow.mapper.WfProcessExpressionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 表达式候选人策略
 * 解析表达式计算候选人
 *
 * 支持的表达式格式：
 * 1. 内置变量：${initiator}, ${startUser}, ${startUserDeptLeader}
 * 2. 流程变量：${variableName} - 从流程变量中获取
 * 3. 表单字段：${form_fieldName} - 从表单数据中获取用户ID
 * 4. 表达式ID：直接传入数字ID，查询已配置的表达式
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressionCandidateStrategy implements BpmTaskCandidateStrategy {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final WfProcessExpressionMapper expressionMapper;

    /**
     * 内置表达式常量
     */
    private static final String INITIATOR = "initiator";
    private static final String START_USER = "startUser";
    private static final String START_USER_DEPT_LEADER = "startUserDeptLeader";
    private static final String FORM_PREFIX = "form_";

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.EXPRESSION.getCode();
    }

    @Override
    public String getDescription() {
        return "表达式";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        // 表达式策略需要 TaskContext，基础方法返回空
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }
        // 尝试解析为表达式ID，查询已配置的表达式
        if (StrUtil.isNumeric(param)) {
            Long expressionId = Long.parseLong(param);
            WfProcessExpression expression = expressionMapper.selectById(expressionId);
            if (expression != null && expression.getStatus() == 1) {
                log.debug("通过表达式ID找到配置: id={}, expression={}", expressionId, expression.getExpression());
                // 返回空集合，需要 TaskContext 才能计算
                return Collections.emptySet();
            }
        }
        log.warn("表达式候选人策略需要 TaskContext: param={}", param);
        return Collections.emptySet();
    }

    @Override
    public Set<Long> calculateUsers(String param, TaskContext taskContext) {
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            // 1. 尝试解析为表达式ID
            if (StrUtil.isNumeric(param)) {
                Long expressionId = Long.parseLong(param);
                WfProcessExpression expression = expressionMapper.selectById(expressionId);
                if (expression != null && expression.getStatus() == 1) {
                    return evaluateExpression(expression.getExpression(), taskContext);
                }
                log.warn("表达式配置不存在或已禁用: id={}", expressionId);
                return Collections.emptySet();
            }

            // 2. 直接解析表达式
            return evaluateExpression(param, taskContext);
        } catch (Exception e) {
            log.error("表达式解析失败: param={}", param, e);
            return Collections.emptySet();
        }
    }

    /**
     * 解析表达式并计算候选人
     *
     * @param expression 表达式内容
     * @param taskContext 任务上下文
     * @return 候选人用户ID集合
     */
    private Set<Long> evaluateExpression(String expression, TaskContext taskContext) {
        if (expression == null || expression.isEmpty()) {
            return Collections.emptySet();
        }

        // 提取表达式变量名（去除 ${} 包裹）
        String variableName = extractVariableName(expression);
        if (variableName == null) {
            log.warn("表达式格式不正确: {}", expression);
            return Collections.emptySet();
        }

        log.debug("解析表达式: expression={}, variable={}", expression, variableName);

        // 处理内置变量
        switch (variableName) {
            case INITIATOR:
            case START_USER:
                return getStartUser(taskContext);
            case START_USER_DEPT_LEADER:
                return getStartUserDeptLeader(taskContext);
            default:
                break;
        }

        // 处理表单字段引用 ${form_fieldName}
        if (variableName.startsWith(FORM_PREFIX)) {
            String fieldName = variableName.substring(FORM_PREFIX.length());
            return getFormFieldValue(fieldName, taskContext);
        }

        // 处理流程变量引用
        return getVariableValue(variableName, taskContext);
    }

    /**
     * 提取表达式变量名
     * 支持格式：${variableName} 或直接 variableName
     */
    private String extractVariableName(String expression) {
        String trimmed = expression.trim();

        // 处理 ${...} 格式
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }

        // 直接返回变量名
        return trimmed;
    }

    /**
     * 获取发起人
     */
    private Set<Long> getStartUser(TaskContext taskContext) {
        Long startUserId = taskContext.getStartUserId();
        if (startUserId != null) {
            log.debug("表达式解析发起人: startUserId={}", startUserId);
            return Set.of(startUserId);
        }
        log.warn("无法获取流程发起人");
        return Collections.emptySet();
    }

    /**
     * 获取发起人部门负责人
     */
    private Set<Long> getStartUserDeptLeader(TaskContext taskContext) {
        Long startUserId = taskContext.getStartUserId();
        if (startUserId == null) {
            log.warn("无法获取流程发起人");
            return Collections.emptySet();
        }

        SysUser startUser = sysUserMapper.selectById(startUserId);
        if (startUser == null || startUser.getDeptId() == null) {
            log.warn("发起人不存在或无部门: startUserId={}", startUserId);
            return Collections.emptySet();
        }

        SysDept dept = sysDeptMapper.selectById(startUser.getDeptId());
        if (dept == null || dept.getLeader() == null || dept.getLeader().isEmpty()) {
            log.warn("部门不存在或无负责人: deptId={}", startUser.getDeptId());
            return Collections.emptySet();
        }

        SysUser leaderUser = sysUserMapper.selectByUsernameSimple(dept.getLeader());
        if (leaderUser != null) {
            log.debug("表达式解析发起人部门负责人: startUserId={}, deptId={}, leaderId={}",
                    startUserId, startUser.getDeptId(), leaderUser.getId());
            return Set.of(leaderUser.getId());
        }

        log.warn("部门负责人用户不存在: leaderUsername={}", dept.getLeader());
        return Collections.emptySet();
    }

    /**
     * 获取表单字段值（假设字段值为用户ID）
     */
    private Set<Long> getFormFieldValue(String fieldName, TaskContext taskContext) {
        Map<String, Object> variables = taskContext.getVariables();
        if (variables == null) {
            log.warn("无法获取流程变量");
            return Collections.emptySet();
        }

        Object value = variables.get(fieldName);
        if (value == null) {
            // 尝试从 form_ 前缀的变量中获取
            value = variables.get(FORM_PREFIX + fieldName);
        }

        if (value == null) {
            log.warn("表单字段不存在: fieldName={}", fieldName);
            return Collections.emptySet();
        }

        // 尝试转换为用户ID
        Long userId = convertToUserId(value);
        if (userId != null) {
            log.debug("表单字段解析为用户ID: fieldName={}, value={}, userId={}", fieldName, value, userId);
            return Set.of(userId);
        }

        log.warn("表单字段值无法转换为用户ID: fieldName={}, value={}", fieldName, value);
        return Collections.emptySet();
    }

    /**
     * 获取流程变量值
     */
    private Set<Long> getVariableValue(String variableName, TaskContext taskContext) {
        Map<String, Object> variables = taskContext.getVariables();
        if (variables == null) {
            log.warn("无法获取流程变量");
            return Collections.emptySet();
        }

        Object value = variables.get(variableName);
        if (value == null) {
            log.warn("流程变量不存在: variableName={}", variableName);
            return Collections.emptySet();
        }

        // 尝试转换为用户ID集合
        Set<Long> userIds = convertToUserIds(value);
        if (!userIds.isEmpty()) {
            log.debug("流程变量解析为用户ID: variableName={}, value={}, userIds={}", variableName, value, userIds);
            return userIds;
        }

        log.warn("流程变量值无法转换为用户ID: variableName={}, value={}", variableName, value);
        return Collections.emptySet();
    }

    /**
     * 将值转换为用户ID
     */
    private Long convertToUserId(Object value) {
        if (value == null) {
            return null;
        }

        // 数字类型直接转换
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        // 字符串类型尝试解析
        if (value instanceof String) {
            String strValue = ((String) value).trim();
            if (StrUtil.isNumeric(strValue)) {
                return Long.parseLong(strValue);
            }
            // 尝试用户名查找
            SysUser user = sysUserMapper.selectByUsernameSimple(strValue);
            if (user != null) {
                return user.getId();
            }
        }

        return null;
    }

    /**
     * 将值转换为用户ID集合
     * 支持单个值、逗号分隔的字符串、集合类型
     */
    private Set<Long> convertToUserIds(Object value) {
        if (value == null) {
            return Collections.emptySet();
        }

        // 单个数字
        if (value instanceof Number) {
            return Set.of(((Number) value).longValue());
        }

        // 字符串处理
        if (value instanceof String) {
            String strValue = ((String) value).trim();
            if (strValue.isEmpty()) {
                return Collections.emptySet();
            }

            // 单个数字字符串
            if (StrUtil.isNumeric(strValue)) {
                return Set.of(Long.parseLong(strValue));
            }

            // 逗号分隔的多个值
            if (strValue.contains(",")) {
                Set<Long> userIds = new java.util.HashSet<>();
                for (String part : strValue.split(",")) {
                    String trimmedPart = part.trim();
                    if (StrUtil.isNumeric(trimmedPart)) {
                        userIds.add(Long.parseLong(trimmedPart));
                    } else {
                        // 尝试用户名查找
                        SysUser user = sysUserMapper.selectByUsernameSimple(trimmedPart);
                        if (user != null) {
                            userIds.add(user.getId());
                        }
                    }
                }
                return userIds;
            }

            // 单个用户名
            SysUser user = sysUserMapper.selectByUsernameSimple(strValue);
            if (user != null) {
                return Set.of(user.getId());
            }
        }

        // 集合类型
        if (value instanceof Iterable) {
            Set<Long> userIds = new java.util.HashSet<>();
            for (Object item : (Iterable<?>) value) {
                Long userId = convertToUserId(item);
                if (userId != null) {
                    userIds.add(userId);
                }
            }
            return userIds;
        }

        return Collections.emptySet();
    }
}