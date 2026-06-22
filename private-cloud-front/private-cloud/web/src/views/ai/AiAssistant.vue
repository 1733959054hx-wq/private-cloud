<template>
  <div class="ai-assistant" :style="aiStyle" ref="containerRef">
    <div
      class="ai-fab"
      :class="{ active: aiStore.isOpen, dragging: isDragging }"
      ref="fabRef"
      @click="handleFabClick"
      @pointerdown="onDragStart"
    >
      <span v-if="!aiStore.isOpen" class="fab-icon"><AiAvatarSvg class="ai-fab-avatar" /></span>
      <span v-else class="fab-icon"><el-icon :size="24"><Close /></el-icon></span>
    </div>

    <transition name="ai-panel">
      <div v-if="aiStore.isOpen" class="ai-panel">
        <div class="ai-panel-header">
          <div class="ai-title">
            <div class="ai-logo"><AiAvatarSvg class="ai-logo-avatar" /></div>
            <span class="session-title-text">AI 小助手</span>
          </div>
          <div class="ai-header-actions">
            <el-tooltip :content="linkTooltip" placement="bottom" :show-after="300">
              <el-button text size="small" @click="handleLinkFile" :class="{ 'link-active': linkedFileInfo }" style="color:#909399;">
                <el-icon :size="16"><Document /></el-icon>
              </el-button>
            </el-tooltip>
            <el-select v-model="currentModel" size="small" style="width:140px;">
              <el-option v-for="m in aiStore.modelOptions" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
            <el-button text size="small" @click="handleClearAll" style="color:#909399;" title="清空对话">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <div v-if="linkedFileInfo" class="ai-file-link-bar">
          <el-icon :size="14"><Document /></el-icon>
          <span>已关联：{{ linkedFileInfo.fileName }}</span>
          <el-button text size="small" @click="handleLinkFile" style="color:#909399;margin-left:auto;padding:0 4px;" title="取消关联">
            <el-icon :size="12"><Close /></el-icon>
          </el-button>
        </div>

        <div class="ai-messages" ref="messagesRef">
          <div v-if="aiStore.messages.length === 0" class="ai-welcome">
            <div class="welcome-icon">
              <div class="welcome-ai-icon"><AiAvatarSvg class="welcome-ai-avatar" /></div>
            </div>
            <h4>你好！我是AI助手</h4>
            <p>我可以帮你搜索文档、总结内容、回答问题</p>

            <!-- 快捷指令轮播卡片 -->
            <div v-if="carouselActions.length > 0" class="quick-carousel" @mouseenter="stopCarousel" @mouseleave="startCarousel">
              <div class="carousel-track" :style="carouselTrackStyle">
                <div class="carousel-page" v-for="(page, pi) in quickActionPages" :key="pi">
                  <div
                    class="quick-card"
                    v-for="action in page"
                    :key="action.id"
                    :style="{ '--card-color': action.color }"
                    @click="handleQuickAction(action)"
                  >
                    <div class="card-icon" :style="{ background: action.color + '18', color: action.color }">
                      <el-icon :size="18"><component :is="resolveIcon(action.icon)" /></el-icon>
                    </div>
                    <div class="card-text">
                      <div class="card-title">{{ action.title }}</div>
                      <div class="card-desc">{{ action.description }}</div>
                    </div>
                    <span class="card-badge" :class="action.actionType" :style="{ background: action.color + '18', color: action.color }">
                      <el-icon :size="12"><component :is="badgeIcon(action.actionType)" /></el-icon>
                    </span>
                  </div>
                </div>
              </div>
              <div class="carousel-dots" v-if="quickActionPages.length > 1">
                <span
                  v-for="(_, pi) in quickActionPages"
                  :key="pi"
                  class="carousel-dot"
                  :class="{ active: pi === currentPage }"
                  @click="currentPage = pi"
                ></span>
              </div>
            </div>
            <!-- 加载中占位 -->
            <div v-else-if="quickActions.length === 0" class="quick-carousel-loading">
              <div class="quick-card skeleton" v-for="n in 3" :key="n">
                <div class="card-icon skeleton-icon"></div>
                <div class="card-text">
                  <div class="skeleton-line skeleton-title"></div>
                  <div class="skeleton-line skeleton-desc"></div>
                </div>
              </div>
            </div>
          </div>

          <div v-for="(msg, idx) in aiStore.messages" :key="idx" class="ai-message" :class="msg.role">
            <div v-if="msg.role === 'assistant'" class="msg-avatar assistant"><AiAvatarSvg class="msg-avatar-svg" /></div>
            <div v-else class="msg-avatar user">我</div>
            <div class="msg-content">
              <template v-if="msg.role === 'assistant'">
                <SystemActionCard v-if="msg.systemData" :data="msg.systemData" />
                <div v-else class="msg-text markdown-body" v-html="renderMsgContent(msg.content, idx)"></div>
              </template>
              <div v-else class="msg-text">{{ msg.content }}</div>
              <div class="msg-actions" :class="{ visible: hoverIdx === idx }">
                <button v-if="msg.role === 'assistant' && msg.content" class="action-btn" @click="handleCopy(msg.content)" :class="{ copied: copiedIdx === idx }">
                  <el-icon :size="13"><CopyDocument /></el-icon>
                  <span>{{ copiedIdx === idx ? '已复制' : '复制' }}</span>
                </button>
                <button class="action-btn delete-action" @click="handleDeleteMessage(idx)" title="删除此条对话">
                  <el-icon :size="13"><Delete /></el-icon>
                  <span>删除</span>
                </button>
              </div>
            </div>
          </div>

          <div v-if="showTypingIndicator" class="ai-message assistant">
            <div class="msg-avatar assistant"><AiAvatarSvg class="msg-avatar-svg" /></div>
            <div class="msg-content">
              <div class="msg-text typing">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 常驻快捷工具条：仅关联文件后显示，左右箭头滑动 -->
        <div v-if="linkedFileInfo && toolbarActions.length > 0" class="ai-toolbar">
          <button class="toolbar-nav left" @click="scrollToolbar(-1)">
            <el-icon :size="12"><ArrowLeft /></el-icon>
          </button>
          <div class="toolbar-scroll" ref="toolbarScrollRef">
            <button
              v-for="action in toolbarActions"
              :key="action.id"
              class="toolbar-chip"
              :style="{ '--chip-color': action.color }"
              @click="handleQuickAction(action)"
            >
              <el-icon :size="14"><component :is="resolveIcon(action.icon)" /></el-icon>
              <span>{{ action.title }}</span>
            </button>
          </div>
          <button class="toolbar-nav right" @click="scrollToolbar(1)">
            <el-icon :size="12"><ArrowRight /></el-icon>
          </button>
        </div>

        <div class="ai-input-area">
          <el-input
            v-model="inputText"
            placeholder="输入问题，按Enter发送..."
            :rows="2"
            type="textarea"
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
          />
          <VoiceInput v-model="inputText" @recognized="onVoiceRecognized" />
          <el-button
            v-if="aiStore.isLoading"
            type="danger"
            :icon="VideoPause"
            circle
            @click="handleStop"
            title="停止生成"
          />
          <el-button
            v-else
            type="primary"
            :icon="Promotion"
            circle
            @click="handleSend"
            :disabled="!inputText.trim()"
          />
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Delete, Promotion, Close, Document, Search, VideoPause, CopyDocument, Reading, FolderOpened, ChatLineSquare, Edit, MagicStick, Stamp, Grid, Collection, EditPen, QuestionFilled, Odometer, Clock, Share, TrendCharts, DataAnalysis, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElMessageBox, ElNotification, ElMessage } from 'element-plus'
import { useAiStore } from '@/stores/ai'
import { useUserStore } from '@/stores/user'
import { fetchStreamChat, fetchFileChat, fetchQuickActions, fetchSystemAction, type QuickActionItem } from '@/api/ai'
import { renderMarkdown, renderStreamingMarkdown } from '@/utils/markdown'
import { getFileDetail, type FileDTO } from '@/api/document'
import VoiceInput from '@/components/VoiceInput.vue'
import AiAvatarSvg from '@/components/AiAvatarSvg.vue'
import SystemActionCard from '@/components/SystemActionCard.vue'

