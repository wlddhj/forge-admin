#!/usr/bin/env node

/**
 * 创建插件模块脚手架
 *
 * 使用方法: pnpm run create-plugin <插件名> "<插件描述>"
 * 示例:     pnpm run create-plugin meeting "会议室预定插件"
 *
 * 生成内容（对齐 docs-base/modules/plugin-development-guide.md）：
 *   - forge-module-<name>/ 完整骨架：pom + 实体/Mapper/Service/Controller(@PluginGuard)
 *     + PluginDescriptor(菜单与 scope 声明骨架) + 业务表 DDL(db/migration + sql/upgrade)
 *   - 自动接线：根 pom modules / forge-dependencies BOM / forge-server 依赖
 *   - 前端骨架：api/<name>.ts + views/<name>/index.vue
 * 菜单与权限点由描述符声明、启动 Runner 自动导入——脚手架不生成菜单 SQL。
 */

const fs = require('fs')
const path = require('path')

const colors = {
  reset: '\x1b[0m', green: '\x1b[32m', yellow: '\x1b[33m', red: '\x1b[31m', cyan: '\x1b[36m'
}

function log(message, color = 'reset') {
  console.log(`${colors[color]}${message}${colors.reset}`)
}

// ===== 参数解析与校验 =====
const args = process.argv.slice(2)
if (args.length < 2) {
  log('使用方法: pnpm run create-plugin <插件名> "<插件描述>"', 'yellow')
  log('示例:     pnpm run create-plugin meeting "会议室预定插件"', 'cyan')
  log('详见 docs-base/modules/plugin-development-guide.md', 'cyan')
  process.exit(1)
}

const moduleName = args[0]           // kebab-case，如 meeting
const description = args[1]
const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')

if (!/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/.test(moduleName)) {
  log('错误: 插件名必须为 kebab-case 格式（如 meeting、asset-check）', 'red')
  process.exit(1)
}
// 插件编码 = 名称去横线（License/启停关联键，保持与 demo 一致的短码习惯）
const pluginCode = moduleName.replace(/-/g, '')
// 包名段与类名前缀
const pkgSegment = pluginCode
const pascal = moduleName.split('-').map(w => w[0].toUpperCase() + w.slice(1)).join('')
const snakeTable = `plugin_${pluginCode}_biz_record`

const rootDir = path.resolve(__dirname, '..')
const serverDir = path.join(rootDir, 'apps', 'forge-server')
const moduleDir = path.join(serverDir, `forge-module-${moduleName}`)

if (fs.existsSync(moduleDir)) {
  log(`错误: 模块 forge-module-${moduleName} 已存在`, 'red')
  process.exit(1)
}

log('\n========================================', 'cyan')
log(`  创建插件模块 forge-module-${moduleName}（插件编码 ${pluginCode}）`, 'cyan')
log('========================================\n', 'cyan')

// ===== 1. 目录结构 =====
log('1. 创建目录结构...', 'yellow')
const javaBase = `forge-module-${moduleName}/src/main/java/com/forge/modules/${pkgSegment}`
const dirs = [
  `${javaBase}/controller/admin`,
  `${javaBase}/entity`,
  `${javaBase}/mapper`,
  `${javaBase}/service/impl`,
  `${javaBase}/plugin`,
  `forge-module-${moduleName}/src/main/resources/db/migration`,
]
dirs.forEach(dir => {
  fs.mkdirSync(path.join(serverDir, dir), { recursive: true })
  log(`  ✓ ${dir}`, 'green')
})

// ===== 2. 生成文件 =====
log('\n2. 生成骨架文件...', 'yellow')

function write(relPath, content) {
  fs.writeFileSync(path.join(serverDir, relPath), content)
  log(`  ✓ ${relPath}`, 'green')
}

