package com.forge.admin.modules.workflow.framework.candidate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 候选人策略枚举
 *
 * @author forge-admin
 */
@Getter
@AllArgsConstructor
public enum CandidateStrategyEnum {

    /**
     * 角色策略
     */
    ROLE(10, "指定角色"),

    /**
     * 部门成员策略
     */
    DEPT_MEMBER(20, "部门成员"),

    /**
     * 部门负责人策略
     */
    DEPT_LEADER(21, "部门负责人"),

    /**
     * 指定用户策略
     */
    USER(30, "指定用户"),

    /**
     * 表达式策略
     */
    EXPRESSION(60, "表达式");

    /**
     * 策略代码
     */
    private final int code;

    /**
     * 策略描述
     */
    private final String description;

    /**
     * 根据代码获取枚举
     *
     * @param code 策略代码
     * @return 枚举值，如果不存在返回 null
     */
    public static CandidateStrategyEnum getByCode(int code) {
        for (CandidateStrategyEnum strategy : values()) {
            if (strategy.getCode() == code) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * 检查代码是否有效
     *
     * @param code 策略代码
     * @return 是否有效
     */
    public static boolean isValidCode(int code) {
        return getByCode(code) != null;
    }
}