const route = useRoute()
const router = useRouter()
const aiStore = useAiStore()
const userStore = useUserStore()
// 按用户初始化聊天记录，用户切换时重新加载；登出时清空
watch(() => userStore.userId, (uid, oldUid) => {
  if (uid) {
    aiStore.initForUser(uid)
    loadPosition() // 不同用户有各自的 FAB 位置
  } else if (oldUid) {
    // 用户登出：清空消息，防止下一个用户看到上一个用户的记录
    aiStore.clearForLogout()
  }
}, { immediate: true })

const inputText = ref('')
const messagesRef = ref<HTMLDivElement>()
const streamingContent = ref('')
const abortController = ref<AbortController | null>(null)
const copiedIdx = ref(-1)
const hoverIdx = ref(-1)

const containerRef = ref<HTMLDivElement | null>(null)
const fabRef = ref<HTMLDivElement | null>(null)
const toolbarScrollRef = ref<HTMLDivElement | null>(null)

const POS_KEY = computed(() => `u${userStore.userId}:ai-assistant-position`)

const fabPos = ref<{ right: number; bottom: number }>({ right: 24, bottom: 24 })
const isDragging = ref(false)
const dragStart = ref<{ x: number; y: number; right: number; bottom: number } | null>(null)
const dragMoved = ref(false)
const wasDragged = ref(false)
const DRAG_THRESHOLD = 5

