package com.forge.admin.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码编码器测试
 */
public class PasswordEncoderTest {

    @Test
    public void testBCrypt() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String password = "123456";
        String hash1 = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";
        String hash2 = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

        System.out.println("Testing password: " + password);
        System.out.println("Hash1 match: " + encoder.matches(password, hash1));
        System.out.println("Hash2 match: " + encoder.matches(password, hash2));

        // 生成新的hash
        String newHash = encoder.encode(password);
        System.out.println("New hash: " + newHash);
        System.out.println("New hash match: " + encoder.matches(password, newHash));
    }
}
