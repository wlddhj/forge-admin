<template>
  <div class="demo-container">
    <el-card shadow="never">
      <template #header>
        <span>hiprint 模板打印 Demo</span>
      </template>
      <el-alert type="info" :closable="false" show-icon>
        本页面演示如何通过模板编号调用打印。实际设计模板请到
        <el-link type="primary" @click="goDesign">打印模板管理 → 设计</el-link>
        页面操作。
      </el-alert>

      <el-form :model="printForm" label-width="100px" style="margin-top: 20px">
        <el-form-item label="模板编号">
          <el-input v-model="printForm.templateCode" placeholder="请输入模板编号（需先在管理页创建）" style="width: 320px" />
        </el-form-item>
        <el-form-item label="示例数据">
          <el-input v-model="printForm.dataJson" type="textarea" :rows="8" placeholder='JSON 格式，如 {"title":"示例","items":[{"name":"商品A","qty":2}]}' />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handlePrint">调用打印</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <print-view ref="printViewRef" @success="onPrintSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PrintView from '@/components/hiprint/print-view.vue'

defineOptions({ name: 'HiprintDemo' })

const router = useRouter()
const printViewRef = ref()

const printForm = reactive({
  templateCode: '',
  dataJson: '{"title":"示例标题","content":"示例内容"}'
})

const handlePrint = () => {
  if (!printForm.templateCode) {
    ElMessage.warning('请输入模板编号')
    return
  }
  let data: Record<string, any> = {}
  try {
    data = JSON.parse(printForm.dataJson)
  } catch (e) {
    ElMessage.error('示例数据 JSON 格式错误')
    return
  }
  printViewRef.value.prePrint(printForm.templateCode, data)
}

const onPrintSuccess = (code: string) => {
  console.log('打印成功', code)
}

const goDesign = () => {
  router.push('/system/print/index')
}
</script>

<style scoped>
.demo-container {
  padding: 15px;
}
</style>
