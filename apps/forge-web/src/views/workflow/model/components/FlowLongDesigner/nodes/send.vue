<template>
  <div class="node-wrap">
    <div class="node-wrap-box" @click="show">
      <div class="title" style="background: #3296fa">
        <el-icon class="icon"><Promotion /></el-icon>
        <span>{{ nodeConfig.nodeName }}</span>
        <el-icon class="close" @click.stop="delNode()"><Close /></el-icon>
      </div>
      <div class="content">
        <span v-if="toText(nodeConfig)">{{ toText(nodeConfig) }}</span>
        <span v-else class="placeholder">请选择人员</span>
      </div>
    </div>
    <add-node v-model="nodeConfig.childNode"></add-node>
    <el-drawer title="抄送人设置" v-model="drawer" destroy-on-close append-to-body :size="500">
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
            <el-form-item label="选择要抄送的人员">
              <el-button type="primary" icon="Plus" round @click="selectHandle(1, form.nodeAssigneeList)">
                选择人员
              </el-button>
              <div class="tags-list">
                <el-tag v-for="(user, index) in form.nodeAssigneeList" :key="user.id" closable @close="delUser(index)">
                  {{ user.name }}
                </el-tag>
              </div>
            </el-form-item>
            <el-form-item label="">
              <el-checkbox v-model="form.userSelectFlag" label="允许发起人自选抄送人"></el-checkbox>
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
import { ref, watch, nextTick, inject } from 'vue'
import { Promotion, Close, Edit } from '@element-plus/icons-vue'
import addNode from './addNode.vue'
import type { FlowlongNodeModel, FlowlongNodeAssignee } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel): void
}>()

const select = inject<(type: number, data: FlowlongNodeAssignee[]) => void>('select')

const nodeConfig = ref<FlowlongNodeModel>({})
const drawer = ref(false)
const isEditTitle = ref(false)
const nodeTitleRef = ref<HTMLInputElement | null>(null)
const form = ref<FlowlongNodeModel>({})

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
  drawer.value = true
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

const delUser = (index: number) => {
  form.value.nodeAssigneeList?.splice(index, 1)
}

const selectHandle = (type: number, data: FlowlongNodeAssignee[]) => {
  select?.(type, data)
}

const toText = (nodeConfig: FlowlongNodeModel): string | false => {
  if (nodeConfig.nodeAssigneeList && nodeConfig.nodeAssigneeList.length > 0) {
    const users = nodeConfig.nodeAssigneeList.map((item) => item.name).join('、')
    return users
  } else {
    if (nodeConfig.userSelectFlag) {
      return '发起人自选'
    } else {
      return false
    }
  }
}
</script>

<style></style>