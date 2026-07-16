package com.forge.modules.system.auth.config;

import com.forge.common.utils.CryptoUtils;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时回填 phone_suffix / email_suffix 明文字段。
 * 由于 phone/email 字段已加密（ENCv1: 前缀），SQL 无法直接解密，
 * 所以在应用启动时通过 Java 读取密文 → 解密 → 写入 suffix 字段。
 *
 * 幂等：suffix 已有值则跳过；每次启动全量扫描，未填的才回填。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class SysUserSuffixBackfillRunner implements ApplicationRunner {

    private static final String ENCRYPTED_PREFIX = "ENCv1:";
    private static final int BATCH_SIZE = 200;

    private final SysUserMapper sysUserMapper;
    private final CryptoUtils cryptoUtils;

    @Override
    public void run(ApplicationArguments args) {
        long total = sysUserMapper.selectCount(null);
        log.info("[用户后缀回填] 开始扫描 sys_user，共 {} 条", total);

        int filledPhone = 0;
        int filledEmail = 0;
        int scanned = 0;
        long lastId = 0L;

        while (true) {
            // 分批查询：必须走 resultMap 触发的 SQL，否则 EncryptTypeHandler 不生效，
            // 读到的 phone/email 是密文，updateById 重新加密导致字段截断
            List<SysUser> batch = sysUserMapper.selectUserRangeById(lastId, BATCH_SIZE);
            if (batch.isEmpty()) break;

            for (SysUser user : batch) {
                lastId = user.getId();
                scanned++;
                // 跳过两个 suffix 都已填充的记录
                if (user.getPhoneSuffix() != null && user.getEmailSuffix() != null) {
                    continue;
                }
                boolean changed = false;
                // phone_suffix
                if (user.getPhoneSuffix() == null) {
                    String phone = user.getPhone();
                    if (phone != null && !phone.isBlank()) {
                        String plain = phone.startsWith(ENCRYPTED_PREFIX) ? cryptoUtils.decrypt(phone) : phone;
                        String digits = plain.replaceAll("\\D", "");
                        if (!digits.isEmpty()) {
                            user.setPhoneSuffix(digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits);
                            changed = true;
                            filledPhone++;
                        }
                    }
                }
                // email_suffix：存加盐 SHA-256 哈希，搜索时同方式哈希后精确匹配
                if (user.getEmailSuffix() == null) {
                    String email = user.getEmail();
                    if (email != null && !email.isBlank()) {
                        String plain = email.startsWith(ENCRYPTED_PREFIX) ? cryptoUtils.decrypt(email) : email;
                        user.setEmailSuffix(cryptoUtils.hashForSearch(plain));
                        changed = true;
                        filledEmail++;
                    }
                }
                if (changed) {
                    sysUserMapper.updateById(user);
                }
            }
        }

        log.info("[用户后缀回填] 完成，扫描 {} 条，填充 phone_suffix={}, email_suffix={}", scanned, filledPhone, filledEmail);
    }
}