// pom.xml
write('pom.xml'.replace('pom.xml', `forge-module-${moduleName}/pom.xml`), `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>forge</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>forge-module-${moduleName}</artifactId>
    <name>forge-module-${moduleName}</name>
    <description>${description}（插件，接入指南见 docs-base/modules/plugin-development-guide.md）</description>

    <!-- 插件采用单模块形态（无第二消费方不拆 api/biz）；依赖 forge-server 可传递获得 web/mybatis 能力 -->

    <dependencies>
        <!-- 插件契约：PluginDescriptor 声明 + @PluginGuard 请求闸 + PluginDataApi 数据门面 -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-plugin</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-tenant</artifactId>
        </dependency>
        <!-- Controller/OperationLog -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-web</artifactId>
        </dependency>
        <!-- MyBatis Plus（Mapper/BaseMapper） -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
`)

// 实体
write(`${javaBase}/entity/${pascal}Record.java`, `package com.forge.modules.${pkgSegment}.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ${description}——业务示例实体（替换为你的真实业务表与字段）
 *
 * <p>注意：业务表带 tenant_id 列（MP 租户插件自动隔离），不要加进 ignore-tables。
 *
 * @author forge-admin
 */
@Data
@TableName("${snakeTable}")
public class ${pascal}Record {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务名称（示例字段） */
    private String bizName;

    /** 租户列：插入自动补当前租户，查询自动隔离 */
    private Long tenantId;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
`)

// Mapper
write(`${javaBase}/mapper/${pascal}RecordMapper.java`, `package com.forge.modules.${pkgSegment}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.${pkgSegment}.entity.${pascal}Record;
import org.apache.ibatis.annotations.Mapper;

/**
 * ${description} Mapper（自定义 SQL 一律落 resources/mapper XML——项目规范）
 *
 * @author forge-admin
 */
@Mapper
public interface ${pascal}RecordMapper extends BaseMapper<${pascal}Record> {
}
`)

// Service
write(`${javaBase}/service/impl/${pascal}Service.java`, `package com.forge.modules.${pkgSegment}.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.utils.UserContext;
import com.forge.modules.${pkgSegment}.entity.${pascal}Record;
import com.forge.modules.${pkgSegment}.mapper.${pascal}RecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ${description} Service
 *
 * @author forge-admin
 */
@Service
@RequiredArgsConstructor
public class ${pascal}Service {

    private final ${pascal}RecordMapper recordMapper;

    /** 列表必须返回 PageResult（list/total）——前端列表页铁规范 */
    public PageResult<${pascal}Record> page(int pageNum, int pageSize, String bizName) {
        Page<${pascal}Record> page = recordMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<${pascal}Record>lambdaQuery()
                        .like(bizName != null && !bizName.isBlank(), ${pascal}Record::getBizName, bizName)
                        .orderByDesc(${pascal}Record::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Long create(String bizName) {
        ${pascal}Record record = new ${pascal}Record();
        record.setBizName(bizName);
        record.setCreateBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        record.setCreateTime(LocalDateTime.now());
        recordMapper.insert(record);   // tenant_id 由租户插件自动写入
        return record.getId();
    }

    public void delete(Long id) {
        recordMapper.deleteById(id);
    }
}
`)

// Controller
write(`${javaBase}/controller/admin/${pascal}Controller.java`, `package com.forge.modules.${pkgSegment}.controller.admin;

import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.plugin.guard.PluginGuard;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.${pkgSegment}.entity.${pascal}Record;
import com.forge.modules.${pkgSegment}.plugin.${pascal}PluginDescriptor;
import com.forge.modules.${pkgSegment}.service.impl.${pascal}Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ${description} Controller——类级 @PluginGuard 是插件的请求闸（租户未启用直接拒绝）
 *
 * @author forge-admin
 */
@PluginGuard(${pascal}PluginDescriptor.PLUGIN_CODE)
@Tag(name = "${description}")
@RestController
@RequestMapping("/${pluginCode}/record")
@RequiredArgsConstructor
public class ${pascal}Controller {

    private final ${pascal}Service ${pkgSegment}Service;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('${pluginCode}:record:list')")
    public Result<PageResult<${pascal}Record>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @RequestParam(required = false) String bizName) {
        return Result.success(${pkgSegment}Service.page(pageNum, pageSize, bizName));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('${pluginCode}:record:add')")
    @OperationLog(title = "${description}", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> create(@RequestBody CreateRequest request) {
        return Result.success(${pkgSegment}Service.create(request.getBizName()));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('${pluginCode}:record:delete')")
    @OperationLog(title = "${description}", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        ${pkgSegment}Service.delete(id);
        return Result.success();
    }

    @Data
    public static class CreateRequest {

        @NotBlank
        private String bizName;
    }
}
`)

