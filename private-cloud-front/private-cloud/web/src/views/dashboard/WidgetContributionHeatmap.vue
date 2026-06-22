<template>
  <div class="widget-contribution-heatmap">
    <el-skeleton v-if="isLoading" :rows="3" animated />
    <div v-else-if="list.length === 0" class="widget-empty">
      <el-icon :size="28" color="#c0c4cc"><TrendCharts /></el-icon>
      <span>暂无贡献记录</span>
    </div>
    <div v-else class="heatmap-container">
      <div class="heatmap-summary">
        <span class="summary-title">近7天活跃度</span>
        <span class="summary-count">共 <strong class="highlight">{{ totalCount }}</strong> 次操作</span>
      </div>

      <div class="heatmap-streak">
        <el-tooltip
          v-for="(day, idx) in paddedDays" :key="idx"
          :content="`${day.date} : ${day.count} 次操作`"
          placement="top"
          effect="dark"
        >
          <div class="streak-pill" :class="getLevelClass(day.count)">
            <span class="pill-day">{{ getShortDay(day.date) }}</span>
            <span class="pill-dot"></span>
          </div>
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'
import type { ContributionDay } from '@/api/workspace'

const props = defineProps<{
  list: ContributionDay[]
  isLoading?: boolean
}>()

const totalCount = computed(() => props.list.reduce((sum, d) => sum + d.count, 0))

// 补齐7天
const paddedDays = computed(() => {
  const map = new Map(props.list.map(d => [d.date, d.count]))
  const result: { date: string; count: number }[] = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    result.push({ date: key, count: map.get(key) || 0 })
  }
  return result
})

// 提取几号，用于显示
function getShortDay(dateStr: string) {
  const parts = dateStr.split('-')
  return parts[2] || ''
}

// 弃用绿色，采用科技感更强的品牌蓝主题
function getLevelClass(count: number): string {
  if (count === 0) return 'level-0'
  if (count <= 2) return 'level-1'
  if (count <= 5) return 'level-2'
  if (count <= 10) return 'level-3'
  return 'level-4'
}
</script>

<style scoped>
.widget-contribution-heatmap { height: 100%; display: flex; flex-direction: column; justify-content: center; }
.widget-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 24px 0; color: #c0c4cc; gap: 8px; font-size: 13px;
}
.heatmap-container { padding: 10px 16px; }

.heatmap-summary {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; padding-bottom: 12px; border-bottom: 1px dashed #ebeef5;
}
.summary-title { font-size: 14px; font-weight: 600; color: #303133; }
.summary-count { font-size: 12px; color: #606266; }
.highlight { color: #409EFF; font-size: 16px; font-weight: 700; margin: 0 4px; font-family: monospace; }

.heatmap-streak {
  display: flex; gap: 12px; justify-content: space-between; align-items: center;
}

/* 创新的胶囊式连续打卡 UI */
.streak-pill {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 8px 0; border-radius: 20px; background: #f5f7fa;
  cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;
}
.streak-pill:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.05); }

.pill-day { font-size: 11px; color: #909399; font-weight: 500; }
.pill-dot { width: 12px; aspect-ratio: 1 / 1; max-width: 16px; border-radius: 50%; background: #e4e7ed; transition: all 0.3s; }

/* 科技蓝渐变色卡 */
.streak-pill.level-0 .pill-dot { background: #e4e7ed; box-shadow: inset 0 2px 4px rgba(0,0,0,0.05); }
.streak-pill.level-1 { background: #ecf5ff; }
.streak-pill.level-1 .pill-dot { background: #a0cfff; }
.streak-pill.level-2 { background: #d9ecff; }
.streak-pill.level-2 .pill-dot { background: #66b1ff; }
.streak-pill.level-3 { background: #c6e2ff; }
.streak-pill.level-3 .pill-dot { background: #409eff; }
.streak-pill.level-4 { background: #409eff; }
.streak-pill.level-4 .pill-day { color: #ffffff; }
.streak-pill.level-4 .pill-dot { background: #ffffff; box-shadow: 0 0 8px rgba(255,255,255,0.6); }
</style>
