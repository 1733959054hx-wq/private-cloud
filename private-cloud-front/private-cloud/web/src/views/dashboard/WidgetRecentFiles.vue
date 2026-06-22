<template>
  <div class="widget-recent-files">
    <el-skeleton v-if="isLoading" :rows="3" animated />
    <div v-else-if="list.length === 0" class="widget-empty">
      <el-icon :size="28" color="#c0c4cc"><Document /></el-icon>
      <span>暂无最近访问</span>
    </div>
    <div v-else class="recent-list">
      <div v-for="file in list" :key="file.fileId" class="recent-item" @click="$emit('goPreview', file.fileId)">
        <span class="file-icon" :style="getIconBgStyle(getFileTypeGroup(file.fileType))">
          <FileIcon :file-type="file.fileType" :size="16" />
        </span>
        <div class="file-info">
          <div class="file-name">{{ file.fileName }}</div>
          <div class="file-meta">{{ formatFileSize(file.fileSize) }} · {{ formatTime(file.accessTime) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'
import FileIcon from '@/components/FileIcon.vue'
import { formatFileSize } from '@/utils/chunk-upload'
import type { RecentFile } from '@/api/workspace'

defineProps<{
  list: RecentFile[]
  isLoading?: boolean
}>()

defineEmits<{
  goPreview: [fileId: number]
}>()

function formatTime(time: string) {
  if (!time) return ''
  return time.replace(/:\d{2}$/, '')
}

function getFileTypeGroup(ext: string): string {
  if (!ext) return 'other'
  const e = ext.toLowerCase()
  if (['doc', 'docx', 'pdf', 'txt', 'md', 'wps'].includes(e)) return 'document'
  if (['xls', 'xlsx', 'csv'].includes(e)) return 'spreadsheet'
  if (['ppt', 'pptx'].includes(e)) return 'presentation'
  if (['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp'].includes(e)) return 'image'
  if (['mp4', 'avi', 'mkv', 'mov', 'flv'].includes(e)) return 'video'
  if (['mp3', 'wav', 'ogg', 'flac'].includes(e)) return 'audio'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(e)) return 'archive'
  return 'other'
}

const typeColorMap: Record<string, string> = {
  document: '#409EFF', spreadsheet: '#2ECC71', presentation: '#E67E22',
  image: '#E74C3C', video: '#9B59B6', audio: '#1ABC9C', archive: '#34495E', other: '#95A5A6',
}

function getIconBgStyle(typeGroup: string) {
  const color = typeColorMap[typeGroup] || typeColorMap.other
  return {
    backgroundColor: `${color}14`, color, borderRadius: '8px',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    width: '32px', height: '32px',
  }
}
</script>

<style scoped>
.widget-recent-files { min-height: 60px; }
.widget-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 24px 0; color: #c0c4cc; gap: 8px; font-size: 13px;
}
.recent-list { display: flex; flex-direction: column; gap: 6px; }
.recent-item {
  display: flex; align-items: center; gap: 10px; padding: 6px 8px;
  border-radius: 8px; cursor: pointer; transition: background 0.2s;
}
.recent-item:hover { background: #f5f7fa; }
.file-info { flex: 1; min-width: 0; }
.file-name { font-size: 13px; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.file-meta { font-size: 11px; color: #909399; margin-top: 2px; }
</style>
