-- ============================================================
-- IM 渠道配置并发双行修复：唯一索引 uk_tenant_channel | 2026-11-12
-- 背景：ImChannelConfigService.upsert 先查后插，管理端并发保存同
--       (tenant_id, channel) 时双双判空、双双 insert，落出两行配置；
--       推送链路 configOf（selectOne）取行不确定，凭据/启停漂移。
-- 治理：
--   1. 存量去重（手工指引见下）；
--   2. deleted TINYINT → BIGINT（墓碑惯用法要求，见步骤 3 说明）；
--   3. 存量墓碑行统一迁移为 deleted = id；
--   4. 加唯一索引 uk_tenant_channel(tenant_id, channel, deleted)。
-- 唯一索引形态说明：实体 WfImChannelConfig 为 @TableLogic 逻辑删除，
--   唯一键必须带 deleted 列；同键墓碑行的惯用法是"删除时 deleted 置本行
--   id"（代码已同步实现），而非置 1——若墓碑统一置 1，同 (tenant_id,
--   channel) 的第二条墓碑即撞唯一索引，"删除-重建-再删除"会失败。
--   deleted 用 BIGINT 正是为容纳行 id；有效行仍恒为 deleted = 0，
--   唯一键等价于"同租户同渠道至多一条有效行"。
-- 注意：
--   1. MySQL ALTER 不支持 IF NOT EXISTS，本脚本仅执行一次；
--      重复执行报 Duplicate key name 'uk_tenant_channel' 可忽略。
--   2. dev 库无 Flyway，须手工执行。
-- ============================================================

-- 1. 存量去重（先查后删，配置表行数极小，人工核对更稳妥）
-- 1.1 找出同租户同渠道多于一条的有效行：
--     SELECT tenant_id, channel, COUNT(*) AS dup
--     FROM wf_im_channel_config
--     WHERE deleted = 0
--     GROUP BY tenant_id, channel HAVING COUNT(*) > 1;
-- 1.2 每组保留 id 最大一行（最新保存的凭据为准），其余物理删除：
--     DELETE c FROM wf_im_channel_config c
--     JOIN (
--         SELECT tenant_id, channel, MAX(id) AS keep_id
--         FROM wf_im_channel_config
--         WHERE deleted = 0
--         GROUP BY tenant_id, channel HAVING COUNT(*) > 1
--     ) d ON c.tenant_id = d.tenant_id AND c.channel = d.channel
--        AND c.id < d.keep_id;

-- 2. deleted 放宽为 BIGINT（墓碑值=行 id，TINYINT 容不下）
ALTER TABLE `wf_im_channel_config`
  MODIFY COLUMN `deleted` BIGINT NOT NULL DEFAULT 0
  COMMENT '逻辑删除(0:正常;非0:已删除,墓碑值=本行id)';

-- 3. 存量墓碑行迁移为 deleted = id（旧值恒为 1，同键多墓碑会撞步骤 4 的唯一索引）
UPDATE `wf_im_channel_config` SET `deleted` = `id` WHERE `deleted` <> 0;

-- 4. 唯一索引：同租户同渠道至多一条有效行（deleted=0）；
--    原 idx_tenant_channel 为其最左前缀，冗余删除
ALTER TABLE `wf_im_channel_config`
  DROP INDEX `idx_tenant_channel`,
  ADD UNIQUE INDEX `uk_tenant_channel` (`tenant_id`, `channel`, `deleted`);