// 插件描述符（核心）
write(`${javaBase}/plugin/${pascal}PluginDescriptor.java`, `package com.forge.modules.${pkgSegment}.plugin;

import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.PluginMenuNode;
import com.forge.framework.plugin.descriptor.ScopeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ${description}——插件描述符（接入插件体系的核心：编码/版本/菜单树/数据权限声明）。
 *
 * <p>菜单与权限点由本声明驱动启动 Runner 自动导入 sys_menu（超管自动授权），
 * 无需编写菜单 SQL；menuKey 是升级幂等键，一经发布不要改名。
 *
 * @author forge-admin
 */
@Component
public class ${pascal}PluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_CODE = "${pluginCode}";

    @Override
    public String pluginCode() {
        return PLUGIN_CODE;
    }

    @Override
    public String name() {
        return "${description}";
    }

    @Override
    public String description() {
        return "${description}（脚手架生成，请完善描述）";
    }

    @Override
    public String provider() {
        return "forge-admin";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public List<PluginMenuNode> menus() {
        return List.of(
                PluginMenuNode.builder()
                        .menuKey("${pluginCode}-root").parentKey(null)
                        .menuName("${description}").menuType(0)
                        .routePath("/${pluginCode}").componentPath("Layout")
                        .icon("Menu").sortOrder(20)
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("${pluginCode}-record").parentKey("${pluginCode}-root")
                        .menuName("记录管理").menuType(1)
                        .routePath("/${pluginCode}/record")
                        .componentPath("/views/${pluginCode}/record/index")
                        .icon("Document").sortOrder(1)
                        .permission("${pluginCode}:record:list")
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("${pluginCode}-record-add").parentKey("${pluginCode}-record")
                        .menuName("新增记录").menuType(2)
                        .sortOrder(1).permission("${pluginCode}:record:add")
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("${pluginCode}-record-delete").parentKey("${pluginCode}-record")
                        .menuName("删除记录").menuType(2)
                        .sortOrder(2).permission("${pluginCode}:record:delete")
                        .build()
        );
    }

    @Override
    public List<ScopeDefinition> declaredScopes() {
        return List.of(
                // 读类默认放行；写类须管理端勾选或 License 内置授予（勾选界面只渲染本声明集）
                ScopeDefinition.readOnly("user:read", "读员工数据", "选人列表（默认放行）"),
                ScopeDefinition.readOnly("dept:read", "读组织数据", "部门树（默认放行）")
        );
    }
}
`)

// 业务表 DDL（db/migration 归档 + sql/upgrade 手工执行 双份）
const ddl = `-- ${description}（插件 forge-module-${moduleName}）
-- 业务表带 tenant_id（MP 租户插件自动隔离）；菜单/权限点由 PluginDescriptor 声明自动导入，本文件不含菜单 SQL
CREATE TABLE \`${snakeTable}\` (
    \`id\`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    \`biz_name\`    VARCHAR(128) NOT NULL COMMENT '业务名称（示例字段，按需修改）',
    \`tenant_id\`   BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（MP 租户插件自动隔离）',
    \`create_by\`   BIGINT DEFAULT NULL,
    \`create_time\` DATETIME DEFAULT NULL,
    \`update_time\` DATETIME DEFAULT NULL,
    PRIMARY KEY (\`id\`),
    KEY \`idx_tenant\` (\`tenant_id\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='${description}';
`
write(`forge-module-${moduleName}/src/main/resources/db/migration/V${today}01__${moduleName}_init.sql`, ddl)
fs.writeFileSync(path.join(rootDir, 'sql', `upgrade-${today}-${moduleName}-plugin.sql`), ddl)
log(`  ✓ sql/upgrade-${today}-${moduleName}-plugin.sql（存量库手工执行）`, 'green')

// ===== 3. 接线三处 pom =====
log('\n3. 接线 Maven 聚合...', 'yellow')

