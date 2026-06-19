package com.forge.modules.system.auth.util;

import com.forge.modules.system.auth.properties.PasswordPolicyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    private PasswordValidator validator;

    @BeforeEach
    void setUp() {
        PasswordPolicyProperties props = new PasswordPolicyProperties();
        validator = new PasswordValidator(props);
    }

    @Test
    void validate_shouldRejectShortPassword() {
        var result = validator.validate("Aa1!");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("长度"));
    }

    @Test
    void validate_shouldAcceptCompliantPassword() {
        var result = validator.validate("GoodPass#2026");
        assertTrue(result.isSuccess(), result.getMessage());
    }

    @Test
    void validate_shouldRejectMissingUppercase() {
        var result = validator.validate("goodpass#2026");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("大写"));
    }

    @Test
    void validate_shouldRejectMissingLowercase() {
        var result = validator.validate("GOODPASS#2026");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("小写"));
    }

    @Test
    void validate_shouldRejectMissingDigit() {
        var result = validator.validate("GoodPass!XYZ");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("数字"));
    }

    @Test
    void validate_shouldRejectMissingSpecialChar() {
        var result = validator.validate("GoodPass2026");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("特殊"));
    }

    @Test
    void validate_shouldRejectTooLong() {
        var result = validator.validate("Aa1!" + "a".repeat(40));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("长度"));
    }
}
