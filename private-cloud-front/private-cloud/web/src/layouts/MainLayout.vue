<template>
  <el-container class="main-layout" :class="{ 'embed-mode': isEmbedMode }">
    <el-aside v-if="!isEmbedMode" :width="isCollapsed ? '64px' : '240px'" class="main-aside">
      <!-- 用户信息卡片 -->
      <div class="user-card" :class="{ collapsed: isCollapsed }">
        <el-avatar v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" class="sidebar-avatar" />
        <span v-else class="sidebar-avatar-text">{{ userInitials }}</span>
        <div v-if="!isCollapsed" class="user-card-info">
          <span class="user-card-name">{{ userStore.userInfo?.realName || userStore.userInfo?.username || '用户' }}</span>
          <span class="user-card-dept">{{ userStore.departmentName || '未分配部门' }}</span>
        </div>
      </div>

      <div class="menu-divider"></div>

      <div class="menu-wrapper">
        <div class="menu-indicator" ref="menuIndicatorRef"></div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        background-color="#ffffff"
        text-color="#5a6072"
        active-text-color="#ffffff"
        @select="handleMenuSelect"
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>个人工作台</template>
        </el-menu-item>
        <el-menu-item index="/document">
          <el-icon><FolderOpened /></el-icon>
          <template #title>文档空间</template>
        </el-menu-item>
        <el-menu-item index="/search">
          <el-icon><Search /></el-icon>
          <template #title>高级搜索</template>
        </el-menu-item>
        <el-menu-item index="/qa">
          <el-icon><ChatLineSquare /></el-icon>
          <template #title>知识问答</template>
        </el-menu-item>
        <el-menu-item index="/collab">
          <el-icon><Share /></el-icon>
          <template #title>外部协作</template>
        </el-menu-item>
        <el-menu-item index="/share/manage">
          <el-icon><Link /></el-icon>
          <template #title>分享管理</template>
        </el-menu-item>
        <el-menu-item index="/workflow/approval">
          <el-icon><DocumentChecked /></el-icon>
          <template #title>申请签章</template>
        </el-menu-item>
        <el-menu-item index="/ai/generate">
          <el-icon><MagicStick /></el-icon>
          <template #title>智能文档生成</template>
        </el-menu-item>
        <el-menu-item index="/ai/generated-docs">
          <el-icon><FolderOpened /></el-icon>
          <template #title>生成文档管理</template>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/admin/sensitive-words">
          <el-icon><Warning /></el-icon>
          <template #title>敏感词管理</template>
        </el-menu-item>
      </el-menu>
      </div><!-- /menu-wrapper -->

      <!-- 底部退出按钮 -->
      <div class="sidebar-footer" :class="{ collapsed: isCollapsed }">
        <div class="logout-btn" @click="handleLogout">
          <el-icon class="logout-icon"><SwitchButton /></el-icon>
          <span v-show="!isCollapsed">退出登录</span>
        </div>
      </div>
    </el-aside>

    <el-container class="main-content">
      <el-header v-if="!isEmbedMode" class="main-header" height="56px">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <div class="theme-toggle" @click="toggleTheme" :title="theme === 'dark' ? '浅色模式' : '深色模式'">
            <el-icon v-if="theme === 'dark'"><Moon /></el-icon>
            <el-icon v-else><Sunny /></el-icon>
          </div>
          <el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount === 0" :max="99" class="notify-badge">
            <span class="header-icon-btn" :class="{ 'has-unread': notificationStore.unreadCount > 0 }" @click="openNotificationDrawer">
              <el-icon class="header-icon bell-icon" :class="{ 'bell-ring': bellRinging }"><Bell /></el-icon>
            </span>
          </el-badge>
        </div>
      </el-header>

      <el-main class="main-body">
        <router-view v-slot="{ Component }">
          <Transition :name="shouldTransition ? 'page-slide' : ''" mode="out-in">
            <keep-alive :include="['DocumentSpace', 'Preview', 'Workbench', 'KnowledgeQA', 'DocGenerate', 'GeneratedDocs']">
              <component :is="Component" :key="$route.name" />
            </keep-alive>
          </Transition>
        </router-view>
      </el-main>
    </el-container>

    <el-drawer v-model="showNotifications" title="通知消息" direction="rtl" size="380px">
      <template #header>
        <div class="drawer-header">
          <span class="drawer-title">通知消息</span>
          <div class="drawer-actions">
