<template>
  <div class="error-page">
    <div class="error-content">
      <h1>500</h1>
      <p>抱歉，服务器出了点问题</p>
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.error-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color-page);
}

.error-content {
  text-align: center;

  h1 {
    font-size: 120px;
    color: #f56c6c;
    margin-bottom: 20px;
  }

  p {
    font-size: 18px;
    color: var(--el-text-color-secondary);
    margin-bottom: 30px;
  }
}
</style>
