# 手动 SQL 迁移

本项目未启用 Flyway，所有 schema 变更通过本目录下脚本手动执行。

## 执行顺序

```
1. V2026071101__create_tenant_tables.sql
2. V2026071102__add_tenant_id_to_sys_user.sql
3. V2026071103__add_tenant_id_to_business_tables.sql
4. V2026071104__backfill_tenant_data.sql
5. V2026071105__init_app_user_tenant.sql
```

## 执行方式

```bash
mysql -u root -p forge_admin < V2026071101__create_tenant_tables.sql
mysql -u root -p forge_admin < V2026071102__add_tenant_id_to_sys_user.sql
...
```

所有脚本使用 `IF NOT EXISTS` / `IGNORE` 关键字，幂等可重跑。
