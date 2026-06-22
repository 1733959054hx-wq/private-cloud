<template>
  <div class="generated-docs">
    <div class="docs-header">
      <div class="header-top">
        <div class="header-title">
          <el-icon :size="24" color="#409EFF"><FolderOpened /></el-icon>
          <h2>智能生成文档</h2>
        </div>
        <div class="docs-tabs">
          <el-radio-group v-model="activeTab" @change="handleTabChange">
            <el-radio-button value="department">部门文档</el-radio-button>
            <el-radio-button value="my">我的文档</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </div>

    <div class="docs-body">
      <div v-if="loading" class="docs-loading">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="docs.length === 0" class="docs-empty">
        <el-empty description="暂无生成文档">
          <template #image>
            <el-icon :size="64" color="#C0C4CC"><Document /></el-icon>
          </template>
        </el-empty>
      </div>

      <!-- 三列看板布局 -->
      <div v-else class="kanban-board">
        <!-- 生成中 -->
        <div class="kanban-column col-generating">
          <div class="column-header">
            <div class="column-title">
              <span class="status-dot generating"></span>
              <span>生成中</span>
              <span class="count-badge">{{ generatingDocs.length }}</span>
            </div>
          </div>
          <div class="kanban-cards">
            <div v-for="doc in generatingDocs" :key="doc.id" class="kanban-card generating">
              <div class="card-top">
                <el-icon class="spin-icon" color="#E6A23C"><Loading /></el-icon>
                <div class="doc-name">{{ getDocTitle(doc) }}</div>
              </div>
              <div class="doc-status-line">
                <span class="generating-text">AI 正在撰写...</span>
              </div>
              <div class="card-footer">
                <el-tag v-if="doc.templateName" size="small" effect="plain">{{ doc.templateName }}</el-tag>
                <span class="doc-time">{{ formatDate(doc.createTime) }}</span>
              </div>
            </div>
            <div v-if="generatingDocs.length === 0" class="kanban-empty">
              <el-icon :size="20" color="#C0C4CC"><Loading /></el-icon>
              <span>暂无生成中的文档</span>
            </div>
          </div>
        </div>

        <!-- 已完成 -->
        <div class="kanban-column col-completed">
          <div class="column-header">
            <div class="column-title">
              <span class="status-dot completed"></span>
              <span>已完成</span>
              <span class="count-badge">{{ completedDocs.length }}</span>
            </div>
          </div>
          <div class="kanban-cards">
            <div
              v-for="doc in completedDocs"
              :key="doc.id"
              class="kanban-card completed"
              @click="handlePreview(doc)"
            >
              <div class="card-top">
                <div class="doc-name">{{ getDocTitle(doc) }}</div>
                <el-button link type="danger" size="small" class="delete-btn" @click.stop="handleDelete(doc)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="card-footer">
                <el-tag v-if="doc.model" size="small" type="info" effect="plain">{{ doc.model }}</el-tag>
                <el-tag v-if="doc.templateName" size="small" type="success" effect="plain">{{ doc.templateName }}</el-tag>
                <span class="doc-time">{{ formatDate(doc.createTime) }}</span>
              </div>
              <div class="card-creator" v-if="doc.creatorName">
                <span class="creator-avatar">{{ doc.creatorName.charAt(0) }}</span>
                <span>{{ doc.creatorName }}</span>
              </div>
            </div>
            <div v-if="completedDocs.length === 0" class="kanban-empty">
              <el-icon :size="20" color="#C0C4CC"><CircleCloseFilled /></el-icon>
              <span>暂无已完成的文档</span>
            </div>
          </div>
        </div>

        <!-- 失败 -->
        <div class="kanban-column col-failed">
          <div class="column-header">
            <div class="column-title">
              <span class="status-dot failed"></span>
              <span>生成失败</span>
              <span class="count-badge">{{ failedDocs.length }}</span>
            </div>
          </div>
          <div class="kanban-cards">
            <div v-for="doc in failedDocs" :key="doc.id" class="kanban-card failed">
              <div class="card-top">
                <el-icon color="#F56C6C"><CircleCloseFilled /></el-icon>
                <div class="doc-name">{{ getDocTitle(doc) }}</div>
                <el-button link type="danger" size="small" class="delete-btn" @click.stop="handleDelete(doc)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="doc-status-line">
                <span class="failed-text">生成失败，请重试</span>
              </div>
              <div class="card-footer">
                <el-tag v-if="doc.templateName" size="small" effect="plain">{{ doc.templateName }}</el-tag>
                <span class="doc-time">{{ formatDate(doc.createTime) }}</span>
              </div>
            </div>
            <div v-if="failedDocs.length === 0" class="kanban-empty">
              <el-icon :size="20" color="#C0C4CC"><CircleCloseFilled /></el-icon>
              <span>暂无失败的文档</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" :title="getDocTitle(previewDoc)" width="70%" top="5vh" destroy-on-close>
      <div v-if="previewDoc" class="preview-container">
        <div class="preview-meta">
          <el-tag v-if="previewDoc.model" size="small" type="info">模型: {{ previewDoc.model }}</el-tag>
          <el-tag v-if="previewDoc.templateName" size="small" type="success">{{ previewDoc.templateName }}</el-tag>
          <span v-if="previewDoc.creatorName">创建者: {{ previewDoc.creatorName }}</span>
          <span>{{ formatDate(previewDoc.createTime) }}</span>
        </div>
        <el-divider />
        <div class="preview-content markdown-body" v-html="renderedContent"></div>
      </div>
      <template #footer>
        <el-button @click="handleCopyContent"><el-icon><CopyDocument /></el-icon> 复制内容</el-button>
        <el-button type="success" @click="handleDownloadDoc"><el-icon><Download /></el-icon> 下载为Word</el-button>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'GeneratedDocs' })
