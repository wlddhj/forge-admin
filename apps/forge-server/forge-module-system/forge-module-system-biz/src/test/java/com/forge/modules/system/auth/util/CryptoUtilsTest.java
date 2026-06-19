package com.forge.modules.system.auth.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {

    private CryptoUtils cryptoUtils;

    @BeforeEach
    void setUp() {
        // 32 字节测试密钥（AES-256）
        cryptoUtils = new CryptoUtils("0123456789abcdef0123456789abcdef");
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertextsForSamePlaintext() {
        String plaintext = "13800138000";
        String cipher1 = cryptoUtils.encrypt(plaintext);
        String cipher2 = cryptoUtils.encrypt(plaintext);
        assertNotEquals(cipher1, cipher2, "相同明文加密结果应不同（因 IV 随机）");
        assertTrue(cipher1.startsWith("ENCv1:"), "密文应以 ENCv1: 前缀标识");
    }

    @Test
    void decrypt_shouldRecoverPlaintext() {
        String plaintext = "user@example.com";
        String ciphertext = cryptoUtils.encrypt(plaintext);
        String recovered = cryptoUtils.decrypt(ciphertext);
        assertEquals(plaintext, recovered);
    }

    @Test
    void decrypt_shouldBeIdempotent() {
        String plaintext = "13800138000";
        String ciphertext = cryptoUtils.encrypt(plaintext);
        // 对已加密内容再次 decrypt 不应抛出异常
        assertEquals(plaintext, cryptoUtils.decrypt(ciphertext));
    }

    @Test
    void isEncrypted_shouldRecognizeEncryptedValue() {
        String ciphertext = cryptoUtils.encrypt("test");
        assertTrue(cryptoUtils.isEncrypted(ciphertext));
        assertFalse(cryptoUtils.isEncrypted("13800138000"));
        assertFalse(cryptoUtils.isEncrypted(""));
        assertFalse(cryptoUtils.isEncrypted(null));
    }

    @Test
    void generateRandomPassword_shouldMeetComplexityRequirements() {
        for (int i = 0; i < 100; i++) {
            String password = cryptoUtils.generateRandomPassword(12);
            assertEquals(12, password.length());
            assertTrue(password.matches(".*[A-Z].*"), "应包含大写字母: " + password);
            assertTrue(password.matches(".*[a-z].*"), "应包含小写字母: " + password);
            assertTrue(password.matches(".*\\d.*"), "应包含数字: " + password);
            assertTrue(password.chars().anyMatch(c -> "!@#$%^&*()-_=+".indexOf(c) >= 0),
                    "应包含特殊字符: " + password);
        }
    }
}