<el-tooltip v-if="notificationStore.unreadCount > 0" content="全部已读" placement="top">
  <span class="action-btn action-btn-success" @click="handleMarkAllRead">
    <el-icon><Check /></el-icon>
  </span>
</el-tooltip>
<el-tooltip content="清除已读" placement="top">
  <span class="action-btn action-btn-warning" @click="handleClearRead">
    <el-icon><Delete /></el-icon>
  </span>
</el-tooltip>
          </div>
        </div>
      </template>
      <div class="notification-list" v-loading="notificationStore.loading">
        <div v-if="notificationStore.sortedNotifications.length === 0" class="no-notifications">
          <el-empty description="暂无通知" :image-size="80" />
        </div>
        <div
          v-for="n in notificationStore.sortedNotifications"
          :key="n.id"
          class="notification-item"
          :class="{ unread: n.isRead === 0 }"
          @click="handleNotificationClick(n)"
        >
          <div class="notif-header">
            <el-tag :type="getNotifTagType(n.type)" size="small" effect="plain" class="notif-type-tag">
              {{ getNotifTypeLabel(n.type) }}
            </el-tag>
            <span v-if="n.isRead === 0" class="unread-dot"></span>
            <el-button link type="danger" size="small" class="notif-delete-btn" @click.stop="handleDeleteNotification(n.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <div class="notif-title">{{ n.title }}</div>
          <div class="notif-content">{{ n.content }}</div>
          <div class="notif-meta">
            <span v-if="n.fileName" class="notif-file">
              <el-icon><Document /></el-icon>
              {{ n.fileName }}
            </span>
            <span class="notif-time">{{ formatTime(n.createTime) }}</span>
          </div>
        </div>
      </div>
    </el-drawer>

    <AiAssistant />
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Odometer, FolderOpened, Search, ChatLineSquare, Share, MagicStick,
  Fold, Expand, Bell, Link, DocumentChecked, UserFilled, Warning,
  Delete, Document, Brush, Check, SwitchButton, Moon, Sunny
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { connectWebSocket, disconnectWebSocket } from '@/utils/websocket'
import { useTheme } from '@/composables/useTheme'
import gsap from 'gsap'
import type { NotificationDTO } from '@/api/notification'
import AiAssistant from '@/views/ai/AiAssistant.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const { theme, toggleTheme } = useTheme()
const isCollapsed = ref(false)
const menuIndicatorRef = ref<HTMLElement | null>(null)

const isEmbedMode = computed(() => route.query.embed === 'true')
const showNotifications = ref(false)
const bellRinging = ref(false)
let bellTimer: ReturnType<typeof setTimeout> | null = null

/** 页面转场：文件预览和协同编辑禁用转场动画 */
const shouldTransition = computed(() => {
  const name = route.name as string
  return !['Preview', 'CollabEditor'].includes(name)
})

/** 触发铃铛晃动一次 + 播放提示音 */
function triggerBellNotification() {
  // 晃动一次
  bellRinging.value = true
  if (bellTimer) clearTimeout(bellTimer)
  bellTimer = setTimeout(() => { bellRinging.value = false }, 2000)

  // 播放提示音（使用 Web Audio API 生成短促提示音，无需音频文件）
  try {
    const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)()
    const oscillator = audioCtx.createOscillator()
    const gainNode = audioCtx.createGain()
    oscillator.connect(gainNode)
    gainNode.connect(audioCtx.destination)
    oscillator.frequency.setValueAtTime(880, audioCtx.currentTime)
    oscillator.frequency.setValueAtTime(660, audioCtx.currentTime + 0.15)
    gainNode.gain.setValueAtTime(0.3, audioCtx.currentTime)
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.4)
    oscillator.start(audioCtx.currentTime)
    oscillator.stop(audioCtx.currentTime + 0.4)
  } catch { /* 音频播放失败静默忽略 */ }
}

