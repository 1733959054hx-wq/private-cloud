<template>
  <div class="widget-my-favorites">
    <el-skeleton v-if="isLoading" :rows="3" animated />
    <div v-else-if="list.length === 0" class="widget-empty">
      <el-icon :size="28" color="#c0c4cc"><StarFilled /></el-icon>
      <span>暂无收藏</span>
    </div>
    <div v-else class="favorite-list">
      <div v-for="fav in list" :key="fav.id" class="favorite-item" @click="$emit('goPreview', fav.targetId)">
        <span class="fav-icon" :style="getIconBgStyle(getFileTypeGroup(fav.fileType || 'default'))">
          <FileIcon :file-type="fav.fileType || 'default'" :size="16" />
        </span>
        <div class="fav-info">
          <div class="fav-name">{{ fav.targetName }}</div>
          <div class="fav-meta">{{ formatTime(fav.createTime) }}</div>
        </div>
        <el-button type="danger" link size="small" @click.stop="$emit('removeFavorite', fav.targetId, fav.targetType)">取消</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { StarFilled } from '@element-plus/icons-vue'
import FileIcon from '@/components/FileIcon.vue'
import type { FavoriteVO } from '@/api/workspace'

defineProps<{
  list: FavoriteVO[]
  isLoading?: boolean
}>()

defineEmits<{
  goPreview: [targetId: number]
  removeFavorite: [targetId: number, targetType: number]
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
  return 'other'
}

const typeColorMap: Record<string, string> = {
  document: '#409EFF', spreadsheet: '#2ECC71', presentation: '#E67E22',
  image: '#E74C3C', other: '#95A5A6',
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
.widget-my-favorites { min-height: 60px; }
.widget-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 24px 0; color: #c0c4cc; gap: 8px; font-size: 13px;
}
.favorite-list { display: flex; flex-direction: column; gap: 6px; }
.favorite-item {
  display: flex; align-items: center; gap: 10px; padding: 6px 8px;
  border-radius: 8px; cursor: pointer; transition: background 0.2s;
}
.favorite-item:hover { background: #f5f7fa; }
.fav-info { flex: 1; min-width: 0; }
.fav-name { font-size: 13px; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fav-meta { font-size: 11px; color: #909399; margin-top: 2px; }
</style>
