#!/usr/bin/env node
/**
 * 前端页面规范扫描(frontend-development-specification.md 可机检条款)
 * 检查 views 下各 index.vue 列表页:
 *   R1 禁止 el-table(列表一律 vxe-table)
 *   R2 vxe-table 必须有 id="xxxTable"
 *   R3 vxe-table 页面须有序号列(type="seq")
 *   R4 分页必须用 TablePagination(有 vxe-table 且有分页数据时)
 *   R5 scoped 样式禁止重复定义布局类(.app-container/.search-card/.table-card)
 * 存量豁免: scripts/frontend-spec-guard-baseline.txt(存量违规冻结,增量严查——ArchUnit 存量例外同策略)
 * 用法: node scripts/frontend-spec-guard.mjs [--base <ref>]
 *   默认全量扫描 views/;--base 时仅检查相对 ref 变更的文件(增量模式,供 pre-push)
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { execSync } from 'node:child_process'
import { join, relative } from 'node:path'

const webRoot = join(import.meta.dirname, '..', 'apps', 'forge-web')
const viewsDir = join(webRoot, 'src', 'views')

function collectVueFiles(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      collectVueFiles(full, acc)
    } else if (name === 'index.vue') {
      acc.push(full)
    }
  }
  return acc
}

function changedFiles(base) {
  const root = join(webRoot, '..', '..')
  const out = execSync(`git diff --name-only ${base}...HEAD`, { cwd: root, encoding: 'utf-8' })
  return out.split('\n')
    .filter(f => f.startsWith('apps/forge-web/src/views/') && f.endsWith('index.vue'))
    .map(f => join(root, f))
}

function check(file) {
  const src = readFileSync(file, 'utf-8')
  const rel = relative(webRoot, file)
  const problems = []
  const hasElTable = /<el-table[\s>]/.test(src)
  const hasVxeTable = /<vxe-table[\s>]/.test(src)
  // R1: 列表页禁止 el-table(允许出现 el-table-column 配合 vxe? 不——vxe 用 vxe-column)
  if (hasElTable) {
    problems.push(`R1 使用了 el-table(列表一律 vxe-table)`)
  }
  // R2: vxe-table 必须有 id
  if (hasVxeTable && !/id="\w+Table"/.test(src)) {
    problems.push(`R2 vxe-table 缺少 id="xxxTable" 唯一标识`)
  }
  // R3: 有 vxe-table 须有序号列(树形页层级缩进与序号语义冲突,豁免——与 R4 同策略)
  const isTreePage = /tree-config/.test(src)
  if (hasVxeTable && !isTreePage && !/type="seq"/.test(src)) {
    problems.push(`R3 缺少序号列(vxe-column type="seq" + seq-method)`)
  }
  // R4: 有 vxe-table 且无"不分页"声明时须 TablePagination(树形页等无分页页可能无——以注释豁免)
  if (hasVxeTable && !isTreePage && !/TablePagination/.test(src) && !/spec-guard-disable-r4/.test(src)) {
    problems.push(`R4 未使用 TablePagination(无分页页请加注释 spec-guard-disable-r4 豁免)`)
  }
  // R6: 无分页页面(豁免注释/树形)用了 useTableHeight 时必须 hasPagination:false——默认 true 会预留 52px 分页高度,页面无分页组件时表格底部多出空白
  const noPagination = isTreePage || /spec-guard-disable-r4/.test(src)
  if (hasVxeTable && noPagination && /useTableHeight\(/.test(src) && !/hasPagination:\s*false/.test(src)) {
    problems.push(`R6 无分页页须 useTableHeight({ hasPagination: false })(否则高度计算多预留 52px 分页空白)`)
  }
  // R5: scoped 样式禁止重复布局类
  const scoped = src.match(/<style[^>]*scoped[^>]*>([\s\S]*?)<\/style>/)
  if (scoped && /\.(app-container|search-card|table-card)\s*\{/.test(scoped[1])) {
    problems.push(`R5 scoped 样式重复定义布局类(.app-container/.search-card/.table-card)`)
  }
  return problems.map(p => `${rel}: ${p}`)
}

const base = process.argv.includes('--base')
  ? process.argv[process.argv.indexOf('--base') + 1]
  : null

const files = base ? changedFiles(base) : collectVueFiles(viewsDir)
const violations = files.flatMap(f => (existsSync(f) ? check(f) : []))

// baseline 键: "相对路径: 规则号"
const baselinePath = join(import.meta.dirname, 'frontend-spec-guard-baseline.txt')
const baseline = new Set(
  existsSync(baselinePath)
    ? readFileSync(baselinePath, 'utf-8').split('\n').filter(l => l.trim() && !l.startsWith('#'))
    : []
)

const fresh = violations.filter(v => {
  const m = v.match(/^(.*): (R\d)/)
  return m && !baseline.has(`${m[1]}: ${m[2]}`)
})

if (fresh.length) {
  console.error(`前端规范扫描: ${fresh.length} 处新增违规(存量豁免 ${violations.length - fresh.length} 处;规范: docs-base/development/frontend-development-specification.md)`)
  fresh.forEach(v => console.error('  ' + v))
  process.exit(1)
}
console.log(`前端规范扫描通过(${files.length} 个列表页,存量豁免 ${violations.length} 处)`)
