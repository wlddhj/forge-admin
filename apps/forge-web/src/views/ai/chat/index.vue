<template>
  <div class="chat-container">
    <!-- 左侧：对话列表 -->
    <div class="conversation-panel">
      <div class="panel-header">
        <div class="mode-row">
          <span class="mode-label">Agent 模式</span>
          <el-switch v-model="agentMode" v-permission="'ai:agent:run'" />
        </div>
        <el-select v-if="!agentMode" v-model="selectedModelId" placeholder="选择模型" style="width: 100%">
          <el-option v-for="model in modelList" :key="model.id" :label="model.modelName" :value="model.id" />
        </el-select>
        <el-select v-else v-model="selectedAgentTarget" placeholder="选择 Agent / 团队" style="width: 100%">
          <el-option-group label="单 Agent">
            <el-option v-for="agent in agentList" :key="'a-' + agent.agentCode" :label="agent.agentName"
              :value="'agent:' + agent.agentCode" />
          </el-option-group>
          <el-option-group v-if="teamList.length" label="团队协作（Supervisor 调度）">
            <el-option v-for="team in teamList" :key="'t-' + team.teamCode" :label="`团队：${team.teamName}`"
              :value="'team:' + team.teamCode" />
          </el-option-group>
        </el-select>
      </div>
      <div class="panel-actions">
        <el-button type="primary" size="small" @click="agentMode ? handleNewAgentConversation() : handleNewConversation()">
          <el-icon><Plus /></el-icon>
          新对话
        </el-button>
      </div>
      <!-- Agent 会话列表 -->
      <div v-if="agentMode" class="conversation-list">
        <div
          v-for="conv in agentConversations"
          :key="conv.conversationId"
          :class="['conversation-item', { active: currentAgentConversationId === conv.conversationId }]"
          @click="handleSelectAgentConversation(conv)"
        >
          <div class="conv-title">{{ conv.title || 'Agent 对话' }}</div>
          <div class="conv-meta">
            <span class="conv-model">{{ conv.runCount }} 次运行</span>
            <div class="conv-actions">
              <el-button type="primary" link size="small" @click.stop="handleRenameAgentConversation(conv)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button type="danger" link size="small" @click.stop="handleDeleteAgentConversation(conv)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 普通会话列表 -->
      <div v-else class="conversation-list">
        <div
          v-for="conv in conversationList"
          :key="conv.id"
          :class="['conversation-item', { active: currentConversation?.id === conv.id }]"
          @click="handleSelectConversation(conv)"
        >
          <div class="conv-title">{{ conv.title || '新对话' }}</div>
          <div class="conv-meta">
            <span class="conv-model">{{ conv.modelName }}</span>
            <div class="conv-actions">
              <el-button type="primary" link size="small" @click.stop="handleRenameConversation(conv)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button type="danger" link size="small" @click.stop="handleDeleteConversation(conv)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧：对话内容 -->
    <div class="chat-panel">
      <!-- Agent 模式 -->
      <template v-if="agentMode">
        <div class="chat-header">
          <span class="chat-title">{{ (selectedTeam && `团队：${selectedTeam.teamName}`) || selectedAgent?.agentName || 'Agent 对话' }}</span>
          <el-tag size="small" type="warning">多步编排</el-tag>
        </div>
        <div ref="messageContainerRef" class="message-container">
          <div v-for="(item, index) in agentSessions" :key="index" :class="['message-item', item.role]">
            <div class="message-avatar">
              <el-avatar v-if="item.role === 'user'" :size="32">{{ userStore.userInfo?.nickname?.charAt(0) ?? "U" }}</el-avatar>
              <el-avatar v-else :size="32" class="ai-avatar">AI</el-avatar>
            </div>
            <div class="message-content">
              <template v-if="item.role === 'user'">
                <div class="message-bubble">
                  <div v-for="(img, i) in item.images" :key="i" class="agent-image">
                    <el-image :src="img" :preview-src-list="item.images" fit="cover" style="width: 120px; height: 120px; border-radius: 6px" />
                  </div>
                  <MarkdownRenderer :content="item.content" />
                </div>
              </template>
              <template v-else>
                <div class="message-bubble">
                  <div v-if="item.approvalNotice" class="approval-notice">
                    <el-icon style="margin-right: 4px"><Checked /></el-icon>{{ item.approvalNotice }}
                  </div>
                  <AgentStepTimeline :frames="item.frames" />
                  <template v-if="item.answer">
                    <el-divider style="margin: 8px 0" />
                    <MarkdownRenderer :content="item.answer" />
                  </template>
                </div>
              </template>
            </div>
          </div>
        </div>
        <div class="input-area">
          <div v-if="agentImages.length" class="agent-upload-preview">
            <el-image v-for="(img, i) in agentImages" :key="i" :src="img" fit="cover"
              style="width: 48px; height: 48px; border-radius: 4px; margin-right: 4px" />
            <el-button link type="danger" size="small" @click="agentImages = []">清空</el-button>
          </div>
          <el-input
            v-model="inputMessage" type="textarea" :rows="3"
            :placeholder="agentMode ? '输入消息（Agent 将多步执行，可附图片）...' : '输入消息...'"
            :disabled="isStreaming" @keydown.enter.ctrl="handleSend" />
          <el-button v-if="agentMode" :disabled="isStreaming" @click="pickImages">图片</el-button>
          <el-button type="primary" :loading="isStreaming" :disabled="!inputMessage.trim() || (agentMode && !selectedAgentTarget)" @click="handleSend">
            发送
          </el-button>
          <input ref="imageInputRef" type="file" accept="image/*" multiple hidden @change="onImagesPicked" />
        </div>
      </template>
      <template v-else>
      <div v-if="!currentConversation" class="empty-state">
        <el-empty description="请选择或创建对话" />
      </div>
      <template v-else>
        <div class="chat-header">
          <span class="chat-title">{{ currentConversation.title || '新对话' }}</span>
          <el-tag size="small">{{ currentConversation.modelName }}</el-tag>
        </div>
        <div ref="messageContainerRef" class="message-container">
          <div v-for="msg in messageList" :key="msg.id" :class="['message-item', msg.role]">
            <div class="message-avatar">
              <el-avatar v-if="msg.role === 'user'" :size="32">{{ userStore.userInfo?.nickname?.charAt(0) ?? "U" }}</el-avatar>
              <el-avatar v-else :size="32" class="ai-avatar">AI</el-avatar>
            </div>
            <div class="message-content">
              <div class="message-bubble">
                <MarkdownRenderer :content="msg.content" />
              </div>
              <div class="message-time">{{ formatDateTime(msg.createTime) }}</div>
            </div>
          </div>
          <div v-if="streamingContent" class="message-item assistant streaming">
            <div class="message-avatar">
              <el-avatar :size="32" class="ai-avatar">AI</el-avatar>
            </div>
            <div class="message-content">
              <div class="message-bubble">
                <MarkdownRenderer :content="streamingContent" />
              </div>
            </div>
          </div>
        </div>
        <div class="input-area">
          <el-input
            ref="inputRef"
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="输入消息..."
            :disabled="isStreaming"
            @keydown.enter.ctrl="handleSend"
          />
          <el-button type="primary" :loading="isStreaming" :disabled="!inputMessage.trim()" @click="handleSend">
            发送
          </el-button>
        </div>
      </template>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useWebSocket } from '@/composables/useWebSocket'
