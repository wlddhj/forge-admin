# 全库租户一致性审计报告

> 审计日期:2026-10-25 | 审计范围:sql/ 下全部 5 个建库脚本共 63 张表 | 方法:DDL 列解析 × application.yml ignore-tables 比对

## 一、结论(Executive Summary)

**多租户体系当前是半成品**:租户框架层(TenantLineInnerInterceptor 拦截器、TenantContextHolder 上下文、sys_tenant 租户管理表、套餐体系)齐备,但 **63 张表中 29 张业务表缺少 tenant_id 列且不在租户忽略清单**——`TENANT_ENABLE=true` 部署下系统无法运行(用户登录查询 sys_user 即 SQL 报错)。

当前唯一可用形态为单租户部署(TENANT_ENABLE=false,默认值),拦截器不注册,所有表正常读写。

## 二、缺陷机理

`TenantDatabaseInterceptor.ignoreTable()` 对**非忽略表无条件返回 false**,拦截器随即对所有 SQL(含 INSERT)注入 `tenant_id = ?`。表无该列时 MySQL 直接报 Unknown column——fail-closed(不泄露数据,但功能全面不可用)。

## 三、审计明细(29 张问题表)

### init.sql(18 张,影响最重——核心账号与组织体系)

| 表 | 影响 |
|----|------|
| sys_user / sys_user_role / sys_user_position / sys_user_password_history / sys_social_user | 用户账号体系:多租户下登录即失败 |
| sys_dept / sys_role_dept | 组织与数据权限:完全不可用 |
| sys_notice / sys_position / sys_attachment | 公告/岗位/附件不可用 |
| sys_login_log / sys_operation_log / sys_job_log | 审计日志不可写 |
| sys_key_sequence | 发号器(主键生成)不可用,连带全部写入失败 |
| app_user / oauth2_registered_client / oauth2_authorization / oauth2_authorization_consent | App 端账号与 OAuth2 授权体系 |

### init-screen.sql(4 张):sys_screen / sys_screen_data_source / sys_screen_sql_whitelist / sys_screen_role——大屏模块整体。

### init-workflow.sql(7 张):wf_category / wf_form / wf_process_ext / wf_process_expression / wf_process_listener / wf_approval_comment / wf_ai_approval_record——工作流扩展表(注:flw_ 前缀的 FlowLong 引擎表由框架自带租户支持,不在问题集)。

### 已治理对照

- AI 模块:2026-10-24 已完成治理(4 表补列 + ai_model_config/ai_sql_whitelist 进忽略清单,见 upgrade-20261024-ai-tenant.sql)。
- 既有忽略清单(15 张):sys_menu/sys_role/sys_dict_*/sys_config/sys_job/sys_tenant*/sys_print_template/ai_model_config/ai_sql_whitelist——均为平台共享语义,合理。

## 四、治理路径选项(供决策)

### 路径 A:全量租户化(真 SaaS 多租户)
- **内容**:29 表 ALTER 补列(存量归属租户 1)+ 逐表租户语义确认 + 全模块回归
- **预估**:DDL+实体机械化部分 1-2 天;**语义决策是大头**——sys_user 的租户归属与登录流程(登录需先选租户)、sys_key_sequence 是否按租户隔离发号、oauth2 表的租户维度、每张表的列表页/权限点是否加租户过滤,均需业务确认
- **风险**:登录/权限主链路改造,回归面覆盖全系统;建议分批(先只读表→日志表→核心账号表)

### 路径 B:显式单租户化(承认现状)
- **内容**:不补列;将 29 表全部加入 ignore-tables;文档与 README 明示"单租户部署";TENANT_ENABLE 保留代码但标注 experimental
- **预估**:半天(清单+文档+冒烟)
- **风险**:几乎为零;代价是放弃短期 SaaS 多租户能力

### 路径 C:维持现状 + 文档警示
- **内容**:仅本报告;README 增加部署警示(TENANT_ENABLE 必须为 false)
- **风险**:配置误开即全挂,且无文档提示(现状)

**建议**:若 SaaS 多租户在产品路线图上 → 路径 A 并单独立项(先语义清单评审);若 12 个月内无计划 → 路径 B(半天消除隐患且诚实)。

## 五、附录:审计方法复现

```bash
# 表列解析 × ignore-tables 比对(本报告用 python re 解析 CREATE TABLE 块)
grep -l "CREATE TABLE" sql/init*.sql
# 拦截器行为源:forge-framework/forge-spring-boot-starter-tenant/.../TenantDatabaseInterceptor.java
```

相关任务:ai-tenant-audit(2026-10-24,AI 模块治理先例,方法与升级脚本模板可复用)。
