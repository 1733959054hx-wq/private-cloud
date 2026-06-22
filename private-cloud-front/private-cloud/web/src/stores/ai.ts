import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage } from '@/api/ai'

const MAX_CONTEXT_CHARS = 180000

interface StoredMessage extends ChatMessage {
  id?: number
  timestamp?: string
}

let _storageKey = ''

function loadMessages(): StoredMessage[] {
  try {
    if (!_storageKey) return []
    const raw = sessionStorage.getItem(_storageKey)
    if (!raw) return []
    const messages: StoredMessage[] = JSON.parse(raw)
    return messages.filter(m => m.content && m.content.length > 0)
  } catch {
    return []
  }
}

function saveMessages(messages: StoredMessage[]) {
  if (!_storageKey) return
  const valid = messages.filter(m => m.content && m.content.length > 0)
  sessionStorage.setItem(_storageKey, JSON.stringify(valid))
}

function buildContextMessages(messages: ChatMessage[]): ChatMessage[] {
  let totalChars = 0
  const history: ChatMessage[] = []
  for (let j = 0; j < messages.length; j++) {
    const cLen = (messages[j].content || '').length
    if (totalChars + cLen > MAX_CONTEXT_CHARS && j > 0) break
    totalChars += cLen
    history.push({ role: messages[j].role, content: messages[j].content })
  }
  return history
}

export const useAiStore = defineStore('ai', () => {
  const isOpen = ref(false)
  const messages = ref<StoredMessage[]>([])
  const isLoading = ref(false)
  const selectedModel = ref('glm-4.7-flash')
  const sessionId = ref('default_session')

    const modelOptions = [
    { label: 'MiMo-V2.5-Pro', value: 'mimo-v2.5-pro' },
    { label: 'MiMo-V2.5', value: 'mimo-v2.5' },
    { label: 'DeepSeek-V4-Flash', value: 'deepseek-v4-flash' },
    { label: 'DeepSeek-V4-Pro', value: 'deepseek-v4-pro' },
    { label: 'GLM-4.7-Flash', value: 'glm-4.7-flash' },
  ]

  const hasMessages = computed(() => messages.value.length > 0)

  function toggleOpen() {
    isOpen.value = !isOpen.value
  }

  function setOpen(val: boolean) {
    isOpen.value = val
  }

  function addMessage(msg: StoredMessage) {
    msg.timestamp = new Date().toISOString()
    messages.value.push(msg)
    saveMessages(messages.value)
  }

  function removeMessage(index: number) {
    messages.value.splice(index, 1)
    saveMessages(messages.value)
  }

  function clearMessages() {
    messages.value = []
    saveMessages(messages.value)
  }

  function setLoading(val: boolean) {
    isLoading.value = val
  }

  function setModel(model: string) {
    selectedModel.value = model
  }

  function persistMessages() {
    saveMessages(messages.value)
  }

  function getContextMessages(): ChatMessage[] {
    return buildContextMessages(messages.value)
  }

  /** 按用户初始化聊天历史（不同用户不同 sessionStorage key） */
  function initForUser(userId: number | string) {
    _storageKey = `ai_session_${userId}`
    messages.value = loadMessages()
  }

  /** 登出时清空当前用户的消息（防止切换用户后看到前一个用户的聊天记录） */
  function clearForLogout() {
    messages.value = []
    _storageKey = ''
  }

  return {
    isOpen, messages, isLoading, selectedModel, modelOptions,
    sessionId, hasMessages,
    toggleOpen, setOpen, addMessage, removeMessage, clearMessages,
    setLoading, setModel, persistMessages, getContextMessages,
    initForUser, clearForLogout,
  }
})