import { getModelList, AI_MODEL_TYPE_TEXT } from '@/api/ai/model'
import { createConversation, getConversationList, getConversationMessages, deleteConversation, saveAiMessage, updateConversationTitle } from '@/api/ai/chat'
import { createSSE, parseSSEData } from '@/utils/sse'
import type { ModelConfigResponse } from '@/api/ai/model'
import type { ConversationResponse, MessageResponse } from '@/api/ai/chat'
import { formatDateTime } from '@/utils/dateFormat'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AgentStepTimeline from './components/AgentStepTimeline.vue'
import { getAgentConfigs, streamAgentChat, getAgentRun, getAgentRuns, getAgentConversations, agentApi } from '@/api/ai/agent'
import type { AgentConfig, AgentEventFrame, AgentConversation } from '@/api/ai/agent'
import { getAgentTeams } from '@/api/ai/agentTeam'
import type { AgentTeam } from '@/api/ai/agentTeam'

const userStore = useUserStore()

const modelList = ref<ModelConfigResponse[]>([])
const selectedModelId = ref<number>()
const conversationList = ref<ConversationResponse[]>([])
const currentConversation = ref<ConversationResponse | null>(null)
const messageList = ref<MessageResponse[]>([])
const inputRef = ref()
const inputMessage = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const messageContainerRef = ref<HTMLElement | null>(null)

