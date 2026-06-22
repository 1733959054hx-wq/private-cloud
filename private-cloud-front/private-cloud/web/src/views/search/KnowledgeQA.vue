<template>
  <div class="knowledge-qa">
    <div class="qa-header">
      <div class="qa-header-left">
        <h2 class="qa-title">
          <div class="qa-logo"><el-icon :size="16"><MagicStick /></el-icon></div>
          知识问答
        </h2>
        <p class="qa-desc">基于企业文档库的智能问答，快速获取精准答案</p>
      </div>
      <div class="qa-header-right">
        <div class="rag-toggle">
          <el-switch v-model="useRag" size="small" />
          <span class="rag-label">文档关联</span>
        </div>
        <el-button
          :type="activePrompt ? 'primary' : 'default'"
          size="default"
          @click="showPromptDrawer = true"
        >
          <el-icon><Setting /></el-icon>
          {{ activePrompt ? activePrompt.label : '对话配置' }}
        </el-button>
        <el-select v-model="selectedModel" size="default" style="width:200px;" placeholder="选择AI模型">
          <el-option v-for="m in aiStore.modelOptions" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </div>
    </div>

    <div class="qa-body">
      <div class="qa-chat-area">
        <div class="qa-messages" ref="messagesRef" @scroll="onMessagesScroll" @click="onMessagesClick">
          <div v-if="chatHistory.length === 0" class="qa-welcome">
            <div class="welcome-icon"><div class="welcome-ai-icon"><el-icon :size="22"><MagicStick /></el-icon></div></div>
            <h3>知识问答助手</h3>
            <p>我可以基于企业文档库回答你的问题</p>
            <div class="quick-questions">
              <div class="question-chip" @click="askQuestion('公司有哪些文档管理规范？')"><el-icon :size="14"><Tickets /></el-icon> 文档管理规范</div>
              <div class="question-chip" @click="askQuestion('项目开发流程是什么？')"><el-icon :size="14"><Refresh /></el-icon> 项目开发流程</div>
              <div class="question-chip" @click="askQuestion('如何申请服务器资源？')"><el-icon :size="14"><Monitor /></el-icon> 服务器资源申请</div>
              <div class="question-chip" @click="askQuestion('新员工入职需要哪些文档？')"><el-icon :size="14"><EditPen /></el-icon> 新员工入职文档</div>
            </div>
          </div>

          <div v-for="(msg, idx) in chatHistory" :key="idx" class="qa-message" :class="msg.role">
            <div v-if="msg.role === 'assistant'" class="msg-avatar assistant"><el-icon :size="14"><MagicStick /></el-icon></div>
            <div v-else class="msg-avatar user">我</div>
            <div class="msg-body">
              <div v-if="msg.role === 'assistant' && (msg.modeLabel || activePrompt?.label || msg.model)" class="msg-mode-label">
                <el-tag v-if="msg.modeLabel || activePrompt?.label" size="small" type="info" effect="plain" round>{{ msg.modeLabel || activePrompt?.label }}</el-tag>
                <el-tag v-if="msg.model" size="small" effect="plain" round class="msg-model-tag">{{ getModelLabel(msg.model) }}</el-tag>
              </div>
              <div v-if="msg.role === 'assistant'" class="msg-text markdown-body">
                <MarkdownRenderer :content="msg.content" :is-streaming="isStreamingMsg(idx)" />
              </div>
              <div v-else class="msg-text">{{ msg.content }}</div>
              <div v-if="msg.stopped" class="msg-stopped-hint">
                <el-icon :size="12"><VideoPause /></el-icon>
                <span>回答已停止</span>
              </div>
              <div v-if="msg.role === 'assistant' && msg.sources?.length" class="msg-sources">
                <div class="sources-title">📎 参考文档（{{ msg.sources.length }} 篇）</div>
                <div v-for="(src, si) in msg.sources" :key="si" class="source-item" @click="goToSource(src)">
                  <el-tag size="small" type="info" round>{{ si + 1 }}</el-tag>
                  <span class="source-name">{{ src.title || src.fileName || `文档 #${src.documentId}` }}</span>
                </div>
              </div>
              <div v-if="msg.role === 'assistant' && msg.content" class="msg-actions">
                <button class="action-btn" @click="handleCopy(msg.content, idx)" :class="{ copied: copiedIdx === idx }">
                  <el-icon :size="13"><CopyDocument /></el-icon>
                  <span>{{ copiedIdx === idx ? '已复制' : '复制' }}</span>
                </button>
                <button class="action-btn" @click="handleRegenerate(idx)" :disabled="isLoading" title="重新回答">
                  <el-icon :size="13"><RefreshRight /></el-icon>
                  <span>重新回答</span>
                </button>
                <button class="action-btn" @click="handleDeleteMessage(idx)" title="删除此条对话">
                  <el-icon :size="13"><Delete /></el-icon>
                  <span>删除</span>
                </button>
              </div>
            </div>
          </div>

          <div v-if="showTypingIndicator" class="qa-message assistant">
            <div class="msg-avatar assistant"><el-icon :size="14"><MagicStick /></el-icon></div>
            <div class="msg-body">
              <div class="thinking-steps">
                <div
                  v-for="(step, si) in thinkingSteps"
                  :key="si"
                  class="thinking-step"
                  :class="{ 'step-active': si === currentThinkingStep, 'step-done': si < currentThinkingStep, 'step-pending': si > currentThinkingStep }"
                >
                  <span class="step-icon">
                    <el-icon v-if="si < currentThinkingStep" :size="14"><Check /></el-icon>
                    <span v-else-if="si === currentThinkingStep" class="step-spinner"></span>
                    <span v-else class="step-circle">{{ si + 1 }}</span>
                  </span>
                  <span class="step-label">{{ step }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="qa-input-area">
          <div class="input-box-wrapper">
            <el-input
              v-model="questionText"
              type="textarea"
              :rows="1"
              :autosize="{ minRows: 1, maxRows: 6 }"
              placeholder="输入您的问题，Enter 发送，Shift + Enter 换行..."
              resize="none"
              @keydown.enter.exact.prevent="handleAsk"
            />
            <div class="input-actions">
              <div class="left-actions">
                <VoiceInput v-model="questionText" @recognized="onVoiceRecognized" />
              </div>
              <div class="right-actions">
                <el-button v-if="isLoading" type="danger" size="small" circle @click="handleStop" title="停止生成">
                  <el-icon><Close /></el-icon>
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="small"
                  circle
                  :disabled="!questionText.trim()"
                  @click="handleAsk"
                >
                  <el-icon><Promotion /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="qa-sidebar">
        <div class="qa-history">
          <div class="sidebar-title">
            <span><el-icon :size="16"><Clock /></el-icon> 历史记录</span>
            <el-button text size="small" @click="startNewChat"><el-icon><Plus /></el-icon> 新对话</el-button>
          </div>
          <!-- 新增：历史搜索框（前端本地过滤，对应设计文档方案 A） -->
          <div class="history-search">
            <el-input
              v-model="searchQuery"
              placeholder="搜索历史对话..."
              size="small"
              clearable
              :prefix-icon="Search"
            />
          </div>
          <div v-if="filteredHistoryList.length === 0" class="empty-history">暂无历史记录</div>
          <div
            v-for="h in filteredHistoryList"
            :key="h.id"
            class="history-item"
            :class="{ active: currentSessionId === h.id }"
            @click="loadHistory(h)"
          >
            <div class="history-question">{{ h.question }}</div>
            <div class="history-meta">
              <span class="history-time">{{ h.time }}</span>
              <el-icon class="history-delete" @click.stop="handleDeleteSession(h)"><Delete /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 提示词工程控制台抽屉 -->
    <el-drawer v-model="showPromptDrawer" title="对话配置" size="380px" direction="rtl">
      <div class="prompt-console">
        <div class="prompt-section">
          <div class="prompt-section-title">预设模式</div>
          <div class="prompt-template-list">
            <div
              v-for="tpl in promptTemplates"
              :key="tpl.id"
              class="prompt-template-item"
              :class="{ active: activePrompt?.id === tpl.id }"
              @click="selectPromptTemplate(tpl)"
            >
              <div class="tpl-header">
                <span class="tpl-name">{{ tpl.label }}</span>
                <el-tag v-if="tpl.isPreset" size="small" type="info" effect="plain">预设</el-tag>
                <el-button v-else type="danger" link size="small" @click.stop="handleDeletePrompt(tpl.id)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="tpl-desc">{{ tpl.description }}</div>
            </div>
          </div>
        </div>

        <div class="prompt-section">
          <div class="prompt-section-title">自定义提示词</div>
          <el-input
            v-model="customSystemPrompt"
            type="textarea"
            :rows="6"
            placeholder="输入自定义 System Prompt，控制 AI 的角色设定、输出格式和知识边界..."
            resize="vertical"
          />
          <div class="prompt-actions">
            <el-button size="small" @click="clearCustomPrompt">清除</el-button>
            <el-button size="small" type="primary" @click="applyCustomPrompt">应用</el-button>
            <el-button size="small" type="success" @click="saveCustomPrompt">保存为模板</el-button>
          </div>
        </div>

        <div v-if="activePrompt" class="prompt-active-info">
          <div class="active-label">当前模式：{{ activePrompt.label }}</div>
          <el-button size="small" type="warning" plain @click="resetPrompt">恢复默认</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 源文档预览抽屉（不打断当前对话上下文） -->
    <el-drawer
      v-model="drawerVisible"
      title="参考文档预览"
      size="50%"
      destroy-on-close
    >
      <iframe
        v-if="activeDocId"
        :src="`/preview/${activeDocId}?embed=true`"
        style="width: 100%; height: 100%; border: none;"
      ></iframe>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onMounted, onActivated, computed } from 'vue'
