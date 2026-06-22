<template>
  <div class="widget-team-updates">
    <el-skeleton v-if="isLoading" :rows="3" animated />
    <div v-else-if="list.length === 0" class="widget-empty">
      <el-icon :size="28" color="#c0c4cc"><Bell /></el-icon>
      <span>暂无团队动态</span>
    </div>
    <div v-else class="update-list">
      <div v-for="(item, idx) in list" :key="idx" class="update-item">
        <span class="update-icon" :class="item.type">
          <el-icon :size="14">
            <UploadFilled v-if="item.type === 'file_upload'" />
            <Bell v-else />
          </el-icon>
        </span>
        <div class="update-info">
          <div class="update-title">{{ item.title }}</div>
          <div class="update-meta">
            <span class="update-operator">{{ item.operator || '系统' }}</span>
            <span class="update-time">{{ item.time }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Bell, UploadFilled } from '@element-plus/icons-vue'
import type { TeamUpdate } from '@/api/workspace'

defineProps<{
  list: TeamUpdate[]
  isLoading?: boolean
}>()
</script>

<style scoped>
.widget-team-updates { min-height: 60px; }
.widget-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 24px 0; color: #c0c4cc; gap: 8px; font-size: 13px;
}
.update-list { display: flex; flex-direction: column; gap: 8px; }
.update-item {
  display: flex; align-items: center; gap: 10px; padding: 6px 8px;
  border-radius: 8px; transition: background 0.2s;
}
.update-item:hover { background: #f5f7fa; }
.update-icon {
  width: 28px; height: 28px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.update-icon.file_upload { background: #e6f4ff; color: #1677ff; }
.update-icon.notification { background: #f9f0ff; color: #722ed1; }
.update-info { flex: 1; min-width: 0; }
.update-title { font-size: 13px; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.update-meta { font-size: 11px; color: #909399; margin-top: 2px; display: flex; gap: 8px; }
.update-operator { color: #606266; }
</style>