const sseClient = createSSE()

// ===== Agent 模式 =====
const agentMode = ref(false)
const agentList = ref<AgentConfig[]>([])
const teamList = ref<AgentTeam[]>([])
/** Agent 模式目标：'agent:<code>' 或 'team:<code>' */
const selectedAgentTarget = ref('')
const selectedAgentCode = computed(() =>
  selectedAgentTarget.value.startsWith('agent:') ? selectedAgentTarget.value.slice(6) : '')
const selectedTeamCode = computed(() =>
  selectedAgentTarget.value.startsWith('team:') ? selectedAgentTarget.value.slice(5) : '')
const agentImages = ref<string[]>([])
const imageInputRef = ref<HTMLInputElement | null>(null)

interface AgentSessionItem {
  role: 'user' | 'assistant'
  content: string
  images?: string[]
  frames: AgentEventFrame[]
  answer: string
  status: 'RUNNING' | 'DONE' | 'WAITING' | 'FAILED'
  /** 审批通过后的气泡提示（轮询发现 WAITING_APPROVAL→RUNNING 迁移时填充） */
  approvalNotice?: string
}
const agentSessions = ref<AgentSessionItem[]>([])
const agentConversations = ref<AgentConversation[]>([])
const currentAgentConversationId = ref<number>()

const selectedAgent = computed(() => agentList.value.find(a => a.agentCode === selectedAgentCode.value))
const selectedTeam = computed(() => teamList.value.find(t => t.teamCode === selectedTeamCode.value))

/** WebSocket 实时消费审批恢复推送（轮询的秒级前置）：审批通过亮提示、完成立即回填 */
const { onNotification } = useWebSocket()
const unsubscribeNotification = onNotification(async (notification) => {
  const runId = Number(notification.relatedId)
  if (!runId) return
  const reply = findReplyByRunId(runId)
  if (!reply) return
  if (notification.type === 'AGENT_RUN_STATUS') {
    if (reply.status === 'WAITING' && !reply.approvalNotice) {
      reply.approvalNotice = notification.content || '人工已审核通过，Agent 正在继续执行…'
      scrollToBottom()
    }
  } else if (notification.type === 'AGENT_RUN') {
    // 完成推送先于轮询命中：直接拉一次 run 回填终态
    try {
      const run = await getAgentRun(runId)
      applyRunResult(reply, run)
    } catch { /* 拉取失败由轮询兜底 */ }
  }
})
onUnmounted(() => {
  unsubscribeNotification()
})

onMounted(async () => {
  modelList.value = await getModelList(AI_MODEL_TYPE_TEXT)
  const defaultModel = modelList.value.find(m => m.isDefault === 1)
  if (defaultModel) {
    selectedModelId.value = defaultModel.id
  }
  loadConversations()
  getAgentConfigs(1).then(list => {
    agentList.value = list
    if (list.length && !selectedAgentTarget.value) {
      selectedAgentTarget.value = 'agent:' + list[0].agentCode
    }
  }).catch(() => { /* 无 Agent 权限时静默 */ })
  getAgentTeams(1).then(teams => {
    teamList.value = teams
    if (!teams.length) return
    if (!selectedAgentTarget.value) {
      selectedAgentTarget.value = 'team:' + teams[0].teamCode
    } else if (selectedAgentTarget.value.startsWith('team:')
      && !teams.some(t => 'team:' + t.teamCode === selectedAgentTarget.value)) {
      selectedAgentTarget.value = 'team:' + teams[0].teamCode
    }
  }).catch(() => { /* 无团队权限时静默 */ })
})