import { ChatLineSquare, Promotion, MagicStick, Tickets, Refresh, Monitor, EditPen, Clock, Plus, Delete, CopyDocument, Close, Search, RefreshRight, Setting, VideoPause, Check } from '@element-plus/icons-vue'
import { fetchStreamChat, type ChatMessage, saveChatMessage, getChatSessions, getChatSessionMessages, deleteChatSession, type ChatSession, getSystemPrompts, createSystemPrompt, deleteSystemPrompt, type SystemPromptTemplate } from '@/api/ai'
import { useAiStore } from '@/stores/ai'
import { useUserStore } from '@/stores/user'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import VoiceInput from '@/components/VoiceInput.vue'
import { generateId } from '@/utils'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'KnowledgeQA' })

const aiStore = useAiStore()
const userStore = useUserStore()
const selectedModel = ref(aiStore.selectedModel)

// 用户前缀的 storage key 工具函数（区分不同用户的状态）
function userKey(key: string): string {
  return `u${userStore.userId}:${key}`
}

interface QAMessage {
  role: 'user' | 'assistant'
  content: string
  sources?: { documentId: number; title?: string; fileName?: string; score?: number }[]
  modeLabel?: string
  model?: string
  stopped?: boolean
}

interface HistoryItem {
  id: string
  question: string
  time: string
  messages: QAMessage[]
}