function loadPosition() {
  try {
    const raw = localStorage.getItem(POS_KEY.value)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (typeof parsed.right === 'number' && typeof parsed.bottom === 'number') {
        fabPos.value = { right: parsed.right, bottom: parsed.bottom }
      }
    }
  } catch {
  }
}

function savePosition() {
  try {
    localStorage.setItem(POS_KEY.value, JSON.stringify(fabPos.value))
  } catch {
  }
}

function clampPosition(right: number, bottom: number) {
  const FAB_SIZE = 56
  const maxRight = Math.max(0, window.innerWidth - FAB_SIZE)
  const maxBottom = Math.max(0, window.innerHeight - FAB_SIZE)
  return {
    right: Math.min(Math.max(0, right), maxRight),
    bottom: Math.min(Math.max(0, bottom), maxBottom),
  }
}

const aiStyle = computed(() => ({
  right: `${fabPos.value.right}px`,
  bottom: `${fabPos.value.bottom}px`,
  left: 'auto',
  top: 'auto',
}))

function onDragStart(e: PointerEvent) {
  if (e.button !== 0) return
  e.preventDefault()
  isDragging.value = true
  dragMoved.value = false
  dragStart.value = {
    x: e.clientX,
    y: e.clientY,
    right: fabPos.value.right,
    bottom: fabPos.value.bottom,
  }
  const target = e.currentTarget as HTMLElement
  target.setPointerCapture(e.pointerId)
  target.addEventListener('pointermove', onDragMove)
  target.addEventListener('pointerup', onDragEnd)
  document.body.style.userSelect = 'none'
}

function onDragMove(e: PointerEvent) {
  if (!isDragging.value || !dragStart.value) return
  const dx = dragStart.value.x - e.clientX
  const dy = dragStart.value.y - e.clientY
  if (!dragMoved.value && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
    dragMoved.value = true
  }
  if (!dragMoved.value) return
  const next = clampPosition(dragStart.value.right + dx, dragStart.value.bottom + dy)
  fabPos.value = next
}

function onDragEnd(e: PointerEvent) {
  if (isDragging.value && dragMoved.value) {
    savePosition()
    wasDragged.value = true
  }
  isDragging.value = false
  dragStart.value = null
  dragMoved.value = false
  const target = e.currentTarget as HTMLElement
  target.releasePointerCapture(e.pointerId)
  target.removeEventListener('pointermove', onDragMove)
  target.removeEventListener('pointerup', onDragEnd)
  document.body.style.userSelect = ''
}

