import request from '@/api'

export interface SystemActionData {
  actionId: string
  content?: string
  data?: Record<string, any>
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  systemData?: SystemActionData
}

export interface ChatRequest {
  messages: ChatMessage[]
  model?: string
  useRag?: boolean
  fileId?: number
  systemPrompt?: string
  modeLabel?: string
}

export interface ChatResponse {
  response: string
  model: string
}

export interface SummarizeRequest {
  content: string
  model?: string
}

export interface SummarizeResponse {
  summary: string
  model: string
}

export interface DocMetadata {
  id: number
  fileId: number
  tagKey: string
  tagValue: string
  confidence: number
  sourceModel: string
  createTime: string
}

export interface DocTag {
  id: number
  fileId: number
  tagName: string
  tagSource: string
  confidence: number | null
  createTime: string
}

export interface GenerateDocumentRequest {
  templateId: string
  params: Record<string, string>
  model?: string
  referenceContent?: string
}

export interface GenerateDocumentResponse {
  documentUrl: string
  fileName: string
}

export interface QuickActionItem {
  id: string
  title: string
  description: string
  icon: string
  actionType: 'chat' | 'route' | 'system'
  actionValue: string
  sortOrder: number
  color: string
}

export function chat(data: ChatRequest) {
  return request.post<{ code: number; message: string; data: ChatResponse }>('/front/ai/chat', data)
}

export function chatStream(data: ChatRequest): EventSource | null {
  const token = sessionStorage.getItem('token')
  const url = `/api/front/ai/chat/stream`
  return null
}

export async function fetchStreamChat(data: ChatRequest, onChunk: (content: string) => void, onDone: () => void, onError: (err: Error) => void, signal?: AbortSignal, onRefs?: (refs: any[]) => void, onModeLabel?: (label: string) => void) {
  if (data.useRag !== false) {
    return fetchRagChat(data, onChunk, onDone, onError, signal, onRefs, onModeLabel)
  }
  return fetchDirectChat(data, onChunk, onDone, onError, signal)
}

/**
 * 解析结构化 JSON 载荷：{id, delta, status}
 * 兼容旧版纯文本格式，平滑过渡
 */
function parseChunkPayload(raw: string): string {
  const trimmed = raw.trim()
  // 尝试解析 JSON 结构化载荷
  if (trimmed.startsWith('{')) {
    try {
      const obj = JSON.parse(trimmed)
      if (obj && typeof obj.delta === 'string') {
        return obj.delta
      }
    } catch {
      // JSON 解析失败，降级为纯文本
    }
  }
  // 兼容旧版纯文本格式
  return raw
}

/**
 * SSE 自动重连封装：指数退避重试
 * - 检测异常断线（未收到 done 事件）
 * - 延迟 1s、2s、4s 重试，最多 3 次
 * - 用户主动终止（AbortError）不重试
 */
async function fetchStreamWithRetry(
  fetchFn: (signal: AbortSignal) => Promise<{ completed: boolean }>,
  signal: AbortSignal | undefined,
  maxRetries = 3
): Promise<void> {
  let retryCount = 0
  let delay = 1000
  // 外部 signal 用于用户主动终止
  const controller = new AbortController()
  if (signal) {
    signal.addEventListener('abort', () => controller.abort())
  }
  while (true) {
    try {
      const result = await fetchFn(controller.signal)
      if (result.completed) return
      // 流结束但未收到完成标记，判定为异常断线
      if (retryCount < maxRetries) {
        console.warn(`[SSE] 连接异常断开，${delay}ms 后重试... 剩余次数: ${maxRetries - retryCount}`)
        await new Promise(resolve => setTimeout(resolve, delay))
        retryCount++
        delay *= 2
      } else {
        throw new Error('SSE 连接重试次数已用尽')
      }
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        // 用户主动终止，不重试
        return
      }
      if (retryCount < maxRetries) {
        console.warn(`[SSE] 连接错误，${delay}ms 后重试... 剩余次数: ${maxRetries - retryCount}`, err)
        await new Promise(resolve => setTimeout(resolve, delay))
        retryCount++
        delay *= 2
      } else {
        throw err
      }
    }
  }
}

