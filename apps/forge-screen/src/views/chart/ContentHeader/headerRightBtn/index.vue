<template>
  <n-space class="go-mt-0" :wrap="false">
    <n-button v-for="item in comBtnList" :key="item.title" :type="item.type" ghost @click="item.event" :loading="item.loading">
      <template #icon>
        <component :is="item.icon"></component>
      </template>
      <span>{{ item.title }}</span>
    </n-button>
  </n-space>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { renderIcon, goDialog, fetchPathByName, routerTurnByPath, setSessionStorage, getSessionStorage } from '@/utils'
import { PreviewEnum } from '@/enums/pageEnum'
import { StorageEnum } from '@/enums/storageEnum'
import { useRoute } from 'vue-router'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { publishScreen as forgePublishScreen, getScreenDetail, getScreenIdFromUrl } from '@/api/forge/screen'
import { syncData } from '../../ContentEdit/components/EditTools/hooks/useSyncUpdate.hook'
import { icon } from '@/plugins'
import { cloneDeep } from 'lodash'

const { BrowsersOutlineIcon, SendIcon, AnalyticsIcon, SaveIcon } = icon.ionicons5
const chartEditStore = useChartEditStore()

const routerParamsInfo = useRoute()
const saving = ref(false)
const publishing = ref(false)

// 保存
const saveHandle = async () => {
  const id = getScreenIdFromUrl()
  if (!id) {
    window['$message']?.error('未找到大屏 ID')
    return
  }
  saving.value = true
  try {
    const detail = await getScreenDetail(id)
    await chartEditStore.saveProjectToApi(id, detail.code, detail.name)
    window['$message']?.success('已保存')
  } catch (e: any) {
    window['$message']?.error('保存失败：' + (e?.message || String(e)))
  } finally {
    saving.value = false
  }
}

// 预览
const previewHandle = () => {
  const path = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
  if (!path) return
  const { id } = routerParamsInfo.params
  const previewId = typeof id === 'string' ? id : id[0]
  const storageInfo = chartEditStore.getStorageInfo()
  const sessionStorageInfo = getSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST) || []

  if (sessionStorageInfo?.length) {
    const repeateIndex = sessionStorageInfo.findIndex((e: { id: string }) => e.id === previewId)
    if (repeateIndex !== -1) {
      sessionStorageInfo.splice(repeateIndex, 1, { id: previewId, ...storageInfo })
      setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, sessionStorageInfo)
    } else {
      sessionStorageInfo.push({ id: previewId, ...storageInfo })
      setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, sessionStorageInfo)
    }
  } else {
    setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, [{ id: previewId, ...storageInfo }])
  }
  routerTurnByPath(path, [previewId], undefined, true)
}

// 发布
const sendHandle = async () => {
  const id = getScreenIdFromUrl()
  if (!id) {
    window['$message']?.error('未找到大屏 ID')
    return
  }
  publishing.value = true
  try {
    const detail = await getScreenDetail(id)
    await chartEditStore.saveProjectToApi(id, detail.code, detail.name)
    await forgePublishScreen(detail.code)
    window['$message']?.success('发布成功')
  } catch (e: any) {
    console.error('[发布失败]', e)
    window['$message']?.error('发布失败：' + (e?.message || String(e)))
  } finally {
    publishing.value = false
  }
}

const btnList = [
  {
    select: true,
    title: '同步内容',
    type: 'primary' as const,
    icon: renderIcon(AnalyticsIcon),
    event: syncData
  },
  {
    select: true,
    title: '保存',
    type: 'primary' as const,
    icon: renderIcon(SaveIcon),
    event: saveHandle,
    loading: saving
  },
  {
    select: true,
    title: '预览',
    icon: renderIcon(BrowsersOutlineIcon),
    event: previewHandle
  },
  {
    select: true,
    title: '发布',
    icon: renderIcon(SendIcon),
    event: sendHandle,
    loading: publishing
  }
]

const comBtnList = computed(() => {
  if (chartEditStore.getEditCanvas.isCodeEdit) {
    return btnList
  }
  const cloneList = cloneDeep(btnList)
  cloneList.shift()
  return cloneList
})
</script>

<style lang="scss" scoped>
.align-center {
  margin-top: -4px;
}
</style>
