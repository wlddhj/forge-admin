<template>
  <el-pagination
    v-model:current-page="currentPage"
    v-model:page-size="pageSize"
    :total="total"
    :page-sizes="PAGE_SIZES"
    :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
    @size-change="handleChange"
    @current-change="handleChange"
  />
</template>

<script setup lang="ts">
import { useResponsive } from '@/composables/useResponsive'

const PAGE_SIZES = [10, 20, 30, 50, 100, 500, 1000]

const props = withDefaults(defineProps<{
  total: number
  pageNum: number
  pageSize?: number
}>(), {
  pageSize: 20
})

const emit = defineEmits<{
  (e: 'update:pageNum', value: number): void
  (e: 'update:pageSize', value: number): void
  (e: 'change'): void
}>()

const { isMobile } = useResponsive()

const currentPage = computed({
  get: () => props.pageNum,
  set: (val: number) => emit('update:pageNum', val)
})

const pageSize = computed({
  get: () => props.pageSize!,
  set: (val: number) => emit('update:pageSize', val)
})

function handleChange() {
  emit('change')
}
</script>