// ======== 会话与状态的同步初始化机制 ========
const savedSessionId = localStorage.getItem(userKey('qa_current_session_id'))
const currentSessionId = ref<string>(savedSessionId || generateId())

// 核心修复：同步读取缓存，防止页面加载时闪烁出“欢迎气泡”
const initHistory = (): QAMessage[] => {
  try {
    const cached = sessionStorage.getItem(userKey(`qa_history_${currentSessionId.value}`))
    if (cached) return JSON.parse(cached)
  } catch (e) { /* ignore */ }
  return []
}
const chatHistory = ref<QAMessage[]>(initHistory())

// 同步恢复输入框草稿
const questionText = ref(sessionStorage.getItem(userKey(`qa_input_${currentSessionId.value}`)) || '')

const isLoading = ref(false)
const isLoadingHistory = ref(false)
const messagesRef = ref<HTMLDivElement>()
const historyList = ref<HistoryItem[]>([])
const copiedIdx = ref(-1)
const abortController = ref<AbortController | null>(null)
const useRag = ref(true)
const userScrolledUp = ref(false)

// ======== 状态实时监听与缓存 ========
watch(chatHistory, (newVal) => {
  sessionStorage.setItem(userKey(`qa_history_${currentSessionId.value}`), JSON.stringify(newVal))
}, { deep: true })

watch(questionText, (newVal) => {
  sessionStorage.setItem(userKey(`qa_input_${currentSessionId.value}`), newVal)
})

// 流式回答期间已有 assistant 消息在 chatHistory 中，不再显示底部多余的打字头像
const showTypingIndicator = computed(() =>
  isLoading.value && chatHistory.value[chatHistory.value.length - 1]?.role !== 'assistant'
)

// --- 多步骤思考过程 ---
const thinkingSteps = ref<string[]>(['检索知识库...', '分析文档...', '生成回答...'])
const currentThinkingStep = ref(0)
let thinkingTimer: ReturnType<typeof setInterval> | null = null

function startThinking() {
  thinkingSteps.value = ['检索知识库...', '分析文档...', '生成回答...']
  currentThinkingStep.value = 0
  thinkingTimer = setInterval(() => {
    if (currentThinkingStep.value < thinkingSteps.value.length - 1) {
      currentThinkingStep.value++
    } else {
      clearInterval(thinkingTimer!)
    }
  }, 1200)
}

function stopThinking() {
  if (thinkingTimer) { clearInterval(thinkingTimer); thinkingTimer = null }
  currentThinkingStep.value = thinkingSteps.value.length // all done
}

// 模型值 → 显示标签
function getModelLabel(modelValue: string): string {
  const opt = aiStore.modelOptions.find(m => m.value === modelValue)
  return opt ? opt.label : modelValue
}

// 来源元数据编码/解码（用于持久化到后端）
// 注意：HTML注释标记不能直接写在源码字符串中，否则 esbuild 解析 .vue 时会误认为 HTML 注释
const META_PREFIX = '\n\n<' + '!--__QA_META__:'
const META_SUFFIX = '--' + '>'
function encodeMeta(msg: QAMessage): string {
  const meta: Record<string, unknown> = {}
  if (msg.sources?.length) meta.sources = msg.sources
  if (msg.model) meta.model = msg.model
  if (msg.modeLabel) meta.modeLabel = msg.modeLabel
  if (Object.keys(meta).length === 0) return msg.content
  return msg.content + META_PREFIX + JSON.stringify(meta) + META_SUFFIX
}
function decodeMeta(raw: string): { content: string; meta: Record<string, unknown> } {
  const idx = raw.lastIndexOf(META_PREFIX)
  if (idx === -1) return { content: raw, meta: {} }
  const metaStr = raw.substring(idx + META_PREFIX.length, raw.length - META_SUFFIX.length)
  try {
    return { content: raw.substring(0, idx), meta: JSON.parse(metaStr) }
  } catch {
    return { content: raw, meta: {} }
  }
}

// ==================== 会话配置持久化（按 sessionId 存 localStorage，跨标签页共享） ====================
interface SessionConfig {
  activePromptId: number | null
  activePromptLabel: string
  activePromptContent: string
  useRag: boolean
}