const pickImages = () => imageInputRef.value?.click()

const onImagesPicked = (e: Event) => {
  const files = (e.target as HTMLInputElement).files
  if (!files) return
  const readers: Promise<string>[] = []
  for (const file of Array.from(files)) {
    if (file.size > 2 * 1024 * 1024) {
      ElMessage.warning(`图片 ${file.name} 超过 2MB，已跳过`)
      continue
    }
    readers.push(new Promise(resolve => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.readAsDataURL(file)
    }))
  }
  Promise.all(readers).then(bases => {
    agentImages.value = [...agentImages.value, ...bases].slice(0, 3)
  })
  ;(e.target as HTMLInputElement).value = ''
}

/** 终态回填（轮询与 WebSocket 推送共用）：成功/失败写回气泡 */
const applyRunResult = (reply: AgentSessionItem, run: { status: string; finalAnswer?: string | null; errorMsg?: string | null }) => {
  if (reply.status === 'DONE' || reply.status === 'FAILED') return
  if (run.status === 'SUCCEEDED') {
    reply.status = 'DONE'
    reply.approvalNotice = undefined
    reply.answer = run.finalAnswer || reply.answer || ''
    scrollToBottom()
  } else if (run.status === 'FAILED' || run.status === 'CANCELLED') {
    reply.status = 'FAILED'
    reply.approvalNotice = undefined
    reply.answer = run.errorMsg ? `运行失败：${run.errorMsg}` : reply.answer
    scrollToBottom()
  } else if (run.status === 'RUNNING' && reply.status === 'WAITING' && !reply.approvalNotice) {
    reply.approvalNotice = '人工已审核通过，Agent 正在继续执行…'
    scrollToBottom()
  }
}

/** 按 runId 找到对话中的对应气泡（审批中断帧携带 runId） */
const findReplyByRunId = (runId: number): AgentSessionItem | undefined =>
  agentSessions.value.find(item =>
    item.role === 'assistant'
    && item.status !== 'DONE' && item.status !== 'FAILED'
    && item.frames.some(f => f.event === 'APPROVAL_REQUIRED' && Number(f.data?.runId) === runId)
  )

/** 中断等待人工确认的 run：轮询状态，审批通过后提示，完成后回填答案到对话气泡（WebSocket 推送的兜底） */
const pollRunResult = (reply: AgentSessionItem, runId: number) => {
  if (!runId) return
  let attempts = 0
  const timer = setInterval(async () => {
    attempts++
    try {
      const run = await getAgentRun(runId)
      if (run.status === 'SUCCEEDED' || run.status === 'FAILED' || run.status === 'CANCELLED') {
        clearInterval(timer)
      }
      applyRunResult(reply, run)
    } catch {
      // 轮询失败忽略，下轮重试
    }
    // 15 分钟上限：覆盖"发起对话 → 离开去审批 → 回来"的长间隔场景
    if (attempts >= 300) {
      clearInterval(timer)
    }
  }, 3000)
}

/** Agent 会话列表 */
const loadAgentConversations = async () => {
  try {
    agentConversations.value = await getAgentConversations()
  } catch {
    // 忽略，不影响新对话
  }
}

const handleNewAgentConversation = async () => {
  const targetName = selectedTeam.value ? `团队：${selectedTeam.value.teamName}` : selectedAgent.value?.agentName || '对话'
  const conv = await createConversation({
    title: `Agent：${targetName}`
  })
  currentAgentConversationId.value = conv.id
  agentSessions.value = []
  loadAgentConversations()
}

const handleSelectAgentConversation = async (conv: AgentConversation) => {
  currentAgentConversationId.value = conv.conversationId
  await loadAgentHistory(conv.conversationId)
}

