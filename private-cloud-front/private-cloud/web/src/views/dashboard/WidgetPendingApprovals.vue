<template>
  <div class="widget-pending-approvals">
    <el-skeleton v-if="isLoading" :rows="3" animated />
    <div v-else-if="list.length === 0" class="widget-empty">
      <el-icon :size="28" color="#c0c4cc"><Tickets /></el-icon>
      <span>暂无待审批</span>
    </div>
    <div v-else class="approval-list">
      <div v-for="item in list" :key="item.approvalId" class="approval-item">
        <span class="approval-icon">
          <el-icon :size="18"><Tickets /></el-icon>
        </span>
        <div class="approval-info">
          <div class="approval-title">{{ item.title }}</div>
          <div class="approval-meta">{{ item.type }} · {{ formatTime(item.createTime) }}</div>
        </div>
        <el-tag type="warning" size="small" effect="light" round>待审批</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Tickets } from '@element-plus/icons-vue'
import type { PendingApproval } from '@/api/workspace'

defineProps<{
  list: PendingApproval[]
  isLoading?: boolean
}>()

function formatTime(time: string) {
  if (!time) return ''
  return time.replace(/:\d{2}$/, '')
}
</script>

<style scoped>
.widget-pending-approvals { min-height: 60px; }
.widget-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 24px 0; color: #c0c4cc; gap: 8px; font-size: 13px;
}
.approval-list { display: flex; flex-direction: column; gap: 10px; }
.approval-item {
  display: flex; align-items: center; gap: 10px; padding: 8px 10px;
  border-radius: 8px; background: #fdf6ec; transition: background 0.2s;
}
.approval-item:hover { background: #faecd8; }
.approval-icon {
  width: 32px; height: 32px; border-radius: 8px; background: #e6a23c22;
  color: #e6a23c; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.approval-info { flex: 1; min-width: 0; }
.approval-title { font-size: 13px; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.approval-meta { font-size: 11px; color: #909399; margin-top: 2px; }
</style>
