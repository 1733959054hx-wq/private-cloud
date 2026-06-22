<template>
  <div class="workbench">
    <div ref="welcomeBannerRef" class="welcome-section" :class="themeClass">
      <div class="welcome-deco welcome-deco-arc" />
      <div class="welcome-deco welcome-deco-noise" />

      <div class="welcome-info">
        <el-dropdown trigger="click" placement="bottom-start" @command="handleFlowerChange">
          <div class="welcome-animation-podium" title="点击切换数字生命">
            <Vue3Lottie
              ref="lottieRef"
              :key="currentFlowerValue"
              :animationData="currentFlowerData"
              :height="75"
              :width="75"
              :loop="true"
              :autoPlay="true"
            />
            <div class="podium-hint"><el-icon><Refresh /></el-icon></div>
          </div>
          <template #dropdown>
            <el-dropdown-menu class="flower-dropdown-menu">
              <el-dropdown-item
                v-for="opt in flowerOptions"
                :key="opt.value"
                :command="opt.value"
                :class="{ 'is-active': currentFlowerValue === opt.value }"
              >
                <span>{{ opt.label }}</span>
                <el-icon v-if="currentFlowerValue === opt.value" class="active-icon"><Check /></el-icon>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <div class="welcome-text">
          <div class="welcome-tagline">
            <span class="welcome-tagline-bar" />
            <span class="welcome-tagline-text">{{ greeting.en }} · {{ todayWeek }}</span>
          </div>
          <h2 class="welcome-title">
            <el-icon class="greeting-icon"><component :is="greeting.icon" /></el-icon>
            <span>{{ greeting.text }}，<span class="welcome-name">{{ userStore.realName }}</span></span>
          </h2>
          <p class="welcome-desc">
            <span class="welcome-quote-mark">「</span>{{ greetingSub }}<span class="welcome-quote-mark">」</span>
          </p>
          <nav class="welcome-quick-nav" aria-label="快速导航">
            <button class="quick-nav-item" type="button" @click="goDocSpace">
              <span class="quick-nav-icon"><el-icon :size="16"><FolderOpened /></el-icon></span>
              <span class="quick-nav-text">文档空间</span>
            </button>
            <button class="quick-nav-item" type="button" @click="goSearch">
              <span class="quick-nav-icon"><el-icon :size="16"><Search /></el-icon></span>
              <span class="quick-nav-text">高级搜索</span>
            </button>
            <button class="quick-nav-item" type="button" @click="goQA">
              <span class="quick-nav-icon"><el-icon :size="16"><ChatLineSquare /></el-icon></span>
              <span class="quick-nav-text">知识问答</span>
            </button>
          </nav>
        </div>
      </div>

      <div class="welcome-right">
        <div class="welcome-clock">
          <div class="clock-time">{{ liveTime }}</div>
          <div class="clock-date">{{ todayStr }}</div>
          <div class="clock-week">{{ todayWeek }} · {{ weatherHint }}</div>
        </div>
        <div class="welcome-stats">
          <div v-for="(s, i) in statCards" :key="i" class="welcome-stat-item"
            :style="{ '--stat-color': s.color, '--stat-bg': s.bg }">
            <div class="stat-icon-wrap"><el-icon :size="16"><component :is="s.icon" /></el-icon></div>
            <div class="stat-body">
              <div class="welcome-stat-value">{{ Math.round(animatedStats[i]) }}</div>
              <div class="welcome-stat-label">{{ s.label }}</div>
            </div>
            <div class="stat-bar"><span class="stat-bar-fill" /></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 今日摘要 -->
    <div class="daily-summary">
      <div class="daily-summary-header">
        <el-icon><Calendar /></el-icon>
        <span>今日摘要</span>
      </div>
      <div class="daily-summary-items">
        <div class="daily-summary-item">
          <span class="summary-dot summary-dot-blue"></span>
          <span>本周上传文档 <strong>{{ dashboard.monthlyUploads }}</strong> 份</span>
        </div>
        <div class="daily-summary-item">
          <span class="summary-dot summary-dot-green"></span>
          <span>累计管理文档 <strong>{{ dashboard.totalDocs }}</strong> 份</span>
        </div>
        <div class="daily-summary-item">
          <span class="summary-dot summary-dot-purple"></span>
          <span>收藏文档 <strong>{{ favorites.length }}</strong> 篇</span>
        </div>
      </div>
    </div>

    <Transition name="mini-header">
      <div v-if="showMiniHeader" class="mini-header">
        <div class="mini-lottie">
          <Vue3Lottie :key="currentFlowerValue" :animationData="currentFlowerData" :height="28" :width="28" :loop="true" :autoPlay="true" />
        </div>
        <span class="mini-user">{{ userStore.realName }}</span>
      </div>
    </Transition>

    <!-- 工具栏：编辑布局 + 保存 -->
    <div class="layout-toolbar">
      <el-button v-if="!isEditing" type="primary" plain size="small" @click="startEditLayout">
        <el-icon><Setting /></el-icon> 编辑布局
      </el-button>
      <template v-else>
        <el-button type="success" size="small" @click="saveLayout">
          <el-icon><Check /></el-icon> 保存布局
        </el-button>
        <el-button size="small" @click="cancelEditLayout">取消</el-button>
        <el-button type="warning" plain size="small" @click="showWidgetDrawer = true">
          <el-icon><Plus /></el-icon> 添加组件
        </el-button>
      </template>
    </div>

    <!-- 可拖拽网格布局 -->
    <div class="widget-grid-area" :class="{ 'no-transition': isRestoring }">
      <GridLayout
        :layout="currentLayout"
        :col-num="12"
        :row-height="32"
        :is-draggable="isEditing"
        :is-resizable="isEditing"
        :vertical-compact="true"
        :margin="[16, 16]"
        :use-css-transforms="true"
        @layout-updated="onLayoutUpdated"
      >
        <GridItem
          v-for="item in currentLayout"
          :key="item.i"
          :x="item.x"
          :y="item.y"
          :w="item.w"
          :h="item.h"
          :min-w="item.minW || 3"
          :min-h="item.minH || 4"
          :i="item.i"
          :is-draggable="isEditing"
          :is-resizable="isEditing"
          drag-allow-from=".widget-drag-handle"
        >
          <el-card shadow="hover" class="widget-card" :class="{ editing: isEditing }">
            <template #header>
              <div class="widget-card-header">
                <span v-if="isEditing" class="widget-drag-handle" style="cursor: move;">
                  <el-icon :size="14"><Rank /></el-icon>
                </span>
                <span class="widget-card-title">
                  <el-icon class="title-icon"><component :is="widgetMeta[item.i]?.icon" /></el-icon>
                  {{ widgetMeta[item.i]?.label || item.i }}
                </span>
                <el-button v-if="isEditing" type="danger" link size="small" @click="removeWidget(item.i)">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </template>
            <component
              :is="widgetComponents[item.i]"
              v-bind="getWidgetProps(item.i)"
              @goPreview="goPreview"
              @removeFavorite="handleRemoveFavorite"
            />
          </el-card>
        </GridItem>
      </GridLayout>

      <div v-if="currentLayout.length === 0" class="empty-grid">
        <el-empty description="工作台为空，点击「编辑布局」添加组件" />
      </div>
    </div>

    <!-- 组件库抽屉 -->
    <el-drawer v-model="showWidgetDrawer" title="组件库" size="320px" direction="rtl">
      <div class="widget-library">
        <div v-for="w in availableWidgets" :key="w.id" class="widget-lib-item" @click="addWidget(w.id)">
          <el-icon :size="20"><component :is="w.icon" /></el-icon>
          <div class="widget-lib-info">
            <div class="widget-lib-name">{{ w.label }}</div>
            <div class="widget-lib-desc">{{ w.desc }}</div>
          </div>
          <el-button type="primary" link size="small" :disabled="isWidgetActive(w.id)">
            {{ isWidgetActive(w.id) ? '已添加' : '添加' }}
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onActivated, nextTick, watch, shallowRef, reactive } from 'vue'
import { useRouter } from 'vue-router'
import {
  StarFilled, Document, Tickets, Coin, TrendCharts, Moon, Sunrise, Sunny, Coffee,
  MoonNight, Refresh, Check, UploadFilled, Clock, FolderOpened, Search, ChatLineSquare,
  Setting, Plus, Close, Rank, Bell, Calendar
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import gsap from 'gsap'
import { useUserStore } from '@/stores/user'
import {
  getDashboard, getFavorites, removeFavorite,
  getWorkspaceLayout, saveWorkspaceLayout, getTeamUpdates,
  type DashboardVO, type FavoriteVO, type LayoutItem, type TeamUpdate
} from '@/api/workspace'
import { formatFileSize } from '@/utils/chunk-upload'
import FileIcon from '@/components/FileIcon.vue'
import { Vue3Lottie } from 'vue3-lottie'
import FlowerJSON1 from '@/assets/flower.json'
import FlowerJSON2 from '@/assets/flower2.json'
import FlowerJSON3 from '@/assets/flower3.json'
import * as echarts from 'echarts'

// grid-layout-plus
import { GridLayout, GridItem } from 'grid-layout-plus'

// Widget 组件
import WidgetPendingApprovals from './WidgetPendingApprovals.vue'
import WidgetRecentFiles from './WidgetRecentFiles.vue'
import WidgetMyFavorites from './WidgetMyFavorites.vue'
import WidgetStorageQuota from './WidgetStorageQuota.vue'
import WidgetTeamUpdates from './WidgetTeamUpdates.vue'
import WidgetContributionHeatmap from './WidgetContributionHeatmap.vue'

const router = useRouter()
const userStore = useUserStore()

defineOptions({ name: 'Workbench' })

// ==================== Widget 注册表 ====================
const widgetComponents: Record<string, any> = {
  pendingApprovals: WidgetPendingApprovals,
  recentFiles: WidgetRecentFiles,
  myFavorites: WidgetMyFavorites,
  storageQuota: WidgetStorageQuota,
  teamUpdates: WidgetTeamUpdates,
  contributionHeatmap: WidgetContributionHeatmap,
}

const widgetMeta: Record<string, { label: string; icon: any; desc: string; defaultLayout: any }> = {
  // 待审批：列表需要一定宽度展示标题和时间，最小宽度给 4
  pendingApprovals: { label: '待审批', icon: Tickets, desc: '我的待办审批', defaultLayout: { i: 'pendingApprovals', x: 0, y: 0, w: 8, h: 6, minW: 4, minH: 5 } },

  // 存储空间：包含环形图和分类条，高度不宜过小，默认高度给 6，最小高度锁死 5
  storageQuota: { label: '存储空间', icon: Coin, desc: '个人空间容量使用率', defaultLayout: { i: 'storageQuota', x: 8, y: 0, w: 4, h: 6, minW: 3, minH: 5 } },

  recentFiles: { label: '最近访问', icon: Document, desc: '最近访问的文档', defaultLayout: { i: 'recentFiles', x: 0, y: 6, w: 6, h: 6, minW: 4, minH: 5 } },
  myFavorites: { label: '我的收藏', icon: StarFilled, desc: '收藏快捷直达', defaultLayout: { i: 'myFavorites', x: 6, y: 6, w: 6, h: 6, minW: 4, minH: 5 } },
  teamUpdates: { label: '团队动态', icon: Bell, desc: '所在部门空间的最新动态', defaultLayout: { i: 'teamUpdates', x: 0, y: 12, w: 6, h: 5, minW: 4, minH: 4 } },
  contributionHeatmap: { label: '贡献热力图', icon: TrendCharts, desc: '近7天操作贡献', defaultLayout: { i: 'contributionHeatmap', x: 6, y: 12, w: 6, h: 5, minW: 4, minH: 4 } },
}

const availableWidgets = Object.entries(widgetMeta).map(([id, meta]) => ({
  id,
  label: meta.label,
  icon: meta.icon,
  desc: meta.desc,
}))

// ==================== 默认布局（增加呼吸感） ====================
const defaultLayout: any[] = [
  { i: 'pendingApprovals', x: 0, y: 0, w: 8, h: 6, minW: 4, minH: 5 },
  { i: 'storageQuota', x: 8, y: 0, w: 4, h: 6, minW: 3, minH: 5 },
  { i: 'recentFiles', x: 0, y: 6, w: 6, h: 6, minW: 4, minH: 5 },
  { i: 'myFavorites', x: 6, y: 6, w: 6, h: 6, minW: 4, minH: 5 },
]

// ==================== 布局状态 ====================
const currentLayout = ref<LayoutItem[]>([...defaultLayout])
const savedLayoutBackup = ref<LayoutItem[] | null>(null)
const isEditing = ref(false)
const showWidgetDrawer = ref(false)

function isWidgetActive(id: string): boolean {
  return currentLayout.value.some(item => item.i === id)
}

function addWidget(id: string) {
  if (isWidgetActive(id)) return
  const meta = widgetMeta[id]
  if (!meta) return
  // 计算新位置：放在最底部
  const maxY = currentLayout.value.reduce((max, item) => Math.max(max, item.y + item.h), 0)
  currentLayout.value.push({ ...meta.defaultLayout, y: maxY })
  ElMessage.success(`已添加「${meta.label}」组件`)
}

function removeWidget(id: string) {
  currentLayout.value = currentLayout.value.filter(item => item.i !== id)
}

function startEditLayout() {
  savedLayoutBackup.value = JSON.parse(JSON.stringify(currentLayout.value))
  isEditing.value = true
}

function cancelEditLayout() {
  if (savedLayoutBackup.value) {
    currentLayout.value = JSON.parse(JSON.stringify(savedLayoutBackup.value))
    savedLayoutBackup.value = null
  }
  isEditing.value = false
}

async function saveLayout() {
  const layoutToSave = JSON.parse(JSON.stringify(currentLayout.value))
  try {
    await saveWorkspaceLayout(layoutToSave)
    isEditing.value = false
    savedLayoutBackup.value = layoutToSave
    ElMessage.success('布局已保存')
  } catch {
    // 保存失败：回滚到上一次保存成功的备份
    if (savedLayoutBackup.value) {
      currentLayout.value = JSON.parse(JSON.stringify(savedLayoutBackup.value))
    }
    ElMessage.error('布局保存失败，已恢复上次保存的布局')
  }
}

function onLayoutUpdated(newLayout: LayoutItem[]) {
  currentLayout.value = newLayout
}

// ==================== 数据 ====================
const dashboard = ref<DashboardVO>({
  totalDocs: 0, monthlyUploads: 0, pendingApprovals: 0,
  favoriteCount: 0, unreadNotifications: 0,
  recentFiles: [], pendingApprovalList: [],
  storageStats: { totalQuota: 10737418240, totalUsed: 0, byType: [] },
  contributionHeatmap: [],
})
const isLoading = ref(true)
const favorites = ref<FavoriteVO[]>([])
const teamUpdates = ref<TeamUpdate[]>([])

function getWidgetProps(id: string): Record<string, any> {
  const base = { isLoading: isLoading.value }
  switch (id) {
    case 'pendingApprovals': return { ...base, list: dashboard.value.pendingApprovalList }
    case 'recentFiles': return { ...base, list: dashboard.value.recentFiles }
    case 'myFavorites': return { ...base, list: favorites.value }
    case 'storageQuota': return { ...base, used: dashboard.value.storageStats?.totalUsed ?? 0, quota: dashboard.value.storageStats?.totalQuota ?? 10737418240, byType: dashboard.value.storageStats?.byType ?? [] }
    case 'teamUpdates': return { ...base, list: teamUpdates.value }
    case 'contributionHeatmap': return { ...base, list: dashboard.value.contributionHeatmap }
    default: return base
  }
}

// ==================== 欢迎横幅逻辑 ====================
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return { icon: Moon, en: 'Late Night', text: '夜深了' }
  if (h < 12) return { icon: Sunrise, en: 'Good Morning', text: '早上好' }
  if (h < 14) return { icon: Sunny, en: 'Good Noon', text: '中午好' }
  if (h < 18) return { icon: Coffee, en: 'Good Afternoon', text: '下午好' }
  return { icon: MoonNight, en: 'Good Evening', text: '晚上好' }
})

