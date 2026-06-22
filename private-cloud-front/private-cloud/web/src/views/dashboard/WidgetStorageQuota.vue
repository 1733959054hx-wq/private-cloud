<template>
  <div class="widget-storage-quota">
    <el-skeleton v-if="isLoading" :rows="4" animated />
    <template v-else>
    <div class="storage-layout">
      <div class="storage-left">
        <div class="multi-ring" :style="{ width: ringSize + 'px', height: ringSize + 'px' }">
          <svg :width="ringSize" :height="ringSize" :viewBox="`0 0 ${ringSize} ${ringSize}`">
            <!-- 背景轨道 -->
            <circle
              :cx="ringSize / 2"
              :cy="ringSize / 2"
              :r="radius"
              fill="none"
              stroke="#f0f2f5"
              :stroke-width="strokeWidth"
            />
            <!-- 各文件类型分段 -->
            <g v-for="(seg, idx) in segments" :key="idx">
              <circle
                :cx="ringSize / 2"
                :cy="ringSize / 2"
                :r="radius"
                fill="none"
                :stroke="seg.color"
                :stroke-width="strokeWidth"
                stroke-linecap="butt"
                :stroke-dasharray="`${seg.dash} ${circumference}`"
                :stroke-dashoffset="seg.offset"
                :transform="`rotate(-90 ${ringSize / 2} ${ringSize / 2})`"
                class="ring-segment"
              />
            </g>
          </svg>
          <div class="ring-center">
            <div class="ring-percent">{{ percent }}<span class="pct">%</span></div>
            <div class="ring-label">已用</div>
          </div>
        </div>
        <div class="storage-text">
          <div class="text-used">已用 {{ formatFileSize(used) }}</div>
          <div class="text-quota">共 {{ formatFileSize(quota) }}</div>
        </div>
      </div>

      <div v-if="byType && byType.length > 0" class="storage-by-type">
        <div v-for="t in byType" :key="t.type" class="type-row">
          <div class="type-header">
            <div class="type-name-wrap">
              <span class="type-dot" :style="{ backgroundColor: typeColorMap[t.type] || typeColorMap.other }"></span>
              <span class="type-name">{{ typeLabelMap[t.type] || t.type }}</span>
            </div>
            <span class="type-size">{{ formatFileSize(t.size) }}</span>
          </div>
          <div class="type-bar-bg">
            <div class="type-bar-fill" :style="{ width: getTypePercent(t.size) + '%', backgroundColor: typeColorMap[t.type] || typeColorMap.other }" />
          </div>
        </div>
      </div>
    </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatFileSize } from '@/utils/chunk-upload'
import type { StorageByType } from '@/api/workspace'

const props = defineProps<{
  used: number
  quota: number
  byType?: StorageByType[]
  isLoading?: boolean
}>()

const ringSize = 88
const strokeWidth = 8
const radius = (ringSize - strokeWidth) / 2
const circumference = 2 * Math.PI * radius

const percent = computed(() => {
  if (!props.quota) return 0
  return Math.min(100, Math.round((props.used / props.quota) * 100))
})

function getTypePercent(size: number): number {
  if (!props.used) return 0
  return Math.min(100, Math.round((size / props.used) * 100))
}

const typeLabelMap: Record<string, string> = {
  document: '文档', spreadsheet: '表格', presentation: '演示',
  image: '图片', video: '视频', audio: '音频', archive: '压缩', other: '其他',
}

// 换用高级低饱和色卡
const typeColorMap: Record<string, string> = {
  document: '#5C82FF', spreadsheet: '#42D392', presentation: '#FFB84C',
  image: '#FF6B6B', video: '#9D7CFD', audio: '#20C997', archive: '#868E96', other: '#ADB5BD',
}

// 圆环分段：按 byType 中各类型 size 占比切割成彩色扇形
const segments = computed(() => {
  const list = props.byType || []
  const total = list.reduce((sum, t) => sum + (t.size || 0), 0)
  if (!total) return []
  const result: { color: string; dash: number; offset: number }[] = []
  let acc = 0
  for (const t of list) {
    const size = t.size || 0
    if (!size) continue
    const ratio = size / total
    const arcLen = ratio * circumference
    // 留 1px 间隙让分段更清晰
    const dash = Math.max(0, arcLen - 1.5)
    // stroke-dashoffset 为正数表示沿路径的反方向偏移（顺时针累加 = 负 offset 累加）
    const offset = -acc
    result.push({
      color: typeColorMap[t.type] || typeColorMap.other,
      dash,
      offset,
    })
    acc += arcLen
  }
  return result
})
</script>

<style scoped>
.widget-storage-quota { padding: 4px; display: flex; flex-direction: column; height: 100%; }
.storage-layout {
  display: flex;
  gap: 20px;
  height: 100%;
  padding: 4px 8px 0;
  box-sizing: border-box;
}
.storage-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-shrink: 0;
  padding-right: 14px;
  border-right: 1px dashed #ebeef5;
}

.multi-ring {
  position: relative;
  flex-shrink: 0;
}
.multi-ring svg { display: block; }
.ring-segment {
  transition: stroke-dasharray 0.6s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.ring-percent { font-size: 18px; font-weight: 700; color: #2c3e50; line-height: 1; }
.pct { font-size: 10px; margin-left: 1px; color: #909399; font-weight: 500; }
.ring-label { font-size: 10px; color: #909399; margin-top: 3px; }

.storage-text { display: flex; flex-direction: column; gap: 4px; }
.text-used { font-size: 16px; font-weight: 600; color: #303133; }
.text-quota { font-size: 12px; color: #909399; }

.storage-by-type {
  flex: 1;
  min-width: 0;
  padding: 4px 4px 0 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}
.storage-by-type::-webkit-scrollbar { width: 4px; }
.storage-by-type::-webkit-scrollbar-track { background: transparent; }
.storage-by-type::-webkit-scrollbar-thumb { background: rgba(144, 147, 153, 0.2); border-radius: 2px; }
.storage-by-type::-webkit-scrollbar-thumb:hover { background: rgba(144, 147, 153, 0.5); }
.type-row { display: flex; flex-direction: column; gap: 6px; }
.type-header { display: flex; justify-content: space-between; align-items: center; }
.type-name-wrap { display: flex; align-items: center; gap: 6px; }
.type-dot { width: 8px; height: 8px; border-radius: 50%; }
.type-name { font-size: 12px; color: #606266; }
.type-size { font-size: 12px; color: #909399; font-weight: 500; }
.type-bar-bg { height: 6px; background: #f0f2f5; border-radius: 999px; overflow: hidden; }
.type-bar-fill { height: 100%; border-radius: 999px; transition: width 0.6s cubic-bezier(0.25, 0.8, 0.25, 1); }
</style>
