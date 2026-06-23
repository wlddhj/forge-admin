<template>
  <div class="node-wrap">
    <div class="node-wrap-box start-node" @click="show">
      <div class="title" style="background: #576a95">
        <el-icon class="icon"><UserFilled /></el-icon>
        <span>{{ nodeConfig.nodeName }}</span>
      </div>
      <div class="content">
        <span>{{ toText(nodeConfig) }}</span>
      </div>
    </div>
    <add-node v-model="nodeConfig.childNode"></add-node>
    <el-drawer title="发起人" v-model="drawer" destroy-on-close append-to-body :size="500">
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
            <el-form-item label="谁可以发起此审批">
              <el-button type="primary" icon="Plus" round @click="selectHandle(2, form.nodeAssigneeList)">
                选择角色
              </el-button>
              <div class="tags-list">
                <el-tag
                  v-for="(role, index) in form.nodeAssigneeList"
                  :key="role.id"
                  type="info"
                  closable
                  @close="delRole(index)"
                >
                  {{ role.name }}
                </el-tag>
              </div>
            </el-form-item>
            <el-alert
              v-if="!form.nodeAssigneeList || form.nodeAssigneeList.length == 0"
              title="不指定则默认所有人都可发起此审批"
              type="info"
              :closable="false"
            />
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
import { UserFilled, Edit } from '@element-plus/icons-vue'
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
  isEditTitle.value = false
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

const selectHandle = (type: number, data: FlowlongNodeAssignee[]) => {
  select?.(type, data)
}

const delRole = (index: number) => {
  form.value.nodeAssigneeList?.splice(index, 1)
}

const save = () => {
  emit('update:modelValue', form.value)
  drawer.value = false
}

const toText = (nodeConfig: FlowlongNodeModel): string => {
  if (nodeConfig.nodeAssigneeList && nodeConfig.nodeAssigneeList.length > 0) {
    return nodeConfig.nodeAssigneeList.map((item) => item.name).join('、')
  } else {
    return '所有人'
  }
}
</script>

<style></style>