const activeMenu = computed(() => {
  if (route.path.startsWith('/document') || route.path.startsWith('/preview')) return '/document'
  if (route.path.startsWith('/ai/generated-docs')) return '/ai/generated-docs'
  if (route.path.startsWith('/ai')) return '/ai/generate'
  if (route.path.startsWith('/admin')) return route.path
  return route.path
})

/** 侧边栏液态滑块动画 */
function updateMenuIndicator() {
  if (!menuIndicatorRef.value || isCollapsed.value) {
    if (menuIndicatorRef.value) menuIndicatorRef.value.style.opacity = '0'
    return
  }
  const menuEl = document.querySelector('.side-menu') as HTMLElement
  if (!menuEl) return
  const activeItem = menuEl.querySelector('.el-menu-item.is-active') as HTMLElement
  if (!activeItem) { menuIndicatorRef.value.style.opacity = '0'; return }
  const wrapper = menuIndicatorRef.value.parentElement as HTMLElement
  const wrapperRect = wrapper.getBoundingClientRect()
  const itemRect = activeItem.getBoundingClientRect()
  const top = itemRect.top - wrapperRect.top
  gsap.to(menuIndicatorRef.value, {
    top: top,
    height: itemRect.height,
    opacity: 1,
    duration: 0.4,
    ease: 'power2.out',
  })
}

watch([activeMenu, isCollapsed], () => {
  nextTick(updateMenuIndicator)
})

onMounted(() => { nextTick(updateMenuIndicator) })

// 导航防抖：快速点击时只触发第一次
let navTimer: ReturnType<typeof setTimeout> | null = null
function handleMenuSelect(index: string) {
  if (navTimer) return
  // 文档空间：恢复最后访问的文档相关路由（文件列表或预览页面）
  if (index === '/document') {
    const lastDocRoute = sessionStorage.getItem(`u${userStore.userId}:last_doc_route`)
    if (lastDocRoute && (lastDocRoute.startsWith('/preview/') || lastDocRoute === '/document')) {
      router.push(lastDocRoute)
    } else {
      router.push('/document')
    }
  } else {
    router.push(index)
  }
  navTimer = setTimeout(() => { navTimer = null }, 300)
}

// 追踪最后访问的文档相关路由（文件列表或预览页面）
watch(() => route.path, (path) => {
  if (path === '/document' || path.startsWith('/preview/')) {
    sessionStorage.setItem(`u${userStore.userId}:last_doc_route`, path)
  }
}, { immediate: true })

const pageTitle = computed(() => {
  const meta = route.meta as { title?: string } | undefined
  return meta?.title || ''
})

// 文字头像：取用户名首字母/首字
const userInitials = computed(() => {
  const name = userStore.userInfo?.realName || userStore.userInfo?.username || '用户'
  if (!name) return 'U'
  // 英文名取首字母大写，中文名取首字
  const first = name.trim().charAt(0)
  if (/[a-zA-Z]/.test(first)) return first.toUpperCase()
  return first
})

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

function handleLogout() {
  ElMessageBox.confirm(
    '您即将退出当前账号，退出后需要重新登录才能继续访问系统。<br>请确保已保存所有未提交的工作内容。',
    '确认退出登录？',
    {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'clean-logout-dialog',
      dangerouslyUseHTMLString: true
    }
  )
    .then(() => {
      userStore.logout()
      router.push('/login')
    })
    .catch(() => {
      // 用户取消退出
    })
}

function openNotificationDrawer() {
  showNotifications.value = true
  notificationStore.fetchNotifications()
}

function handleNotificationClick(n: NotificationDTO) {
  if (n.isRead === 0) {
    notificationStore.markAsRead(n.id)
  }
  if (n.type === 'MENTION' && n.fileId) {
    showNotifications.value = false
    router.push({ name: 'Preview', params: { id: String(n.fileId) }, query: { tab: 'comments' } })
  }
}

function handleMarkAllRead() {
  notificationStore.markAllAsRead()
  ElMessage.success('已全部标记为已读')
}

function handleClearRead() {
  notificationStore.clearReadNotifications()
  ElMessage.success('已清除已读通知')
}

function handleDeleteNotification(id: number) {
  notificationStore.deleteNotification(id)
}

