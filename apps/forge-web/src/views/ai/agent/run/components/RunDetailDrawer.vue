<template>
  <el-drawer v-model="visible" :title="`运行 #${run?.id} 步骤明细`" size="62%">
    <div v-if="run" class="run-summary">
      <div>
        <el-tag :type="statusType(run.status)" size="small">{{ statusLabel(run.status) }}</el-tag>
        <el-tag v-if="run.triggerType" size="small" type="info" style="margin-left: 6px">{{ triggerLabel(run.triggerType) }}</el-tag>
      </div>
      <span>输入：{{ run.inputSummary || '-' }}</span>
      <span v-if="run.finalAnswer" class="final-answer">结论：{{ run.finalAnswer }}</span>
    </div>
    <el-timeline v-if="parsedSteps.length" class="step-timeline">
      <el-timeline-item v-for="(step, i) in parsedSteps" :key="i"
        :type="step.tagType" :hollow="step.status === 'RUNNING'">
        <div class="step-card">
          <div class="step-head">
            <span class="step-title">{{ step.title }}</span>
            <el-tag v-if="step.toolName" size="small" type="warning">{{ step.toolName }}</el-tag>
            <el-tag :type="step.tagType" size="small">{{ step.statusLabel }}</el-tag>
            <span v-if="step.durationMs" class="step-meta">{{ (step.durationMs / 1000).toFixed(1) }}s</span>
            <span v-if="step.tokens" class="step-meta">{{ step.tokens }}</span>
          </div>
          <div v-if="step.thought" class="step-thought">思考：{{ step.thought }}</div>
          <div v-for="(call, j) in step.calls" :key="j" class="step-call">
            <div class="call-name">调用工具：{{ call.tool }}</div>
            <pre class="code-block">{{ call.args }}</pre>
          </div>
          <div v-if="step.answer" class="step-answer">{{ step.answer }}</div>
          <pre v-if="step.raw" class="code-block">{{ step.raw }}</pre>
          <div v-if="step.error" class="step-error">{{ step.error }}</div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="无步骤记录" />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { getAgentSteps } from '@/api/ai/agent'
import type { AgentRun, AgentRunStep } from '@/api/ai/agent'

defineOptions({ name: 'RunDetailDrawer' })

const visible = ref(false)
const run = ref<AgentRun | null>(null)
const steps = ref<AgentRunStep[]>([])

/** 打开：传入 run（或 runId 自动拉详情）并加载步骤 */
const open = async (target: AgentRun | number) => {
  if (typeof target === 'number') {
    const { getAgentRun } = await import('@/api/ai/agent')
    run.value = await getAgentRun(target)
  } else {
    run.value = target
  }
  steps.value = await getAgentSteps(run.value.id)
  visible.value = true
}

defineExpose({ open })

interface ParsedStep {
  title: string
  status: string
  statusLabel: string
  tagType: 'primary' | 'success' | 'warning' | 'info' | 'danger'
  toolName?: string
  durationMs?: number
  tokens?: string
  thought?: string
  calls: { tool: string; args: string }[]
  answer?: string
  raw?: string
  error?: string
}

const NODE_TITLES: Record<string, string> = {
  multimodal: '图片理解',
  decision: '工具决策',
  tools: '工具执行',
  approval: '人工确认',
  summary: '总结回答',
  supervisor: '调度决策',
  expert_decide: '专家决策',
  expert_tools: '专家工具执行',
  expert_approval: '专家人工确认',
  expert_report: '专家汇报'
}

const STATUS_LABELS: Record<string, string> = {
  SUCCESS: '完成', FAILED: '失败', INTERRUPTED: '已中断', SKIPPED: '已跳过', RUNNING: '执行中'
}

/** 步骤输出结构化解析：payload.output → 决策(thought/calls) | 答案 | 原文 */
const parseSteps = (rows: AgentRunStep[]): ParsedStep[] => {
  return rows.map(row => {
    let payload: any = {}
    try { payload = JSON.parse(row.stepOutput || '{}') } catch { /* 保持空 */ }
    const output: string = payload.output || ''
    const status = String(payload.status || row.status || 'SUCCESS')
    const tokensIn = Number(payload.tokensIn) || row.tokensIn || 0
    const tokensOut = Number(payload.tokensOut) || row.tokensOut || 0
    const step: ParsedStep = {
      title: row.nodeName === 'tools' ? '工具执行' : (NODE_TITLES[row.nodeName] || row.nodeName),
      status,
      statusLabel: STATUS_LABELS[status] || status,
      tagType: status === 'FAILED' ? 'danger' : status === 'SKIPPED' ? 'info'
        : status === 'INTERRUPTED' ? 'warning' : 'success',
      toolName: row.toolName || undefined,
      durationMs: Number(payload.durationMs) || row.durationMs || undefined,
      tokens: tokensIn || tokensOut ? `${tokensIn}/${tokensOut} tok` : undefined,
      calls: []
    }
    if (row.errorMsg) {
      step.error = row.errorMsg
    }
    if (!output) return step
    const start = output.indexOf('{')
    const end = output.lastIndexOf('}')
    if (start >= 0 && end > start) {
      try {
        const decision = JSON.parse(output.slice(start, end + 1))
        if (decision.action === 'tool' && Array.isArray(decision.calls)) {
          step.thought = decision.thought || undefined
          step.calls = decision.calls.map((c: any) => ({
            tool: c.tool || '',
            args: c.args ? JSON.stringify(c.args, null, 2) : ''
          }))
          return step
        }
        if (decision.action === 'final' && decision.answer) {
          step.answer = String(decision.answer)
          return step
        }
      } catch { /* 非纯 JSON，按原文展示 */ }
    }
    if (row.stepType === 'TOOL') {
      step.raw = output
    } else {
      step.answer = step.answer || output
    }
    return step
  })
}

const parsedSteps = computed(() => parseSteps(steps.value))

const statusLabel = (s: string) => ({ RUNNING: '运行中', WAITING_APPROVAL: '待确认', SUCCEEDED: '成功', FAILED: '失败', CANCELLED: '已取消' } as Record<string, string>)[s] || s
const statusType = (s: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' =>
  (({ RUNNING: 'primary', WAITING_APPROVAL: 'warning', SUCCEEDED: 'success', FAILED: 'danger', CANCELLED: 'info' } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>)[s] || 'info')
const triggerLabel = (t: string) => ({ CONVERSATION: '会话', MANUAL: '手动', SCHEDULED: '调度' } as Record<string, string>)[t] || t
</script>

<style scoped lang="scss">
.run-summary {
  margin-bottom: 16px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .final-answer {
    color: var(--el-color-success);
    font-weight: 500;
  }
}

.step-timeline {
  .step-card {
    .step-head {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;

      .step-title { font-weight: 600; font-size: 13px; }
      .step-meta { color: var(--el-text-color-secondary); font-size: 12px; }
    }

    .step-thought {
      margin-top: 6px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    .step-call {
      margin-top: 8px;

      .call-name { font-size: 12px; color: var(--el-color-warning); margin-bottom: 4px; }
    }

    .step-answer {
      margin-top: 8px;
      font-size: 13px;
      line-height: 1.6;
      white-space: pre-wrap;
      word-break: break-all;
    }

    .step-error {
      margin-top: 6px;
      color: var(--el-color-danger);
      font-size: 12px;
    }
  }
}

.code-block {
  margin: 4px 0;
  padding: 8px 10px;
  background: var(--el-fill-color-darker);
  border-radius: 4px;
  font-size: 12px;
  font-family: Menlo, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
}
</style>
