package com.forge.modules.contract.listener;

import com.forge.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动预置：默认租户（tenantId=1）无 contract_approval 定义时以系统身份自动部署。
 * 其他租户在首次提交合同时由 {@link ContractModelProvisioner} 懒部署（定义表租户隔离）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractModelInitializer implements ApplicationRunner {

    private final ContractModelProvisioner provisioner;

    @Override
    public void run(ApplicationArguments args) {
        UserContext systemUser = new UserContext();
        systemUser.setUserId(1L);
        systemUser.setUsername("admin");
        systemUser.setNickname("系统管理员");
        systemUser.setTenantId(1L);
        UserContext.set(systemUser);
        try {
            provisioner.ensureModelExists();
        } catch (Exception e) {
            // 预置失败不阻断启动：提交时懒部署兜底
            log.warn("合同默认审批模型启动预置失败（提交时将重试）: {}", e.getMessage());
        } finally {
            UserContext.clear();
        }
    }
}