const handleRenameAgentConversation = (conv: AgentConversation) => {
  ElMessageBox.prompt('请输入新的会话名称', '重命名', {
    inputValue: conv.title || '',
    inputValidator: (v: string) => (v && v.trim().length <= 50) || '名称不能为空且不超过 50 字',
  }).then(async ({ value }) => {
    await updateConversationTitle(conv.conversationId, value.trim())
    ElMessage.success('重命名成功')
    loadAgentConversations()
  }).catch(() => { /* 取消 */ })
}

const handleDeleteAgentConversation = (conv: AgentConversation) => {
  ElMessageBox.confirm(`确定删除会话"${conv.title || 'Agent 对话'}"吗？其 ${conv.runCount} 次运行记录将一并删除。`, '提示', {
    type: 'warning'
  }).then(async () => {
    await agentApi.removeConversation(conv.conversationId)
    if (currentAgentConversationId.value === conv.conversationId) {
      currentAgentConversationId.value = undefined
      agentSessions.value = []
    }
    ElMessage.success('删除成功')
    loadAgentConversations()
  }).catch(() => { /* 取消 */ })
}

/** 加载指定 Agent 会话的运行记录渲染为问答对 */
const loadAgentHistory = async (conversationId?: number) => {
  agentSessions.value = []
  try {
    const result = await getAgentRuns({
      triggerType: 'CONVERSATION',
      conversationId,
      pageNum: 1,
      pageSize: 50
    })
    const runs = (result.records || []).slice().reverse()
    for (const run of runs) {
      agentSessions.value.push({
        role: 'user', content: run.inputSummary || '', frames: [], answer: '', status: 'DONE'
      })
      const reply: AgentSessionItem = reactive({
        role: 'assistant', content: '', frames: [], answer: run.finalAnswer || '', status: 'DONE'
      })
      if (run.status === 'WAITING_APPROVAL') {
        reply.frames.push({
          event: 'APPROVAL_REQUIRED', node: null, name: 'approval',
          data: { runId: run.id, todoId: null }
        })
        reply.status = 'WAITING'
        pollRunResult(reply, run.id)
      } else if (run.status === 'FAILED') {
        reply.answer = run.errorMsg ? `运行失败：${run.errorMsg}` : '运行失败'
        reply.status = 'FAILED'
      } else if (run.status === 'RUNNING') {
        reply.status = 'RUNNING'
        pollRunResult(reply, run.id)
      }
      agentSessions.value.push(reply)
    }
  } catch {
    // 无权限或接口异常时保持空白，不打断新对话
  }
}

watch(agentMode, (enabled) => {
  if (enabled) {
    loadAgentConversations()
  }
})

const handleAgentSend = async () => {
  const content = inputMessage.value.trim()
  if (!content || !selectedAgentTarget.value || isStreaming.value) return
  inputMessage.value = ''
  isStreaming.value = true
  const images = [...agentImages.value]
  agentImages.value = []

  // 未选定会话时懒创建（否则 run 不归属会话，历史无法保存/恢复）
  if (!currentAgentConversationId.value) {
    const targetName = selectedTeam.value ? `团队：${selectedTeam.value.teamName}` : selectedAgent.value?.agentName || '对话'
    const conv = await createConversation({ title: `Agent：${targetName}` })
    currentAgentConversationId.value = conv.id
  }

  agentSessions.value.push({ role: 'user', content, images, frames: [], answer: '', status: 'RUNNING' })
  // reactive：SSE/审批轮询等异步回调会跨渲染周期修改该对象，普通对象不触发视图更新
  const reply: AgentSessionItem = reactive({ role: 'assistant', content: '', frames: [], answer: '', status: 'RUNNING' })
  agentSessions.value.push(reply)
  scrollToBottom()

  await streamAgentChat(
    {
      agentCode: selectedAgentCode.value || undefined,
      teamCode: selectedTeamCode.value || undefined,
      conversationId: currentAgentConversationId.value,
      content,
      images
    },
    (frame) => {
      reply.frames.push(frame)
      if (frame.event === 'FINAL') {
        reply.answer = String(frame.data?.answer ?? '')
        reply.status = 'DONE'
      } else if (frame.event === 'CONTENT') {
        reply.answer = (reply.answer || '') + String(frame.data?.delta ?? '')
      } else if (frame.event === 'APPROVAL_REQUIRED') {
        reply.status = 'WAITING'
        pollRunResult(reply, Number(frame.data?.runId))
      } else if (frame.event === 'ERROR') {
        reply.status = 'FAILED'
        if (frame.data?.message) ElMessage.error(String(frame.data.message))
      }
      scrollToBottom()
    },
    (message) => {
      reply.status = 'FAILED'
      ElMessage.error(message || 'Agent 执行失败')
      isStreaming.value = false
    }
  )
  isStreaming.value = false
  scrollToBottom()
  loadAgentConversations()
}

