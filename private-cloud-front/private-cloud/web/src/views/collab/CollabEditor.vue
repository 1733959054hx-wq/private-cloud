<template>
  <div class="collab-editor-page">
    <div class="editor-header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="editor-title">{{ roomName }} - 协同编辑</span>

      <div class="editor-actions">
        <el-tag :type="connStatus.type" effect="plain" size="small">
          <span class="status-dot" :style="{ background: connStatus.color }"></span>
          {{ connStatus.label }}
        </el-tag>
        <el-tag v-if="onlineUsers.length > 0" type="info" effect="plain" size="small">
          {{ onlineUsers.length }} 人在线
        </el-tag>
        <el-tag v-if="saveStatus" :type="saveStatus.type" effect="plain" size="small">
          {{ saveStatus.label }}
        </el-tag>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave" :icon="Plus">
          保存
        </el-button>
        <VoiceInput @recognized="onVoiceRecognized" />
        <el-button size="small" @click="handleCopyLink" :icon="Link">
          复制链接
        </el-button>
      </div>
    </div>

    <div class="editor-body">
      <div class="editor-main">
        <div ref="editorContainer" class="quill-container"></div>
        <div v-if="!connected" class="connecting-overlay">
          <div class="connecting-content">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>正在连接协作服务器...</p>
          </div>
        </div>
      </div>

      <div class="editor-sidebar">
        <div class="sidebar-section">
          <h4><el-icon><UserFilled /></el-icon> 在线用户 <el-tag size="small" type="info" round>{{ onlineUsers.length }}</el-tag></h4>
          <div class="user-list">
            <div v-for="(u, i) in onlineUsers" :key="i" class="user-item">
              <el-avatar :size="28" :style="{ background: u.color }">{{ u.name.charAt(0) }}</el-avatar>
              <span>{{ u.name }}</span>
              <el-tag v-if="u.isMe" size="small" type="success" round>我</el-tag>
            </div>
            <el-empty v-if="onlineUsers.length === 0" description="暂无其他用户" :image-size="32" />
          </div>
        </div>
        <div class="sidebar-section">
          <h4>连接信息</h4>
          <div class="info-list">
            <div class="info-item"><span class="info-label">房间</span><span class="info-value">{{ roomName }}</span></div>
            <div class="info-item"><span class="info-label">ID</span><span class="info-value">#{{ sessionId }}</span></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Link, Loading, Plus, UserFilled } from '@element-plus/icons-vue'
import { QuillEditor } from './QuillEditor'
import { CollaborationService, type CollabInstance } from './collaborationService'
import VoiceInput from '@/components/VoiceInput.vue'
import { getCollabContent, saveCollabContent, getSessionById } from '@/api/collab'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.id as string
const roomName = ref('协同编辑')

// ========== 状态 ==========
const editorContainer = ref<HTMLElement | null>(null)
const connected = ref(false)
const saving = ref(false)
const saveStatus = ref<{ type: string; label: string } | null>(null)
const onlineUsers = ref<Array<{ name: string; color: string; isMe: boolean }>>([])

let quillEditor: QuillEditor | null = null
let collab: CollabInstance | null = null
let autoSaveTimer: ReturnType<typeof setInterval> | null = null
let contentLoaded = false

const currentUser = getCurrentUserName()
const wsServerUrl = `ws://${location.hostname}:1234`
const yjsRoomName = computed(() => `doc-${sessionId}`)
const connStatus = computed(() => ({
  type: connected.value ? 'success' as const : 'warning' as const,
  color: connected.value ? '#67C23A' : '#E6A23C',
  label: connected.value ? '已连接' : '连接中...',
}))

// ========== 生命周期 ==========
onMounted(async () => {
  if (!editorContainer.value) return

  // 1. 获取房间信息
  try {
    const res = await getSessionById(Number(sessionId))
    const session = res.data?.data || res.data
    if (session?.roomName) roomName.value = session.roomName
  } catch { /* 使用默认名称 */ }

  // 2. 初始化 Quill 编辑器（空）
  quillEditor = new QuillEditor(editorContainer.value)
  const quill = quillEditor.getInstance()

  // 3. 绑定 Yjs（先不连接，等注册完监听再激活）
  collab = CollaborationService.setup({
    wsUrl: wsServerUrl,
    docId: yjsRoomName.value,
    userName: currentUser,
    quill,
    autoConnect: false, // 防止连接太快导致 sync 事件丢失
  })

  // 先注册监听
  collab.provider.on('status', (event: { status: string }) => {
    connected.value = event.status === 'connected'
  })
  collab.provider.awareness.on('change', () => updateOnlineUsers())

  // 然后手动连接
  console.log('[Yjs] 手动调用 connect()')
  collab.connect()

  // 等 4 秒后，如果 Quill 还是空的就从 DB 加载
  setTimeout(() => {
    if (contentLoaded) return
    contentLoaded = true
    if (quill.getLength() > 1) {
      console.log(`[Yjs] Quill已有内容(长度=${quill.getLength()})，跳过DB加载`)
      return
    }
    getCollabContent(sessionId).then(res => {
      const html = res.data?.data ?? ''
      if (!html || quill.getLength() > 1) return
      console.log(`[Yjs] 从DB加载HTML: ${html.length} 字符`)
      quill.clipboard.dangerouslyPasteHTML(html)
    }).catch(() => {})
  }, 4000)



  // 5. 每分钟自动保存
  autoSaveTimer = setInterval(() => autoSave(), 60000)
  setTimeout(() => updateOnlineUsers(), 1000)
})

