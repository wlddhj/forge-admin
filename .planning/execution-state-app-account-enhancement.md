# App 端账号管理增强 - 执行进度

**日期：** 2026-06-16
**状态：** 进行中，Context 耗尽暂停

## 相关文件

| 类型 | 路径 |
|------|------|
| Spec | `docs/superpowers/specs/2026-06-15-app-account-enhancement-design.md` |
| Plan | `docs/superpowers/plans/2026-06-16-app-account-enhancement.md` |

## 已完成 Task

| Task | 描述 | Commit | 状态 |
|------|------|--------|------|
| Task 1 | 数据库迁移 + AppUser 实体扩展 | `5978e42` | ✅ DONE |

**已完成文件：**
- `apps/forge-server/forge-server/src/main/resources/db/migration/V2026061601__app_user_extend.sql` (新增)
- `apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/AppUser.java` (修改)

## 剩余 Task (19 个)

| Task ID | 描述 | 难度 |
|---------|------|------|
| Task 2 | DTO 与 ResultCode 扩展 | 机械 |
| Task 3 | SmsService 接口 + 配置 + Mock 实现 | 机械 |
| Task 4 | SmsCodeManager 验证码管理 | 机械 |
| Task 5 | AppUserService 扩展 | 集成 |
| Task 6 | AOP 注解 + 切面 | 机械 |
| Task 7 | app_user_sessions SET 维护 | 集成 |
| Task 8 | AppAttachmentController | 机械 |
| Task 9 | AppUserController 扩展 | 集成 |
| Task 10 | AppUserAdminController | 机械 |
| Task 11 | Admin 前端 API + 列表页 | 前端 |
| Task 12 | Admin 前端详情抽屉 | 前端 |
| Task 13 | uni-app 工程脚手架 | 新工程 |
| Task 14 | uni-app stores + api 封装 | 机械 |
| Task 15 | uni-app 登录页 | 前端 |
| Task 16 | uni-app 个人中心页 | 前端 |
| Task 17 | uni-app 编辑资料页 | 前端 |
| Task 18 | uni-app 绑定手机号 | 前端 |
| Task 19 | uni-app 注销页 | 前端 |
| Task 20 | 端到端验证 | 验证 |

## 下次启动指令

```
继续执行 Task 2-20，使用 subagent-driven-development 模式。

从 Task 2 开始：
"继续执行 docs/superpowers/plans/2026-06-16-app-account-enhancement.md 的 Task 2-20，使用 subagent-driven-development 模式。已完成 Task 1（commit 5978e42）。"
```

## 模型选择建议

| Task 类型 | 建议模型 |
|----------|----------|
| 机械 (2-4, 6, 8, 10, 14) | haiku |
| 集成 (5, 7, 9) | sonnet |
| 前端 (11-12, 15-19) | sonnet 或 frontend-developer |
| 新工程 (13) | sonnet |
| 验证 (20) | sonnet |

## Task 依赖关系

- Task 5 依赖 Task 2 (DTO)
- Task 9 依赖 Task 3, 4, 5, 6 (SmsService + SmsCodeManager + AppUserService + AOP)
- Task 10 依赖 Task 5 (AppUserService)
- Task 11-12 依赖 Task 10 (后台 API)
- Task 14-19 依赖 Task 13 (uni-app 脚手架)
- Task 20 依赖全部

建议执行顺序：Task 2-10（后端） → Task 11-12（admin 前端） → Task 13-19（uni-app） → Task 20（验证）