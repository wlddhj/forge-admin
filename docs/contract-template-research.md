# 合同模板功能 技术调研与方案（路线B：在线富文本 HTML）

> 初版日期：2026-11-15 ｜ 路线B重写：2026-11-16 ｜ 性质：技术调研报告（不编码）
> **用户决策记录（2026-11-16）**：① 模板载体走**路线B：在线富文本（HTML）**，模板必须能在线编辑；② 合同**必须转 PDF**；③ 支持**在线打印且可选择纸张大小**；④ **否决 Playwright**（Chromium 常驻内存/部署负担不可接受），PDF 引擎定**纯 Java：openhtmltopdf 社区后继 fork**；⑤ 模块形态=**内置业务模块**（`create-module` 脚手架，非插件）；⑥ 中文字体=**思源宋体/黑体**（取 Google Fonts Noto Serif/Sans SC 的 TTF 版，SIL OFL 协议可随 jar 自由分发）。

## 1. 需求口径

| 能力 | 要求 |
|---|---|
| 模板管理 | 分类、**在线富文本编辑**、受控样式、版本管理、启用停用 |
| 变量体系 | 占位符定义为「变量芯片」节点，从模板内容自动提取，字段属性（必填/类型/选项）可维护 |
| 合同生成 | 选模板 → 动态表单填变量 → 渲染合同 → **转 PDF** → 存档 → 编号 |
| 打印 | **在线打印，可选择纸张大小**（A4/A5/Letter/Legal/自定义） |
| 周边联动 | 审批流（FlowLong）、水印、（远期）AI 起草、电子签章 |

**排除项（本期不做）**：电子签章对接、合同到期提醒/履约管理、docx 导出（P3 可加 HTML→docx）。

## 2. 项目现状盘点（路线B视角）

| 能力 | 现状 | 路线B下的复用性 |
|---|---|---|
| 富文本编辑器 | **前端为零**（无 wangeditor/quill/tinymce/tiptap） | ❌ 全新引入（路线B核心增量） |
| HTML→PDF | 后端依赖树空白（仅 easyexcel 的 POI、AI 模块 Tika） | ❌ 全新引入（路线B核心增量） |
| 文件存储 | `sys_attachment` + `FileStorageClient` SPI（local/minio/oss/cos 四实现） | ✅ 定稿 PDF 挂附件表 |
| 打印范式 | 既有「打印区挂 body 顶层 + @media print visibility」方案（jQuery 已移除） | ✅ iframe/PDF 打印沿用此范式 |
| 审批流 | FlowLong 1.2.7 形态A事件范式，`forge-module-demo` 全链路可照抄 | ✅ 直接复用 |
| 多租户 | SQL 拦截器自动注入，DDL 带 `tenant_id` 即可 | ✅ 按约定走 |
| AI | `AiChatService.chat/chatStream`、Agent 编排 | P3「AI 起草」入口 |
| 脚手架 | `create-module` / `create-plugin` | 模块形态二选一（§8 开放决策） |
| 合同存量代码 | 无 | 全新开发 |

版本基线：Spring Boot 4.1.2 / JDK 21 / vue 3.4 + element-plus 2.4 + vxe-table 4.9。

## 3. 技术选型

### 3.1 富文本编辑器：tiptap 3（定选）

| 候选 | 结论 |
|---|---|
| **tiptap 3.x** | ✅ 定选。core **MIT**（仅 Cloud/协同等平台功能收费，本功能不需要）；Vue3 官方包 `@tiptap/vue-3`；**表格（含合并/列宽）免费进 core**；headless 架构，自定义 inline Node 做「变量芯片」是它的强项 |
| wangEditor 5 | ❌ 已停更，不引入新项目 |
| CKEditor 5 | ❌ GPL2+/商业双授权，商用 SaaS 有协议风险 |
| Quill 2 | 自定义节点（blot）能力弱于 tiptap，不选 |

**变量芯片设计**（路线B的核心优势）：
- tiptap 自定义 inline Node `variable`，attrs `{ fieldKey }`，编辑器内渲染为不可拆分的芯片 UI（如 `「甲方名称」`），用户无法误删半个变量；
- 序列化 HTML：`<span data-field="party_a_name" class="ct-var">甲方名称</span>`；
- **变量提取零成本**：编辑保存时从 editor JSON 遍历 `variable` 节点即得变量清单，比 Word 路线解析 `{{}}` 更可靠；
- 填充渲染：后端按 `data-field` 替换芯片内容（jsoup 处理，字段值 HTML 转义防注入）。