function saveSessionConfig() {
  const config: SessionConfig = {
    activePromptId: activePrompt.value?.id ?? null,
    activePromptLabel: activePrompt.value?.label ?? '',
    activePromptContent: activePrompt.value?.promptContent ?? '',
    useRag: useRag.value,
  }
  localStorage.setItem(userKey(`qa_config_${currentSessionId.value}`), JSON.stringify(config))
}

function restoreSessionConfig(sessionId: string) {
  try {
    const raw = localStorage.getItem(userKey(`qa_config_${sessionId}`))
    if (!raw) {
      activePrompt.value = null
      customSystemPrompt.value = ''
      useRag.value = true
      return
    }
    const config: SessionConfig = JSON.parse(raw)
    if (config.activePromptId !== null && config.activePromptContent) {
      activePrompt.value = {
        id: config.activePromptId,
        name: config.activePromptLabel,
        label: config.activePromptLabel,
        promptContent: config.activePromptContent,
        description: null,
        isPreset: false,
        sortOrder: 0,
        userId: null,
        createTime: null,
        updateTime: null,
      }
      customSystemPrompt.value = config.activePromptContent
    } else {
      activePrompt.value = null
      customSystemPrompt.value = ''
    }
    useRag.value = config.useRag ?? true
  } catch {
    activePrompt.value = null
    customSystemPrompt.value = ''
    useRag.value = true
  }
}

// ==================== 提示词控制台 ====================
const showPromptDrawer = ref(false)
const promptTemplates = ref<SystemPromptTemplate[]>([])
const activePrompt = ref<SystemPromptTemplate | null>(null)
const customSystemPrompt = ref('')

async function loadPromptTemplates() {
  try {
    const res = await getSystemPrompts()
    promptTemplates.value = res.data.data || []
  } catch { /* ignore */ }
}

function selectPromptTemplate(tpl: SystemPromptTemplate) {
  activePrompt.value = tpl
  customSystemPrompt.value = tpl.promptContent
  saveSessionConfig()
  ElMessage.success(`已切换为「${tpl.label}」`)
}

function applyCustomPrompt() {
  if (!customSystemPrompt.value.trim()) {
    activePrompt.value = null
    saveSessionConfig()
    ElMessage.info('已清除自定义提示词，将使用默认模式')
    return
  }
  activePrompt.value = {
    id: -1,
    name: '自定义',
    label: '自定义模式',
    promptContent: customSystemPrompt.value,
    description: '用户自定义提示词',
    isPreset: false,
    sortOrder: 0,
    userId: null,
    createTime: null,
    updateTime: null,
  }
  saveSessionConfig()
  ElMessage.success('已应用自定义提示词')
}

function clearCustomPrompt() {
  customSystemPrompt.value = ''
  activePrompt.value = null
  saveSessionConfig()
}

function resetPrompt() {
  activePrompt.value = null
  customSystemPrompt.value = ''
  saveSessionConfig()
  ElMessage.info('已恢复默认模式')
}