function getNotifTypeLabel(type: string): string {
  switch (type) {
    case 'MENTION': return '@提及'
    case 'APPROVAL': return '审批'
    case 'SYSTEM': return '系统'
    default: return '通知'
  }
}

function getNotifTagType(type: string): 'primary' | 'warning' | 'info' | 'success' | 'danger' {
  switch (type) {
    case 'MENTION': return 'primary'
    case 'APPROVAL': return 'warning'
    case 'SYSTEM': return 'info'
    default: return 'info'
  }
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return date.toLocaleDateString()
}

function handleWebSocketMessage(msg: any) {
  const realtimeNotification: NotificationDTO = {
    id: msg.id || Date.now(),
    type: msg.type || 'SYSTEM',
    title: msg.title || '新消息',
    content: msg.content || msg.commentContent || '',
    fromUserId: msg.fromUserId || null,
    fromUsername: msg.fromUsername || null,
    fileId: msg.fileId || null,
    fileName: msg.fileName || null,
    commentId: msg.commentId || null,
    isRead: 0,
    createTime: new Date().toISOString(),
  }
  notificationStore.addRealtimeNotification(realtimeNotification)
  triggerBellNotification()

  if (msg.type === 'MENTION') {
    ElMessage({
      message: `${msg.fromUsername || '有人'} 在文档中@提到了你`,
      type: 'info',
      duration: 5000,
    })
  }
}

onMounted(() => {
  const userId = userStore.userId
  if (userId) {
    connectWebSocket(userId, handleWebSocketMessage)
  }
  notificationStore.fetchNotifications()
  notificationStore.fetchUnreadCount()
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg, #f5f7fa);
}

/* 嵌入模式：隐藏侧栏和顶栏，内容区占满整个视口 */
.main-layout.embed-mode .main-body {
  padding: 0;
}

.main-aside {
  background: var(--color-surface, #ffffff);
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border, #eef0f4);
}

/* ---- 用户卡片 ---- */
.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px 16px;
  flex-shrink: 0;
}

.user-card.collapsed {
  justify-content: center;
  padding: 16px 0;
}

.sidebar-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.sidebar-avatar-text {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #337ecc);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-card-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  overflow: hidden;
}

.user-card-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-card-dept {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---- 分割线 ---- */
.menu-divider {
  height: 1px;
  background: #eef0f4;
  margin: 0 16px;
  flex-shrink: 0;
}

/* ---- 菜单 ---- */
.menu-wrapper {
  position: relative;
  flex: 1;
  overflow: hidden;
}
.menu-indicator {
  position: absolute;
  left: 8px;
  right: 8px;
  top: 0;
  height: 42px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.08);
  backdrop-filter: blur(12px);
  pointer-events: none;
  z-index: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.side-menu {
  border-right: none !important;
  flex: 1;
  padding: 8px 0;
  position: relative;
  z-index: 1;
}

.side-menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 2px 8px;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s ease;
}

.side-menu :deep(.el-menu-item:hover) {
  background-color: #f5f6f8 !important;
  color: #303133 !important;
}

.side-menu :deep(.el-menu-item.is-active) {
  background-color: #ecf5ff !important;
  color: #409eff !important;
  font-weight: 600;
}

.side-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #409eff !important;
}

.side-menu :deep(.el-menu-item .el-icon) {
  font-size: 18px;
  color: #5a6072;
}

/* 折叠状态下的菜单项 */
/* 折叠状态下的菜单项 */
.side-menu.el-menu--collapse :deep(.el-menu-item) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 !important;
  margin: 2px 8px;
  text-align: center;
}

.side-menu.el-menu--collapse :deep(.el-menu-item .el-icon) {
  margin: 0 !important;
  font-size: 20px;
}

.side-menu.el-menu--collapse :deep(.el-menu-item .el-icon svg) {
  width: 20px;
  height: 20px;
}

/* ---- 底部退出 ---- */
.sidebar-footer {
  flex-shrink: 0;
  padding: 8px 16px 16px;
  border-top: 1px solid #eef0f4;
}

.sidebar-footer.collapsed {
  padding: 8px 0 16px;
  display: flex;
  justify-content: center;
}

.sidebar-footer.collapsed .logout-btn {
  width: 48px;
  padding: 10px 0;
  justify-content: center;
}

