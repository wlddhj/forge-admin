<template>
  <Transition name="slide-up">
    <div v-if="show" class="mobile-bottom-actions">
      <div class="action-bar">
        <span class="item-name">{{ itemTitle }}</span>
        <div class="actions">
          <slot name="actions" :item="item"></slot>
          <el-button size="small" @click.stop="handleCancel">取消</el-button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
interface Props {
  show: boolean
  item?: any
  itemTitle?: string
}

interface Emits {
  (e: 'cancel'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const handleCancel = () => {
  emit('cancel')
}
</script>

<style scoped lang="scss">
.mobile-bottom-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: #fff;
  border-top: 1px solid #e6e6e6;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);
  z-index: 100;

  .action-bar {
    padding: 10px 12px;

    .item-name {
      display: block;
      font-size: 13px;
      color: #909399;
      margin-bottom: 8px;
      text-align: center;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .actions {
      display: flex;
      gap: 6px;
      justify-content: center;

      .el-button {
        flex: 1;
        min-width: 0;
        padding: 6px 8px;
        font-size: 12px;
        height: 36px;
        white-space: nowrap;
      }
    }
  }
}

// 滑入动画
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.3s ease-out;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}
</style>