const loadConversations = async () => {
  const result = await getConversationList({ pageNum: 1, pageSize: 50, modelId: selectedModelId.value })
  conversationList.value = result.list
}

const handleNewConversation = async () => {
  if (!selectedModelId.value) {
    ElMessage.warning('请先选择模型')
    return
  }
  const model = modelList.value.find(m => m.id === selectedModelId.value)
  const conv = await createConversation({ modelId: selectedModelId.value, title: `与 ${model?.modelName} 的对话` })
  conversationList.value.unshift(conv)
  handleSelectConversation(conv)
}

const handleSelectConversation = async (conv: ConversationResponse) => {
  currentConversation.value = conv
  messageList.value = await getConversationMessages(conv.id)
  scrollToBottom()
}

const handleRenameConversation = (conv: ConversationResponse) => {
  ElMessageBox.prompt('请输入新的会话名称', '重命名', {
    inputValue: conv.title || '',
    inputValidator: (v: string) => (v && v.trim().length <= 50) || '名称不能为空且不超过 50 字',
  }).then(async ({ value }) => {
    await updateConversationTitle(conv.id, value.trim())
    ElMessage.success('重命名成功')
    conv.title = value.trim()
  }).catch(() => { /* 取消 */ })
}

const handleDeleteConversation = (conv: ConversationResponse) => {
  ElMessageBox.confirm('确定要删除该对话吗？', '提示', { type: 'warning' }).then(async () => {
    await deleteConversation(conv.id)
    conversationList.value = conversationList.value.filter(c => c.id !== conv.id)
    if (currentConversation.value?.id === conv.id) {
      currentConversation.value = null
      messageList.value = []
    }
    ElMessage.success('删除成功')
  })
}

const handleSend = async () => {
  if (agentMode.value) {
    await handleAgentSend()
    return
  }
  if (!inputMessage.value.trim() || !currentConversation.value || isStreaming.value) return

  const content = inputMessage.value.trim()
  inputMessage.value = ''
  isStreaming.value = true
  streamingContent.value = ''

  // 添加用户消息
  const userMsg: MessageResponse = {
    id: Date.now(),
    conversationId: currentConversation.value.id,
    role: 'user',
    content: content,
    createTime: new Date().toISOString()
  }
  messageList.value.push(userMsg)
  scrollToBottom()

  try {
    await sseClient.connectPost('/ai/chat/message/stream', {
      conversationId: currentConversation.value.id,
      content: content
    }, {
      onMessage: (dataStr) => {
        // 兼容两种格式：JSON { content: "xxx" } 或纯字符串
        const data = parseSSEData<{ content?: string; done?: boolean; error?: boolean; message?: string }>(dataStr)
        // 检查错误消息
        if (data?.error) {
          ElMessage.error(data.message || 'AI服务暂时不可用')
          isStreaming.value = false
          streamingContent.value = ''
          return
        }
        if (data?.content) {
          streamingContent.value += data.content
          scrollToBottom()
        } else if (dataStr && dataStr !== '[DONE]' && !dataStr.startsWith('{')) {
          // 纯字符串格式
          streamingContent.value += dataStr
          scrollToBottom()
        }
      },
      onDone: async () => {
        // 将累积的流式内容作为 AI 回复添加到消息列表
        if (streamingContent.value) {
          const aiMsg: MessageResponse = {
            id: Date.now(),
            conversationId: currentConversation.value!.id,
            role: 'assistant',
            content: streamingContent.value,
            createTime: new Date().toISOString()
          }
          messageList.value.push(aiMsg)
          // 保存到数据库
          await saveAiMessage(currentConversation.value!.id, streamingContent.value)
        }
        streamingContent.value = ''
        isStreaming.value = false
        scrollToBottom()
        // 自动聚焦输入框
        nextTick(() => inputRef.value?.focus())
      },
      onError: (error) => {
        ElMessage.error(error.message || '发送失败')
        isStreaming.value = false
        streamingContent.value = ''
      }
    })
  } catch (e) {
    ElMessage.error('发送失败')
    isStreaming.value = false
    streamingContent.value = ''
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageContainerRef.value) {
      messageContainerRef.value.scrollTop = messageContainerRef.value.scrollHeight
    }
  })
}