.sidebar-footer.collapsed .logout-btn .el-icon {
  margin: 0;
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #909399;
  font-size: 14px;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  background-color: #fef0f0;
  color: #e65050;
}

.logout-icon {
  font-size: 16px;
}

.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-surface, #fff);
  border-bottom: 1px solid var(--color-border, #eef0f4);
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: #5a6072;
  transition: color 0.2s;
}

.collapse-btn:hover {
  color: #1a1a2e;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* ==================== 主题切换按钮 ==================== */
.theme-toggle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--duration-fast, 200ms) ease;
  background: rgba(0,0,0,0.04);
  color: var(--color-text-secondary, #5a6072);
}
.theme-toggle:hover {
  background: rgba(0,0,0,0.08);
  transform: rotate(15deg);
}
[data-theme="dark"] .theme-toggle {
  background: rgba(255,255,255,0.08);
}
[data-theme="dark"] .theme-toggle:hover {
  background: rgba(255,255,255,0.15);
}

.header-icon {
  font-size: 18px;
  cursor: pointer;
  color: #5a6072;
  transition: color 0.2s;
}

.header-icon:hover {
  color: #4a6fdc;
}

.header-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #f7f8fc;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.header-icon-btn:hover {
  background: #eef0f4;
  transform: translateY(-1px);
}

.header-icon-btn.has-unread {
  background: #eef0ff;
}

.bell-icon {
  transition: transform 0.3s ease;
}

.bell-ring {
  animation: bellSwing 2s ease-in-out 1;
  transform-origin: top center;
}

@keyframes bellSwing {
  0%, 100% { transform: rotate(0deg); }
  5% { transform: rotate(14deg); }
  10% { transform: rotate(-12deg); }
  15% { transform: rotate(10deg); }
  20% { transform: rotate(-8deg); }
  25% { transform: rotate(4deg); }
  30% { transform: rotate(0deg); }
}

.notify-badge {
  line-height: 1;
}

.main-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.main-body {
  min-width: 0;
  background: var(--color-bg, #f5f7fa);
  overflow-y: auto;
  height: calc(100vh - 56px);
  padding: 0;
}

/* ==================== 页面转场动画 ==================== */
.page-slide-enter-active {
  transition: all var(--duration-normal, 300ms) var(--ease-out, cubic-bezier(0.2,0.8,0.2,1));
}
.page-slide-leave-active {
  transition: all var(--duration-fast, 200ms) var(--ease-in, cubic-bezier(0.4,0,1,1));
}
.page-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.page-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0 4px;
}

.drawer-title {
  font-size: 18px;
  font-weight: 700;
  color: #1a1a2e;
  letter-spacing: 0.5px;
}

.drawer-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

.action-btn:active {
  transform: scale(0.92);
  opacity: 0.9;
}

.action-btn-success {
  background: linear-gradient(135deg, rgba(103, 194, 58, 0.12) 0%, rgba(103, 194, 58, 0.06) 100%);
  color: #67c23a;
  border: 1px solid rgba(103, 194, 58, 0.18);
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.1);
}

.action-btn-success:hover {
  background: linear-gradient(135deg, rgba(103, 194, 58, 0.22) 0%, rgba(103, 194, 58, 0.12) 100%);
  border-color: rgba(103, 194, 58, 0.35);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.2);
}

.action-btn-warning {
  background: transparent;
  color: #909399;
  border: 1px solid rgba(144, 147, 153, 0.2);
}

.action-btn-warning:hover {
  background: rgba(144, 147, 153, 0.08);
  color: #e6a23c;
  border-color: rgba(230, 162, 60, 0.3);
}

.notification-list {
  padding: 8px 16px;
}

.notification-item {
  padding: 16px;
  margin-bottom: 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff 0%, #fafbfe 100%);
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.notification-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #409eff 0%, #66b1ff 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.notification-item:hover {
  background: linear-gradient(135deg, #f8faff 0%, #f0f5ff 100%);
  border-color: rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.1);
}

.notification-item:hover::before {
  opacity: 1;
}