**受控样式白名单**（企业模板管理惯例，防自由排版毁打印）：仅开放 标题1-3/段落/有序无序列表/表格(合并列宽)/加粗斜体下划线/对齐/缩进/图片(走附件上传)/变量芯片；**禁**自由字体、字号用受控档位、禁彩色（水印除外）。编辑器按纸张宽度约束编辑区（所见即所编辑宽度）。

### 3.2 HTML→PDF 引擎：openhtmltopdf 社区后继 fork（纯 Java，定选）

| 方案 | 评价 | 结论 |
|---|---|---|
| **`io.github.openhtmltopdf:openhtmltopdf-pdfbox`**（社区后继 fork） | 纯 Java 进程内渲染，**无常驻进程、每次渲染仅数十 MB 瞬时内存**；CSS 2.1 + 扩展 CSS3 Paged Media（`@page`/页码 counter/running elements，比 Flying Saucer 原线更全）；已升级 **PDFBox 3 + Java 21**，社区持续发版（节奏偏慢但活跃，docx4j 生态已迁入）；LGPL（SaaS 不分发场景无协议负担） | ✅ **定选** |
| Flying Saucer v10.7.0（`org.xhtmlrenderer`） | 同源前代，2025 年仍活跃发版；但 Paged Media 支持是 CSS 2.1 子集，弱于 openhtmltopdf | SPI 层备胎（不实现，仅保留可切换性） |
| Playwright/Headless Chromium | 渲染保真最好 | ❌ **用户否决：常驻 ~300MB + 部署负担** |
| Gotenberg（HTML→PDF 微服务） | 底层同为 Chromium | ❌ 同上否决，且多一个外部服务 |
| iText pdfHTML | AGPL/商业双授权 | ❌ 多租户商用排除 |
| html2pdf.js/jsPDF（前端） | 截图栅格化、分页差 | ❌ 归档件不可用 |

**两个引擎契约级集成要点（落地必做）**：

1. **XHTML 输入要求**：openhtmltopdf 要求良构 XHTML → 渲染前 jsoup 一趟完成「sanitize → 芯片填充 → XML 语法序列化」（jsoup 本来就在栈内，顺路完成，无额外成本）；
2. **CJK 字体注册**：`builder.useFont(...)` 注册中文字体（**TTF 格式优先**，OTF 支持不完整，落地首日验证），字体文件随模块 resources 分发或目录配置——服务器**无需安装任何字体包**。

**SPI 抽象保留（对齐 `FileStorageClient` 模式）**，当前仅一个实现，为未来引擎（含付费方案）留切换缝：

```java
public interface PdfRenderClient {
    String engineType();                          // openhtmltopdf
    byte[] render(String html, PaperSize paper);  // 注入 @page size，返回 PDF 字节
}
```

**CSS 2.1 限制的正面消化**：渲染器不支持 flex/grid，但 §3.1 的**受控样式白名单本来就只开放标题/段落/表格/列表/有限格式**——编辑器产出的样式天然落在渲染器支持面内。「限制」经白名单设计转化为「所见即所渲染」的保障，不会出现用户排了渲染不了的版式。

### 3.3 PDF 与打印（含纸张选择）

**核心思路：PDF 是唯一「定稿真身」，打印即打印 PDF**——归档件与打印件必然一致（WYSIWYG 闭环），避免「HTML 打印一套、PDF 归档另一套」的样式漂移。

```
纸张大小 = 模板属性(paper_size，默认A4，可选A5/Letter/Legal/自定义mm)
     ↓
生成 PDF：渲染引擎注入 @page { size: A4; margin: 25.4mm 31.8mm } 等
     ↓
在线预览：<iframe> 内嵌 PDF 流（浏览器原生查看器，真实分页）
     ↓
在线打印：打印对话框选纸张 → 带 pageSize 参数请求 /pdf 端点按所选纸张重渲染
        → 隐藏 iframe 挂 body 顶层（沿用既有打印范式）→ iframe.contentWindow.print()
```