import { ref, computed, onMounted, onActivated, onDeactivated } from 'vue'
import { FolderOpened, Document, Delete, CopyDocument, Download, Loading, CircleCloseFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getGeneratedDocs, getMyGeneratedDocs, deleteGeneratedDoc, type GeneratedDoc } from '@/api/ai'
import { renderMarkdown } from '@/utils/markdown'

const activeTab = ref<'department' | 'my'>('department')
const docs = ref<GeneratedDoc[]>([])
const loading = ref(false)
const previewVisible = ref(false)
const previewDoc = ref<GeneratedDoc | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

// 看板分组：按 status 分列
const generatingDocs = computed(() => docs.value.filter(d => d.status === 0))
const completedDocs = computed(() => docs.value.filter(d => d.status === 1))
const failedDocs = computed(() => docs.value.filter(d => d.status === 2))

const renderedContent = computed(() => {
  if (!previewDoc.value?.content) return ''
  return renderMarkdown(previewDoc.value.content)
})

/** 获取文档显示名：优先用 title（从内容提取的标题），兑底用 templateName，再兑底用 fileName（去掉 .md 后缀） */
function getDocTitle(doc: GeneratedDoc | null | undefined): string {
  if (!doc) return '文档预览'
  if (doc.title && doc.title.trim()) return doc.title.trim()
  if (doc.templateName && doc.templateName.trim()) return doc.templateName.trim()
  if (doc.fileName) {
    // 去除 .md/.txt 后缀和 generated_ 前缀
    return doc.fileName.replace(/\.(md|txt)$/i, '').replace(/^generated_[a-z-]+_[a-f0-9]+$/i, doc.templateName || '未命名文档')
  }
  return '未命名文档'
}

async function loadDocs(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = activeTab.value === 'department'
      ? await getGeneratedDocs()
      : await getMyGeneratedDocs()
    docs.value = res.data?.data || []
  } catch {
    if (!silent) docs.value = []
  } finally {
    loading.value = false
  }
}

/** 切换 tab 时静默加载，不显示骨架屏 */
function handleTabChange() {
  loadDocs(true)
}

/** 轮询：有生成中的文档时每 5s 刷新一次 */
function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (generatingDocs.value.length > 0) {
      loadDocs(true)
    }
  }, 5000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function handlePreview(doc: GeneratedDoc) {
  previewDoc.value = doc
  previewVisible.value = true
}

