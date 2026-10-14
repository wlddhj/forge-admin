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
        <el-select v-else v-model="selectedAgentCode" placeholder="选择 Agent" style="width: 100%">
          <el-option v-for="agent in agentList" :key="agent.agentCode" :label="agent.agentName" :value="agent.agentCode" />
        </el-select>
      </div>
      <div class="panel-actions">
        <el-button type="primary" size="small" @click="handleNewConversation">
          <el-icon><Plus /></el-icon>
          新对话
        </el-button>
      </div>
      <div class="conversation-list">
        <div
          v-for="conv in conversationList"
          :key="conv.id"
          :class="['conversation-item', { active: currentConversation?.id === conv.id }]"
          @click="handleSelectConversation(conv)"
        >
          <div class="conv-title">{{ conv.title || '新对话' }}</div>
          <div class="conv-meta">
            <span class="conv-model">{{ conv.modelName }}</span>
            <el-button type="danger" link size="small" @click.stop="handleDeleteConversation(conv)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧：对话内容 -->
    <div class="chat-panel">
      <!-- Agent 模式 -->
      <template v-if="agentMode">
        <div class="chat-header">
          <span class="chat-title">{{ selectedAgent?.agentName || 'Agent 对话' }}</span>
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
          <el-button type="primary" :loading="isStreaming" :disabled="!inputMessage.trim() || (agentMode && !selectedAgentCode)" @click="handleSend">
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
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getModelList, AI_MODEL_TYPE_TEXT } from '@/api/ai/model'
import { createConversation, getConversationList, getConversationMessages, deleteConversation, saveAiMessage } from '@/api/ai/chat'
import { createSSE, parseSSEData } from '@/utils/sse'
import type { ModelConfigResponse } from '@/api/ai/model'
import type { ConversationResponse, MessageResponse } from '@/api/ai/chat'
import { formatDateTime } from '@/utils/dateFormat'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AgentStepTimeline from './components/AgentStepTimeline.vue'
import { getAgentConfigs, streamAgentChat, getAgentRun } from '@/api/ai/agent'
import type { AgentConfig, AgentEventFrame } from '@/api/ai/agent'

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
const selectedAgentCode = ref('')
const agentImages = ref<string[]>([])
const imageInputRef = ref<HTMLInputElement | null>(null)

interface AgentSessionItem {
  role: 'user' | 'assistant'
  content: string
  images?: string[]
  frames: AgentEventFrame[]
  answer: string
  status: 'RUNNING' | 'DONE' | 'WAITING' | 'FAILED'
}
const agentSessions = ref<AgentSessionItem[]>([])

const selectedAgent = computed(() => agentList.value.find(a => a.agentCode === selectedAgentCode.value))

onMounted(async () => {
  modelList.value = await getModelList(AI_MODEL_TYPE_TEXT)
  const defaultModel = modelList.value.find(m => m.isDefault === 1)
  if (defaultModel) {
    selectedModelId.value = defaultModel.id
  }
  loadConversations()
  getAgentConfigs(1).then(list => {
    agentList.value = list
    if (list.length) {
      selectedAgentCode.value = list[0].agentCode
    }
  }).catch(() => { /* 无 Agent 权限时静默 */ })
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

/** 中断等待人工确认的 run：轮询状态，完成后回填答案到对话气泡 */
const pollRunResult = (reply: AgentSessionItem, runId: number) => {
  if (!runId) return
  let attempts = 0
  const timer = setInterval(async () => {
    attempts++
    try {
      const run = await getAgentRun(runId)
      if (run.status === 'SUCCEEDED') {
        clearInterval(timer)
        reply.status = 'DONE'
        reply.answer = run.finalAnswer || reply.answer || ''
        scrollToBottom()
      } else if (run.status === 'FAILED' || run.status === 'CANCELLED') {
        clearInterval(timer)
        reply.status = 'FAILED'
        reply.answer = run.errorMsg ? `运行失败：${run.errorMsg}` : reply.answer
        scrollToBottom()
      }
    } catch {
      // 轮询失败忽略，下轮重试
    }
    if (attempts >= 60) {
      clearInterval(timer)
    }
  }, 3000)
}

const handleAgentSend = async () => {
  const content = inputMessage.value.trim()
  if (!content || !selectedAgentCode.value || isStreaming.value) return
  inputMessage.value = ''
  isStreaming.value = true
  const images = [...agentImages.value]
  agentImages.value = []

  agentSessions.value.push({ role: 'user', content, images, frames: [], answer: '', status: 'RUNNING' })
  // reactive：SSE/审批轮询等异步回调会跨渲染周期修改该对象，普通对象不触发视图更新
  const reply: AgentSessionItem = reactive({ role: 'assistant', content: '', frames: [], answer: '', status: 'RUNNING' })
  agentSessions.value.push(reply)
  scrollToBottom()

  await streamAgentChat(
    { agentCode: selectedAgentCode.value, content, images },
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