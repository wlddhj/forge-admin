package com.forge.common.utils;

import cn.hutool.core.util.StrUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具
 *
 * @author standadmin
 */
public class SensitiveDataMasker {

    /**
     * 需要脱敏的字段名（密码类）
     */
    private static final Set<String> PASSWORD_FIELDS = new HashSet<>();
    static {
        PASSWORD_FIELDS.add("password");
        PASSWORD_FIELDS.add("oldPassword");
        PASSWORD_FIELDS.add("newPassword");
        PASSWORD_FIELDS.add("confirmPassword");
        PASSWORD_FIELDS.add("rawPassword");
    }

    /**
     * 需要脱敏的字段名（手机号）
     */
    private static final Set<String> PHONE_FIELDS = new HashSet<>();
    static {
        PHONE_FIELDS.add("phone");
        PHONE_FIELDS.add("mobile");
        PHONE_FIELDS.add("phoneNumber");
    }

    /**
     * 需要脱敏的字段名（邮箱）
     */
    private static final Set<String> EMAIL_FIELDS = new HashSet<>();
    static {
        EMAIL_FIELDS.add("email");
        EMAIL_FIELDS.add("mail");
    }

    /**
     * 需要脱敏的字段名（身份证）
     */
    private static final Set<String> ID_CARD_FIELDS = new HashSet<>();
    static {
        ID_CARD_FIELDS.add("idCard");
        ID_CARD_FIELDS.add("idNumber");
        ID_CARD_FIELDS.add("identityCard");
    }

    /**
     * 脱敏字符串值
     */
    public static String mask(String fieldName, String value) {
        if (StrUtil.isEmpty(value)) {
            return value;
        }

        String lowerField = fieldName.toLowerCase();

        // 密码类字段完全隐藏
        if (PASSWORD_FIELDS.stream().anyMatch(f -> lowerField.contains(f.toLowerCase()))) {
            return "******";
        }

        // 手机号脱敏：保留前3后4
        if (PHONE_FIELDS.stream().anyMatch(f -> lowerField.contains(f.toLowerCase()))) {
            return maskPhone(value);
        }

        // 邮箱脱敏：保留前2后@域名
        if (EMAIL_FIELDS.stream().anyMatch(f -> lowerField.contains(f.toLowerCase()))) {
            return maskEmail(value);
        }

        // 身份证脱敏：保留前4后4
        if (ID_CARD_FIELDS.stream().anyMatch(f -> lowerField.contains(f.toLowerCase()))) {
            return maskIdCard(value);
        }

        return value;
    }

    /**
     * 手机号脱敏：138****8000
     */
    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏：ab***@example.com
     */
    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex < 2) {
            return "****" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "****" + email.substring(atIndex);
    }

    /**
     * 身份证脱敏：1101**********1234
     */
    private static String maskIdCard(String idCard) {
        if (idCard.length() < 8) {
            return "****";
        }
        return idCard.substring(0, 4) + "****" + idCard.substring(idCard.length() - 4);
    }
}