export async function fetchDirectChat(data: ChatRequest, onChunk: (content: string) => void, onDone: () => void, onError: (err: Error) => void, signal?: AbortSignal) {
  try {
    await fetchStreamWithRetry(async (retrySignal) => {
      const token = sessionStorage.getItem('token')
      let completed = false
      try {
        const response = await fetch('/api/front/ai/chat/stream', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify(data),
          signal: retrySignal,
        })
        if (!response.ok || !response.body) {
          throw new Error(`HTTP error! status: ${response.status}`)
        }
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let currentEvent = 'message'
        let dataLines: string[] = []
        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          for (const line of lines) {
            if (line === '' || line === '\r') {
              if (dataLines.length > 0) {
                const payload = dataLines.join('\n')
                if (currentEvent === 'content') {
                  onChunk(parseChunkPayload(payload))
                } else if (currentEvent === 'done') {
                  completed = true
                  onDone()
                  return { completed: true }
                } else if (currentEvent === 'error') {
                  throw new Error(payload)
                }
              }
              currentEvent = 'message'
              dataLines = []
              continue
            }
            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim()
            } else if (line.startsWith('data:')) {
              const part = line.substring(5)
              dataLines.push(part)
            }
          }
        }
        if (currentEvent === 'content' && dataLines.length > 0) {
          onChunk(parseChunkPayload(dataLines.join('\n')))
        }
        return { completed }
      } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') {
          return { completed: true }
        }
        throw err
      }
    }, signal)
  } catch (err) {
    onError(err instanceof Error ? err : new Error(String(err)))
  }
}

export async function fetchRagChat(data: ChatRequest, onChunk: (content: string) => void, onDone: () => void, onError: (err: Error) => void, signal?: AbortSignal, onRefs?: (refs: any[]) => void, onModeLabel?: (label: string) => void) {
  try {
    await fetchStreamWithRetry(async (retrySignal) => {
      const token = sessionStorage.getItem('token')
      let completed = false
      try {
        const response = await fetch('/api/front/ai/chat/rag/stream', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify(data),
          signal: retrySignal,
        })
        if (!response.ok || !response.body) {
          throw new Error(`HTTP error! status: ${response.status}`)
        }
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let currentEvent = 'message'
        let dataLines: string[] = []
        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          for (const line of lines) {
            if (line === '' || line === '\r') {
              if (dataLines.length > 0) {
                const payload = dataLines.join('\n')
                if (currentEvent === 'content') {
                  onChunk(parseChunkPayload(payload))
                } else if (currentEvent === 'refs') {
                  try { onRefs?.(JSON.parse(payload)) } catch {}
                } else if (currentEvent === 'modeLabel') {
                  onModeLabel?.(payload)
                } else if (currentEvent === 'done') {
                  completed = true
                  onDone()
                  return { completed: true }
                } else if (currentEvent === 'error') {
                  throw new Error(payload)
                }
              }
              currentEvent = 'message'
              dataLines = []
              continue
            }
            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim()
            } else if (line.startsWith('data:')) {
              const part = line.substring(5)
              dataLines.push(part)
            }
          }
        }
        if (currentEvent === 'content' && dataLines.length > 0) {
          onChunk(parseChunkPayload(dataLines.join('\n')))
        }
        return { completed }
      } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') {
          return { completed: true }
        }
        throw err
      }
    }, signal)
  } catch (err) {
    onError(err instanceof Error ? err : new Error(String(err)))
  }
}

export function summarize(data: SummarizeRequest) {
  return request.post<{ code: number; message: string; data: SummarizeResponse }>('/front/ai/summarize', data)
}

export function getFileTags(fileId: number) {
  return request.get<{ code: number; message: string; data: DocMetadata[] }>(`/front/ai/tags/${fileId}`)
}

export function triggerTagExtraction(fileId: number) {
  return request.post<{ code: number; message: string; data: { fileId: number; status: string; message: string } }>(`/front/ai/tags/${fileId}`)
}

export function reExtractTags(fileId: number) {
  return request.post<{ code: number; message: string; data: { fileId: number; status: string; message: string } }>(`/front/ai/tags/${fileId}/re-extract`)
}

export function confirmAiTags(fileId: number) {
  return request.post<{ code: number; message: string; data: { fileId: number; status: string; message: string } }>(`/front/ai/tags/${fileId}/confirm`)
}

export function dismissAiTags(fileId: number) {
  return request.post<{ code: number; message: string; data: { fileId: number; status: string; message: string } }>(`/front/ai/tags/${fileId}/dismiss`)
}

export function getDocTags(fileId: number) {
  return request.get<{ code: number; message: string; data: DocTag[] }>(`/front/ai/tags/${fileId}/doc-tags`)
}

export function addDocTag(fileId: number, tagName: string) {
  return request.post<{ code: number; message: string; data: DocTag }>(`/front/ai/tags/${fileId}/doc-tags`, { tagName })
}