.notification-item.unread {
  background: linear-gradient(135deg, #f0f7ff 0%, #e8f2ff 100%);
  border-color: rgba(64, 158, 255, 0.12);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.08);
}

.notification-item.unread:hover {
  background: linear-gradient(135deg, #e6f0ff 0%, #dbe8ff 100%);
  border-color: rgba(64, 158, 255, 0.25);
  box-shadow: 0 8px 28px rgba(64, 158, 255, 0.15);
}

.notif-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.notif-type-tag {
  flex-shrink: 0;
  font-weight: 600;
  letter-spacing: 0.3px;
  border-radius: 6px;
  padding: 0 8px;
  height: 22px;
  line-height: 22px;
}

.unread-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e8e 100%);
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.4);
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.2); opacity: 0.8; }
}

.notif-delete-btn {
  margin-left: auto;
  opacity: 0;
  transition: all 0.25s ease;
  color: #c0c4cc;
}

.notification-item:hover .notif-delete-btn {
  opacity: 1;
  color: #f56c6c;
}

.notif-delete-btn:hover {
  transform: scale(1.1);
}

.notif-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
  line-height: 1.5;
  letter-spacing: 0.2px;
}

.notif-content {
  font-size: 13px;
  color: #5a6072;
  line-height: 1.6;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  letter-spacing: 0.1px;
}

.notif-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

.notif-file {
  font-size: 12px;
  color: #8a90a0;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  flex: 1;
  min-width: 0;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 6px;
  word-break: break-all;
  line-height: 1.5;
}

.notif-time {
  font-size: 12px;
  color: #a0a4b0;
  flex-shrink: 0;
  font-weight: 500;
}

.no-notifications {
  padding: 60px 0;
  text-align: center;
}

@media (max-width: 1180px) {
  .main-header {
    padding: 0 14px;
  }
}

@media (max-width: 768px) {
  .header-left {
    gap: 10px;
  }
  .header-right {
    gap: 12px;
  }
}

@media (max-width: 480px) {
  .header-left .el-breadcrumb {
    display: none;
  }
}

/* 退出登录弹窗样式（需非scoped才能影响动态弹窗） */
.clean-logout-dialog .el-message-box__header {
  justify-content: center;
}

.clean-logout-dialog .el-message-box__title {
  font-size: 18px;
  font-weight: 600;
}

.clean-logout-dialog .el-message-box__content {
  padding: 16px 0;
}

.clean-logout-dialog .el-message-box__message {
  line-height: 1.6;
  color: #606266;
}

/* ==================== Dark Mode 深色模式覆盖 ==================== */
:global([data-theme="dark"]) .user-card-name {
  color: var(--color-text);
}
:global([data-theme="dark"]) .user-card-dept {
  color: var(--color-text-secondary);
}
:global([data-theme="dark"]) .menu-divider {
  background: var(--color-border);
}
:global([data-theme="dark"]) .sidebar-footer {
  border-top-color: var(--color-border);
}
:global([data-theme="dark"]) .side-menu :deep(.el-menu) {
  background: transparent;
}
:global([data-theme="dark"]) .side-menu :deep(.el-menu-item) {
  color: var(--color-text-secondary);
}
:global([data-theme="dark"]) .side-menu :deep(.el-menu-item:hover) {
  background-color: rgba(255,255,255,0.06) !important;
  color: var(--color-text) !important;
}
:global([data-theme="dark"]) .side-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(59,130,246,0.15) !important;
  color: #60a5fa !important;
}
:global([data-theme="dark"]) .side-menu :deep(.el-menu-item .el-icon) {
  color: var(--color-text-secondary);
}
:global([data-theme="dark"]) .side-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #60a5fa !important;
}
:global([data-theme="dark"]) .collapse-btn {
  color: var(--color-text-secondary);
}
:global([data-theme="dark"]) .collapse-btn:hover {
  color: var(--color-text);
}
:global([data-theme="dark"]) .header-icon-btn {
  color: var(--color-text-secondary);
}
:global([data-theme="dark"]) .header-icon-btn:hover {
  color: #60a5fa;
}
:global([data-theme="dark"]) .header-breadcrumb {
  color: var(--color-text);
}
:global([data-theme="dark"]) .main-aside {
  background: var(--color-surface);
}
</style>
