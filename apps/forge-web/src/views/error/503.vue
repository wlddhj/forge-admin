<template>
  <div class="error-page">
    <div class="error-content">
      <h1>503</h1>
      <p>抱歉，服务暂时不可用，p>
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
  background: #f0f2f5;
}

.error-content {
  text-align: center;

  h1 {
    font-size: 120px;
    color: #e6a23c;
    margin-bottom: 20px;
  }

  p {
    font-size: 18px;
    color: #666;
    margin-bottom: 30px;
  }
}
</style>