function handleFabClick() {
  if (wasDragged.value) {
    wasDragged.value = false
    return
  }
  aiStore.toggleOpen()
}

function onWindowResize() {
  fabPos.value = clampPosition(fabPos.value.right, fabPos.value.bottom)
}

// loadPosition() 已在 userId watch 中调用
window.addEventListener('resize', onWindowResize)

onBeforeUnmount(() => {
  stopCarousel()
  if (fabRef.value) {
    fabRef.value.removeEventListener('pointermove', onDragMove)
    fabRef.value.removeEventListener('pointerup', onDragEnd)
  }
  document.body.style.userSelect = ''
  window.removeEventListener('resize', onWindowResize)
})

const linkedFileInfo = ref<{ fileId: number; fileName: string } | null>(null)

// ===== 快捷指令轮播卡片 =====
const quickActions = ref<QuickActionItem[]>([])
const currentPage = ref(0)
const CARDS_PER_PAGE = 6

// 图标名称 → 组件 映射表
const iconMap: Record<string, any> = {
  Search, Document, Reading, Grid, Collection, EditPen, QuestionFilled,
  Odometer, Clock, Share, TrendCharts, DataAnalysis,
  FolderOpened, ChatLineSquare, Edit, MagicStick, Stamp,
}

function resolveIcon(name: string) {
  return iconMap[name] || Document
}

function badgeIcon(type: string) {
  if (type === 'chat') return ChatLineSquare
  if (type === 'route') return Promotion
  return TrendCharts
}

// chat 类 → 输入框上方常驻快捷工具条
const toolbarActions = computed(() => quickActions.value.filter(a => a.actionType === 'chat'))
// system + route 类 → 轮播卡片
const carouselActions = computed(() => quickActions.value.filter(a => a.actionType !== 'chat'))

const quickActionPages = computed(() => {
  const pages: QuickActionItem[][] = []
  for (let i = 0; i < carouselActions.value.length; i += CARDS_PER_PAGE) {
    pages.push(carouselActions.value.slice(i, i + CARDS_PER_PAGE))
  }
  return pages
})

const carouselTrackStyle = computed(() => ({
  transform: `translateX(-${currentPage.value * 100}%)`,
}))

let carouselTimer: ReturnType<typeof setInterval> | null = null

function startCarousel() {
  stopCarousel()
  if (quickActionPages.value.length <= 1) return
  carouselTimer = setInterval(() => {
    currentPage.value = (currentPage.value + 1) % quickActionPages.value.length
  }, 4000)
}

function stopCarousel() {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
}

async function fetchQuickActionsData() {
  try {
    const res = await fetchQuickActions()
    const data = (res.data as any).data as QuickActionItem[] | undefined
    if (data && data.length > 0) {
      quickActions.value = data.sort((a, b) => a.sortOrder - b.sortOrder)
      currentPage.value = 0
      startCarousel()
    }
  } catch {
    // 静默失败，不影响 AI 助手其他功能
  }
}

const TOOLBAR_SCROLL_STEP = 120

function scrollToolbar(direction: number) {
  if (!toolbarScrollRef.value) return
  toolbarScrollRef.value.scrollBy({ left: direction * TOOLBAR_SCROLL_STEP, behavior: 'smooth' })
}

function handleQuickAction(action: QuickActionItem) {
  if (action.actionType === 'chat') {
    inputText.value = action.actionValue
    handleSend()
  } else if (action.actionType === 'route') {
    router.push(action.actionValue)
    aiStore.setOpen(false)
  } else if (action.actionType === 'system') {
    handleSystemAction(action)
  }
}

