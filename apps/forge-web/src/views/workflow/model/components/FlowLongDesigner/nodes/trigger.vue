<!--
  触发器任务节点（FlowLong TaskType.trigger = 7）

  支持 4 种业务模式：
  - expression  : SpEL 表达式
  - bean        : Spring Bean(必须带 @FlowLongTrigger 注解)
  - class       : 反射类全限定名
  - delegateExpression : SpEL 求出 Bean 名,再走 bean 路径
-->
<template>
  <div class="node-wrap">
    <div class="node-wrap-box" @click="show">
      <div class="title" style="background: #9b59b6">
        <el-icon class="icon"><Lightning /></el-icon>
        <span>{{ nodeConfig.nodeName }}</span>
        <el-icon class="close" @click.stop="delNode()"><Close /></el-icon>
      </div>
      <div class="content">
        <el-tag size="small" :type="modeTagType">{{ modeLabel }}</el-tag>
        <span style="margin-left: 6px">{{ modeSummary }}</span>
      </div>
    </div>
    <add-node v-model="nodeConfig.childNode"></add-node>
    <el-drawer title="触发器任务设置" v-model="drawer" destroy-on-close append-to-body :size="560">
      <template #header>
        <div class="node-wrap-drawer__title">
          <label @click="editTitle" v-if="!isEditTitle">
            {{ form.nodeName }}
            <el-icon class="node-wrap-drawer__title-edit"><Edit /></el-icon>
          </label>
          <el-input
            v-if="isEditTitle"
            ref="nodeTitleRef"
            v-model="form.nodeName"
            clearable
            @blur="saveTitle"
            @keyup.enter="saveTitle"
          ></el-input>
        </div>
      </template>
      <el-container>
        <el-main style="padding: 0 20px 20px 20px">
          <el-form label-position="top">
            <el-form-item label="业务模式">
              <el-select v-model="form.triggerType" style="width: 100%">
                <el-option label="expression - SpEL 表达式" value="expression" />
                <el-option label="bean - Spring Bean(白名单)" value="bean" />
                <el-option label="class - 反射 Java 类" value="class" />
                <el-option label="delegateExpression - SpEL 求 Bean 名" value="delegateExpression" />
              </el-select>
            </el-form-item>

            <!-- expression 模式 -->
            <template v-if="form.triggerType === 'expression'">
              <el-form-item label="触发器表达式 (SpEL)">
                <el-input
                  v-model="form.triggerExpression"
                  type="textarea"
                  :rows="4"
                  placeholder="例：${true}&#10;例：${amount > 1000}&#10;例：T(SpelUtil).setVar(execution, 'riskLevel', 'A')"
                />
                <el-alert
                  title="返回 true 视为触发成功。常用内置变量: task / instance / execution / args (流程变量)。"
                  type="info"
                  :closable="false"
                  style="margin-top: 10px"
                />
              </el-form-item>
            </template>

            <!-- bean 模式 -->
            <template v-if="form.triggerType === 'bean'">
              <el-form-item label="选择触发器 Bean">
                <el-select
                  v-model="form.triggerBean"
                  filterable
                  placeholder="选择带 @FlowLongTrigger 注解的 Bean"
                  style="width: 100%"
                  :loading="beanListLoading"
                >
                  <el-option
                    v-for="b in beanList"
                    :key="b.beanName"
                    :value="b.beanName"
                    :label="b.name"
                  >
                    <span style="float: left">{{ b.name }}</span>
                    <span style="float: right; color: #909399; font-size: 12px">{{ b.className }}</span>
                  </el-option>
                </el-select>
                <el-button link type="primary" @click="loadBeanList" style="margin-top: 4px">刷新列表</el-button>
              </el-form-item>
              <el-form-item label="方法名">
                <el-input v-model="form.triggerMethod" placeholder="如: execute / evaluateFlexible" />
                <el-alert
                  title="形参规则: 标注 @TriggerParam('key') 的按 ctx 中变量名注入;Execution/NodeModel/Map 类型自动注入;其他注入 null"
                  type="info"
                  :closable="false"
                  style="margin-top: 8px"
                />
              </el-form-item>
            </template>

            <!-- class 模式 -->
            <template v-if="form.triggerType === 'class'">
              <el-form-item label="类全限定名">
                <el-input v-model="form.triggerClass" placeholder="如: com.example.MyTrigger" />
              </el-form-item>
              <el-form-item label="方法名">
                <el-input v-model="form.triggerMethod" placeholder="如: execute" />
                <el-alert
                  title="class 模式不走 Spring 容器,会调用 Class.forName().newInstance(),生产环境建议额外做包前缀白名单"
                  type="warning"
                  :closable="false"
                  style="margin-top: 8px"
                />
              </el-form-item>
            </template>

            <!-- delegateExpression 模式 -->
            <template v-if="form.triggerType === 'delegateExpression'">
              <el-form-item label="Bean 名称 SpEL 表达式">
                <el-input
                  v-model="form.triggerExpression"
                  type="textarea"
                  :rows="2"
                  placeholder="例：'contractRiskTrigger'&#10;例：args.triggerBean"
                />
                <el-alert
                  title="SpEL 求值结果必须是带 @FlowLongTrigger 注解的 Bean 名"
                  type="info"
                  :closable="false"
                  style="margin-top: 8px"
                />
              </el-form-item>
              <el-form-item label="方法名">
                <el-input v-model="form.triggerMethod" placeholder="如: execute" />
              </el-form-item>
            </template>

            <el-divider></el-divider>

            <el-form-item>
              <el-checkbox v-model="form.continueOnError">异常时继续流转(不暂停流程)</el-checkbox>
            </el-form-item>

            <el-form-item>
              <el-alert
                title="触发器节点没有审批人,执行完后自动跳到 childNode。返回值: boolean → 触发结果, void/其他 → 视为成功。"
                type="warning"
                :closable="false"
              />
            </el-form-item>
          </el-form>
        </el-main>
        <el-footer>
          <el-button type="primary" @click="save">保存</el-button>
          <el-button @click="drawer = false">取消</el-button>
        </el-footer>
      </el-container>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted } from 'vue'
