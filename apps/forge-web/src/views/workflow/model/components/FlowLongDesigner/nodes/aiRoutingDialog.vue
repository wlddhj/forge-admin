<template>
  <el-drawer v-model="drawer" title="AI 智能路由设置" destroy-on-close append-to-body :size="600">
    <template #header>
      <div class="node-wrap-drawer__title">
        <label>AI 智能路由设置</label>
      </div>
    </template>
    <el-container>
      <el-main style="padding: 0 20px 20px 20px">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px">
          <p>启用后，流程到达该网关时优先由 AI 根据流程变量选择分支；AI 失败、超时、置信度不足或返回无效分支时，自动回退到分支条件判断，仍不中则走默认分支（无条件的分支）。</p>
          <p style="color: #e6a23c">启用 AI 路由的网关必须保留一个无条件默认分支，否则发起流程时将被拦截。</p>
        </el-alert>

        <el-form label-width="110px">
          <el-form-item label="启用 AI 路由">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <template v-if="form.enabled">
            <el-form-item label="路由提示词">
              <el-input
                v-model="form.prompt"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
                placeholder="描述路由规则，例如：金额大于 1 万且客户等级为 VIP 走「高额审批」分支"
              />
            </el-form-item>
            <el-form-item label="模型名称">
              <el-input v-model="form.modelName" placeholder="留空使用系统默认模型" clearable />
            </el-form-item>
            <el-form-item label="决策超时(秒)">
              <el-input-number v-model="form.timeoutSeconds" :min="5" :max="60" :step="5" />
            </el-form-item>
            <el-form-item label="置信度阈值">
              <el-input-number v-model="form.confidenceThreshold" :min="0" :max="100" :step="5" />
              <div class="form-tip">AI 置信度低于该值时不采纳其选择，回退条件判断</div>
            </el-form-item>
          </template>
        </el-form>
      </el-main>
      <el-footer>
        <el-button type="primary" @click="save">保存</el-button>
        <el-button @click="drawer = false">取消</el-button>
      </el-footer>
    </el-container>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { FlowlongNodeModel, FlowlongAiRoutingConfig } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: boolean
  node: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', payload: { enabled: boolean; config?: FlowlongAiRoutingConfig }): void
}>()

const drawer = ref(false)
const form = ref({
  enabled: false,
  prompt: '',
  modelName: '',
  timeoutSeconds: 30,
  confidenceThreshold: 80
})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      form.value = {
        enabled: !!props.node.aiRouting,
        prompt: props.node.aiRoutingConfig?.prompt ?? '',
        modelName: props.node.aiRoutingConfig?.modelName ?? '',
        timeoutSeconds: props.node.aiRoutingConfig?.timeoutSeconds ?? 30,
        confidenceThreshold: props.node.aiRoutingConfig?.confidenceThreshold ?? 80
      }
      drawer.value = true
    }
  }
)

watch(drawer, (val) => {
  if (!val) {
    emit('update:modelValue', false)
  }
})

const save = () => {
  if (form.value.enabled) {
    emit('save', {
      enabled: true,
      config: {
        prompt: form.value.prompt,
        modelName: form.value.modelName,
        timeoutSeconds: form.value.timeoutSeconds,
        confidenceThreshold: form.value.confidenceThreshold
      }
    })
  } else {
    emit('save', { enabled: false })
  }
  drawer.value = false
}
</script>

<style scoped lang="scss">
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