export function deleteDocTag(tagId: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/ai/tags/doc-tags/${tagId}`)
}

export function updateDocTag(tagId: number, tagName: string) {
  return request.put<{ code: number; message: string; data: DocTag }>(`/front/ai/tags/doc-tags/${tagId}`, { tagName })
}

export function searchByTagName(tagName: string) {
  return request.get<{ code: number; message: string; data: DocTag[] }>('/front/ai/tags/by-name', { params: { tagName } })
}

export function generateDocument(data: GenerateDocumentRequest) {
  return request.post<{ code: number; message: string; data: GenerateDocumentResponse }>('/front/ai/generate', data, { timeout: 300000 })
}

export interface SubmitTaskResult {
  taskId: string
  docId: number
  mode: 'mq' | 'sse'
}

export function submitGenerateTask(data: GenerateDocumentRequest) {
  return request.post<{ code: number; message: string; data: SubmitTaskResult }>('/front/ai/generate/submit', data)
}

/**
 * 轮询查询任务状态（MQ 模式推荐使用）
 */
export function getGenerateTaskStatus(docId: number) {
  return request.get<{ code: number; message: string; data: {
    docId: number
    status: number  // 0=生成中, 1=成功, 2=失败
    title?: string
    fileName?: string
    failReason?: string
    content?: string
    filePath?: string
  } }>(`/front/ai/generate/status/${docId}`)
}

export async function fetchGenerateStream(
  taskId: string,
  onProgress: (status: string, message: string) => void,
  onDone: (result: any) => void,
  onError: (err: Error) => void
) {
  const token = sessionStorage.getItem('token')
  try {
    const response = await fetch(`/api/front/ai/generate/stream/${taskId}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
    if (!response.body) throw new Error('No response body')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = 'message'

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line === '' || line === '\r') continue

        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          const dataContent = line.substring(5)

          if (currentEvent === 'COMPLETED') {
            try { onDone(JSON.parse(dataContent)) } catch { onDone(dataContent) }
            return
          } else if (currentEvent === 'FAILED') {
            onError(new Error(dataContent))
            return
          } else if (currentEvent === 'CHUNK') {
            onProgress('CHUNK', dataContent)
          } else {
            onProgress(currentEvent, dataContent)
          }
        }
      }
    }
  } catch (err) {
    onError(err instanceof Error ? err : new Error(String(err)))
  }
}

export function extractTemplateFields(data: { templateId: string; referenceContent: string; model?: string }) {
  return request.post<{ code: number; message: string; data: Record<string, string> }>('/front/ai/generate/extract-fields', data)
}

export interface ChatSession {
  sessionId: string
  lastTime: string
  messageCount: number
  firstQuestion?: string
}

export interface ChatHistoryMessage {
  id: number
  userId: number
  sessionId: string
  role: string
  content: string
  model: string | null
  createTime: string
}

export function saveChatMessage(data: { sessionId: string; role: string; content: string; model?: string }) {
  return request.post<{ code: number; message: string; data: ChatHistoryMessage }>('/front/ai/history', data)
}

export function getChatSessions() {
  return request.get<{ code: number; message: string; data: ChatSession[] }>('/front/ai/history/sessions')
}

export function getChatSessionMessages(sessionId: string) {
  return request.get<{ code: number; message: string; data: ChatHistoryMessage[] }>(`/front/ai/history/sessions/${sessionId}`)
}

export function deleteChatSession(sessionId: string) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/ai/history/sessions/${sessionId}`)
}

export function deleteChatMessage(messageId: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/ai/history/messages/${messageId}`)
}

export function clearChatSessionMessages(sessionId: string) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/ai/history/sessions/${sessionId}/clear`)
}

export interface GeneratedDoc {
  id: number
  fileName: string
  title: string | null
  templateId: string | null
  templateName: string | null
  filePath: string | null
  content: string | null
  model: string | null
  departmentId: number | null
  creatorId: number | null
  creatorName: string | null
  status: number
  createTime: string
}

export function getGeneratedDocs() {
  return request.get<{ code: number; message: string; data: GeneratedDoc[] }>('/front/ai/generated-docs')
}

export function getMyGeneratedDocs() {
  return request.get<{ code: number; message: string; data: GeneratedDoc[] }>('/front/ai/generated-docs/my')
}

export function getGeneratedDoc(id: number) {
  return request.get<{ code: number; message: string; data: GeneratedDoc }>(`/front/ai/generated-docs/${id}`)
}

export function deleteGeneratedDoc(id: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/ai/generated-docs/${id}`)
}