const currentHour = new Date().getHours()
const themeClass = computed(() => {
  if (currentHour < 11) return 'morning'
  if (currentHour < 17) return 'afternoon'
  if (currentHour < 20) return 'evening'
  return 'night'
})

const showMiniHeader = ref(false)
const welcomeBannerRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

const flowerOptions = [
  { label: '晨曦红莲', value: 'flower1', data: FlowerJSON1 },
  { label: '幽夜繁花', value: 'flower2', data: FlowerJSON2 },
  { label: '水晶木槿', value: 'flower3', data: FlowerJSON3 },
]
const currentFlowerValue = ref(localStorage.getItem(`u${userStore.userId}:preferred_flower`) || 'flower1')
const currentFlowerData = computed(() => {
  const found = flowerOptions.find(opt => opt.value === currentFlowerValue.value)
  return found ? found.data : flowerOptions[0].data
})
const handleFlowerChange = (val: string) => {
  currentFlowerValue.value = val
  localStorage.setItem(`u${userStore.userId}:preferred_flower`, val)
}

const greetingSub = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '注意休息，劳逸结合'
  if (h < 9) return '新的一天，元气满满'
  if (h < 12) return '专注工作，高效产出'
  if (h < 14) return '适当休息，下午更精神'
  if (h < 18) return '继续加油，胜利在望'
  if (h < 22) return '辛苦了，记得放松'
  return '夜深了，早点休息'
})