async function handleSystemAction(action: QuickActionItem) {
  aiStore.setLoading(true)
  try {
    const res = await fetchSystemAction(action.actionValue)
    const payload = (res.data as any).data as { content: string; actionId: string; data?: Record<string, any> } | undefined
    const content = payload?.content || '查询完成，暂无数据。'
    const systemData = payload
      ? { actionId: payload.actionId, content: payload.content, data: payload.data }
      : undefined
    aiStore.addMessage({ role: 'assistant', content, systemData })
    aiStore.persistMessages()
  } catch {
    aiStore.addMessage({ role: 'assistant', content: '查询失败，请稍后重试。' })
    aiStore.persistMessages()
  } finally {
    aiStore.setLoading(false)
  }
}

// 组件挂载时获取快捷指令
onMounted(() => {
  fetchQuickActionsData()
})

const NON_TEXT_TYPES = ['mp4', 'webm', 'avi', 'mov', 'mp3', 'wav', 'flac', 'aac', 'ogg', 'wma', 'm4a']

const isOnPreviewPage = computed(() => {
  return route.path.includes('/preview/')
})

const previewFileId = computed(() => {
  if (!isOnPreviewPage.value) return null
  const id = Number(route.params.id)
  return isNaN(id) ? null : id
})

const linkTooltip = computed(() => {
  if (!isOnPreviewPage.value) return '当前不在预览页面'
  if (linkedFileInfo.value) return `已关联：${linkedFileInfo.value.fileName}（点击取消）`
  return '关联当前预览文件'
})

async function handleLinkFile() {
  if (linkedFileInfo.value) {
    linkedFileInfo.value = null
    ElMessage.info('已取消文件关联')
    return
  }
  if (!previewFileId.value) {
    ElMessage.warning('当前不在文件预览页面，无法关联')
    return
  }
  try {
    const res = await getFileDetail(previewFileId.value)
    const file = (res.data as any).data as FileDTO
    const ft = file.fileType?.toLowerCase() || ''
    if (NON_TEXT_TYPES.includes(ft)) {
      ElMessage.warning('该文件类型（视频/音频）不支持内容读取，无法关联')
      return
    }
    linkedFileInfo.value = { fileId: previewFileId.value, fileName: file.fileName }
    ElMessage.success(`已关联文件：${file.fileName}`)
  } catch {
    ElMessage.error('获取文件信息失败，无法关联')
  }
}

// 进入预览页时自动关联文件（静默，不弹提示）
async function autoLinkPreviewFile() {
  if (!previewFileId.value || linkedFileInfo.value?.fileId === previewFileId.value) return
  try {
    const res = await getFileDetail(previewFileId.value)
    const file = (res.data as any).data as FileDTO
    const ft = file.fileType?.toLowerCase() || ''
    if (NON_TEXT_TYPES.includes(ft)) return
    linkedFileInfo.value = { fileId: previewFileId.value, fileName: file.fileName }
  } catch { /* 静默失败，不弹提示 */ }
}

watch(previewFileId, () => {
  if (linkedFileInfo.value && linkedFileInfo.value.fileId !== previewFileId.value) {
    linkedFileInfo.value = null
  }
  autoLinkPreviewFile()
}, { immediate: true })

const currentModel = computed({
  get: () => aiStore.selectedModel,
  set: (val: string) => aiStore.setModel(val),
})

// 流式回答期间消息列表末尾已有 assistant 消息时，不显示多余的打字头像
const showTypingIndicator = computed(() =>
  aiStore.isLoading && streamingContent.value === '' &&
  aiStore.messages[aiStore.messages.length - 1]?.role !== 'assistant'
)

function isStreamingMsg(idx: number): boolean {
  return aiStore.isLoading && idx === aiStore.messages.length - 1 && aiStore.messages[idx]?.role === 'assistant'
}