onUnmounted(() => {
  if (autoSaveTimer) clearInterval(autoSaveTimer)
  if (collab && connected.value) {
    const html = quillEditor?.getInstance().root.innerHTML || ''
    if (html && html !== '<p><br></p>') {
      saveCollabContent(sessionId, html).catch(() => {})
    }
  }
  if (collab) { collab.destroy(); collab = null }
  if (quillEditor) { quillEditor.destroy(); quillEditor = null }
})

// ========== 保存 ==========
async function handleSave() {
  if (!collab) return
  saving.value = true
  saveStatus.value = { type: 'warning', label: '保存中...' }
  try {
    const html = quillEditor!.getInstance().root.innerHTML
    console.log(`[Yjs] 保存HTML: session=${sessionId}, 长度=${html.length}`)
    await saveCollabContent(sessionId, html)
    saveStatus.value = { type: 'success', label: '已保存' }
    setTimeout(() => { saveStatus.value = null }, 3000)
  } catch (e) {
    saveStatus.value = { type: 'danger', label: '保存失败' }
    console.error('[Yjs] 保存失败:', e)
  } finally {
    saving.value = false
  }
}

async function autoSave() {
  if (!collab || !connected.value) return
  try {
    const html = quillEditor?.getInstance().root.innerHTML || ''
    if (html && html !== '<p><br></p>') {
      await saveCollabContent(sessionId, html)
    }
  } catch { /* 静默 */ }
}

// ========== 方法 ==========
function updateOnlineUsers() {
  if (!collab) return
  const states = collab.provider.awareness.getStates()
  const users: Array<{ name: string; color: string; isMe: boolean }> = []
  const myClientId = collab.provider.awareness.clientID
  states.forEach((state, clientId) => {
    const user = state.user
    if (user && user.name) {
      users.push({
        name: user.name,
        color: user.color || '#409EFF',
        isMe: clientId === myClientId,
      })
    }
  })
  onlineUsers.value = users
}

function onVoiceRecognized(text: string) {
  if (!quillEditor || !text) return
  const quill = quillEditor.getInstance()
  if (quill) {
    // 在光标位置插入文本
    const range = quill.getSelection(true)
    quill.insertText(range.index, text)
  }
}

function handleCopyLink() {
  const url = `${window.location.origin}/collab/editor/${sessionId}`
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('协作链接已复制')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

function goBack() {
  router.push('/collab')
}

function getCurrentUserName(): string {
  try {
    const info = sessionStorage.getItem('userInfo')
    if (info) {
      const parsed = JSON.parse(info)
      return parsed.realName || parsed.username || '匿名用户'
    }
  } catch { /* ignore */ }
  return '用户' + Math.random().toString(36).slice(2, 6)
}
</script>

<style scoped>
.collab-editor-page { max-width: 1400px; margin: 0 auto; padding: 20px 24px; height: calc(100vh - 80px); display: flex; flex-direction: column; }
.editor-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 10px 16px; background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,0.04); flex-shrink: 0; }
.editor-title { font-size: 15px; font-weight: 600; color: #303133; flex: 1; }
.editor-actions { display: flex; align-items: center; gap: 6px; }
.status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:0.5 } }
.editor-body { display: flex; gap: 16px; flex: 1; min-height: 0; }
.editor-main { flex: 1; background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,0.04); position: relative; overflow: hidden; display: flex; flex-direction: column; }
.quill-container { flex: 1; padding: 0; }
.quill-container :deep(.ql-toolbar) { border-top: none; border-left: none; border-right: none; border-bottom: 1px solid #ebeef5; background: #fafafa; border-radius: 10px 10px 0 0; }
.quill-container :deep(.ql-container) { border: none; font-size: 15px; line-height: 1.8; height: calc(100% - 42px); }
.quill-container :deep(.ql-editor) { padding: 20px 24px; min-height: 400px; }
.connecting-overlay { position: absolute; inset: 0; background: rgba(255,255,255,0.85); display: flex; align-items: center; justify-content: center; z-index: 10; }
.connecting-content { text-align: center; color: #909399; }
.connecting-content p { margin-top: 12px; font-size: 14px; }
.editor-sidebar { width: 240px; flex-shrink: 0; display: flex; flex-direction: column; gap: 12px; }
.sidebar-section { background: #fff; border-radius: 10px; padding: 14px; box-shadow: 0 1px 4px rgba(0,0,0,0.04); }
.sidebar-section h4 { font-size: 13px; font-weight: 600; color: #303133; margin: 0 0 10px; padding-bottom: 8px; border-bottom: 1px solid #f0f2f5; display: flex; align-items: center; gap: 6px; }
.user-list { display: flex; flex-direction: column; gap: 8px; }
.user-item { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; }
.info-list { font-size: 12px; color: #909399; }
.info-item { display: flex; justify-content: space-between; padding: 4px 0; }
.info-label { color: #c0c4cc; }
.info-value { color: #606266; max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 768px) { .editor-sidebar { display: none; } }
</style>
