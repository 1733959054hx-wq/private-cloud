<template>
  <div class="collaboration-page">
    <div class="page-banner">
      <h2 class="banner-title"><el-icon :size="22"><UserFilled /></el-icon> 协同编辑</h2>
      <p class="banner-desc">实时多人协同编辑，共享文档内容</p>
      <div class="banner-actions">
        <el-button type="primary" @click="showCreateDialog = true" :icon="Plus" size="large">创建协作房间</el-button>
        <el-input v-model="joinRoomId" placeholder="输入房间号加入" size="large" style="width:200px" clearable @keyup.enter="handleJoinRoom">
          <template #prefix><el-icon><Connection /></el-icon></template>
        </el-input>
        <el-button type="success" size="large" @click="handleJoinRoom" :icon="Link">加入</el-button>
      </div>
    </div>

    <!-- 一句话统计 -->
    <div class="info-bar" v-if="!loading">
      <span class="info-text">共 <strong>{{ sessions.length }}</strong> 个协作房间</span>
      <el-button link type="primary" size="small" @click="fetchSessions"><el-icon><Refresh /></el-icon> 刷新</el-button>
    </div>

    <!-- 房间卡片 -->
    <div class="session-grid" v-loading="loading">
      <el-empty v-if="!loading && sessions.length === 0" description="暂无协作房间">
        <el-button type="primary" @click="showCreateDialog = true" :icon="Plus">创建协作房间</el-button>
      </el-empty>

      <el-card v-for="s in sessions" :key="s.id" shadow="hover" class="session-card" @click="goEditor(s)">
        <div class="card-head">
          <div class="card-icon"><el-icon :size="20"><Document /></el-icon></div>
          <span class="card-id">房间号：{{ s.id }}</span>
        </div>
        <h3 class="card-title">{{ s.roomName || '未命名房间' }}</h3>
        <p class="card-hint">创建于 {{ timeAgo(s.createTime) }}</p>
        <div class="card-actions" @click.stop>
          <el-button type="primary" size="small" @click="goEditor(s)">进入协作</el-button>
          <el-button size="small" @click="copyRoomLink(s)"><el-icon><CopyDocument /></el-icon> 复制链接</el-button>
          <el-button type="danger" size="small" @click="handleClose(s.id)">关闭</el-button>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="showCreateDialog" title="创建协作房间" width="440px" center>
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="房间名称">
          <el-input v-model="createForm.docName" placeholder="输入房间名称" maxlength="50" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Link, Plus, UserFilled, Connection, CopyDocument, Document, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMySessions, createCollabSession, closeSession, getSessionById, type CollabSessionVO } from '@/api/collab'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const sessions = ref<CollabSessionVO[]>([])
const showCreateDialog = ref(false)
const createForm = ref({ docName: '' })
const joinRoomId = ref('')

function timeAgo(timeStr: string) {
  if (!timeStr) return ''
  const diff = Date.now() - new Date(timeStr).getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 30) return `${days} 天前`
  return new Date(timeStr).toLocaleDateString('zh-CN')
}

async function fetchSessions() {
  loading.value = true
  try {
    const res = await getMySessions()
    sessions.value = res.data.data || res.data
  } catch { sessions.value = [] } finally { loading.value = false }
}

async function handleCreate() {
  if (!createForm.value.docName.trim()) { ElMessage.warning('请输入文档名称'); return }
  creating.value = true
  try {
    await createCollabSession({ roomName: createForm.value.docName })
    ElMessage.success('协作房间已创建')
    showCreateDialog.value = false; createForm.value.docName = ''; fetchSessions()
  } catch (err: any) { ElMessage.error(err?.message || '创建失败') } finally { creating.value = false }
}

async function handleClose(sessionId: number) {
  try {
    await ElMessageBox.confirm('确定关闭此协作房间？', '确认', { type: 'warning' })
    await closeSession(sessionId)
    ElMessage.success('已关闭'); fetchSessions()
  } catch { /* 取消 */ }
}

function goEditor(session: CollabSessionVO) { router.push(`/collab/editor/${session.id}`) }

function copyRoomLink(session: CollabSessionVO) {
  const url = `${window.location.origin}/collab/editor/${session.id}`
  navigator.clipboard.writeText(url).then(() => ElMessage.success('协作链接已复制')).catch(() => ElMessage.error('复制失败'))
}

async function handleJoinRoom() {
  const id = joinRoomId.value.trim()
  if (!id) { ElMessage.warning('请输入房间号'); return }
  const roomNum = parseInt(id, 10)
  if (isNaN(roomNum) || roomNum <= 0) { ElMessage.warning('请输入有效的房间号'); return }
  try { await getSessionById(roomNum); router.push(`/collab/editor/${roomNum}`) }
  catch { ElMessage.error('房间不存在') }
}

onMounted(fetchSessions)
</script>

<style scoped>
.collaboration-page { max-width: 1100px; margin: 0 auto; padding: 24px 20px 40px }

.page-banner {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  border-radius: 16px; padding: 28px 32px; margin-bottom: 16px;
  display: flex; align-items: flex-start; gap: 24px; flex-wrap: wrap;
}
.banner-title { font-size: 22px; font-weight: 700; color: #fff; display: flex; align-items: center; gap: 8px; margin: 0; flex-shrink: 0 }
.banner-desc { font-size: 13px; color: rgba(200,210,230,0.7); margin: 0; flex: 1; min-width: 200px; align-self: center }
.banner-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0 }

.info-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; padding: 0 4px }
.info-text { font-size: 13px; color: #909399 }
.info-text strong { color: #303133 }

.session-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(310px, 1fr)); gap: 14px; min-height: 120px }

.session-card { cursor: pointer; border-radius: 12px; transition: all 0.25s }
.session-card :deep(.el-card__body) { padding: 20px }
.session-card:hover { border-color: var(--el-color-primary-light-5); transform: translateY(-2px) }

.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px }
.card-icon {
  width: 38px; height: 38px; border-radius: 10px;
  background: var(--el-color-primary-light-9); color: var(--el-color-primary);
  display: flex; align-items: center; justify-content: center;
}
.card-id { font-size: 18px; color: var(--el-color-primary); font-weight: 700; font-family: 'Courier New', monospace; letter-spacing: 1px }
.card-title { font-size: 15px; font-weight: 600; color: #303133; margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.card-hint { font-size: 12px; color: #c0c4cc; margin: 0 0 14px }
.card-actions { display: flex; align-items: center; gap: 6px; padding-top: 12px; border-top: 1px solid #f0f2f5 }

@media (max-width: 768px) {
  .page-banner { padding: 20px; flex-direction: column; gap: 12px }
  .banner-actions { flex-wrap: wrap }
  .session-grid { grid-template-columns: 1fr }
}
</style>