async function handleDelete(doc: GeneratedDoc) {
  try {
    await ElMessageBox.confirm('确定要删除此文档吗？此操作不可恢复。', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteGeneratedDoc(doc.id)
    ElMessage.success('删除成功')
    // 静默刷新，不闪烁
    loadDocs(true)
  } catch {}
}

function handleCopyContent() {
  if (!previewDoc.value?.content) return
  navigator.clipboard.writeText(previewDoc.value.content).then(() => {
    ElMessage.success('已复制到剪贴板')
  })
}

function handleDownloadDoc() {
  if (!previewDoc.value?.content) return
  const htmlContent = renderMarkdown(previewDoc.value.content)
  const docHtml = `
<html xmlns:o="urn:schemas-microsoft-com:office:office"
      xmlns:w="urn:schemas-microsoft-com:office:word"
      xmlns="http://www.w3.org/TR/REC-html40">
<head>
<meta charset="utf-8">
<style>
  body { font-family: 'SimSun', serif; font-size: 12pt; line-height: 1.5; padding: 20px; }
  h1 { font-size: 16pt; font-weight: bold; font-family: 'SimSun', serif; margin: 20px 0 10px; }
  h2 { font-size: 14pt; font-weight: bold; font-family: 'SimSun', serif; margin: 18px 0 8px; }
  h3 { font-size: 12pt; font-weight: bold; font-family: 'SimSun', serif; margin: 14px 0 6px; }
  img + em, img + caption, .figure-caption { font-size: 10.5pt; font-family: 'SimHei', sans-serif; text-align: center; display: block; margin-top: 4px; }
  table { border-collapse: collapse; width: 100%; margin: 10px 0; }
  th, td { border: 1px solid #999; padding: 6px 10px; text-align: left; }
  th { background-color: #f0f0f0; font-weight: bold; }
  p { margin: 6px 0; }
  ul, ol { padding-left: 20px; }
  li { margin: 4px 0; }
  code { background: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-size: 11pt; }
  pre { background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto; }
  pre code { background: none; padding: 0; }
</style>
</head>
<body>${htmlContent}</body>
</html>`
  const blob = new Blob(['\uFEFF' + docHtml], { type: 'application/msword;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${getDocTitle(previewDoc.value)}.doc`
  a.click()
  window.URL.revokeObjectURL(url)
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(() => {
  loadDocs()
  startPolling()
})

onActivated(() => {
  // keep-alive 激活时静默刷新，不闪烁
  loadDocs(true)
  startPolling()
})

onDeactivated(() => {
  stopPolling()
})
</script>

<style scoped>
.generated-docs {
  padding: 24px; height: 100%;
  display: flex; flex-direction: column;
  background: #f8f9fb;
}

/* Header */
.docs-header { margin-bottom: 20px; }
.header-top {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.header-title {
  display: flex; align-items: center; gap: 10px;
}
.header-title h2 {
  font-size: 20px; font-weight: 700; color: #1a1a2e; margin: 0;
}
.docs-tabs { margin-top: 0; }

.docs-body { flex: 1; overflow-y: auto; }
.docs-loading, .docs-empty { padding: 40px 0; }

/* 看板布局 */
.kanban-board {
  display: grid;
  grid-template-columns: 1fr 2fr 1fr;
  gap: 20px;
  padding: 4px 0 20px;
}
.kanban-column {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  display: flex; flex-direction: column; gap: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  min-height: 300px;
}
.col-generating { border-top: 3px solid #E6A23C; }
.col-completed { border-top: 3px solid #67C23A; }
.col-failed { border-top: 3px solid #F56C6C; }

.column-header { padding-bottom: 4px; }
.column-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; font-weight: 600; color: #303133;
}
.status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  display: inline-block;
}
.status-dot.generating { background: #E6A23C; animation: blink 1.5s infinite; }
.status-dot.completed { background: #67C23A; }
.status-dot.failed { background: #F56C6C; }
.count-badge {
  background: #f0f2f5; color: #606266;
  font-size: 12px; font-weight: 500;
  padding: 1px 8px; border-radius: 10px;
  margin-left: 4px;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.kanban-cards { display: flex; flex-direction: column; gap: 10px; flex: 1; }

/* 卡片 */
.kanban-card {
  border-radius: 10px;
  padding: 14px 16px;
  cursor: default;
  transition: all 0.2s ease;
  border: 1px solid #ebeef5;
  background: #fff;
}
.kanban-card.completed {
  cursor: pointer;
}
.kanban-card.completed:hover {
  border-color: #409EFF;
  box-shadow: 0 4px 14px rgba(64,158,255,0.12);
  transform: translateY(-2px);
}
.kanban-card.generating {
  border-color: #faecd8;
  background: linear-gradient(135deg, #fff8ef 0%, #fff 100%);
}
.kanban-card.failed {
  border-color: #fde2e2;
  background: linear-gradient(135deg, #fff5f5 0%, #fff 100%);
}

.card-top {
  display: flex; align-items: flex-start; gap: 8px;
  margin-bottom: 8px;
}
.doc-name {
  font-size: 14px; font-weight: 600; color: #1a1a2e;
  flex: 1; line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}
.delete-btn { opacity: 0; transition: opacity 0.2s; flex-shrink: 0; }
.kanban-card:hover .delete-btn { opacity: 1; }

.doc-status-line {
  margin-bottom: 8px;
}
.generating-text {
  font-size: 12px; color: #E6A23C;
  display: flex; align-items: center; gap: 4px;
}
.failed-text {
  font-size: 12px; color: #F56C6C;
}

.card-footer {
  display: flex; align-items: center; gap: 6px;
  flex-wrap: wrap;
  margin-top: auto;
}
.doc-time { font-size: 12px; color: #b0b3b8; margin-left: auto; }

.card-creator {
  display: flex; align-items: center; gap: 6px;
  margin-top: 8px; font-size: 12px; color: #909399;
}
.creator-avatar {
  width: 20px; height: 20px; border-radius: 50%;
  background: #409EFF; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 600;
}

.kanban-empty {
  text-align: center; padding: 40px 0;
  font-size: 13px; color: #C0C4CC;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}

/* 旋转加载图标 */
.spin-icon {
  animation: spin 1.5s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 预览对话框 */
.preview-container { max-height: 65vh; overflow-y: auto; }
.preview-meta {
  display: flex; align-items: center; gap: 12px;
  font-size: 13px; color: #909399; flex-wrap: wrap;
}
.preview-content { padding: 16px 0; line-height: 1.8; }

@media (max-width: 1180px) {
  .generated-docs { padding: 16px; }
  .kanban-board { grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
}
@media (max-width: 992px) {
  .kanban-board { grid-template-columns: 1fr; }
  .kanban-column { min-height: auto; }
}
@media (max-width: 768px) {
  .header-top { flex-direction: column; align-items: flex-start; gap: 10px; }
  .header-title h2 { font-size: 18px; }
}
</style>