- 浏览器打印对话框默认跟随 PDF 页面尺寸，选择其他纸张则自动缩放——**选择纸张 = 服务端按该纸张重出 PDF**，保证 1:1 不缩放；
- 分页 CSS 约定：`p/table/tr { break-inside: avoid }`、`h1-h3 { break-after: avoid }`、合同专用基础样式（宋体正文小四、1.5 倍行距、黑体标题，单位用 pt/mm）作为受控样式表由后端统一注入——全部落在渲染器 CSS 2.1 + Paged Media 支持面内；
- 页眉页脚/页码：`@page @bottom-center { content: counter(page) " / " counter(pages) }`、页眉合同名用 running elements（`position: running()`）——openhtmltopdf 对 CSS Paged Media 的支持覆盖这些经典形态；
- **边界**：不接入 hiprint 模板体系——hiprint 适合设计「打印版式」（清单/标签），合同是流式文档本体，两者分离；hiprint 存量能力不受影响。

### 3.4 编辑器分页的诚实说明（路线B固有取舍）

富文本编辑器是连续流，**编辑态没有真实分页**（tiptap 无内置分页，业界通用做法亦如此）。对策：
- 编辑区宽度按纸张内容宽约束（如 A4@96dpi ≈ 794px 减页边距），行宽所见即所得；
- **真实分页在预览/导出时由渲染引擎完成**（PDF iframe 预览即真实分页）；
- 若后续强需求「编辑态分页」，P3 评估 CSS 分页模拟（div 翻页占位），不进 MVP。

### 3.5 安全

- 模板 HTML 保存时白名单 sanitize（tags/attrs 白名单，剥离 script/事件属性/外链样式）；
- 变量值填充前 HTML 转义（jsoup `text`），杜绝存储型 XSS；
- Chromium 启动参数最小化（`--no-sandbox` 仅容器内、禁 GPU/网络请求白名单仅本地资源）；
- PDF 端点挂 `@PreAuthorize('contract:contract:print')`，产物流经 `/uploads/**` 既有鉴权链。

## 4. 数据模型草案

```sql
-- 模板主表
ct_template        (id, tenant_id, category_code, name,
                    current_version_id, status,          -- 0草稿/1启用/2停用
                    paper_size,        -- A4/A5/LETTER/LEGAL/CUSTOM
                    custom_width_mm, custom_height_mm,   -- CUSTOM 时有效
                    remark, create_by/time, update_by/time, deleted)

-- 模板版本表（content 即版本内容，可追溯可回滚）
ct_template_version(id, tenant_id, template_id, version_no,
                    content_html MEDIUMTEXT,  -- 编辑器序列化 HTML（含 data-field 芯片）
                    field_defs_json,          -- 本版变量定义快照
                    change_log, create_by/time, deleted)

-- 合同记录
ct_contract        (id, tenant_id, contract_no, title,
                    template_id, template_version_id,
                    field_values_json,        -- Jackson3TypeHandler
                    status,                   -- 0草稿/1审批中/2已通过/3已驳回/4已撤销
                    rendered_html MEDIUMTEXT, -- 定稿渲染快照（模板后续改动不影响已定稿合同）
                    pdf_attachment_id,        -- 定稿 PDF 挂 sys_attachment（终态后才有值）
                    process_instance_id, process_no,
                    create_by/time, update_by/time, deleted)
```

要点：变量定义随版本存 JSON（与版本强绑定，不单独建表）；分类走字典 `contract_category`（写入须调 `reloadDictCache`）；`rendered_html` 快照保证法务可追溯「定稿那一刻的内容」；三表 `tenant_id BIGINT NOT NULL DEFAULT 1 + idx_tenant`，不进 `ignore-tables`；唯一索引含 deleted 的表按编码规范 v1.1 用 `deleted BIGINT DEFAULT 0 + delval="id"`。

## 5. 关键流程

1. **模板维护**：在线编辑器编辑（芯片插入变量、受控样式）→ 保存时前端从 editor JSON 提取变量清单 → 与上版 diff（新增/删除）→ 补全字段属性 → 存新版本，主表 `current_version_id` 前移。
2. **合同生成**：选模板（锁版本）→ 动态表单（el-form 按字段定义渲染：文本/数字/日期/下拉/长文本）→ 后端填充渲染 HTML → **按模板纸张实时出 PDF**（内存流预览，不落盘）→ 保存草稿（`field_values` 存 JSON）。
3. **审批**：提交即 `startProcess(businessKey="contract:"+id)`，`ContractWorkflowEventHandler` 监听终态（照抄 demo 幂等+CAS 范式）→ APPROVED 渲染定稿：`rendered_html` 快照 + PDF 落 `sys_attachment` + 发编号 `CT-YYYYMMDD-序列`（Redis 自增，应用连 DB1）。
4. **打印**：合同详情/列表 → 打印对话框选纸张 → 按所选纸张重渲染 PDF → iframe 打印（§3.3）。

