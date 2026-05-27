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

    ROLE(10, "指定角色"),
    DEPT_MEMBER(20, "部门成员"),
    DEPT_LEADER(21, "部门负责人"),
    POST(22, "指定岗位"),
    USER(30, "指定用户"),
    APPROVE_USER_SELECT(34, "审批人自选"),
    START_USER_SELECT(35, "发起人自选"),
    START_USER(36, "发起人自己"),
    START_USER_DEPT_LEADER(37, "发起人部门负责人"),
    DEPT_LEADER_MULTI(38, "连续多级部门负责人"),
    EXPRESSION(60, "表达式");

    private final int code;
    private final String description;

    public static CandidateStrategyEnum getByCode(int code) {
        for (CandidateStrategyEnum strategy : values()) {
            if (strategy.getCode() == code) {
                return strategy;
            }
        }
        return null;
    }

    public static boolean isValidCode(int code) {
        return getByCode(code) != null;
    }
}
