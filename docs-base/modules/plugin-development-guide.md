# 插件开发指南（Tutorial）：从零开发一个完整插件

> 目标读者：要在 forge-admin 底座上开发业务插件的开发者。
>
> 本文以一个**会议室预定插件（meeting）**为贯穿示例——包含业务表、增删改查、插件菜单、权限点、数据门面调用的完整闭环。照做即可产出一个可直接运行的插件；每个步骤同时标注 demo 插件（`forge-module-demo`）的对应文件，可随时对照"官方答案"。
>
> 插件体系的架构与运维（License 签发/启停语义/故障排查）见 [plugin-module.md](./plugin-module.md)，本文不再重复。

## 目录

- [0. 前置认知](#0-前置认知)
- [1. 创建插件模块](#1-创建插件模块)
- [2. 业务表与实体](#2-业务表与实体)
- [3. Mapper 与 Service](#3-mapper-与-service)
- [4. Controller：挂上请求闸](#4-controller挂上请求闸)
- [5. 插件描述符：声明菜单与数据权限](#5-插件描述符声明菜单与数据权限)
- [6. SQL 落盘](#6-sql-落盘)
- [7. 前端页面](#7-前端页面)
- [8. 数据门面：读写底座数据](#8-数据门面读写底座数据)
- [9. 重启验证清单](#9-重启验证清单)
- [10. 升级与卸载](#10-升级与卸载)
- [附录 A：常见错误对照表](#附录-a常见错误对照表)
- [附录 B：meeting 插件完整文件清单](#附录-bmeeting-插件完整文件清单)

---

## 0. 前置认知

开发前先建立三个概念，后面所有步骤都围绕它们：

| 概念 | 一句话 | 你的代码里对应什么 |
|------|--------|------------------|
| **描述符** | 插件的"身份证+清单"：编码、版本、菜单树、需要哪些数据权限 | 一个实现 `PluginDescriptor` 的 `@Component` |
| **请求闸** | 租户没启用插件时，你的接口直接被切面拒绝 | Controller 类上的 `@PluginGuard("你的编码")` |
| **数据门面** | 插件读写底座员工/组织数据的唯一合法通道 | 注入 `PluginDataApi`（禁止直连 sys_user/sys_dept 的 Mapper） |

**一张表看懂插件生命周期**：

```
你写代码 → 打包进 forge-server → 启动时 Runner 扫描描述符
   → sys_plugin 注册 + 菜单自动导入 sys_menu（超管自动授权）
   → 平台管理员：License（导入或在线签发）→ 租户授权开关打开 → 套餐勾选菜单
   → 租户用户：菜单可见、接口可用
停用（开关关闭/License 到期）→ 菜单隐藏 + 接口 403，业务数据保留
```

**两条铁律**：

1. 插件的**业务表**带 `tenant_id` 列（MP 租户插件自动隔离，和 demo_biz_project 一样）；只有插件管理那四张 `sys_plugin*` 表才是全局表——不要把自己的业务表加进 `ignore-tables`。
2. 插件的**菜单和权限点不写 SQL**——由描述符声明，启动自动导入；你只写业务表 DDL。

## 1. 创建插件模块

> 🚀 **快捷方式**：`pnpm run create-plugin <插件名> "<插件描述>"` 一键生成下述全部骨架（pom/实体/Mapper/Service/Controller/描述符/DDL 双份/前端占位页）并自动接线三处 pom，生成后从"替换业务代码"开始即可。本章仍完整讲解每一步的原理与写法（脚手架不能替代理解）。

插件 = 一个普通 Maven 模块。**不建议**用 `pnpm run create-module` 脚手架（它生成 api/biz 拆分结构，插件用单模块即可）：

```bash
cd apps/forge-server
mkdir -p forge-module-meeting/src/main/java/com/forge/modules/meeting/{controller/admin,dto,entity,mapper,service/impl,plugin}
mkdir -p forge-module-meeting/src/main/resources
mkdir -p forge-module-meeting/src/test/java/com/forge/modules/meeting
```

**pom.xml**（完整可抄，改两处 artifactId/description）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.forge</groupId>
        <artifactId>forge</artifactId>
        <version>1.0.0</version>
    </parent>
    <artifactId>forge-module-meeting</artifactId>
    <name>forge-module-meeting</name>
    <description>会议室预定插件</description>
    <dependencies>
        <!-- 插件契约：描述符 + 请求闸 + 数据门面 -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-plugin</artifactId>
        </dependency>
        <!-- 需要工作流/底座服务时按需加（demo 依赖了 workflow-biz；meeting 不需要） -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-tenant</artifactId>
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
```

> Controller/MyBatis 等能力经传递依赖（system-biz → starter-plugin 链）已可用；若编译缺类再显式补 `forge-spring-boot-starter-web` / `forge-spring-boot-starter-mybatis`。

**三处聚合**（照 demo 的方式各加一行）：

| 文件 | 加什么 |
|------|--------|
| `apps/forge-server/pom.xml` 的 `<modules>` | `<module>forge-module-meeting</module>`（`<module>forge-server</module>` 之前） |
| `apps/forge-server/forge-dependencies/pom.xml` | `forge-module-meeting` 的 dependencyManagement 条目（抄 demo 条目改 artifactId） |
| `apps/forge-server/forge-server/pom.xml` | `forge-module-meeting` 依赖（抄 demo 依赖） |

对照：`forge-module-demo/pom.xml`。

## 2. 业务表与实体

**表 DDL**（注意 `tenant_id` 必须有；墓碑列如需软删可仿 `sys_plugin*` 的 `deleted BIGINT` 模式，简单业务用硬删也可以）：

```sql
CREATE TABLE `meeting_booking` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_name`   VARCHAR(64) NOT NULL COMMENT '会议室名称',
    `booking_date` DATE NOT NULL COMMENT '预定日期',
    `start_time`  VARCHAR(10) NOT NULL COMMENT '开始时段 09:00',
    `end_time`    VARCHAR(10) NOT NULL COMMENT '结束时段 10:00',
    `topic`       VARCHAR(128) DEFAULT NULL COMMENT '会议主题',
    `booker_id`   BIGINT DEFAULT NULL COMMENT '预定人ID',
    `tenant_id`   BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（MP 租户插件自动隔离）',
    `create_by`   BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT NULL,
    `update_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_date` (`booking_date`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室预定（插件示例）';
```

**实体**（MP 惯例，对照 `demo/entity/BizProject.java`）：

```java
package com.forge.modules.meeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("meeting_booking")
public class MeetingBooking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roomName;

    private LocalDate bookingDate;

    private String startTime;

    private String endTime;

    private String topic;

    private Long bookerId;

    /** 租户列：字段存在即可，MP 租户插件自动注入条件与写入值 */
    private Long tenantId;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

> tenant_id 不需要手动 set——插入时 TenantLineInnerInterceptor 自动补当前租户；查询自动带隔离条件。**不要**把 `meeting_booking` 加进 `ignore-tables`。

## 3. Mapper 与 Service

**Mapper**（无自定义 SQL 时只需空接口；有自定义 SQL 一律落 XML——项目规范）：

```java
package com.forge.modules.meeting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.meeting.entity.MeetingBooking;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MeetingBookingMapper extends BaseMapper<MeetingBooking> {
}
```

**Service**（业务逻辑与底座 Service 无区别——租户隔离、数据权限都由框架层接管）：

```java
package com.forge.modules.meeting.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.utils.UserContext;
import com.forge.modules.meeting.entity.MeetingBooking;
import com.forge.modules.meeting.mapper.MeetingBookingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MeetingBookingService {

    private final MeetingBookingMapper bookingMapper;

    public PageResult<MeetingBooking> page(int pageNum, int pageSize, String roomName, LocalDate date) {
        Page<MeetingBooking> page = bookingMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<MeetingBooking>lambdaQuery()
                        .like(roomName != null && !roomName.isBlank(), MeetingBooking::getRoomName, roomName)
                        .eq(date != null, MeetingBooking::getBookingDate, date)
                        .orderByDesc(MeetingBooking::getBookingDate));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Long create(MeetingBooking booking) {
        booking.setBookerId(UserContext.get() == null ? null : UserContext.get().getUserId());
        booking.setCreateTime(LocalDateTime.now());
        bookingMapper.insert(booking);   // tenant_id 由租户插件自动写入
        return booking.getId();
    }

    public void delete(Long id) {
        bookingMapper.deleteById(id);
    }
}
```

> 列表返回**必须** `PageResult`（list/total），禁裸 MP `Page.getRecords()`——前端列表页铁规范。

## 4. Controller：挂上请求闸

```java
package com.forge.modules.meeting.controller.admin;

import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.plugin.guard.PluginGuard;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.meeting.entity.MeetingBooking;
import com.forge.modules.meeting.plugin.MeetingPluginDescriptor;
import com.forge.modules.meeting.service.impl.MeetingBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@PluginGuard(MeetingPluginDescriptor.PLUGIN_CODE)   // ← 请求闸：租户未启用直接 403
@Tag(name = "会议室预定")
@RestController
@RequestMapping("/meeting/booking")
@RequiredArgsConstructor
public class MeetingBookingController {

    private final MeetingBookingService bookingService;

    @Operation(summary = "分页查询预定")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('meeting:booking:list')")
    public Result<PageResult<MeetingBooking>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @RequestParam(required = false) String roomName,
                                                   @RequestParam(required = false) LocalDate date) {
        return Result.success(bookingService.page(pageNum, pageSize, roomName, date));
    }

    @Operation(summary = "新增预定")
    @PostMapping
    @PreAuthorize("hasAuthority('meeting:booking:add')")
    @OperationLog(title = "会议室预定", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> create(@Valid @RequestBody BookingCreateRequest request) {
        return Result.success(bookingService.create(request.toEntity()));
    }

    @Operation(summary = "取消预定")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('meeting:booking:delete')")
    @OperationLog(title = "会议室预定", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        bookingService.delete(id);
        return Result.success();
    }

    @Data
    public static class BookingCreateRequest {
        @NotBlank private String roomName;
        @NotNull private LocalDate bookingDate;
        @NotBlank private String startTime;
        @NotBlank private String endTime;
        private String topic;

        MeetingBooking toEntity() {
            MeetingBooking booking = new MeetingBooking();
            booking.setRoomName(roomName);
            booking.setBookingDate(bookingDate);
            booking.setStartTime(startTime);
            booking.setEndTime(endTime);
            booking.setTopic(topic);
            return booking;
        }
    }
}
```

要点：
- `@PluginGuard` 挂**类**上，值引用描述符常量（防手抖两处不一致）；
- `@PreAuthorize` 的权限码（`meeting:booking:*`）与第 5 步描述符菜单里的 `permission` 一致；
- 非幂等端点（新增/删除）挂 `@OperationLog`；`@RepeatSubmit` 只挂导入类非幂等动作，本例不需要。

对照：`demo/controller/admin/ProjectApprovalController.java`。

## 5. 插件描述符：声明菜单与数据权限

插件接入的**核心一步**——这一步之后，注册、菜单、权限点全部自动化：

```java
package com.forge.modules.meeting.plugin;

import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.PluginMenuNode;
import com.forge.framework.plugin.descriptor.ScopeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MeetingPluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_CODE = "meeting";

    @Override
    public String pluginCode() { return PLUGIN_CODE; }          // 全局唯一，kebab-case

    @Override
    public String name() { return "会议室预定"; }

    @Override
    public String description() { return "会议室查询与预定管理"; }

    @Override
    public String provider() { return "your-team"; }

    @Override
    public String version() { return "1.0.0"; }                  // SemVer，与 pom 版本一致

    @Override
    public List<PluginMenuNode> menus() {
        return List.of(
                // 目录（顶级，parentKey=null）
                PluginMenuNode.builder()
                        .menuKey("meeting-root").parentKey(null)
                        .menuName("会议室").menuType(0)
                        .routePath("/meeting").componentPath("Layout")
                        .icon("OfficeBuilding").sortOrder(10)
                        .build(),
                // 菜单页
                PluginMenuNode.builder()
                        .menuKey("meeting-booking").parentKey("meeting-root")
                        .menuName("预定管理").menuType(1)
                        .routePath("/meeting/booking")
                        .componentPath("/views/meeting/booking/index")
                        .icon("Calendar").sortOrder(1)
                        .permission("meeting:booking:list")
                        .build(),
                // 按钮权限点（menuType=2，只带 permission）
                PluginMenuNode.builder()
                        .menuKey("meeting-booking-add").parentKey("meeting-booking")
                        .menuName("新增预定").menuType(2)
                        .sortOrder(1).permission("meeting:booking:add")
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("meeting-booking-delete").parentKey("meeting-booking")
                        .menuName("取消预定").menuType(2)
                        .sortOrder(2).permission("meeting:booking:delete")
                        .build()
        );
    }

    @Override
    public List<ScopeDefinition> declaredScopes() {
        return List.of(
                // 读类默认放行；写类须管理端勾选或 License 内置授予
                ScopeDefinition.readOnly("user:read", "读员工数据", "预定人选人列表（默认放行）"),
                ScopeDefinition.writable("user:write", "写员工数据", "把预定同步回员工日程（须授予）")
        );
    }
}
```

**字段速查**：

| 字段 | 规则 |
|------|------|
| `pluginCode` | kebab-case 全局唯一；License 与启停都关联它 |
| `version` | 必须 SemVer（`1.0.0`）；变更自动记操作日志 |
| `menuKey` | 插件内唯一，**幂等键**——升级时改菜单靠它保 id 不断角色关联，**一经发布不要改**（要改=删旧 key 加新 key） |
| `parentKey` | 只能指向同一声明内的 menuKey 或 null（顶级）；成环/悬空启动报错但不影响其他插件 |
| `componentPath` | 前端组件路径（`/views/...` 开头，与第 7 步文件位置一致） |
| `permission` | RBAC 权限码，须与 Controller `@PreAuthorize` 一致 |
| `declaredScopes` | 数据权限**上限**：勾选界面/在线签发/License 校验都以它为边界，运行期还会再裁剪一次 |

对照：`demo/plugin/DemoPluginDescriptor.java`。

## 6. SQL 落盘

插件自己的 SQL 三处（照 demo 惯例）：

| 位置 | 内容 | 何时执行 |
|------|------|---------|
| `forge-module-meeting/src/main/resources/db/migration/V20261113__meeting_init.sql` | 业务表 DDL（归档性质） | — |
| `sql/upgrade-20261113-meeting-plugin.sql`（仓库根） | 同一份 DDL | **存量库手工执行**（本项目无 Flyway） |
| 全新安装 | 把 DDL 追加进 `sql/init-meeting.sql`（仿 init-demo.sql：只写业务表，**菜单/角色授权都不写**——Runner 自动） | 全新库初始化时 |

菜单、权限点、超管授权**永远不写 SQL**——描述符声明 → 启动自动导入。这是与旧式模块（如 upgrade-20261029 给 demo 手工插菜单）的关键区别。

## 7. 前端页面

**位置约定**：`apps/forge-web/src/views/meeting/booking/index.vue`（与描述符 `componentPath="/views/meeting/booking/index"` 对应；插件页面随 forge-web 主包编译，停用仅隐藏路由）。

**API 封装** `src/api/meeting/booking.ts`：

```typescript
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface MeetingBooking {
  id: number
  roomName: string
  bookingDate: string
  startTime: string
  endTime: string
  topic: string | null
  bookerId: number | null
  createTime: string
}

export const meetingApi = {
  page: (params: { pageNum: number; pageSize: number; roomName?: string; date?: string }) =>
    request.get<PageResult<MeetingBooking>>('/meeting/booking/list', { params }),
  create: (data: Omit<MeetingBooking, 'id' | 'createTime'>) =>
    request.post<number>('/meeting/booking', data),
  remove: (id: number) => request.delete(`/meeting/booking/${id}`)
}
```

**页面骨架** `index.vue`（vxe-table + 搜索卡 + 新增弹窗，遵守列表页铁规范；完整范式直接抄 `views/demo/project/index.vue` 改字段）：

```vue
<template>
  <div class="app-container">
    <!-- 搜索卡 + vxe-table（id 必填，:height="tableHeight"，useTableHeight） -->
    <!-- 列：roomName / bookingDate / startTime~endTime / topic / 操作(新增 v-permission="'meeting:booking:add'"、取消) -->
    <!-- 新增/编辑用 el-dialog 表单；分页用 PageResult.total -->
  </div>
</template>

<script setup lang="ts">
import { useTableHeight } from '@/composables/useTableHeight'
// ... 与 demo/project/index.vue 同构，换字段与 api
</script>
```

插件页面与普通业务页面**写法完全一样**（无任何插件感知代码）——可见性完全由后端菜单过滤控制。

## 8. 数据门面：读写底座数据

插件需要员工/组织数据时，**唯一合法通道**是 `PluginDataApi`（直连 sys_user/sys_dept Mapper 属于违反架构约束）：

```java
@Service
@RequiredArgsConstructor
public class MeetingBookingService {

    private final PluginDataApi pluginDataApi;   // 契约层接口，实现由插件管理模块提供

    /** 选人列表：读操作默认放行（声明 user:read 即可，无需授予） */
    public List<PluginUserDTO> listBookerCandidates() {
        return pluginDataApi.listUsers(MeetingPluginDescriptor.PLUGIN_CODE,
                PluginUserQuery.builder().status(1).build());
    }

    /** 把预定同步回员工日程：写操作——须管理端勾选授予 user:write，否则抛 SCOPE_DENIED 并记审计 */
    public void syncToUserCalendar(Long bookingId) {
        // pluginDataApi.updateUserDept(PLUGIN_CODE, userId, deptId);  // 示例 API
    }
}
```

三条规则：
1. **读**（listUsers/pageUsers/getDeptTree）默认放行；
2. **写**（如 updateUserDept）必须：插件声明了该 scope（第 5 步）**且**管理端勾选或 License 内置授予；
3. 门面在请求链路内自动按当前租户过滤数据——不需要也不能自己传租户。

## 9. 重启验证清单

编译打包重启 8180 后，按序核对（每步不通看 [plugin-module.md 第 10 节](./plugin-module.md#10-故障排查)）：

```bash
cd apps/forge-server && mvn -pl forge-module-meeting -am compile   # ① 编译过
```

| # | 检查点 | 预期 |
|---|--------|------|
| ② | 启动日志 | `插件[meeting]注册成功: 版本 1.0.0` / `菜单同步完成` |
| ③ | DB `sys_plugin` | meeting 行，global_status=1，menus_synced=1 |
| ④ | DB `sys_menu` | plugin_code='meeting' 的 4 行（id 已自动生成、超管角色已授权） |
| ⑤ | 执行 `sql/upgrade-*.sql`（业务表）后调接口 | 未启用时：`GET /admin-api/meeting/booking/list` → 60xx PLUGIN_DISABLED（请求闸生效） |
| ⑥ | License | 插件管理 → License 管理 → 在线签发（选 meeting）或导入 CLI 文件 |
| ⑦ | 启用 | 租户授权抽屉 → meeting → 打开开关 |
| ⑧ | 套餐 | 套餐管理 → 编辑 default 套餐 → 勾选「会议室」菜单 |
| ⑨ | 用户侧 | 超管刷新即见「会议室」菜单；租户用户需角色勾选 meeting 菜单 |
| ⑩ | 停用回归 | 关闭开关 → 菜单消失、接口 403、`meeting_booking` 数据原样保留 |

## 10. 升级与卸载

**升级**（独立演进，不碰底座代码）：

1. 模块 `version()` 与 pom 版本号同步递增（如 `1.1.0`）；
2. 新表/加列：新写 `sql/upgrade-*.sql` + 模块内 `db/migration/V*.sql`，存量库手工执行；
3. 菜单变更：直接改描述符 `menus()`——**已有 menuKey 只改属性（保 id 断不了授权）；新增/删除节点用新/弃 key**；
4. 重新打包重启：启动日志出现 `插件[meeting]版本变化: 1.0.0 → 1.1.0`，操作日志自动留痕（无人工确认环节）。

**卸载**（物理移除）：forge-server pom 移除依赖 → 删除描述符后**最后一次启动会自动清理该插件菜单**（存量菜单按 plugin_code 全量清除）→ 再移除模块目录；业务表数据保留（是否 DROP 由运维决策）。

## 附录 A：常见错误对照表

| 症状 | 原因 → 处理 |
|------|------------|
| 启动报 `菜单声明无效: parentKey=xxx 不在声明集内` | parentKey 拼错或引用了已删节点 → 对照描述符树 |
| 启动报 `菜单声明存在父子环` | parentKey 成环 → 画一遍树检查 |
| 注册失败但不影响启动 | 看该插件 ERROR 日志（版本非 SemVar / 编码为空等），per-plugin 隔离 |
| 接口 403 `插件未启用或授权已失效` | 正常的请求闸语义：先 License → 启用 → 套餐勾选 |
| 写数据门面报 6012 SCOPE_DENIED | scope 未授予：租户授权抽屉展开行勾选，或 License 内置 |
| 勾选界面看不到想要的 scope | `declaredScopes()` 没声明——界面只渲染声明集 |
| 租户用户看不到菜单 | 三层依次查：插件启用？套餐勾选？角色勾选？ |
| 单测 `insert(any())` 编译歧义 | MP 3.5.18 重载——写 `any(YourEntity.class)` |
| 改依赖模块后单测跑的老行为 | `.m2` 旧 jar——先 `mvn -pl 被改模块 install` 再跑本模块测试 |

## 附录 B：meeting 插件完整文件清单

```
forge-module-meeting/
├── pom.xml
├── src/main/java/com/forge/modules/meeting/
│   ├── controller/admin/MeetingBookingController.java   # @PluginGuard + RBAC
│   ├── entity/MeetingBooking.java                       # 含 tenantId 字段
│   ├── mapper/MeetingBookingMapper.java
│   ├── plugin/MeetingPluginDescriptor.java              # ★ 核心：菜单+scope 声明
│   └── service/impl/MeetingBookingService.java          # + PluginDataApi 注入(按需)
├── src/main/resources/db/migration/V20261113__meeting_init.sql
└── src/test/java/...                                    # 业务测试照常写

apps/forge-web/src/
├── api/meeting/booking.ts
└── views/meeting/booking/index.vue                      # componentPath 对应位

sql/upgrade-20261113-meeting-plugin.sql                  # 业务表 DDL（手工执行）
改动四处 pom：根 modules / forge-dependencies BOM / forge-server 依赖 /（无其他）
```

更多参考：架构与运维细节 [plugin-module.md](./plugin-module.md) · 标准范例源码 `apps/forge-server/forge-module-demo` · License 签发工具 [tools/license-cli/README.md](../../tools/license-cli/README.md)。
