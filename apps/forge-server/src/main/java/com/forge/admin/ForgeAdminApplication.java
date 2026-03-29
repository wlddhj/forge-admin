package com.forge.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * forge-admin 应用程序入口
 *
 * @author standadmin
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.forge.admin.modules.*.mapper")
public class ForgeAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeAdminApplication.class, args);
        System.out.println("==========================================");
        System.out.println("   forge-admin 启动成功!");
        System.out.println("   API文档地址: http://localhost:8180/api/doc.html");
        System.out.println("==========================================");
    }
}