const todayStr = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})
const todayWeek = computed(() => {
  const weekMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekMap[new Date().getDay()]
})
const weatherHint = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜阑人静'
  if (h < 9) return '晨光熹微'
  if (h < 12) return '晴空万里'
  if (h < 14) return '暖阳正午'
  if (h < 18) return '午后时光'
  if (h < 20) return '夕阳西下'
  return '星河璀璨'
})

const liveTime = ref('')
let clockTimer: number | null = null
function refreshClock() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  liveTime.value = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const statCards = computed(() => {
  const colorMap: Record<string, { color: string; bg: string }> = {
    docs: { color: '#4a90d9', bg: 'rgba(74, 144, 217, 0.25)' },
    uploads: { color: '#34d399', bg: 'rgba(52, 211, 153, 0.25)' },
    approvals: { color: '#fbbf24', bg: 'rgba(251, 191, 36, 0.25)' },
    favorites: { color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.25)' },
  }
  return [
    { key: 'docs', value: dashboard.value.totalDocs, label: '文档总数', icon: Document, color: colorMap.docs.color, bg: colorMap.docs.bg },
    { key: 'uploads', value: dashboard.value.monthlyUploads, label: '本月上传', icon: UploadFilled, color: colorMap.uploads.color, bg: colorMap.uploads.bg },
    { key: 'approvals', value: dashboard.value.pendingApprovals, label: '待审批', icon: Tickets, color: colorMap.approvals.color, bg: colorMap.approvals.bg },
    { key: 'favorites', value: favorites.value.length, label: '收藏数', icon: StarFilled, color: colorMap.favorites.color, bg: colorMap.favorites.bg },
  ]
})

