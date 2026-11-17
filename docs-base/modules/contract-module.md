# 合同模板模块（forge-module-contract）使用手册

> 适用范围：`apps/forge-server/forge-module-contract`（后端 api/biz 两子模块）、`apps/forge-web/src/views/contract`（前端）、`sql/init-contract.sql`（全新建库）与 `sql/upgrade-*-contract*.sql`（存量库升级）。
>
> 形态：**业务模块 + FlowLong 审批集成**——模板在线设计（tiptap3 富文本）、合同按模板起草、提交走审批流，审批通过自动定稿归档 PDF。

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 架构设计](#2-架构设计)
- [3. 前端页面](#3-前端页面)
- [4. API 端点](#4-api-端点)
- [5. 数据库表结构](#5-数据库表结构)
- [6. 关键行为口径](#6-关键行为口径)
- [7. 部署与升级](#7-部署与升级)
- [8. 注意事项与常见问题](#8-注意事项与常见问题)

---

## 1. 模块概述

| 能力 | 说明 |
|------|------|
| 模板在线编辑 | tiptap3 富文本编辑器（加粗/斜体/下划线/删除线/高亮 5 色/字号档位/对齐/引用/水平线/表格/列表/清除格式），`{{变量}}` 芯片插入，编辑器内直接调整纸张（A 系列 + 信函 + B3）、方向（横/竖）、页边距三档（窄/正常/宽，Word 同口径） |
| 所见即所渲染 | 编辑器与 PDF 双向统一基准（表格/列表/标题样式、白纸 padding 与 PDF 页边距同口径、分页参考虚线 + 页码角标）；字体仅 PDF 内嵌思源（Noto Sans/Serif SC），编辑器用系统字体省 41MB 前端体积 |
| 纯 Java PDF 渲染 | HTML → PDF（openhtmltopdf，包名已 fork 为 `com.openhtmltopdf`），无浏览器/无头进程依赖；水印 PDFBox 3（3×3 平铺小字 + 文案带日期） |
| 版本管理 | 模板每次保存生成新版本（版本说明留痕），可回滚历史版本为当前；被合同引用的版本禁改字段定义 |
| 合同起草 | 按启用模板新建，表单变量回填；编辑表单内免保存临时预览（版本 + 当前表单值实时渲染） |
| 合同编号 | 新建/编辑可手填（租户内查重），留空则定稿时自动生成 |
| 审批集成 | 提交走 FlowLong（默认模型启动时预置 + 懒部署双通道）；审批中可预览/打印（带『审批中』水印）；审批通过自动定稿——实时渲染 PDF 流式归档附件，此后走定稿直链 |
| 备注字段 | 任意状态（含审批中/已定稿）可改的管理性信息，不参与渲染与定稿快照 |
| 分类树 | `ct_category` 树形分类（层级码、防环校验），仅叶子可挂模板，列表页左树筛选 |

## 2. 架构设计

```
forge-module-contract-api                     ← 实体 + DTO + 枚举
  entity/      CtTemplate / CtTemplateVersion / CtContract / CtCategory
  enums/       PaperSize（A 系列+信函+B3）/ PaperOrientation / PaperMarginPreset

forge-module-contract-biz                     ← 业务实现
  controller/admin/   ContractTemplateController · ContractContractController · ContractCategoryController
  service/            模板/合同/分类服务 + ContractNoGenerator（编号生成）
  service/checker/    附件引用校验（模板图片被引用时拒删附件，注册进 AttachmentRefChecker SPI）
  framework/html      HTML 清洗与样式归一（sanitize、变量替换、空段落/对齐/列表预处理）
  framework/pdf       openhtmltopdf 渲染 + PDFBox 3 水印（审批中/定稿章两态）
  listener/           ContractModelInitializer（启动预置审批模型）
                      ContractModelProvisioner（默认模型懒部署）
                      ContractWorkflowEventHandler（终态回写：定稿 PDF 归档/驳回/撤销）
```

**依赖关系**：`contract-biz` ← `contract-api`、`system-api`、`system-biz`（附件 SPI）、`workflow-biz`（审批联动）。合同模块以 biz 级依赖接入工作流（同 demo 模块形态），不反向依赖。

**渲染链路**：模板 HTML（编辑器产物）→ sanitize 白名单清洗 → `{{变量}}` 替换为合同表单值 → 基础样式注入（`white-space: pre-wrap`、空段落 `<p><br/>` 占位等）→ openhtmltopdf 渲染 → 按需加水印。预览/打印/定稿三路共用同一条链，保证口径一致。

## 3. 前端页面

| 页面 | 路径 | 说明 |
|------|------|------|
| 合同模板 | `views/contract/template` | 列表（分类左树筛选、启用状态、方向列）+ 编辑器抽屉（工具栏/灰底白纸画布/右侧字段与版本面板） |
| 合同列表 | `views/contract/contract` | 分页列表（状态/分类/关键字筛选）、新建/编辑对话框（含免保存临时预览）、详情对话框（含改备注）、PDF 预览独立全屏弹窗、打印（纸张可选）、导出 |
| 合同分类 | `views/contract/category` | 树表维护（增删改、停启用） |

菜单 id：合同管理 285-299，合同分类 326-330。

## 4. API 端点

前缀 `/admin-api`（Controller 包名自动注入），权限码前缀 `contract:template` / `contract:contract` / `contract:category`。

### 模板 `/contract/template`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list`、`/list-enabled` | 分页查询；启用中下拉（合同新建用） |
| POST / PUT | `/`、`/paper`、`/status` | 新建；修改基础信息；仅更新纸张配置（编辑器内调纸张/方向/边距）；启停 |
| GET | `/{id}`、`/{id}/versions`、`/version/{versionId}` | 详情；版本分页；版本详情（含内容与字段定义） |
| POST | `/{id}/version` | 保存新版本（编辑器保存） |
| PUT | `/version/{versionId}/field-defs`、`/version/{versionId}/set-current` | 修正字段定义（被引用版本禁改）；回滚为当前版本 |
| DELETE | `/{id}` | 删除模板（有合同引用禁删） |

### 合同 `/contract/contract`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list`、`/{id}` | 分页查询；详情 |
| POST / PUT | `/`、`/{id}/remark` | 新建草稿；编辑草稿（驳回/撤销后可重编）；改备注（任意状态） |
| DELETE | `/{id}` | 删除（仅草稿态） |
| POST | `/{id}/preview`、`/preview-draft`、`/{id}/print` | 实时预览（草稿态）；临时预览（编辑表单未保存值，不落库）；打印（按所选纸张重渲染）；三者 `produces=application/pdf` |
| POST | `/{id}/submit`、`/export` | 提交审批；导出合同清单 |

### 分类 `/contract/category`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list`、`/all` | 平铺列表（status 筛选，前端 transform 组树）；全量树（表单下拉） |
| POST / PUT / DELETE | `/`、`/`、`/{ids}` | 增；改；删（有子分类或模板挂载禁删） |

## 5. 数据库表结构

| 表 | 关键列 | 说明 |
|----|--------|------|
| `ct_category` | id、parent_id、层级码、name、status | 树形分类，防环校验 |
| `ct_template` | id、name、category_id（仅叶子）、paper_size、paper_orientation、margin_preset、current_version_id、status | 模板主表；纸张三要素与版本指针 |
| `ct_template_version` | id、template_id、version_no、content（HTML）、field_defs（JSON，Jackson3TypeHandler）、version_remark | 版本内容与字段定义快照 |
| `ct_contract` | id、contract_no、title、template_id、template_version_id、field_values_json（JSON，Jackson3TypeHandler）、status、rendered_html、pdf_attachment_id、process_instance_id、process_no、remark | 合同；状态 0 草稿 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已撤销；定稿后 pdf_attachment_id 指向归档附件 |

四表均含 `tenant_id`（多租户）与逻辑删除列。

## 6. 关键行为口径

- **状态机**：草稿(0) → 提交审批(1) → 通过定稿(2) / 驳回(3) / 撤销(4)；驳回与撤销后可重新编辑再提交。
- **水印三态**：审批中预览/打印 = 『审批中』+日期平铺水印；定稿后预览 = 定稿章水印；编辑表单临时预览（未提交）同样盖章。实时预览与定稿水印口径一致。
- **编号**：手填则租户内查重；留空定稿自动生成（`ContractNoGenerator`），定稿沿用不覆盖。
- **版本引用**：合同锁定提交时的模板版本，后续模板改版不影响存量合同渲染。
- **附件绑定**：模板图片保存时按内容自动绑定/解绑 bizId；模板被合同引用时其图片附件拒删（引用校验五方之一）。
- **备注**：走独立端点 `PUT /{id}/remark`，不进合同编辑表单校验链，任意状态可改。

## 7. 部署与升级

```bash
# 全新建库（README 快速开始已含）
mysql -u root -p forge_admin < sql/init-contract.sql

# 存量库按日期顺序手工执行（幂等，可重复跑）
sql/upgrade-20261116-contract.sql                 # 三表 + 菜单 285-299 + 分类字典（旧方案）
sql/upgrade-20261116-contract-orientation.sql     # paper_orientation 列
sql/upgrade-20261116-contract-margin.sql          # margin_preset 列
sql/upgrade-20261117-contract-category.sql        # ct_category 树表 + 字典迁移 + 菜单 326-330，旧分类字典停用
sql/upgrade-20261117-contract-remark.sql          # remark 列
```

- 项目未启用 Flyway 自动迁移（模块内 `db/migration/` 目录仅作结构演进留档），**升级 SQL 一律手工执行**。
- 渲染字体随 jar 打包（`resources/fonts/NotoSansSC.ttf`、`NotoSerifSC.ttf`），无需服务器装字体。
- 无额外配置项；审批模型由启动预置 + 懒部署自动就绪，无需手工建流程模型。

## 8. 注意事项与常见问题

| 现象 | 原因与处理 |
|------|-----------|
| PDF 预览/打印返回乱码文本 | 端点已 `produces=application/pdf`；若仍乱码多为网关改写 Content-Type，直连后端验证 |
| 「审批通过但合同停在审批中」 | 定稿动作在事件回调中落库：先确认 `sys_attachment.file_config_id` 列已执行附件重构升级脚本（缺列时定稿附件写入失败）；事件终败不自动重放，可撤销实例后重新提交恢复 |
| 合同分类下拉为空 | 分类树仅叶子可选；旧字典数据须先执行 `upgrade-20261117-contract-category.sql` 完成迁移 |
| 编辑器与 PDF 个别字体粗细观感不同 | 编辑器用系统字体近似，PDF 内嵌思源为准（省 41MB 前端体积的取舍） |
| 修改模板方向/边距不生效 | 确认走 `PUT /paper` 端点且保存链路完整；旧版本存在两字段漏落问题，v2.2.0 已修复 |
