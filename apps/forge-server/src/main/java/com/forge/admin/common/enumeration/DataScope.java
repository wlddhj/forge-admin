package com.forge.admin.common.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 数据权限范围枚举
 *
 * @author standadmin
 */
@Getter
public enum DataScope {

    /**
     * 全部数据权限
     */
    ALL("1", "全部数据权限"),

    /**
     * 自定义数据权限
     */
    CUSTOM("2", "自定义数据权限"),

    /**
     * 本部门数据权限
     */
    DEPT("3", "本部门数据权限"),

    /**
     * 本部门及以下数据权限
     */
    DEPT_AND_CHILD("4", "本部门及以下数据权限"),

    /**
     * 仅本人数据权限
     */
    SELF("5", "仅本人数据权限");

    @EnumValue
    @JsonValue
    private final String value;

    private final String description;

    DataScope(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static DataScope fromValue(String value) {
        for (DataScope scope : DataScope.values()) {
            if (scope.getValue().equals(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown data scope value: " + value);
    }

    /**
     * 从代码获取数据权限范围（别名方法）
     */
    public static DataScope fromCode(String code) {
        return fromValue(code);
    }

    /**
     * 获取权限级别（用于比较权限大小）
     * 数值越小，权限越大
     */
    public int getLevel() {
        return Integer.parseInt(this.value);
    }
}
