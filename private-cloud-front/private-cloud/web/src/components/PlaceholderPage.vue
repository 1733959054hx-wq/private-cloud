<template>
  <div class="placeholder-page">
    <div class="placeholder-content">
      <el-icon :size="64" color="#c0c4cc"><Warning /></el-icon>
      <h2>{{ title }}</h2>
      <p class="placeholder-desc">功能待对接</p>
      <p class="placeholder-owner" v-if="owner">负责人：{{ owner }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Warning } from '@element-plus/icons-vue'

defineProps<{
  title: string
  owner?: string
}>()
</script>

<style scoped>
.placeholder-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 56px);
  background: #f5f7fa;
}

.placeholder-content {
  text-align: center;
  padding: 60px 40px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.placeholder-content h2 {
  margin-top: 20px;
  font-size: 22px;
  color: #303133;
  font-weight: 600;
}

.placeholder-desc {
  margin-top: 12px;
  font-size: 15px;
  color: #909399;
}

.placeholder-owner {
  margin-top: 8px;
  font-size: 13px;
  color: #c0c4cc;
}
</style>
