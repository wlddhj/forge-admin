-- 套餐菜单过滤接入：补齐默认套餐(id=1)缺失的菜单关联
-- 背景：init.sql 仅在首次初始化时给默认套餐绑定当时的全量菜单，此后各 migration 新增菜单
--   均未同步 sys_tenant_package_menu；接入套餐过滤后非平台超管用户将丢失这些菜单。
-- 口径与 init.sql 一致：默认套餐含全部未删除菜单，品牌设置(2540/2541)为平台全局配置不对租户套餐开放。
-- 注意：自定义套餐（运营手工收窄的商业边界）不补数。
INSERT IGNORE INTO `sys_tenant_package_menu` (`tenant_package_id`, `menu_id`)
SELECT 1, m.`id`
FROM `sys_menu` m
WHERE m.`deleted` = 0
  AND m.`id` NOT IN (2540, 2541)
  AND NOT EXISTS (
      SELECT 1 FROM `sys_tenant_package_menu` tpm
      WHERE tpm.`tenant_package_id` = 1 AND tpm.`menu_id` = m.`id`
  );
