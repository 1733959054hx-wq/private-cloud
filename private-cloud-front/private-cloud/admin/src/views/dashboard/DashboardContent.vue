<template>
  <div class="dashboard-content">
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div v-for="s in statCards" :key="s.key" class="stat-card" :style="{ '--c': s.color, '--bg': s.bg }">
        <div class="stat-icon"><el-icon :size="20"><component :is="s.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
        <div class="stat-spark" v-if="s.trend !== undefined">
          <span :class="s.trend > 0 ? 'up' : 'down'">{{ s.trend > 0 ? '↑' : '↓' }}{{ Math.abs(s.trend) }}%</span>
        </div>
      </div>
    </div>

    <!-- 图表 -->
    <div class="charts-row">
      <el-card shadow="hover" class="chart-card">
        <template #header><span class="chart-title"><el-icon :size="16"><TrendCharts /></el-icon> 存储容量趋势</span></template>
        <v-chart :option="storageOption" autoresize style="height:280px" />
      </el-card>
      <el-card shadow="hover" class="chart-card">
        <template #header><span class="chart-title"><el-icon :size="16"><PieChart /></el-icon> 文档类型占比</span></template>
        <v-chart :option="docTypeOption" autoresize style="height:280px" />
      </el-card>
    </div>

    <div class="charts-row three-col">
      <el-card shadow="hover" class="chart-card">
        <template #header><span class="chart-title"><el-icon :size="16"><CircleCheck /></el-icon> 审批通过率</span></template>
        <v-chart :option="approvalOption" autoresize style="height:240px" />
      </el-card>
      <el-card shadow="hover" class="chart-card">
        <template #header><span class="chart-title"><el-icon :size="16"><StarFilled /></el-icon> 热门文档 TOP10</span></template>
        <div class="rank-list">
          <div v-for="(d, i) in hotDocs" :key="d.docId" class="rank-item">
            <span class="rank-badge" :class="'rank-' + Math.min(i + 1, 4)">{{ i + 1 }}</span>
            <div class="rank-info">
              <div class="rank-name-row">
                <span class="rank-name" :title="d.fileName">{{ d.fileName }}</span>
                <span class="rank-file-tag" v-if="d.fileType">{{ d.fileType }}</span>
              </div>
              <div class="rank-bar-track">
                <span class="rank-bar-fill" :style="{ width: (d.viewCount / maxDocViewCount * 100) + '%', background: rankColors[i % 8] }"></span>
              </div>
            </div>
            <span class="rank-count">
              <el-icon :size="13"><View /></el-icon>
              <span>{{ d.viewCount }}</span>
            </span>
          </div>
        </div>
      </el-card>
      <el-card shadow="hover" class="chart-card">
        <template #header><span class="chart-title"><el-icon :size="16"><Search /></el-icon> 全局搜索热词</span></template>
        <v-chart :option="wordcloudOption" autoresize style="height:280px" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Files, User, Upload, TrendCharts, Timer, CircleCheck, PieChart, StarFilled, Search, View } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart as EChartsPie, GaugeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import 'echarts-wordcloud'
import { getDashboardStats, getDocTypeStats, getStorageTrend, getHotDocs, getHotKeywords } from '@/api/dashboard'
use([CanvasRenderer, LineChart, EChartsPie, GaugeChart, GridComponent, TooltipComponent, LegendComponent])

onMounted(async () => {
  try {
    const [stats, types, trend, hDocs, hWords] = await Promise.all([
      getDashboardStats(), getDocTypeStats(), getStorageTrend(),
      getHotDocs(), getHotKeywords(),
    ])
    const s = stats.data.data || stats.data
    if (s) {
      totalDocs.value = s.totalDocs || 0
      totalUsers.value = s.totalUsers || 0
      usedStorage.value = s.usedStorage || 0
      todayUploads.value = s.todayUploads || 0
      monthlyUploads.value = s.monthlyUploads || 0
      approvalRate.value = s.approvalPassRate || 0
    }
    docTypes.value = (types.data.data || types.data || [])
    const trendData = (trend.data.data || trend.data || []) as { date: string; size: number }[]
    if (trendData.length) {
      storageTrendDates.value = trendData.map(t => t.date)
      storageTrendSizes.value = trendData.map(t => +(t.size / 1073741824).toFixed(2))
    }
    hotDocs.value = (hDocs.data.data || hDocs.data || [])
    hotKeywords.value = (hWords.data.data || hWords.data || [])
  } catch { /* 接口不可用时使用默认值 */ }
})