import { Lightning, Close, Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import addNode from './addNode.vue'
import type { FlowlongNodeModel } from '@/composables/useFlowLongDataTransform'
import request from '@/utils/request'

const props = defineProps<{
  modelValue: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel): void
}>()

interface TriggerBeanInfo {
  beanName: string
  className: string
  name: string
  description: string
  category: string
}

const nodeConfig = ref<FlowlongNodeModel>({})
const drawer = ref(false)
const isEditTitle = ref(false)
const nodeTitleRef = ref<HTMLInputElement | null>(null)
const form = ref<FlowlongNodeModel>({})

const beanList = ref<TriggerBeanInfo[]>([])
const beanListLoading = ref(false)

const modeLabel = computed(() => {
  const m = nodeConfig.value.triggerType || 'expression'
  const map: Record<string, string> = {
    expression: 'SpEL 表达式',
    bean: 'Spring Bean',
    class: 'Java 类',
    delegateExpression: '委托表达式'
  }
  return map[m] || m
})

const modeTagType = computed(() => {
  const m = nodeConfig.value.triggerType || 'expression'
  return m === 'bean' ? 'success' : m === 'class' ? 'warning' : 'info'
})

const modeSummary = computed(() => {
  const n = nodeConfig.value
  switch (n.triggerType) {
    case 'bean':
      return `${n.triggerBean || '?'}#${n.triggerMethod || '?'}`
    case 'class':
      return `${n.triggerClass || '?'}#${n.triggerMethod || '?'}`
    case 'delegateExpression':
      return `${n.triggerExpression || '?'}#${n.triggerMethod || '?'}`
    default:
      return n.triggerExpression || ''
  }
})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

const show = () => {
  form.value = JSON.parse(JSON.stringify(nodeConfig.value))
  if (!form.value.triggerType) {
    form.value.triggerType = 'expression'
  }
  drawer.value = true
  if (beanList.value.length === 0) {
    loadBeanList()
  }
}

const loadBeanList = async () => {
  beanListLoading.value = true
  try {
    const res: any = await request.get('/workflow/trigger/beans')
    beanList.value = (res.data || res || []) as TriggerBeanInfo[]
  } catch (e) {
    ElMessage.warning('加载触发器 Bean 列表失败')
  } finally {
    beanListLoading.value = false
  }
}

const editTitle = () => {
  isEditTitle.value = true
  nextTick(() => {
    nodeTitleRef.value?.focus()
  })
}

const saveTitle = () => {
  isEditTitle.value = false
}

const save = () => {
  emit('update:modelValue', form.value)
  drawer.value = false
}

const delNode = () => {
  emit('update:modelValue', nodeConfig.value.childNode || null)
}
</script>

<style></style>