watch(selectedModelId, () => {
  loadConversations()
  currentConversation.value = null
  messageList.value = []
})
</script>

<style scoped lang="scss">
.chat-container {
  .mode-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .mode-label {
      font-size: 13px;
      color: var(--el-text-color-regular);
    }
  }

  .agent-image {
    margin-bottom: 6px;
  }

  .agent-upload-preview {
    display: flex;
    align-items: center;
    margin-bottom: 6px;
  }

  display: flex;
  height: calc(100vh - 100px);
  background: #f5f7fa;

  .conversation-panel {
    width: 280px;
    background: #fff;
    border-right: 1px solid #e4e7ed;
    display: flex;
    flex-direction: column;

    .panel-header {
      padding: 15px;
      border-bottom: 1px solid #e4e7ed;
    }

    .panel-actions {
      padding: 10px 15px;
    }

    .conversation-list {
      flex: 1;
      overflow-y: auto;
      padding: 10px;

      .conversation-item {
        padding: 12px;
        border-radius: 8px;
        cursor: pointer;
        margin-bottom: 8px;
        border: 1px solid #e4e7ed;
        transition: all 0.3s;

        &:hover {
          background: #f5f7fa;
        }

        &.active {
          background: #ecf5ff;
          border-color: #409eff;
        }

        .conv-title {
          font-weight: 500;
          margin-bottom: 4px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .conv-meta {
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 12px;
          color: #909399;

          .conv-model {
            font-size: 12px;
          }

          .conv-actions {
            display: flex;
            align-items: center;

            .el-button + .el-button {
              margin-left: 4px;
            }
          }
        }
      }
    }
  }

  .chat-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    background: #fff;

    .empty-state {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .chat-header {
      padding: 15px 20px;
      border-bottom: 1px solid #e4e7ed;
      display: flex;
      align-items: center;
      gap: 10px;

      .chat-title {
        font-weight: 500;
        font-size: 16px;
      }
    }

    .message-container {
      flex: 1;
      overflow-y: auto;
      padding: 20px;

      .message-item {
        display: flex;
        gap: 12px;
        margin-bottom: 20px;

        .message-avatar {
          flex-shrink: 0;
        }

        .message-content {
          flex: 1;
          max-width: 80%;

          .message-bubble {
            padding: 12px 16px;
            border-radius: 8px;
          }

          .approval-notice {
            display: flex;
            align-items: center;
            margin-bottom: 8px;
            padding: 6px 10px;
            border-radius: 4px;
            background: #f0f9eb;
            color: #67c23a;
            font-size: 13px;
          }

          .message-time {
            font-size: 12px;
            color: #909399;
            margin-top: 4px;
          }
        }

        &.user {
          .message-content {
            .message-bubble {
              background: #ecf5ff;
            }
          }
        }

        &.assistant {
          .message-content {
            .message-bubble {
              background: transparent;
              padding: 0;
            }
          }

          .ai-avatar {
            background: #67c23a;
            color: #fff;
          }
        }

        &.streaming {
          .message-bubble {
            opacity: 0.8;
          }
        }
      }
    }

    .input-area {
      padding: 15px 20px;
      border-top: 1px solid #e4e7ed;
      display: flex;
      gap: 10px;

      .el-textarea {
        flex: 1;
      }
    }
  }
}
</style>