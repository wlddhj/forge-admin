package com.forge.common.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * AES-256-GCM 加解密工具 + 随机强密码生成。
 *
 * 密文格式：ENCv1:{base64(iv || ciphertext || tag)}
 * - 前缀 ENCv1: 用于幂等判断与版本识别
 * - iv 长度 12 字节，每次加密随机生成
 * - tag 长度 16 字节，提供完整性保护
 *
 * @author standadmin
 */
public class CryptoUtils {

    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final String ENCRYPTED_PREFIX = "ENCv1:";

    private static final char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] SPECIAL = "!@#$%^&*()-_=+".toCharArray();

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoUtils(String aesKey) {
        byte[] keyBytes = normalizeKey(aesKey);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] normalizeKey(String aesKey) {
        if (aesKey == null || aesKey.isBlank()) {
            throw new IllegalArgumentException("AES 密钥不能为空");
        }
        byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
        // 截断或填充到 32 字节（AES-256）
        byte[] normalized = new byte[32];
        System.arraycopy(raw, 0, normalized, 0, Math.min(raw.length, 32));
        return normalized;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        if (!isEncrypted(ciphertext)) {
            // 未加密的明文直接返回（用于兼容历史数据）
            return ciphertext;
        }
        try {
            String body = ciphertext.substring(ENCRYPTED_PREFIX.length());
            byte[] decoded = Base64.getDecoder().decode(body);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decoded, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * 生成符合等保复杂度要求的随机密码。
     * 保证至少包含：1 个大写、1 个小写、1 个数字、1 个特殊字符。
     */
    public String generateRandomPassword(int length) {
        if (length < 4) {
            throw new IllegalArgumentException("密码长度至少为 4");
        }
        List<Character> chars = new ArrayList<>(length);
        chars.add(pick(UPPERCASE));
        chars.add(pick(LOWERCASE));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIAL));

        char[] all = new char[UPPERCASE.length + LOWERCASE.length + DIGITS.length + SPECIAL.length];
        int pos = 0;
        System.arraycopy(UPPERCASE, 0, all, pos, UPPERCASE.length); pos += UPPERCASE.length;
        System.arraycopy(LOWERCASE, 0, all, pos, LOWERCASE.length); pos += LOWERCASE.length;
        System.arraycopy(DIGITS, 0, all, pos, DIGITS.length); pos += DIGITS.length;
        System.arraycopy(SPECIAL, 0, all, pos, SPECIAL.length);

        for (int i = 4; i < length; i++) {
            chars.add(all[secureRandom.nextInt(all.length)]);
        }
        Collections.shuffle(chars, secureRandom);

        StringBuilder sb = new StringBuilder(length);
        chars.forEach(sb::append);
        return sb.toString();
    }

    private char pick(char[] source) {
        return source[secureRandom.nextInt(source.length)];
    }
}