function replaceIn(file, anchor, insert) {
  const p = path.join(serverDir, file)
  let s = fs.readFileSync(p, 'utf8')
  if (s.includes(`forge-module-${moduleName}`)) {
    log(`  ⚠ ${file} 已含 ${moduleName}，跳过`, 'yellow')
    return
  }
  if (!s.includes(anchor)) {
    log(`  ✗ ${file} 未找到锚点，请手工接线（参考 docs-base/modules/plugin-development-guide.md 第1步）`, 'red')
    return
  }
  s = s.replace(anchor, insert)
  fs.writeFileSync(p, s)
  log(`  ✓ ${file}`, 'green')
}

replaceIn('pom.xml',
  '        <module>forge-server</module>',
  `        <module>forge-module-${moduleName}</module>\n        <module>forge-server</module>`)

replaceIn('forge-dependencies/pom.xml',
  `            <dependency>
                <groupId>com.forge</groupId>
                <artifactId>forge-module-plugin</artifactId>
                <version>\${forge.version}</version>
            </dependency>`,
  `            <dependency>
                <groupId>com.forge</groupId>
                <artifactId>forge-module-plugin</artifactId>
                <version>\${forge.version}</version>
            </dependency>
            <dependency>
                <groupId>com.forge</groupId>
                <artifactId>forge-module-${moduleName}</artifactId>
                <version>\${forge.version}</version>
            </dependency>`)

replaceIn('forge-server/pom.xml',
  `        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-module-plugin</artifactId>
        </dependency>`,
  `        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-module-plugin</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-module-${moduleName}</artifactId>
        </dependency>`)

// ===== 4. 前端骨架 =====
log('\n4. 生成前端骨架...', 'yellow')

const webApiDir = path.join(rootDir, 'apps', 'forge-web', 'src', 'api', pluginCode)
fs.mkdirSync(webApiDir, { recursive: true })
fs.writeFileSync(path.join(webApiDir, 'record.ts'), `import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface ${pascal}Record {
  id: number
  bizName: string
  createTime: number
}

export const ${pluginCode}Api = {
  page: (params: { pageNum: number; pageSize: number; bizName?: string }) =>
    request.get<PageResult<${pascal}Record>>('/${pluginCode}/record/list', { params }),
  create: (bizName: string) => request.post<number>('/${pluginCode}/record', { bizName }),
  remove: (id: number) => request.delete(\`/${pluginCode}/record/\${id}\`)
}
`)
log(`  ✓ apps/forge-web/src/api/${pluginCode}/record.ts`, 'green')

const viewDir = path.join(rootDir, 'apps', 'forge-web', 'src', 'views', pluginCode, 'record')
fs.mkdirSync(viewDir, { recursive: true })
fs.writeFileSync(path.join(viewDir, 'index.vue'), `<template>
  <div class="app-container">
    <!-- 完整列表页范式参考 views/demo/project/index.vue（vxe-table + useTableHeight + 新增弹窗） -->
    <el-empty description="${description}——脚手架占位页，请按 views/demo/project/index.vue 范式完善" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: '${pascal}Record' })
</script>
`)
log(`  ✓ apps/forge-web/src/views/${pluginCode}/record/index.vue`, 'green')

// ===== 汇总 =====
log('\n========================================', 'cyan')
log('  插件骨架创建完成！', 'green')
log('========================================\n', 'cyan')
log('后续步骤：', 'yellow')
log(`  1. 替换业务实体/接口（entity/${pascal}Record.java 及 Service/Controller 示例代码）`)
log(`  2. 完善描述符声明（plugin/${pascal}PluginDescriptor.java：菜单树/scope/图标）`)
log(`  3. 手工执行 sql/upgrade-${today}-${moduleName}-plugin.sql（dev 库无 Flyway）`)
log(`  4. 按 views/demo/project/index.vue 完善前端页面`)
log(`  5. 编译验证: cd apps/forge-server && mvn -pl forge-module-${moduleName} -am compile`)
log(`  6. 重启 8180 → 插件管理页出现新插件 → License → 启用 → 套餐勾选`)
log('\n完整指南: docs-base/modules/plugin-development-guide.md\n', 'cyan')