export async function fetchFileChat(data: ChatRequest, onChunk: (content: string) => void, onDone: () => void, onError: (err: Error) => void, signal?: AbortSignal) {
  try {
    await fetchStreamWithRetry(async (retrySignal) => {
      const token = sessionStorage.getItem('token')
      let completed = false
      try {
        const response = await fetch('/api/front/ai/chat/file/stream', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify(data),
          signal: retrySignal,
        })
        if (!response.ok || !response.body) {
          throw new Error(`HTTP error! status: ${response.status}`)
        }
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let currentEvent = 'message'
        let dataLines: string[] = []
        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          for (const line of lines) {
            if (line === '' || line === '\r') {
              if (dataLines.length > 0) {
                const payload = dataLines.join('\n')
                if (currentEvent === 'content') {
                  onChunk(parseChunkPayload(payload))
                } else if (currentEvent === 'done') {
                  completed = true
                  onDone()
                  return { completed: true }
                } else if (currentEvent === 'error') {
                  throw new Error(payload)
                }
              }
              currentEvent = 'message'
              dataLines = []
              continue
            }
            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim()
            } else if (line.startsWith('data:')) {
              const part = line.substring(5)
              dataLines.push(part)
            }
          }
        }
        if (currentEvent === 'content' && dataLines.length > 0) {
          onChunk(parseChunkPayload(dataLines.join('\n')))
        }
        return { completed }
      } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') {
          return { completed: true }
        }
        throw err
      }
    }, signal)
  } catch (err) {
    onError(err instanceof Error ? err : new Error(String(err)))
  }
}

// ==================== System Prompt 模板管理 ====================

export interface SystemPromptTemplate {
  id: number
  name: string
  label: string
  promptContent: string
  description: string | null
  isPreset: boolean
  sortOrder: number
  userId: number | null
  createTime: string | null
  updateTime: string | null
}

export function getSystemPrompts() {
  return request.get<{ code: number; data: SystemPromptTemplate[] }>('/front/ai/prompts')
}

export function createSystemPrompt(data: Partial<SystemPromptTemplate>) {
  return request.post<{ code: number; data: SystemPromptTemplate }>('/front/ai/prompts', data)
}

export function updateSystemPrompt(id: number, data: Partial<SystemPromptTemplate>) {
  return request.put<{ code: number; data: SystemPromptTemplate }>(`/front/ai/prompts/${id}`, data)
}

export function deleteSystemPrompt(id: number) {
  return request.delete<{ code: number; message: string }>(`/front/ai/prompts/${id}`)
}

// ==================== 任务三：长文档一键脑图生成 ====================

/**
 * 生成文件的 Markdown 脑图（含页码锚点）
 * 后端基于文件全文内容调用大模型生成，节点附带 <!-- page: N --> 标记
 * 前端使用 Markmap 渲染，点击节点可跳转到对应页
 * @param force 是否强制重新生成（忽略缓存）
 */
export function generateMindmap(fileId: number, model?: string, force = false) {
  return request.post<{ code: number; message: string; data: string }>(`/front/ai/mindmap/${fileId}`, { model, force }, { timeout: 120000 })
}

/** 获取已持久化保存的脑图（不触发 AI 生成） */
export function getSavedMindmap(fileId: number) {
  return request.get<{ code: number; message: string; data: string | null }>(`/front/ai/mindmap/${fileId}/saved`)
}

export interface SubmitMindmapResult {
  fileId: number
  taskId?: string
  mode: 'mq' | 'sync' | 'cached'
  content?: string
}

export interface MindmapTaskStatus {
  fileId: number
  status: 'pending' | 'completed' | 'failed'
  content?: string
  failReason?: string
}

/** 提交脑图生成任务到 RabbitMQ */
export function submitMindmapTask(fileId: number, model?: string, force = false) {
  return request.post<{ code: number; message: string; data: SubmitMindmapResult }>(`/front/ai/mindmap/${fileId}/submit`, { model, force })
}

/** 查询脑图生成任务状态 */
export function getMindmapTaskStatus(fileId: number) {
  return request.get<{ code: number; message: string; data: MindmapTaskStatus }>(`/front/ai/mindmap/${fileId}/status`)
}

// ==================== AI 助手快捷指令卡片 ====================

/** 获取 AI 助手欢迎页快捷指令卡片列表（后端动态下发） */
export function fetchQuickActions() {
  return request.get<{ code: number; message: string; data: QuickActionItem[] }>('/front/ai/quick-actions')
}

/** 执行系统操作查询（查询真实数据库数据） */
export function fetchSystemAction(actionId: string) {
  return request.post<{ code: number; message: string; data: { content: string } }>('/front/ai/system-action', { actionId })
}
