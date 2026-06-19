package com.forge.modules.system.auth.util;

import com.forge.modules.system.auth.properties.PasswordPolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordValidator {

    private final PasswordPolicyProperties properties;

    public Result validate(String password) {
        if (password == null || password.isBlank()) {
            return Result.fail("密码不能为空");
        }
        int len = password.length();
        if (len < properties.getMinLength()) {
            return Result.fail("密码长度不能少于 " + properties.getMinLength() + " 位");
        }
        if (len > properties.getMaxLength()) {
            return Result.fail("密码长度不能超过 " + properties.getMaxLength() + " 位");
        }
        if (properties.isRequireUppercase() && !password.matches(".*[A-Z].*")) {
            return Result.fail("密码必须包含至少 1 个大写字母");
        }
        if (properties.isRequireLowercase() && !password.matches(".*[a-z].*")) {
            return Result.fail("密码必须包含至少 1 个小写字母");
        }
        if (properties.isRequireDigit() && !password.matches(".*\\d.*")) {
            return Result.fail("密码必须包含至少 1 个数字");
        }
        if (properties.isRequireSpecial()) {
            String specials = properties.getSpecialChars();
            boolean hasSpecial = password.chars().anyMatch(c -> specials.indexOf(c) >= 0);
            if (!hasSpecial) {
                return Result.fail("密码必须包含至少 1 个特殊字符（" + specials + "）");
            }
        }
        return Result.ok();
    }

    public static class Result {
        private final boolean success;
        private final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static Result ok() {
            return new Result(true, "密码符合策略");
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