async function saveCustomPrompt() {
  if (!customSystemPrompt.value.trim()) {
    ElMessage.warning('请先输入提示词内容')
    return
  }
  try {
    await createSystemPrompt({
      name: '自定义模板',
      label: '自定义模式',
      promptContent: customSystemPrompt.value,
      description: '用户自建提示词模板',
    })
    ElMessage.success('模板已保存')
    loadPromptTemplates()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleDeletePrompt(id: number) {
  try {
    await deleteSystemPrompt(id)
    ElMessage.success('模板已删除')
    if (activePrompt.value?.id === id) {
      activePrompt.value = null
      customSystemPrompt.value = ''
      saveSessionConfig()
    }
    loadPromptTemplates()
  } catch {
    ElMessage.error('删除失败')
  }
}

// 设计文档：历史搜索关键词（前端本地过滤，方案 A）
const searchQuery = ref('')
// 计算属性：按问题文本过滤历史列表
const filteredHistoryList = computed(() => {
  if (!searchQuery.value.trim()) {
    return historyList.value
  }
  const query = searchQuery.value.toLowerCase()
  return historyList.value.filter(item =>
    (item.question || '').toLowerCase().includes(query)
  )
})

// 设计文档：源文档预览抽屉（不打断当前对话上下文）
const drawerVisible = ref(false)
const activeDocId = ref<number | null>(null)

function onMessagesScroll() {
  if (!messagesRef.value) return
  const el = messagesRef.value
  const threshold = 80
  userScrolledUp.value = el.scrollHeight - el.scrollTop - el.clientHeight > threshold
  
  // 实时记录当前会话的精确像素位置
  sessionStorage.setItem(userKey(`qa_scroll_${currentSessionId.value}`), el.scrollTop.toString())
}

// 拦截消息内所有非 http/https 链接点击（防止 AI 输出的错误链接导致页面跳转）
function onMessagesClick(e: MouseEvent) {
  const anchor = (e.target as HTMLElement).closest('a') as HTMLAnchorElement | null
  if (anchor && anchor.href && !anchor.href.startsWith('http')) {
    e.preventDefault()
    e.stopPropagation()
  }
}

function scrollToBottom() {
  if (userScrolledUp.value) return
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(() => chatHistory.value.length, (newLen, oldLen) => {
  if (!isLoadingHistory.value && newLen > oldLen) {
    scrollToBottom()
  }
})

// useRag 变更时自动持久化到当前会话配置
watch(useRag, () => {
  saveSessionConfig()
})

function askQuestion(q: string) {
  questionText.value = q
  handleAsk()
}

function handleCopy(rawText: string, idx: number) {
  navigator.clipboard.writeText(rawText).then(() => {
    copiedIdx.value = idx
    setTimeout(() => { copiedIdx.value = -1 }, 1800)
  })
}

function onVoiceRecognized(text: string) {
  questionText.value = text
}

async function handleAsk() {
  const q = questionText.value.trim()
  if (!q || isLoading.value) return

  const userMsg: QAMessage = { role: 'user', content: q }
  chatHistory.value.push(userMsg)
  questionText.value = ''
  isLoading.value = true
  startThinking()

  userScrolledUp.value = false
  const controller = new AbortController()
  abortController.value = controller

  saveChatMessage({
    sessionId: currentSessionId.value,
    role: 'user',
    content: q,
    model: selectedModel.value,
  }).catch(() => {})

  let answerContent = ''
  let currentRefs: any[] = []
  const assistantMsg: QAMessage = { role: 'assistant', content: '', model: selectedModel.value }
  chatHistory.value.push(assistantMsg)
  // 用唯一标记锁定流式目标消息，避免闭包 idx 错位
  const streamTargetId = Symbol('streamTarget')
  ;(assistantMsg as any)._streamId = streamTargetId

  // 动态查找流式目标消息的索引
  function findStreamIdx(): number {
    return chatHistory.value.findIndex((m: any) => m._streamId === streamTargetId)
  }

  const aiMessages: ChatMessage[] = chatHistory.value
    .filter(m => (m as any)._streamId !== streamTargetId)
    .filter(m => m.role === 'user' || m.role === 'assistant')
    .map(m => ({
      role: m.role,
      content: m.content,
    }))

  const currentModeLabel = activePrompt.value?.label || ''

  await fetchStreamChat(
    { messages: aiMessages, model: selectedModel.value, useRag: useRag.value, systemPrompt: activePrompt.value?.promptContent || '', modeLabel: currentModeLabel },
    (chunk) => {
      answerContent += chunk
      const idx = findStreamIdx()
      if (idx >= 0) {
        chatHistory.value[idx] = { ...chatHistory.value[idx], content: answerContent }
      }
      scrollToBottom()
    },
    () => {
      stopThinking()
      isLoading.value = false
      abortController.value = null
      const idx = findStreamIdx()
      if (idx >= 0) {
        const updated = { ...chatHistory.value[idx] }
        if (currentModeLabel) updated.modeLabel = currentModeLabel
        delete (updated as any)._streamId
        chatHistory.value[idx] = updated
      }
      saveChatMessage({
        sessionId: currentSessionId.value,
        role: 'assistant',
        content: encodeMeta({ role: 'assistant', content: answerContent, sources: currentRefs, model: selectedModel.value, modeLabel: currentModeLabel }),
        model: selectedModel.value,
      }).catch(() => {})
      loadSessions()
    },
    (err) => {
      if (err.name === 'AbortError') {
        const idx = findStreamIdx()
        if (idx >= 0) {
          const updated: QAMessage = {
            ...chatHistory.value[idx],
            content: answerContent || '',
            stopped: true,
          }
          if (currentModeLabel) updated.modeLabel = currentModeLabel
          delete (updated as any)._streamId
          chatHistory.value[idx] = updated
        }
        saveChatMessage({
          sessionId: currentSessionId.value,
          role: 'assistant',
          content: encodeMeta({ role: 'assistant', content: answerContent || '[回答已停止]', model: selectedModel.value, modeLabel: currentModeLabel }),
          model: selectedModel.value,
        }).catch(() => {})
      } else if (!answerContent) {
        const idx = findStreamIdx()
        if (idx >= 0) {
          chatHistory.value[idx] = { role: 'assistant', content: `抱歉，请求出错：${err.message}` }
        }
      }
      stopThinking()
      isLoading.value = false
      abortController.value = null
    },
    controller.signal,
    (refs) => {
      currentRefs = refs
      const idx = findStreamIdx()
      if (idx >= 0) {
        chatHistory.value[idx] = { ...chatHistory.value[idx], sources: refs }
      }
    },
    (label) => {
      const idx = findStreamIdx()
      if (idx >= 0) {
        chatHistory.value[idx] = { ...chatHistory.value[idx], modeLabel: label }
      }
    }
  )
}

function handleStop() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
    stopThinking()
    isLoading.value = false
  }
}

async function loadSessions() {
  try {
    const res = await getChatSessions()
    const sessions = res.data.data || res.data
    historyList.value = sessions.map((s: ChatSession) => ({
      id: s.sessionId,
      question: s.firstQuestion || '新对话',
      time: s.lastTime ? new Date(s.lastTime).toLocaleString() : '',
      messages: [],
    }))
  } catch { /* ignore */ }
}

async function loadHistory(h: HistoryItem, scrollBehavior: 'bottom' | 'preserve' = 'bottom') {
  if (currentSessionId.value === h.id && chatHistory.value.length > 0) return
  isLoadingHistory.value = true
  currentSessionId.value = h.id
  localStorage.setItem(userKey('qa_current_session_id'), h.id)

  // 切换时立刻尝试使用缓存呈现，做到 0 延迟反馈
  try {
    const cached = sessionStorage.getItem(userKey(`qa_history_${h.id}`))
    if (cached) {
      chatHistory.value = JSON.parse(cached)
      questionText.value = sessionStorage.getItem(userKey(`qa_input_${h.id}`)) || ''
    } else {
      chatHistory.value = []
    }
  } catch (e) { /* ignore */ }

  try {
    const res = await getChatSessionMessages(h.id)
    const msgs = (res.data.data || res.data) as any[]
    const mapped: QAMessage[] = msgs.map((m: any) => {
      const msg: QAMessage = {
        role: m.role as 'user' | 'assistant',
        content: m.content,
        model: m.model || undefined,
      }
      // 解码嵌入在 content 中的元数据（sources、model、modeLabel）
      if (msg.role === 'assistant' && m.content) {
        const { content, meta } = decodeMeta(m.content)
        msg.content = content
        if (meta.sources) msg.sources = meta.sources as QAMessage['sources']
        if (meta.model) msg.model = meta.model as string
        if (meta.modeLabel) msg.modeLabel = meta.modeLabel as string
      }
      return msg
    })
    // 更新为最新数据
    chatHistory.value = mapped
    restoreSessionConfig(h.id)
    isLoadingHistory.value = false
    
    if (scrollBehavior === 'bottom') {
      nextTick(scrollToBottom)
    } else {
      restorePersistedScroll()
    }
  } catch {
    isLoadingHistory.value = false
  }
}

function startNewChat() {
  currentSessionId.value = generateId()
  chatHistory.value = []
  questionText.value = ''
  activePrompt.value = null
  customSystemPrompt.value = ''
  useRag.value = true
  localStorage.setItem(userKey('qa_current_session_id'), currentSessionId.value)
  sessionStorage.removeItem(userKey(`qa_scroll_${currentSessionId.value}`))
}

async function handleDeleteSession(h: HistoryItem) {
  try {
    await deleteChatSession(h.id)
    historyList.value = historyList.value.filter(item => item.id !== h.id)
    // 清理已删会话的缓存
    sessionStorage.removeItem(userKey(`qa_history_${h.id}`))
    sessionStorage.removeItem(userKey(`qa_scroll_${h.id}`))
    sessionStorage.removeItem(userKey(`qa_input_${h.id}`))
    if (currentSessionId.value === h.id) {
      startNewChat()
    }
  } catch { /* ignore */ }
}

function goToSource(src: { documentId: number }) {
  // 设计文档优化：不再跳转路由，改为抽屉 iframe 嵌入预览
  activeDocId.value = src.documentId
  drawerVisible.value = true
}

// 设计文档：重新回答（删除该轮 Q&A，回填问题到输入框并重新触发 handleAsk）
async function handleRegenerate(idx: number) {
  if (isLoading.value) return
  const userMsgIdx = idx - 1
  if (userMsgIdx < 0 || chatHistory.value[userMsgIdx]?.role !== 'user') {
    ElMessage.warning('上一条不是用户消息，无法重新回答')
    return
  }
  const prevPrompt = chatHistory.value[userMsgIdx].content
  // 移除该轮对话（用户问题 + AI回答）
  chatHistory.value.splice(userMsgIdx, 2)
  // 回填问题并触发提问
  questionText.value = prevPrompt
  await handleAsk()
}

// 设计文档：删除单条消息（仅本地，不同步后端）
function handleDeleteMessage(idx: number) {
  if (isLoading.value && chatHistory.value[idx]?.role === 'assistant' && (chatHistory.value[idx] as any)?._streamId) {
    ElMessage.warning('正在生成中，无法删除')
    return
  }
  chatHistory.value.splice(idx, 1)
}

function isStreamingMsg(idx: number): boolean {
  return isLoading.value && chatHistory.value[idx]?.role === 'assistant' && !!(chatHistory.value[idx] as any)?._streamId
}

// ======== 滚动位置的精准追踪与无缝恢复 ========
/**
 * 多次重试机制：确保在 DOM 内元素（特别是 Markdown/代码块）完全撑开后还原滚动高度
 */
function restorePersistedScroll() {
  const stored = sessionStorage.getItem(userKey(`qa_scroll_${currentSessionId.value}`))
  if (!stored) return
  const top = parseInt(stored, 10)
  if (isNaN(top) || top <= 0) return

  let attempts = 0
  const maxAttempts = 12
  const tryRestore = () => {
    attempts++
    if (messagesRef.value) {
      messagesRef.value.scrollTop = top
      if (Math.abs(messagesRef.value.scrollTop - top) < 5 || attempts >= maxAttempts) {
        return
      }
    }
    if (attempts < maxAttempts) {
      requestAnimationFrame(tryRestore)
    }
  }
  requestAnimationFrame(tryRestore)
}

// ======== 生命周期调度 ========
onMounted(async () => {
  // 组件挂载瞬间立刻恢复位置，因为 History 已被同步初始化
  restorePersistedScroll()

  await loadSessions()
  loadPromptTemplates()

  if (savedSessionId && historyList.value.length > 0) {
    const target = historyList.value.find(h => h.id === savedSessionId)
    if (target) {
      loadHistory(target, 'preserve')
    }
  } else if (!savedSessionId) {
    restoreSessionConfig(currentSessionId.value)
  }
})

onActivated(() => {
  loadSessions()
  // Keep-Alive 重新激活时精准归位
  restorePersistedScroll()
})
</script>

<style scoped>
.knowledge-qa {
  max-width: 1200px;
  margin: 0 auto;
  height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
}

.qa-header {
  padding: 20px 24px 12px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.qa-header-left {
  flex: 1;
}

.qa-header-right {
  flex-shrink: 0;
  padding-top: 4px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.rag-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.2s;
}

.rag-toggle:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

.rag-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  user-select: none;
}

.qa-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.qa-logo {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, #409eff, #337ecc);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
}

.qa-desc {
  font-size: 14px;
  color: #909399;
}

.qa-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 0 24px 24px;
  min-height: 0;
}

