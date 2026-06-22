<template>
  <div class="tag-cloud-wrapper">
    <div v-if="title" class="tag-cloud-header">
      <el-icon :size="16"><PriceTag /></el-icon>
      <span>{{ title }}</span>
      <el-tag v-if="activeTag" size="small" type="warning" closable @close="clearActiveTag" class="active-tag-label">
        {{ activeTag }}
      </el-tag>
    </div>

    <div v-if="loading" class="tag-cloud-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载标签...</span>
    </div>

    <el-empty
      v-else-if="tags.length === 0"
      :image-size="60"
      description="暂无标签"
    />

    <div v-else class="tag-cloud-container">
      <transition-group name="tag-fade" tag="div" class="tag-items">
        <div
          v-for="(tag, index) in tags"
          :key="tag.tagName"
          :class="['tag-chip', { active: activeTag === tag.tagName }]"
          :style="getTagStyle(tag, index)"
          :title="tag.tagName"
          @click="handleTagClick(tag.tagName)"
        >
          {{ tag.tagName }}
        </div>
      </transition-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { PriceTag, Loading } from '@element-plus/icons-vue'

export interface TagCloudItem {
  tagName: string
  count: number
}

const props = defineProps<{
  title?: string
  initialTags?: TagCloudItem[]
  clickable?: boolean
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'tagClick', tagName: string): void
  (e: 'update:modelValue', tagName: string): void
  (e: 'clear'): void
}>()

const loading = ref(false)
const tags = ref<TagCloudItem[]>([])
const activeTag = ref(props.modelValue || '')

const colorPalette = [
  { bg: '#E8F5E9', text: '#2E7D32', border: '#66BB6A' },
  { bg: '#E3F2FD', text: '#1565C0', border: '#42A5F5' },
  { bg: '#FFF3E0', text: '#E65100', border: '#FFA726' },
  { bg: '#F3E5F5', text: '#7B1FA2', border: '#AB47BC' },
  { bg: '#E0F7FA', text: '#00695C', border: '#26A69A' },
  { bg: '#FCE4EC', text: '#C62828', border: '#EF5350' },
  { bg: '#F1F8E9', text: '#558B2F', border: '#9CCC65' },
  { bg: '#EDE7F6', text: '#4527A0', border: '#7E57C2' },
  { bg: '#FFF8E1', text: '#F57F17', border: '#FFB300' },
  { bg: '#E8EAF6', text: '#283593', border: '#5C6BC0' },
  { bg: '#FBE9E7', text: '#BF360C', border: '#FF7043' },
  { bg: '#E0F2F1', text: '#004D40', border: '#26A69A' },
]

function getTagStyle(tag: TagCloudItem, index: number) {
  const palette = colorPalette[index % colorPalette.length]
  return {
    '--chip-bg': palette.bg,
    '--chip-text': palette.text,
    '--chip-border': palette.border,
  }
}

function handleTagClick(tagName: string) {
  if (!props.clickable) return
  if (activeTag.value === tagName) {
    clearActiveTag()
    return
  }
  activeTag.value = tagName
  emit('update:modelValue', tagName)
  emit('tagClick', tagName)
}

function clearActiveTag() {
  activeTag.value = ''
  emit('update:modelValue', '')
  emit('clear')
}

async function loadTagCloud() {
  loading.value = true
  try {
    const { default: api } = await import('@/api')
    const res = await api.get('/front/ai/tags/cloud')
    if (res.data && res.data.code === 200) {
      tags.value = res.data.data || []
    }
  } catch {
    tags.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  syncTags()
})

// 监听 initialTags 变化，支持切换文档、提取标签后动态更新
watch(() => props.initialTags, () => {
  syncTags()
}, { deep: true })

function syncTags() {
  if (props.initialTags && props.initialTags.length > 0) {
    tags.value = [...props.initialTags]
  } else {
    tags.value = []
  }
}
</script>

<style scoped>
.tag-cloud-wrapper {
  background: #fff;
  border-radius: 8px;
  padding: 0;
}

.tag-cloud-header {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #606266;
  margin-bottom: 10px;
}

.tag-cloud-header .el-icon { color: var(--el-color-primary); }
.active-tag-label { margin-left: auto; }

.tag-cloud-loading {
  display: flex; align-items: center; justify-content: center;
  gap: 8px; padding: 32px; color: #909399; font-size: 13px;
}

.tag-cloud-container { min-height: 48px; }

.tag-items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 8px;
}

.tag-chip {
  display: flex; align-items: center; justify-content: center;
  min-height: 48px;
  border-radius: 8px;
  border: 1.5px solid var(--chip-border);
  background: var(--chip-bg);
  color: var(--chip-text);
  cursor: pointer; user-select: none;
  font-size: 12px; font-weight: 500;
  transition: all 0.2s;
  text-align: center;
  word-break: break-word;
  padding: 8px 10px;
}

.tag-chip:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.tag-chip:active {
  transform: scale(0.97);
}

.tag-chip.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.tag-fade-enter-active {
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.tag-fade-leave-active {
  transition: all 0.2s ease-in;
}

.tag-fade-enter-from {
  opacity: 0; transform: translateY(12px) scale(0.7);
}

.tag-fade-leave-to {
  opacity: 0; transform: scale(0.5);
}
</style>