// ========== 数据 ==========
const totalDocs = ref(0), totalUsers = ref(0)
const totalStorage = ref(10737418240), usedStorage = ref(0)
const todayUploads = ref(0), monthlyUploads = ref(0), approvalRate = ref(0)

const docTypes = ref<{ type: string; count: number }[]>([])
const hotDocs = ref<{ docId: number; fileName: string; viewCount: number; fileType?: string }[]>([])
const hotKeywords = ref<{ keyword: string; count: number }[]>([])
const maxDocViewCount = computed(() => Math.max(...hotDocs.value.map(d => d.viewCount), 1))
const rankColors = ['#2563eb','#059669','#d97706','#7c3aed','#db2777','#ca8a04','#c2410c','#4b5563']


// ========== ECharts 词云 ==========
const wordcloudOption = computed(() => {
  const maxCount = Math.max(...hotKeywords.value.map(k => k.count), 1)
  return {
    tooltip: { show: true, formatter: (p: any) => `${p.name}: ${p.value} 次` },
    series: [{
      type: 'wordCloud',
      shape: 'circle',
      width: '100%',
      height: '100%',
      sizeRange: [12, 32],
      rotationRange: [-20, 20],
      rotationStep: 15,
      gridSize: 16,
      drawOutOfBound: false,
      layoutAnimation: true,
      textStyle: {
        fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif',
        fontWeight: 'normal',
        color: () => {
          const colors = ['#2563eb','#059669','#d97706','#7c3aed','#db2777','#ca8a04','#c2410c','#4b5563']
          return colors[Math.floor(Math.random() * colors.length)]
        },
      },
      emphasis: {
        textStyle: {
          fontWeight: 'bold',
        },
      },
      data: hotKeywords.value.map(k => ({
        name: k.keyword,
        value: k.count,
      })),
    }],
  }
})

const storageTrendDates = ref<string[]>(['1月','2月','3月','4月','5月','6月'])
const storageTrendSizes = ref<number[]>([0.8, 1.2, 1.5, 2.1, 2.7, 3.2])

// ========== 统计卡片 ==========
const usedGB = computed(() => (usedStorage.value / 1073741824).toFixed(1))
const totalGB = computed(() => (totalStorage.value / 1073741824).toFixed(1))
const statCards = computed(() => [
  { key: 'docs', icon: Files, label: '文档总数', value: totalDocs.value, color: '#4a90d9', bg: 'rgba(74,144,217,0.15)', trend: 12 },
  { key: 'users', icon: User, label: '用户总数', value: totalUsers.value, color: '#34d399', bg: 'rgba(52,211,153,0.15)', trend: undefined },
  { key: 'storage', icon: TrendCharts, label: `存储 (${usedGB.value}/${totalGB.value} GB)`, value: Math.round(usedStorage.value / totalStorage.value * 100) + '%', color: '#f59e0b', bg: 'rgba(245,158,11,0.15)', trend: undefined },
  { key: 'today', icon: Upload, label: '今日上传', value: todayUploads.value, color: '#fbbf24', bg: 'rgba(251,191,36,0.15)', trend: 8 },
  { key: 'month', icon: Timer, label: '本月上传', value: monthlyUploads.value, color: '#8b5cf6', bg: 'rgba(139,92,246,0.15)', trend: undefined },
  { key: 'approval', icon: CircleCheck, label: '审批通过率', value: approvalRate.value + '%', color: '#ec4899', bg: 'rgba(236,72,153,0.15)', trend: undefined },
])

// ========== ECharts ==========
const storageOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: storageTrendDates.value, axisLine: { lineStyle: { color: '#e0e0e0' } } },
  yAxis: { type: 'value', name: 'GB', splitLine: { lineStyle: { color: '#f0f0f0' } } },
  series: [{ data: storageTrendSizes.value, type: 'line', smooth: true, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(74,144,217,0.3)' }, { offset: 1, color: 'rgba(74,144,217,0.02)' }] } }, itemStyle: { color: '#4a90d9' }, lineStyle: { color: '#4a90d9', width: 2 } }],
}))

const docTypeOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, textStyle: { fontSize: 11 } },
  series: [{ type: 'pie', radius: ['45%','70%'], center: ['50%','43%'], itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 }, label: { show: false }, emphasis: { label: { show: true, fontWeight: 'bold' } }, data: docTypes.value.map(d => ({ name: d.type, value: d.count })), color: ['#4a90d9','#34d399','#f59e0b','#fbbf24','#8b5cf6','#909399'] }],
}))

const approvalOption = computed(() => ({
  series: [{ type: 'gauge', startAngle: 200, endAngle: -20, center: ['50%','55%'], radius: '85%', min: 0, max: 100, axisLine: { lineStyle: { width: 12, color: [[0.6,'#f56c6c'],[0.8,'#e6a23c'],[1,'#67c23a']] } }, pointer: { length: '60%', width: 6, itemStyle: { color: 'auto' } }, detail: { valueAnimation: true, formatter: '{value}%', fontSize: 22, offsetCenter: [0,'65%'] }, data: [{ value: approvalRate.value }] }],
}))
</script>

<style scoped>
.dashboard-content { max-width: 1300px }
.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px }
.stat-card { background: #fff; border-radius: 14px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 6px rgba(0,0,0,0.04); border: 1px solid #f0f0f0; transition: all 0.25s }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.08) }
.stat-icon { width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center; background: var(--bg); color: var(--c); flex-shrink: 0 }
.stat-body { flex: 1 } .stat-num { font-size: 24px; font-weight: 700; color: #303133; line-height: 1.1 } .stat-label { font-size: 12px; color: #909399; margin-top: 2px }
.stat-spark { font-size: 12px; font-weight: 600 } .stat-spark .up { color: #34d399 } .stat-spark .down { color: #f56c6c }
.charts-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; margin-bottom: 14px }
.charts-row.three-col { grid-template-columns: 1fr 1fr 1fr }
.chart-card { border-radius: 14px; border: 1px solid #f0f0f0 }
.chart-card :deep(.el-card__header) { padding: 12px 18px; border-bottom: 1px solid #f5f5f5 }
.chart-title { font-size: 14px; font-weight: 600; color: #303133; display: inline-flex; align-items: center; gap: 4px }
.rank-list { display: flex; flex-direction: column; gap: 6px; padding: 4px 0; max-height: 255px; overflow-y: auto; scrollbar-width: thin; scrollbar-color: #e0e0e0 transparent }
.rank-list::-webkit-scrollbar { width: 4px }
.rank-list::-webkit-scrollbar-thumb { background: #e0e0e0; border-radius: 2px }
.rank-item { display: flex; align-items: center; gap: 10px; padding: 9px 10px; border-radius: 10px; transition: all 0.2s }
.rank-item:hover { background: #f5f7fa; transform: translateX(4px) }
.rank-badge { width: 28px; height: 28px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; flex-shrink: 0; color: #909399; background: #f0f2f5 }
.rank-badge.rank-1 { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #fff; box-shadow: 0 2px 10px rgba(245,158,11,0.4) }
.rank-badge.rank-2 { background: linear-gradient(135deg, #94a3b8, #64748b); color: #fff; box-shadow: 0 2px 8px rgba(100,116,139,0.35) }
.rank-badge.rank-3 { background: linear-gradient(135deg, #d4a373, #b87333); color: #fff; box-shadow: 0 2px 8px rgba(184,115,51,0.35) }
.rank-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5px }
.rank-name-row { display: flex; align-items: center; gap: 6px }
.rank-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; color: #303133 }
.rank-file-tag { flex-shrink: 0; padding: 1px 6px; border-radius: 4px; font-size: 10px; font-weight: 600; color: #667eea; background: rgba(102,126,234,0.1); border: 1px solid rgba(102,126,234,0.2) }
.rank-bar-track { height: 5px; border-radius: 3px; background: #f0f2f5; overflow: hidden }
.rank-bar-fill { display: block; height: 100%; border-radius: 3px; transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1) }
.rank-count { display: flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 600; color: #606266; flex-shrink: 0 }
@media (max-width: 1024px) { .stats-row { grid-template-columns: repeat(2, 1fr) } .charts-row, .charts-row.three-col { grid-template-columns: 1fr } }
@media (max-width: 640px) { .stats-row { grid-template-columns: 1fr } }
</style>