/* 设计文档：去边框化、软阴影、白底设计 */
.qa-chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* ==================== 滚动条美化 (macOS 风格) ==================== */
.qa-messages::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}

.qa-messages::-webkit-scrollbar-track {
  background: transparent;
}

.qa-messages::-webkit-scrollbar-thumb {
  background: rgba(144, 147, 153, 0.2);
  border-radius: 4px;
  transition: background 0.3s ease;
}

.qa-messages::-webkit-scrollbar-thumb:hover {
  background: rgba(144, 147, 153, 0.6);
}

.qa-messages:not(:hover)::-webkit-scrollbar-thumb {
  background: rgba(144, 147, 153, 0.05);
}

.qa-welcome {
  text-align: center;
  padding: 60px 20px;
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
  background: linear-gradient(135deg, #409eff, #337ecc);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.qa-welcome h3 {
  font-size: 20px;
  color: #303133;
  margin-bottom: 8px;
}

.qa-welcome p {
  font-size: 14px;
  color: #909399;
  margin-bottom: 24px;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.question-chip {
  padding: 8px 18px;
  border-radius: 20px;
  background: #fff;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.question-chip:hover {
  background: #ecf5ff;
  color: #409eff;
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.qa-message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.qa-message.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 700;
}

/* 设计文档：温和的浅色头像，移除深色硬色块 */
.msg-avatar.user {
  background: #e8eaed;
  color: #606266;
}

.msg-avatar.assistant {
  background: #f9f0ff;
  color: #722ed1;
}

.msg-body {
  max-width: 75%;
  min-width: 0;
}

.msg-text {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

/* 用户气泡：浅蓝底，一眼区分 */
.qa-message.user .msg-text {
  background: #e6f4ff;
  color: #1f1f1f;
  border-top-right-radius: 2px;
}

/* AI 气泡：浅灰底，无边框 */
.qa-message.assistant .msg-text {
  background: #f0f2f5;
  color: #1f1f1f;
  border-top-left-radius: 2px;
  box-shadow: none;
  border: none;
}

/* ---- 多步骤思考过程 ---- */
.thinking-steps {
  padding: 16px 20px;
  background: var(--color-surface, #fff);
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: var(--radius-card, 16px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.thinking-step {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  transition: all 0.3s ease;
}
.thinking-step.step-pending {
  opacity: 0.4;
}
.thinking-step.step-active {
  opacity: 1;
}
.thinking-step.step-done {
  opacity: 0.6;
}
.step-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.step-done .step-icon {
  background: rgba(52, 211, 153, 0.15);
  color: #34d399;
}
.step-active .step-icon {
  background: rgba(59, 130, 246, 0.1);
}
.step-pending .step-icon {
  background: var(--color-border, #f0f0f0);
}
.step-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid #3B82F6;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.step-circle {
  font-size: 10px;
  font-weight: 600;
  color: var(--color-text-secondary, #909399);
}
.step-label {
  color: var(--color-text-secondary, #5a6072);
}
.step-active .step-label {
  color: var(--color-text, #1a1a2e);
  font-weight: 500;
}

.msg-text.typing {
  display: flex;
  gap: 4px;
  padding: 16px 20px;
  background: var(--color-surface, #fff);
  border: 1px solid var(--color-border, #e4e7ed);
}

.dot {
  width: 8px;
  height: 8px;
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

.qa-message.assistant:hover .msg-actions {
  opacity: 1;
}

.msg-sources {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.sources-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 600;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s;
}

.source-item:hover {
  color: #409eff;
}

.source-name {
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s;
}

.source-item:hover .source-name {
  color: #409eff;
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
  background: #ecf5ff;
  color: #409eff;
  border-color: #409eff;
}

.action-btn.copied {
  background: #e8f5e9;
  color: #5a9e6f;
  border-color: #5a9e6f;
}

/* 设计文档：精致的多行输入框容器 */
.qa-input-area {
  padding: 16px 24px 24px;
  background: #ffffff;
}

.input-box-wrapper {
  border: 1px solid #dcdfe6;
  border-radius: 12px;
  padding: 8px 12px;
  background: #fff;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
}

.input-box-wrapper:focus-within {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
}

/* 清除 Element Plus 默认 textarea 边框 */
.input-box-wrapper :deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  padding: 4px 0;
  color: #303133;
  font-size: 14px;
  line-height: 1.5;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  border-top: 1px solid #f0f0f0;
  padding-top: 8px;
}

.right-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
}

.qa-sidebar {
  width: 260px;
  flex-shrink: 0;
}

/* 设计文档：历史搜索框 */
.history-search {
  margin-bottom: 12px;
}
.history-search :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.qa-history {
  background: #f5f7fa;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  max-height: calc(100vh - 140px);
  overflow-y: auto;
  border: 1px solid #e4e7ed;
}

/* ==================== 历史列表滚动条美化 ==================== */
.qa-history::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}

.qa-history::-webkit-scrollbar-track {
  background: transparent;
}

.qa-history::-webkit-scrollbar-thumb {
  background: rgba(144, 147, 153, 0.2);
  border-radius: 4px;
  transition: background 0.3s ease;
}

.qa-history::-webkit-scrollbar-thumb:hover {
  background: rgba(144, 147, 153, 0.6);
}

.qa-history:not(:hover)::-webkit-scrollbar-thumb {
  background: rgba(144, 147, 153, 0.05);
}

.sidebar-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-title span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.empty-history {
  text-align: center;
  padding: 24px 0;
  font-size: 13px;
  color: #c0c4cc;
}

.history-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 4px;
}

.history-item:hover {
  background: #ecf5ff;
}

.history-item.active {
  background: #ecf5ff;
  border: 1px solid #409eff;
}

.history-question {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.history-time {
  font-size: 11px;
  color: #c0c4cc;
}

.history-delete {
  font-size: 14px;
  color: #c0c4cc;
  cursor: pointer;
  display: none;
}

.history-item:hover .history-delete {
  display: inline-flex;
  color: #409eff;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  word-wrap: break-word;
  overflow-x: auto;
}

.markdown-body :deep(h1) { font-size: 20px; font-weight: 700; margin: 16px 0 10px; border-bottom: 2px solid #e4e7ed; padding-bottom: 6px; color: #303133; }
.markdown-body :deep(h2) { font-size: 18px; font-weight: 700; margin: 14px 0 8px; border-bottom: 1px solid #e4e7ed; padding-bottom: 4px; color: #303133; }
.markdown-body :deep(h3) { font-size: 16px; font-weight: 600; margin: 12px 0 6px; color: #303133; }
.markdown-body :deep(h4) { font-size: 15px; font-weight: 600; margin: 10px 0 4px; color: #303133; }
.markdown-body :deep(p) { margin: 10px 0; line-height: 1.8; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 10px 0; }
.markdown-body :deep(li) { margin: 4px 0; line-height: 1.7; }
.markdown-body :deep(code) {
  background: #ecf5ff;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 13px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #409eff;
}
.markdown-body :deep(pre) {
  background: #1d1e2c;
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
  font-size: 13px;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding: 4px 12px;
  margin: 8px 0;
  background: #ecf5ff;
  color: #606266;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 13px;
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

@media (max-width: 1180px) {
  .knowledge-qa {
    max-width: 100%;
    padding: 0 12px;
  }
  .qa-sidebar {
    width: 220px;
  }
}

@media (max-width: 992px) {
  .qa-header {
    flex-direction: column;
    gap: 10px;
  }
  .qa-header-right {
    align-self: flex-end;
  }
  .qa-sidebar {
    display: none;
  }
  .qa-body {
    padding: 0 12px;
  }
}

@media (max-width: 768px) {
  .qa-header {
    padding: 14px 12px 8px;
  }
  .qa-title {
    font-size: 18px;
  }
  .qa-desc {
    font-size: 12px;
  }
  .qa-input-area {
    padding: 10px 12px;
  }
}

@media (max-width: 480px) {
  .qa-welcome h3 { font-size: 16px; }
  .qa-welcome p { font-size: 12px; }
  .quick-questions {
    gap: 6px;
  }
  .question-chip {
    padding: 6px 10px;
    font-size: 11px;
  }
  .msg-content {
    max-width: 90%;
  }
}

/* ==================== 提示词控制台样式 ==================== */
.msg-mode-label {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.msg-model-tag {
  color: #909399;
  border-color: #dcdfe6;
  font-size: 11px;
}

.msg-stopped-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 3px 10px;
  border-radius: 12px;
  background: #fdf6ec;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.5;
  user-select: none;
}

.prompt-console {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.prompt-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.prompt-template-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.prompt-template-item {
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.2s;
}

.prompt-template-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

.prompt-template-item.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 1px #409eff;
}

.tpl-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.tpl-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}

.tpl-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.prompt-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.prompt-active-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f0f9eb;
  border-radius: 8px;
  border: 1px solid #e1f3d8;
}

.active-label {
  font-size: 13px;
  color: #67c23a;
  font-weight: 600;
}
</style>
