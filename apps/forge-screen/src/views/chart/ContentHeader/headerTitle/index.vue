<template>
  <n-space>
    <n-icon size="20" :depth="3">
      <fish-icon></fish-icon>
    </n-icon>
    <n-text @click="handleFocus">
      工作空间 -
      <n-button v-show="!focus" secondary size="tiny">
        <span class="title">
          {{ comTitle }}
        </span>
      </n-button>
    </n-text>

    <n-input
      v-show="focus"
      ref="inputInstRef"
      size="small"
      type="text"
      maxlength="16"
      show-count
      placeholder="请输入项目名称"
      v-model:value.trim="title"
      @keyup.enter="handleBlur"
      @blur="handleBlur"
    ></n-input>
  </n-space>
</template>

<script setup lang="ts">
import { ref, nextTick, computed, watch } from 'vue'
import { setTitle } from '@/utils'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasConfigEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { getScreenDetail, getScreenIdFromUrl } from '@/api/forge/screen'
import { icon } from '@/plugins'

const { FishIcon } = icon.ionicons5
const chartEditStore = useChartEditStore()

const focus = ref<boolean>(false)
const inputInstRef = ref(null)
const oldTitle = ref<string>('')
const initialized = ref<boolean>(false)

// 初始值为空，加载完 store 中的 projectName 后再同步
const title = ref<string>('')

// 监听 store 中的 projectName：加载完成后用真实名称替换 title
watch(
  () => chartEditStore.editCanvasConfig.projectName,
  (val) => {
    if (!initialized.value && val) {
      title.value = String(val)
      initialized.value = true
    }
  },
  { immediate: true }
)

const comTitle = computed(() => {
  const cleanTitle = title.value.replace(/\s/g, '')
  const newTitle = cleanTitle.length ? cleanTitle : '新项目'
  setTitle(`工作空间-${newTitle}`)
  return newTitle
})

const handleFocus = () => {
  oldTitle.value = title.value
  focus.value = true
  nextTick(() => {
    inputInstRef.value && (inputInstRef.value as any).focus()
  })
}

const handleBlur = async () => {
  focus.value = false
  const newName = title.value.replace(/\s/g, '') || '新项目'
  // 同步到 store
  if (newName !== chartEditStore.editCanvasConfig.projectName) {
    chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.PROJECT_NAME, newName)
  }
  // 名称变更后同步到后端
  if (newName !== oldTitle.value) {
    const id = getScreenIdFromUrl()
    if (id) {
      try {
        const detail = await getScreenDetail(id)
        await chartEditStore.saveProjectToApi(id, detail.code, newName)
      } catch (e) {
        console.error('[headerTitle] 名称同步失败', e)
      }
    }
  }
}
</script>
<style lang="scss" scoped>
.title {
  padding-left: 5px;
  padding-right: 5px;
  font-size: 15px;
}
</style>
