<template>
  <el-icon :size="size" :color="resolvedColor">
    <component :is="iconComponent" />
  </el-icon>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Document, DocumentCopy, DataAnalysis, DataBoard, VideoPlay, Headset,
  PictureFilled, Tickets, Box, CollectionTag, Monitor,
  Brush, Setting, Paperclip,
} from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  fileType?: string
  size?: number
  color?: string
}>(), {
  fileType: '',
  size: 18,
})

// 文件类型 -> 图标组件映射（PDF 与 DOC 使用不同图标，避免撞图）
const iconComponent = computed(() => {
  const map: Record<string, any> = {
    pdf: Document,
    doc: DocumentCopy, docx: DocumentCopy,
    xls: DataAnalysis, xlsx: DataAnalysis,
    ppt: DataBoard, pptx: DataBoard,
    mp4: VideoPlay, avi: VideoPlay, mov: VideoPlay, webm: VideoPlay,
    mp3: Headset, wav: Headset, ogg: Headset,
    png: PictureFilled, jpg: PictureFilled, jpeg: PictureFilled, gif: PictureFilled, webp: PictureFilled,
    md: Tickets, txt: Tickets,
    zip: Box, rar: Box, '7z': Box,
    ftl: CollectionTag,
    html: Monitor,
    css: Brush,
    js: Setting,
  }
  return map[props.fileType?.toLowerCase()] || Paperclip
})

// 文件类型 -> 主题色映射（区分不同文件类型的视觉识别度）
const typeColorMap: Record<string, string> = {
  pdf: '#E53935',       // 红色 - PDF 专属
  doc: '#1976D2',       // 蓝色 - Word
  docx: '#1976D2',      // 蓝色 - Word
  xls: '#2E7D32',       // 绿色 - Excel
  xlsx: '#2E7D32',      // 绿色 - Excel
  ppt: '#EF6C00',       // 橙色 - PPT
  pptx: '#EF6C00',      // 橙色 - PPT
  mp4: '#7B1FA2',       // 紫色 - 视频
  avi: '#7B1FA2',
  mov: '#7B1FA2',
  webm: '#7B1FA2',
  mp3: '#00838F',       // 青色 - 音频
  wav: '#00838F',
  ogg: '#00838F',
  png: '#5C6BC0',       // 靛蓝 - 图片
  jpg: '#5C6BC0',
  jpeg: '#5C6BC0',
  gif: '#5C6BC0',
  webp: '#5C6BC0',
  md: '#6D4C41',        // 棕色 - Markdown
  txt: '#546E7A',       // 灰蓝 - 文本
  zip: '#8D6E63',       // 棕灰 - 压缩包
  rar: '#8D6E63',
  '7z': '#8D6E63',
}

// 优先使用外部传入的 color，否则按文件类型取主题色
const resolvedColor = computed(() => {
  if (props.color) return props.color
  return typeColorMap[props.fileType?.toLowerCase()] || '#606266'
})
</script>
