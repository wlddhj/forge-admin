# 手动 SQL 迁移

本项目未启用 Flyway，所有 schema 变更通过本目录下脚本手动执行。

## MySQL 版本兼容性

- **MySQL 5.7 / 8.0 < 8.0.29 / MariaDB 10.2+**：直接执行（当前脚本已兼容）
- **MySQL 8.0.29+**：可选用 `IF NOT EXISTS` 语法获得完全幂等性（未启用）

如果重跑时遇到 `Duplicate column name` 或 `Duplicate key name` 错误，说明对应表已添加过 tenant_id，可使用 `--force` 选项忽略错误继续：

```bash
mysql -u root -p --force forge_admin < V2026071103__add_tenant_id_to_business_tables.sql
```

## 执行顺序

```
1. V2026071101__create_tenant_tables.sql       (CREATE TABLE IF NOT EXISTS，全版本幂等)
2. V2026071102__add_tenant_id_to_sys_user.sql  (注意：DROP INDEX uk_username 需该索引存在)
3. V2026071103__add_tenant_id_to_business_tables.sql
4. V2026071104__backfill_tenant_data.sql       (INSERT IGNORE/UPDATE，全版本幂等)
5. V2026071105__init_app_user_tenant.sql
```

## 执行方式

```bash
mysql -u root -p forge_admin < V2026071101__create_tenant_tables.sql
mysql -u root -p forge_admin < V2026071102__add_tenant_id_to_sys_user.sql
mysql -u root -p forge_admin < V2026071103__add_tenant_id_to_business_tables.sql
mysql -u root -p forge_admin < V2026071104__backfill_tenant_data.sql
mysql -u root -p forge_admin < V2026071105__init_app_user_tenant.sql
```

## 常见问题

**Q: V2026071102 报 `Can't DROP 'uk_username'; check that column/key exists`**

A: 原 sys_user 表没有 `uk_username` 索引（项目历史版本可能用了不同索引名）。先查 `SHOW INDEX FROM sys_user WHERE Key_name LIKE '%username%';`，把脚本中 `uk_username` 改成实际索引名，或者直接注释掉该 DROP 语句。

**Q: V2026071103 报 `Duplicate column name 'tenant_id'`**

A: 该业务表已添加过 tenant_id 列。使用 `--force` 重跑，或单独跳过该表对应的语句。

**Q: V2026071102 重跑失败因为联合索引已存在**

A: 同上，使用 `--force` 或注释掉对应 ALTER 语句。