## 6. 新增依赖清单

| 端 | 依赖 | 版本 | 用途 |
|---|---|---|---|
| 前端 | `@tiptap/vue-3` + `@tiptap/starter-kit` + `@tiptap/extension-table(+row/header)` + `@tiptap/extension-image` | 3.x | 编辑器（表格 v3 起免费进 core） |
| 后端 | `io.github.openhtmltopdf:openhtmltopdf-pdfbox` | 最新（社区 fork，PDFBox 3；版本钉进 forge-dependencies） | HTML→PDF 引擎（进程内，无常驻进程） |
| 后端 | `org.jsoup:jsoup` | 1.2x | sanitize → 芯片填充 → XHTML 序列化（一趟完成） |
| 资源 | Noto Serif/Sans SC（思源宋体/黑体）**TTF** 字体文件 | — | SIL OFL 协议可随 jar 分发；`useFont` 注册；服务器**零安装** |

> 无任何外部进程/系统依赖：不需要 Chromium、不需要 LibreOffice、不需要装字体包——部署形态与现有 fat jar 完全一致。

## 7. 实施分期

| 期 | 内容 |
|---|---|
| P1（MVP） | 模板管理（tiptap 编辑器+芯片+受控样式+版本）+ 变量提取与字段定义 + 合同生成（表单/填充/实时 PDF 预览）+ 打印（纸张选择）+ PDF 引擎 SPI |
| P2 | FlowLong 审批联动 + 定稿归档（rendered_html+PDF 附件）+ 合同编号 + 列表/详情/水印 |
| P3（演进） | AI 起草与条款审查（复用 AiChatService/Agent）、docx 导出、编辑态分页模拟、电子签章对接 |

## 8. 风险与开放决策点

| # | 决策点 | 结论 |
|---|---|---|
| 1 | PDF 引擎 | **已定：openhtmltopdf 社区 fork（纯 Java）** |
| 2 | 模块形态 | **已定：内置业务模块**（`create-module contract` 脚手架；未来若需按租户收费可迁插件，业务代码同构） |
| 3 | 中文字体 | **已定：思源宋体/黑体**——取 Google Fonts Noto Serif/Sans SC 的 **TTF** 版（与思源同源），SIL OFL 协议可随 jar 分发，商用无授权成本；落地首日验证 TTF 渲染与字重效果 |
| 4 | 编辑态无真实分页 | 预览用 PDF iframe 真分页兜底；编辑态分页 P3 评估 |
| 5 | fork 维护节奏偏慢 | 版本钉死 + SPI 隔离（换引擎只动一个 Client 实现）；LGPL SaaS 场景无协议负担 |
| 6 | CSS 2.1 渲染边界 | 受控样式白名单正面消化（§3.2）；落地时建立「白名单样式 → PDF 产物」对照测试防回归 |
| 7 | 纸张尺寸枚举范围 | A4/A5/Letter/Legal/自定义(mm)，模板级配置，打印时可覆盖 |

## 9. 参考资料

- tiptap 3：[官方文档](https://tiptap.dev/docs)、[Vue3 安装指南](https://tiptap.dev/docs/editor/getting-started/install/vue3)、[@tiptap/vue-3 npm（MIT）](https://www.npmjs.com/package/@tiptap/vue-3)、[功能对照（core 免费/平台功能付费）](https://tiptap.dev/docs/editor/pricing/feature-comparison)
- openhtmltopdf 社区后继 fork：[github.com/openhtmltopdf](https://github.com/openhtmltopdf)（新 Maven 坐标 `io.github.openhtmltopdf`，PDFBox 3 + Java 21）；原仓库 [danfickle/openhtmltopdf](https://github.com/danfickle/openhtmltopdf) 已停滞（issue #921/#955 确认后继指向）
- Flying Saucer 同源线：[Releases（v10.7.0，2025 仍活跃）](https://github.com/flyingsaucerproject/flyingsaucer/releases)、[Maven Central](https://central.sonatype.com/artifact/org.xhtmlrenderer/flying-saucer-pdf-openpdf)（Paged Media 为 CSS 2.1 子集，弱于 openhtmltopdf）
- 项目内范式：`forge-module-demo`（工作流形态A）、`FileStorageClient` SPI（存储抽象参照）、打印范式记忆（body 顶层打印区 + @media print）
