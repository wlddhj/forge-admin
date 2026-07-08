<template>
  <div>
    <n-modal
      class="go-view-pro-ai"
      title="产品公告"
      v-model:show="showModal"
      preset="card"
      size="small"
      transform-origin="center"
      style="width: 900px"
    >
      <n-divider style="margin: 4px 0 10px" />
      <n-grid :cols="2" :x-gap="16">
        <n-gi>
          <n-flex vertical>
            <n-flex align="center">
              <n-text strong>GoView AI</n-text>
              <n-a href="https://ai.goviewlink.com/saas/" target="_blank">前往查看 👉</n-a>
            </n-flex>
            <n-a href="https://ai.goviewlink.com/saas/" target="_blank">
              <div class="go-view-img-wrapper">
                <img class="go-view-pro-img" :src="aiImage" />
              </div>
            </n-a>
          </n-flex>
        </n-gi>
        <n-gi>
          <n-flex vertical>
            <n-flex align="center">
              <n-text strong>GoView Flow</n-text>
              <n-a href="https://ai.goviewlink.com/saas/flow/" target="_blank">前往查看 👉</n-a>
            </n-flex>
            <n-a href="https://ai.goviewlink.com/saas/flow/" target="_blank">
              <div class="go-view-img-wrapper">
                <img class="go-view-pro-img" :src="flowImage" />
              </div>
            </n-a>
          </n-flex>
        </n-gi>
      </n-grid>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import aiImage from '@/assets/images/tips/go-view-pro-ai.png'
import flowImage from '@/assets/images/tips/go-view-flow.png'

const showModal = ref(true)
</script>

<style scoped lang="scss">
.go-view-pro-ai {
  .go-view-img-wrapper {
    width: 100%;
    border-radius: 8px;
    overflow: hidden;
    margin-top: 10px;
    cursor: pointer;

    :deep(img) {
      width: 100%;
      height: auto;
      display: block;
      border-radius: 8px;
      transition: transform 0.3s ease;
    }

    &:hover :deep(img) {
      transform: scale(1.02);
    }
  }
}
</style>