function renderMsgContent(content: string, idx: number): string {
  if (!content) return ''
  if (isStreamingMsg(idx)) {
    return renderStreamingMarkdown(content)
  }
  return renderMarkdown(content)
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(() => aiStore.messages.length, scrollToBottom)
watch(() => aiStore.isLoading, scrollToBottom)

function handleCopy(rawText: string) {
  navigator.clipboard.writeText(rawText).then(() => {
    const idx = aiStore.messages.findIndex(m => m.role === 'assistant' && m.content === rawText)
    copiedIdx.value = idx
    setTimeout(() => { copiedIdx.value = -1 }, 1800)
  })
}

function handleDeleteMessage(index: number) {
  aiStore.removeMessage(index)
}

async function handleClearAll() {
  if (aiStore.messages.length === 0) return
  try {
    await ElMessageBox.confirm('确定要清空所有对话内容吗？此操作不可恢复。', '清空对话', {
      confirmButtonText: '确定清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
    aiStore.clearMessages()
  } catch {}
}

function onVoiceRecognized(text: string) {
  inputText.value = text
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || aiStore.isLoading) return

  aiStore.addMessage({ role: 'user', content: text })
  inputText.value = ''
  aiStore.setLoading(true)
  streamingContent.value = ''

  let assistantContent = ''
  aiStore.addMessage({ role: 'assistant', content: '' })
  const msgIdx = aiStore.messages.length - 1

  const controller = new AbortController()
  abortController.value = controller

  const contextMessages = aiStore.getContextMessages()
  const mappedMessages = contextMessages.map(m => ({ role: m.role, content: m.content }))
  const isFileChat = linkedFileInfo.value !== null

  const onChunk = (chunk: string) => {
    assistantContent += chunk
    streamingContent.value = assistantContent
    const msg = aiStore.messages[msgIdx]
    if (msg) msg.content = assistantContent
    scrollToBottom()
  }

  const onDone = () => {
    aiStore.setLoading(false)
    streamingContent.value = ''
    abortController.value = null
    aiStore.persistMessages()
  }

  const onError = (err: Error) => {
    const errMsg = err.message || ''
    if (errMsg.includes('敏感词')) {
      ElNotification({
        title: '内容安全提醒',
        message: errMsg,
        type: 'warning',
        duration: 5000,
      })
      const msg = aiStore.messages[msgIdx]
      if (msg) msg.content = `⚠️ ${errMsg}`
    } else if (!assistantContent) {
      const msg = aiStore.messages[msgIdx]
      if (msg) msg.content = `抱歉，请求出错：${errMsg}`
    }
    aiStore.setLoading(false)
    streamingContent.value = ''
    abortController.value = null
    aiStore.persistMessages()
  }

  if (isFileChat) {
    await fetchFileChat(
      {
        messages: mappedMessages,
        model: aiStore.selectedModel,
        fileId: linkedFileInfo.value!.fileId,
      },
      onChunk,
      onDone,
      onError,
      controller.signal,
    )
  } else {
    await fetchStreamChat(
      {
        messages: mappedMessages,
        model: aiStore.selectedModel,
      },
      onChunk,
      onDone,
      onError,
      controller.signal,
    )
  }
}

function handleStop() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
    aiStore.setLoading(false)
    streamingContent.value = ''
    aiStore.persistMessages()
  }
}
</script>

<style scoped>
.ai-assistant {
  position: fixed;
  z-index: 2000;
}

.ai-fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s, box-shadow 0.3s;
  z-index: 2001;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
}

.ai-fab:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.2);
}

.ai-fab.active {
  background: #f5f7fa;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.ai-fab.dragging {
  cursor: grabbing;
  transform: scale(1.1);
  transition: none;
}

.fab-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-fab-avatar {
  width: 32px;
  height: 32px;
}

.ai-panel {
  position: absolute;
  bottom: 72px;
  right: 0;
  width: 440px;
  height: 600px;
  background: #f5f7fa;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-panel-enter-active,
.ai-panel-leave-active {
  transition: all 0.3s ease;
}

.ai-panel-enter-from,
.ai-panel-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
  color: #303133;
  border-bottom: 1px solid #e4e7ed;
}

.ai-file-link-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
  font-size: 12px;
  color: #409eff;
}

.ai-file-link-bar span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 15px;
}

.ai-logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.ai-logo-avatar {
  width: 100%;
  height: 100%;
}

.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-header-actions :deep(.el-select) {
  --el-fill-color-blank: #f5f7fa;
}

