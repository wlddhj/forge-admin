package com.forge.admin.auth;

import com.forge.admin.forge-adminApplication;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysUserMapper;
import com.forge.admin.modules.system.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 用户查询测试
 */
@SpringBootTest(classes = forge-adminApplication.class)
public class UserQueryTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserService sysUserService;

    @Test
    public void testDirectMapperQuery() {
        System.out.println("=== 测试直接 Mapper 查询 ===");
        SysUser user = sysUserMapper.selectByUsernameSimple("admin");
        System.out.println("查询结果: " + (user != null ? user.getUsername() + " (ID: " + user.getId() + ")" : "null"));
    }

    @Test
    public void testServiceQuery() {
        System.out.println("=== 测试 Service 查询 ===");
        SysUser user = sysUserService.getByUsername("admin");
        System.out.println("查询结果: " + (user != null ? user.getUsername() + " (ID: " + user.getId() + ")" : "null"));
    }
}