// GSAP CountUp 动画
const animatedStats = reactive([0, 0, 0, 0])
watch(() => statCards.value.map(s => s.value), (newValues) => {
  newValues.forEach((targetValue, index) => {
    gsap.to(animatedStats, {
      [index]: targetValue,
      duration: 1.5,
      ease: 'power2.out',
    })
  })
}, { immediate: true, deep: true })

function goDocSpace() { router.push('/document') }
function goSearch() { router.push('/search') }
function goQA() { router.push('/qa') }

function goPreview(fileId: number) {
  router.push(`/preview/${fileId}`)
}

async function handleRemoveFavorite(targetId: number, targetType: number) {
  try {
    await removeFavorite(targetId, targetType)
    favorites.value = favorites.value.filter(f => !(f.targetId === targetId && f.targetType === targetType))
    ElMessage.success('已取消收藏')
  } catch { /* ignore */ }
}

async function fetchData(silent = false) {
  if (!silent) isLoading.value = true
  try {
    // 三个请求并行发起，不互相阻塞
    const [dashRes, favRes, teamRes] = await Promise.allSettled([
      getDashboard(),
      getFavorites(),
      getTeamUpdates(),
    ])

    if (dashRes.status === 'fulfilled') {
      const data = dashRes.value.data.data || dashRes.value.data
      if (data.recentFiles) data.recentFiles = data.recentFiles.slice(0, 6)
      if (!data.storageStats) data.storageStats = { totalQuota: 10737418240, totalUsed: 0, byType: [] }
      if (!data.contributionHeatmap) data.contributionHeatmap = []
      dashboard.value = data
    }
    if (favRes.status === 'fulfilled') {
      favorites.value = favRes.value.data.data || favRes.value.data
    }
    if (teamRes.status === 'fulfilled') {
      teamUpdates.value = teamRes.value.data.data || teamRes.value.data
    }
  } finally {
    if (!silent) isLoading.value = false
  }
}