.ai-header-actions :deep(.el-select .el-input__wrapper) {
  background: #f5f7fa;
  box-shadow: none;
  color: #303133;
}

.ai-header-actions :deep(.el-select .el-input__inner) {
  color: #303133;
}

.ai-header-actions .link-active {
  color: #409eff !important;
}

.session-title-text {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f7fa;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.ai-messages::-webkit-scrollbar {
  width: 4px;
}

.ai-messages::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 2px;
}

.ai-messages::-webkit-scrollbar-track {
  background: transparent;
}

.ai-welcome {
  text-align: center;
  padding: 40px 20px;
}

.welcome-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.welcome-ai-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.welcome-ai-avatar {
  width: 100%;
  height: 100%;
}

.ai-welcome h4 {
  font-size: 17px;
  color: #303133;
  margin-bottom: 8px;
}

.ai-welcome p {
  font-size: 13px;
  color: #909399;
  margin-bottom: 20px;
}

/* ===== 快捷指令轮播卡片 ===== */
.quick-carousel {
  margin-top: 8px;
  overflow: hidden;
  position: relative;
}

.quick-carousel-loading {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.carousel-track {
  display: flex;
  transition: transform 0.45s cubic-bezier(0.4, 0, 0.2, 1);
}

.carousel-page {
  min-width: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 2px;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.25s;
  position: relative;
  overflow: hidden;
}

.quick-card:hover {
  border-color: var(--card-color, #409eff);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.quick-card::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 60px;
  height: 60px;
  background: var(--card-color, #409eff);
  opacity: 0.04;
  border-radius: 0 0 0 60px;
  pointer-events: none;
}

.card-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: transform 0.2s;
}

.quick-card:hover .card-icon {
  transform: scale(1.1);
}

.card-text {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
  margin-bottom: 2px;
}

.card-desc {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-badge {
  position: absolute;
  top: 6px;
  right: 8px;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  opacity: 0.7;
}

.card-badge.chat {
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
}

.card-badge.route {
  background: rgba(245, 108, 108, 0.1);
  color: #F56C6C;
}

.card-badge.system {
  background: rgba(103, 194, 58, 0.1);
  color: #67C23A;
}

/* 轮播圆点指示器 */
.carousel-dots {
  display: flex;
  justify-content: center;
  gap: 5px;
  margin-top: 10px;
}

.carousel-dot {
  width: 4px;
  height: 4px;
  border-radius: 2px;
  background: #dcdfe6;
  cursor: pointer;
  transition: all 0.3s;
}

.carousel-dot.active {
  width: 14px;
  background: #409eff;
}

.carousel-dot:hover {
  background: #b3d8ff;
}

/* 骨架屏加载 */
.skeleton {
  pointer-events: none;
}

.skeleton-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(90deg, #f0f2f5 25%, #e4e7ed 50%, #f0f2f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-line {
  height: 12px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f2f5 25%, #e4e7ed 50%, #f0f2f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-title {
  width: 60%;
  margin-bottom: 6px;
}

.skeleton-desc {
  width: 80%;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

/* 移除旧 prompt-chip 样式 */
.quick-prompts,
.prompt-chip,
.prompt-chip-avatar {
  display: none;
}

.ai-message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.ai-message.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
}

.msg-avatar.user {
  background: #e8eaed;
  color: #606266;
}

.msg-avatar.assistant {
  background: transparent;
  color: #409eff;
  font-size: 14px;
  overflow: hidden;
}

.msg-avatar-svg {
  width: 100%;
  height: 100%;
}

.msg-content {
  max-width: 80%;
}

.msg-text {
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.ai-message.user .msg-text {
  background: #e6f4ff;
  color: #1f2329;
  border-bottom-right-radius: 4px;
}

.ai-message.assistant .msg-text {
  background: #f0f2f5;
  color: #303133;
  border-bottom-left-radius: 4px;
}

.msg-text.typing {
  display: flex;
  gap: 4px;
  padding: 14px 18px;
  background: #f0f2f5;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #409eff;
  animation: dotBounce 1.4s infinite ease-in-out;
}

.dot:nth-child(1) { animation-delay: 0s; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.msg-actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}

.ai-message:hover .msg-actions,
.msg-actions.visible {
  opacity: 1;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f0f2f5;
  color: #409eff;
  border-color: #409eff;
}

.action-btn.copied {
  background: #f0f9eb;
  color: #67C23A;
  border-color: #67C23A;
}

.delete-action:hover {
  background: #fef0f0;
  color: #F56C6C;
  border-color: #F56C6C;
}

/* 常驻快捷工具条 */
.ai-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px 0;
  background: #fff;
  overflow: hidden;
}

.toolbar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 10px;
  border: none;
  background: #f0f2f5;
  color: #909399;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.toolbar-nav:hover {
  background: #e4e7ed;
  color: #606266;
}

.toolbar-scroll {
  flex: 1;
  display: flex;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding-bottom: 4px;
  -webkit-overflow-scrolling: touch;
}

.toolbar-scroll::-webkit-scrollbar {
  display: none;
}

.toolbar-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 14px;
  border: 1px solid #e4e7ed;
  background: #f5f7fa;
  color: #606266;
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.toolbar-chip:hover {
  background: var(--chip-color, #409eff)15;
  border-color: var(--chip-color, #409eff);
  color: var(--chip-color, #409eff);
}

.ai-input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}

.ai-input-area :deep(.el-textarea__inner) {
  border-radius: 12px;
  font-size: 13px;
  border-color: #e4e7ed;
  background: #f5f7fa;
}

.ai-input-area :deep(.el-textarea__inner:focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
}

.ai-input-area :deep(.el-button--primary) {
  background: linear-gradient(135deg, #409eff, #337ecc);
  border-color: #409eff;
}

.ai-input-area :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #337ecc, #409eff);
  border-color: #337ecc;
}

.markdown-body {
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  word-wrap: break-word;
  overflow-x: auto;
}

.markdown-body :deep(h1) { font-size: 18px; font-weight: 700; margin: 16px 0 10px; border-bottom: 2px solid #e4e7ed; padding-bottom: 6px; color: #303133; }
.markdown-body :deep(h2) { font-size: 16px; font-weight: 700; margin: 14px 0 8px; border-bottom: 1px solid #e4e7ed; padding-bottom: 4px; color: #303133; }
.markdown-body :deep(h3) { font-size: 15px; font-weight: 600; margin: 12px 0 6px; color: #303133; }
.markdown-body :deep(h4) { font-size: 14px; font-weight: 600; margin: 10px 0 4px; color: #303133; }
.markdown-body :deep(p) { margin: 10px 0; line-height: 1.8; }
.markdown-body :deep(p:first-child) { margin-top: 4px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 10px 0; }
.markdown-body :deep(li) { margin: 4px 0; line-height: 1.7; }
.markdown-body :deep(li > p) { margin: 4px 0; }
.markdown-body :deep(code) {
  background: #f0f2f5;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #337ecc;
}
.markdown-body :deep(pre) {
  background: #303133;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}
.markdown-body :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
  font-size: 12px;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding: 4px 12px;
  margin: 8px 0;
  background: #f0f2f5;
  color: #606266;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 12.5px;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid #ebeef5;
  padding: 8px 12px;
  text-align: left;
  word-break: break-word;
}
.markdown-body :deep(th) {
  background: linear-gradient(135deg, #f5f7fa, #ecf1f7);
  font-weight: 600;
  color: #303133;
}
.markdown-body :deep(tr:hover) { background: #f5f7fa; }
.markdown-body :deep(tr:nth-child(even)) { background: #fafbfc; }
.markdown-body :deep(tr:nth-child(even):hover) { background: #f0f2f5; }
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e4e7ed;
  margin: 12px 0;
}
.markdown-body :deep(strong) { font-weight: 600; color: #303133; }
.markdown-body :deep(a) { color: #409eff; text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
</style>
