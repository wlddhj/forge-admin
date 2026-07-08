<template>
  <n-space class="go-mt-0" :wrap="false">
    <n-button v-for="item in comBtnList" :key="item.title" :type="item.type" ghost @click="item.event" :loading="getLoading(item.title)">
      <template #icon>
        <n-icon size="16">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-html="iconSvg(item.title)"></svg>
        </n-icon>
      </template>
      <span>{{ item.title }}</span>
    </n-button>
  </n-space>
</template>

<script setup lang="ts">
import { computed, ref, h } from 'vue'
import { goDialog, fetchPathByName, routerTurnByPath, setSessionStorage, getSessionStorage } from '@/utils'
import { PreviewEnum } from '@/enums/pageEnum'
import { StorageEnum } from '@/enums/storageEnum'
import { useRoute } from 'vue-router'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { publishScreen as forgePublishScreen, getScreenDetail, getScreenIdFromUrl } from '@/api/forge/screen'
import { syncData } from '../../ContentEdit/components/EditTools/hooks/useSyncUpdate.hook'

const chartEditStore = useChartEditStore()

const routerParamsInfo = useRoute()
const saving = ref(false)
const publishing = ref(false)

const getLoading = (title: string) => {
  if (title === '保存') return saving.value
  if (title === '发布') return publishing.value
  return false
}

const iconSvg = (title: string) => {
  const map: Record<string, string> = {
    '保存': '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline>',
    '发布': '<line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>',
    '预览': '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>',
    '同步内容': '<polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>'
  }
  return map[title] || ''
}

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
    event: syncData
  },
  {
    select: true,
    title: '保存',
    type: 'primary' as const,
    event: saveHandle
  },
  {
    select: true,
    title: '预览',
    event: previewHandle
  },
  {
    select: true,
    title: '发布',
    event: sendHandle
  }
]

const comBtnList = computed(() => {
  if (chartEditStore.getEditCanvas.isCodeEdit) {
    return btnList
  }
  return btnList.slice(1)
})
</script>

<style lang="scss" scoped>
.align-center {
  margin-top: -4px;
}
</style>