// 刷新单个统计指标：上传后触发存储空间实时同步
async function refreshStorageStats() {
  try {
    const res = await getDashboard()
    const data = res.data.data || res.data
    if (data && data.storageStats) {
      dashboard.value = { ...dashboard.value, storageStats: data.storageStats }
    }
  } catch { /* ignore */ }
}

async function fetchLayout() {
  try {
    const res = await getWorkspaceLayout()
    const layoutStr = res.data.data?.workspaceLayout
    if (layoutStr) {
      const parsed = JSON.parse(layoutStr)
      if (Array.isArray(parsed) && parsed.length > 0) {
        // 校验：如果已保存的布局中有不存在的组件，回退到默认布局
        const validIds = new Set(Object.keys(widgetMeta))
        const allValid = parsed.every((item: LayoutItem) => validIds.has(item.i))
        if (allValid) {
          currentLayout.value = parsed
        } else {
          // 包含无效组件，回退默认并清空旧数据
          currentLayout.value = [...defaultLayout]
          saveLayout()
        }
      }
    }
  } catch { /* ignore */ }
}

onMounted(() => {
  fetchData()
  fetchLayout()
  window.addEventListener('resize', handleResize)
  refreshClock()
  clockTimer = window.setInterval(refreshClock, 1000)
  observer = new IntersectionObserver(([entry]) => { showMiniHeader.value = !entry.isIntersecting }, { threshold: 0 })
  if (welcomeBannerRef.value) observer.observe(welcomeBannerRef.value)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

// keep-alive 激活时：关闭动画避免飞入闪烁 + 恢复时钟 + 后台静默刷新数据
const isRestoring = ref(false)

onActivated(() => {
  isRestoring.value = true
  setTimeout(() => {
    isRestoring.value = false
  }, 150)

  refreshClock()
  if (clockTimer === null) {
    clockTimer = window.setInterval(refreshClock, 1000)
  }
  if (welcomeBannerRef.value && observer) {
    observer.observe(welcomeBannerRef.value)
  }
  // 静默刷新数据，不设 isLoading 避免整页闪烁
  fetchData(true)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  observer?.disconnect()
  if (clockTimer !== null) { clearInterval(clockTimer); clockTimer = null }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

function handleResize() { /* placeholder for chart resize if needed */ }

// ==================== Lottie 动画后台暂停 ====================
const lottieRef = ref()
function handleVisibilityChange() {
  if (document.hidden) {
    lottieRef.value?.pause()
  } else {
    lottieRef.value?.play()
  }
}
</script>

<style scoped>
.workbench {
  max-width: 1240px;
  margin: 0 auto;
  padding: 24px;
  min-height: calc(100vh - 64px);
  box-sizing: border-box;
  background: linear-gradient(180deg, #f5f6fa 0%, #eef0f6 100%);
}

/* ==================== 欢迎横幅 ==================== */
.welcome-section {
  position: relative; overflow: hidden; display: flex; align-items: center;
  justify-content: space-between; gap: 32px; border-radius: 24px; padding: 30px 40px;
  margin-bottom: 20px; color: #f3f5fb; border: 1px solid rgba(74, 144, 217, 0.18);
  box-shadow: 0 1px 0 rgba(255,255,255,0.06) inset, 0 14px 40px -10px rgba(20,50,100,0.20), 0 4px 14px rgba(20,50,100,0.10);
  transition: background 0.6s ease;
  background: linear-gradient(135deg, #1e3a5f 0%, #2a5080 50%, #1a5276 100%);
}
.welcome-deco { position: absolute; pointer-events: none; z-index: 0; }
.welcome-deco-arc {
  top: -40%; right: -10%; width: 60%; height: 200%;
  background: radial-gradient(ellipse at center, rgba(74,144,217,0.18) 0%, rgba(74,144,217,0.05) 35%, transparent 65%);
  filter: blur(20px); opacity: 0.9;
}
.welcome-deco-noise {
  inset: 0;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 0.2 0 0 0 0 0.2 0 0 0 0 0.3 0 0 0 0.04 0'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>");
  opacity: 0.5; mix-blend-mode: multiply;
}
.welcome-section.morning { background: linear-gradient(135deg, #3a6b8c 0%, #4a7fa5 50%, #5a8fb5 100%); }
.welcome-section.afternoon { background: linear-gradient(135deg, #1a4a7a 0%, #2a6090 50%, #3a75a5 100%); }
.welcome-section.evening { background: linear-gradient(135deg, #1e3a5f 0%, #2a5080 50%, #1a5276 100%); }
.welcome-section.night {
  background: linear-gradient(135deg, #1a2a3e 0%, #152238 50%, #0f1a2c 100%);
  color: #f0f2ff; border-color: rgba(255,255,255,0.08);
  box-shadow: 0 20px 60px rgba(20,25,50,0.4);
}
.welcome-info { display: flex; align-items: center; gap: 20px; position: relative; z-index: 2; }
.welcome-right { display: flex; flex-direction: column; align-items: flex-end; gap: 14px; position: relative; z-index: 2; }
.welcome-clock {
  position: relative; display: flex; flex-direction: column; align-items: flex-end;
  padding: 10px 18px; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12);
  border-radius: 16px; backdrop-filter: blur(20px);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.10), 0 4px 16px rgba(0,0,0,0.20); overflow: hidden;
}
.clock-time {
  font-family: 'JetBrains Mono', 'Fira Code', 'Menlo', monospace; font-size: 26px; font-weight: 700;
  color: #ffffff; line-height: 1.1; letter-spacing: 1px; text-shadow: 0 0 12px rgba(74,144,217,0.25);
}
.clock-date { font-size: 12px; color: rgba(230,235,250,0.85); margin-top: 4px; letter-spacing: 0.5px; }
.clock-week { font-size: 11px; color: rgba(200,210,230,0.75); margin-top: 2px; letter-spacing: 0.5px; }
.welcome-animation-podium {
  position: relative; flex-shrink: 0; width: 90px; height: 90px;
  background: rgba(255,255,255,0.10); border: 1px solid rgba(255,255,255,0.20);
  backdrop-filter: blur(16px); border-radius: 50%; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  box-shadow: inset 0 2px 6px rgba(255,255,255,0.10), 0 8px 24px rgba(0,0,0,0.25);
  cursor: pointer; transition: all 0.3s cubic-bezier(0.34,1.56,0.64,1);
}
.welcome-animation-podium:hover {
  transform: scale(1.05); background: rgba(255,255,255,0.18);
  box-shadow: inset 0 2px 6px rgba(255,255,255,0.14), 0 12px 32px rgba(0,0,0,0.32);
}
.podium-hint {
  position: absolute; bottom: 0; right: -4px; width: 24px; height: 24px;
  background: #4a90d9; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  color: white; font-size: 14px; box-shadow: 0 4px 12px rgba(74,144,217,0.4); border: 2px solid #fff;
  opacity: 0; transform: scale(0.5); transition: all 0.3s cubic-bezier(0.34,1.56,0.64,1);
}
.welcome-animation-podium:hover .podium-hint { opacity: 1; transform: scale(1); }
.welcome-title {
  font-size: 38px; font-weight: 800; letter-spacing: -0.5px; margin: 6px 0 8px;
  color: #ffffff; text-shadow: 0 2px 8px rgba(0,0,0,0.20);
  display: flex; align-items: center; gap: 10px; line-height: 1.15;
}
.welcome-name { color: #ffffff; font-weight: 800; text-shadow: 0 0 20px rgba(100,180,255,0.35); }
.greeting-icon { font-size: 32px; color: #ffc88a; filter: drop-shadow(0 0 6px rgba(255,200,138,0.35)); }
.welcome-tagline {
  display: inline-flex; align-items: center; gap: 8px; padding: 3px 12px 3px 6px;
  background: rgba(255,255,255,0.10); border: 1px solid rgba(255,255,255,0.18);
  border-radius: 999px; width: fit-content; backdrop-filter: blur(12px);
}
.welcome-tagline-bar {
  display: inline-block; width: 14px; height: 2px;
  background: linear-gradient(90deg, #7ab8ff, #90c8ff); border-radius: 1px;
  box-shadow: 0 0 6px rgba(122,184,255,0.5);
}
.welcome-tagline-text { font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; color: rgba(235,240,255,0.85); font-weight: 500; }
.welcome-desc { margin: 0 0 16px; color: rgba(230,235,250,0.85); font-size: 15px; line-height: 1.7; font-weight: 400; letter-spacing: 0.3px; max-width: 460px; }
.welcome-quote-mark { color: rgba(195,210,250,0.65); font-weight: 600; font-size: 16px; margin: 0 2px; }
.welcome-quick-nav { display: flex; gap: 8px; align-items: stretch; flex-wrap: wrap; }
.quick-nav-item {
  display: inline-flex; align-items: center; gap: 8px; padding: 8px 14px;
  background: rgba(255,255,255,0.10); border: 1px solid rgba(255,255,255,0.18);
  border-radius: 10px; color: #f3f5fb; cursor: pointer; font-size: 13px; font-weight: 500;
  letter-spacing: 0.3px; backdrop-filter: blur(20px);
  transition: all 0.25s cubic-bezier(0.4,0,0.2,1); outline: none; white-space: nowrap;
}
.quick-nav-item:hover, .quick-nav-item:focus-visible {
  background: rgba(255,255,255,0.18); border-color: rgba(255,255,255,0.32);
  transform: translateY(-1px); box-shadow: 0 8px 20px rgba(0,0,0,0.25);
}
.quick-nav-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: 7px;
  background: linear-gradient(135deg, rgba(255,255,255,0.20), rgba(255,255,255,0.08));
  color: #ffffff; flex-shrink: 0; box-shadow: inset 0 0 0 1px rgba(255,255,255,0.10);
}
.welcome-stats { display: flex; gap: 12px; }
.welcome-stat-item {
  --stat-color: #4a90d9; --stat-bg: rgba(74,144,217,0.25);
  position: relative; display: flex; align-items: center; gap: 10px;
  background: rgba(255,255,255,0.10); backdrop-filter: blur(24px);
  border: 1px solid rgba(255,255,255,0.14); border-radius: 16px;
  padding: 12px 16px; min-width: 110px; overflow: hidden;
  transition: all .3s cubic-bezier(.25,.8,.25,1); box-shadow: 0 6px 20px rgba(0,0,0,0.20);
}
.welcome-stat-item:hover { transform: translateY(-3px); background: rgba(255,255,255,0.18); border-color: rgba(255,255,255,0.28); box-shadow: 0 12px 30px rgba(0,0,0,0.32), 0 0 0 1px var(--stat-color); }
.stat-icon-wrap { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; background: var(--stat-bg); color: var(--stat-color); border-radius: 10px; flex-shrink: 0; }
.stat-body { display: flex; flex-direction: column; gap: 2px; }
.welcome-stat-value { font-size: 22px; font-weight: 800; color: #ffffff; line-height: 1; text-shadow: 0 0 8px rgba(0,0,0,0.20); font-feature-settings: 'tnum'; font-variant-numeric: tabular-nums; }
.welcome-stat-label { color: rgba(230,235,250,0.75); font-size: 11px; letter-spacing: 0.5px; font-weight: 400; }
.stat-bar { position: absolute; left: 0; right: 0; bottom: 0; height: 2px; background: rgba(255,255,255,0.05); overflow: hidden; }
.stat-bar-fill { position: absolute; top: 0; left: 0; height: 100%; width: 40%; background: linear-gradient(90deg, transparent, var(--stat-color), transparent); animation: barShimmer 3s ease-in-out infinite; opacity: 0.85; }
@keyframes barShimmer { 0% { transform: translateX(-100%); } 100% { transform: translateX(350%); } }

/* ==================== 今日摘要 ==================== */
.daily-summary {
  margin-bottom: 20px;
  padding: 20px 28px;
  background: #ffffff;
  border-radius: var(--radius-card, 16px);
  border: 1px solid rgba(60, 80, 130, 0.08);
  box-shadow: var(--shadow-s, 0 1px 3px rgba(0,0,0,0.06));
}
.daily-summary-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text, #1a1a2e);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.daily-summary-header .el-icon {
  color: var(--color-primary, #3B82F6);
}
.daily-summary-items {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.daily-summary-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-text-secondary, #5a6072);
}
.daily-summary-item strong {
  font-weight: 700;
  color: var(--color-text, #1a1a2e);
  font-size: 16px;
  font-feature-settings: 'tnum';
}
.summary-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.summary-dot-blue { background: #4a90d9; }
.summary-dot-green { background: #34d399; }
.summary-dot-purple { background: #8b5cf6; }

/* ==================== 布局工具栏 ==================== */
.layout-toolbar {
  display: flex; align-items: center; gap: 8px; margin-bottom: 16px;
  padding: 0 4px;
}

/* ==================== 网格布局区域 ==================== */
.widget-grid-area {
  min-height: 300px;
}
.widget-card {
  border-radius: 14px; border: 1px solid rgba(60,80,130,0.08);
  box-shadow: 0 4px 16px 0 rgba(45,60,110,0.05);
  transition: all 0.3s ease; background: #ffffff; height: 100%;
  overflow: hidden; display: flex; flex-direction: column;
}
.widget-card :deep(.el-card__body) {
  flex: 1; overflow-y: auto; min-height: 0;
}

/* ==================== 滚动条终极美化 (macOS 风格) ==================== */
/* 针对所有 Widget 卡片内部的内容区 */
.widget-card :deep(.el-card__body)::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}

/* 滚动条轨道：完全透明，不占据视觉空间 */
.widget-card :deep(.el-card__body)::-webkit-scrollbar-track {
  background: transparent;
}

/* 滚动条滑块：默认半透明极浅灰色，圆角 */
.widget-card :deep(.el-card__body)::-webkit-scrollbar-thumb {
  background: rgba(144, 147, 153, 0.2);
  border-radius: 4px;
  transition: background 0.3s ease;
}

/* 悬停在滚动条上时：加深颜色，方便拖拽 */
.widget-card :deep(.el-card__body)::-webkit-scrollbar-thumb:hover {
  background: rgba(144, 147, 153, 0.6);
}

/* 卡片未被悬停时，滚动条几乎不可见，保持极度清爽 */
.widget-card:not(:hover) :deep(.el-card__body)::-webkit-scrollbar-thumb {
  background: rgba(144, 147, 153, 0.05);
}
.widget-card.editing {
  border-color: #409eff44;
  box-shadow: 0 0 0 2px #409eff22, 0 4px 16px rgba(45,60,110,0.08);
}
.widget-card:hover { box-shadow: 0 8px 24px 0 rgba(0,0,0,0.06); }
.widget-card-header {
  display: flex; align-items: center; gap: 8px;
}
.widget-card-title {
  font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 8px; color: var(--el-text-color-primary); flex: 1;
}
.widget-card-title .title-icon { color: var(--el-color-primary); }
.widget-drag-handle { cursor: move; display: flex; align-items: center; color: #909399; }
.empty-grid { padding: 60px 0; }

/* ==================== 解决 KeepAlive 网格飞入闪烁 ==================== */
.widget-grid-area.no-transition :deep(.vgl-item),
.widget-grid-area.no-transition :deep(.vue-grid-item) {
  transition: none !important;
  transform-origin: center center;
}

/* ==================== 组件库抽屉 ==================== */
.widget-library { display: flex; flex-direction: column; gap: 12px; }
.widget-lib-item {
  display: flex; align-items: center; gap: 12px; padding: 12px 16px;
  border-radius: 10px; border: 1px solid #e4e7ed; cursor: pointer; transition: all 0.2s;
}
.widget-lib-item:hover { background: #ecf5ff; border-color: #409eff; }
.widget-lib-info { flex: 1; }
.widget-lib-name { font-size: 14px; font-weight: 600; color: #303133; }
.widget-lib-desc { font-size: 12px; color: #909399; margin-top: 2px; }

/* ==================== Mini Header ==================== */
.mini-header {
  position: fixed; top: 76px; left: 220px; height: 48px; padding: 0 20px;
  display: flex; align-items: center; gap: 16px; border-radius: 999px;
  backdrop-filter: blur(20px); background: rgba(15,23,42,.85);
  border: 1px solid rgba(255,255,255,.08); box-shadow: 0 8px 32px rgba(0,0,0,.25);
  z-index: 999; transition: all .3s ease;
}
.mini-lottie { width: 28px; height: 28px; border-radius: 50%; overflow: hidden; display: flex; align-items: center; justify-content: center; }
.mini-user { font-size: 14px; font-weight: 600; color: #fff; }
.mini-header-enter-active, .mini-header-leave-active { transition: all .3s ease; }
.mini-header-enter-from, .mini-header-leave-to { opacity: 0; transform: translateY(-20px); }

/* ==================== 响应式 ==================== */
@media (max-width: 1180px) {
  .welcome-section { flex-direction: column; align-items: flex-start; gap: 20px; padding: 26px 28px; }
  .welcome-right { width: 100%; align-items: stretch; }
  .welcome-clock { align-items: flex-start; }
  .welcome-quick-nav { width: 100%; }
  .quick-nav-item { flex: 1 1 0; min-width: 100px; justify-content: center; }
  .welcome-stats { flex-wrap: wrap; width: 100%; justify-content: flex-start; }
  .welcome-stat-item { flex: 1 1 calc(50% - 12px); min-width: 130px; }
}
@media (max-width: 768px) {
  .workbench { padding: 16px; }
  .welcome-section { padding: 22px 20px; border-radius: 20px; }
  .welcome-info { gap: 14px; }
  .welcome-animation-podium { width: 72px; height: 72px; }
  .welcome-title { font-size: 24px; gap: 6px; }
  .greeting-icon { font-size: 24px; }
  .welcome-desc { font-size: 13px; max-width: 100%; }
  .welcome-clock { padding: 8px 14px; }
  .clock-time { font-size: 22px; }
}
</style>
