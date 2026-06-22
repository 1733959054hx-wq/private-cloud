<template>
  <div class="share-manage">
    <div class="page-header">
      <h2 class="page-title"><el-icon :size="22"><Link /></el-icon> 分享管理</h2>
      <p class="page-desc">管理您创建的所有分享链接，可查看访问情况、关闭分享</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card active">
        <div class="stat-num">{{ stats.active }}</div>
        <div class="stat-label">✅ 有效</div>
      </div>
      <div class="stat-card exhausted">
        <div class="stat-num">{{ stats.exhausted }}</div>
        <div class="stat-label">🔒 次数耗尽</div>
      </div>
      <div class="stat-card expired">
        <div class="stat-num">{{ stats.expired }}</div>
        <div class="stat-label">⏰ 已过期</div>
      </div>
      <div class="stat-card closed">
        <div class="stat-num">{{ stats.closed }}</div>
        <div class="stat-label">🚫 已关闭</div>
      </div>
    </div>

    <el-card shadow="hover" class="share-card">
      <div class="share-toolbar">
        <el-input v-model="filters.keyword" placeholder="搜索文件名..." clearable size="small" style="width:220px" :prefix-icon="Search" />
        <div style="display:flex;align-items:center;gap:12px">
          <span class="share-count">共 {{ filteredList.length }} 条</span>
          <el-button @click="handleRefresh" :loading="loading" size="small">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </div>

      <!-- 筛选下拉 -->
      <div class="filter-bar">
        <div class="filter-group">
          <span class="filter-label">状态</span>
          <el-select v-model="filters.status" size="small" style="width:110px" @change="() => {}">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">权限</span>
          <el-select v-model="filters.permission" size="small" style="width:110px" @change="() => {}">
            <el-option v-for="opt in permOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">加密</span>
          <el-select v-model="filters.password" size="small" style="width:110px" @change="() => {}">
            <el-option v-for="opt in pwdOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">次数</span>
          <el-select v-model="filters.access" size="small" style="width:110px" @change="() => {}">
            <el-option v-for="opt in accessOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
      </div>

      <div v-loading="loading">
        <el-empty v-if="!loading && filteredList.length === 0" description="没有匹配的分享链接" />

        <el-table v-else :data="filteredList" stripe style="width: 100%" @row-click="handleRowClick">
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">
              <span class="file-name" @click.stop="previewFile(row.fileId)">{{ row.fileName || '未知文件' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="链接状态" width="200">
            <template #default="{ row }">
              <div class="status-cell">
                <el-tag v-if="row.status !== 1" type="danger" size="small" effect="dark">已关闭</el-tag>
                <el-tag v-else-if="isExpired(row)" type="warning" size="small" effect="dark">已过期</el-tag>
                <el-tag v-else-if="isExhausted(row)" type="info" size="small" effect="dark">次数耗尽</el-tag>
                <el-tag v-else type="success" size="small" effect="dark">有效</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="权限" width="80">
            <template #default="{ row }">
              <el-tag :type="row.permissionType === 'DOWNLOAD' ? 'success' : 'info'" size="small">
                {{ row.permissionType === 'DOWNLOAD' ? '可下载' : '只读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="密码" width="100">
            <template #default="{ row }">
              <span v-if="row.password" class="pwd-text">{{ row.password }}</span>
              <span v-else class="no-pwd">未加密</span>
            </template>
          </el-table-column>
          <el-table-column label="有效期" width="150">
            <template #default="{ row }">
              <span v-if="row.expireTime">{{ formatDate(row.expireTime) }}</span>
              <span v-else class="no-pwd">永久</span>
            </template>
          </el-table-column>
          <el-table-column label="访问次数" width="140">
            <template #default="{ row }">
              <div class="access-info">
                <span class="access-text">{{ row.accessCount }} / {{ row.maxAccess || '∞' }}</span>
                <el-progress v-if="row.maxAccess > 0" :percentage="Math.min(100, Math.round(row.accessCount / row.maxAccess * 100))"
                  :status="row.accessCount >= row.maxAccess ? 'exception' : ''"
                  :stroke-width="4" :show-text="false" style="margin-top:4px" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="180">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="previewFile(row.fileId)">
                预览
              </el-button>
              <el-button link type="primary" size="small" @click.stop="copyShareUrl(row)">
                复制
              </el-button>
              <el-button
                v-if="row.status === 1"
                link type="danger" size="small"
                @click.stop="handleClose(row)"
              >关闭</el-button>
              <el-button
                v-else
                link type="danger" size="small"
                @click.stop="handleDelete(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Link, Refresh, Search } from '@element-plus/icons-vue'
import { getAllMyShareLinks, deleteShareLink, forceDeleteShareLink, type ShareLinkVO } from '@/api/share'

const router = useRouter()
const loading = ref(false)
const shareList = ref<ShareLinkVO[]>([])

// 筛选条件
const filters = reactive({
  status: 'all',
  permission: 'all',
  password: 'all',
  access: 'all',
  keyword: '',
})

const statusOptions = [
  { value: 'all', label: '全部' },
  { value: 'active', label: '有效' },
  { value: 'exhausted', label: '次数耗尽' },
  { value: 'expired', label: '已过期' },
  { value: 'closed', label: '已关闭' },
]
const permOptions = [
  { value: 'all', label: '全部' },
  { value: 'DOWNLOAD', label: '可下载' },
  { value: 'VIEW', label: '只读' },
]
const pwdOptions = [
  { value: 'all', label: '全部' },
  { value: 'yes', label: '已加密' },
  { value: 'no', label: '未加密' },
]
const accessOptions = [
  { value: 'all', label: '全部' },
  { value: 'available', label: '有余量' },
  { value: 'exhausted', label: '已用完' },
]

function isExpired(row: ShareLinkVO): boolean {
  return !!row.expireTime && new Date(row.expireTime) < new Date()
}

function isExhausted(row: ShareLinkVO): boolean {
  return row.maxAccess > 0 && row.accessCount >= row.maxAccess
}

const stats = computed(() => {
  const s = { active: 0, exhausted: 0, expired: 0, closed: 0 }
  for (const row of shareList.value) {
    if (row.status !== 1) { s.closed++; continue }
    if (isExpired(row)) { s.expired++; continue }
    if (isExhausted(row)) { s.exhausted++; continue }
    s.active++
  }
  return s
})

const filteredList = computed(() => {
  return shareList.value.filter((row) => {
    // 关键词
    if (filters.keyword && !(row.fileName || '').toLowerCase().includes(filters.keyword.toLowerCase())) return false
    // 状态
    if (filters.status === 'active' && (row.status !== 1 || isExpired(row) || isExhausted(row))) return false
    if (filters.status === 'exhausted' && (row.status !== 1 || isExpired(row) || !isExhausted(row))) return false
    if (filters.status === 'expired' && (!row.expireTime || !isExpired(row))) return false
    if (filters.status === 'closed' && row.status === 1) return false
    // 权限
    if (filters.permission !== 'all' && row.permissionType !== filters.permission) return false
    // 加密
    if (filters.password === 'yes' && !row.password) return false
    if (filters.password === 'no' && row.password) return false
    // 访问次数余量
    if (filters.access === 'available' && row.maxAccess > 0 && row.accessCount >= row.maxAccess) return false
    if (filters.access === 'exhausted' && (row.maxAccess === 0 || row.accessCount < row.maxAccess)) return false
    return true
  })
})

onMounted(() => {
  loadShares()
})

function resetFilters() {
  filters.status = 'all'
  filters.permission = 'all'
  filters.password = 'all'
  filters.access = 'all'
}

async function handleRefresh() {
  resetFilters()
  await loadShares()
}

async function loadShares() {
  loading.value = true
  try {
    const res = await getAllMyShareLinks()
    shareList.value = res.data.data || []
  } catch {
    shareList.value = []
  } finally {
    loading.value = false
  }
}

function handleRowClick(row: ShareLinkVO) {
  previewFile(row.fileId)
}

function previewFile(fileId: number) {
  router.push(`/preview/${fileId}`)
}

function copyShareUrl(row: ShareLinkVO) {
  const url = `${window.location.origin}/s/${row.token}`
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('分享链接已复制')
  }).catch(() => {
    const textarea = document.createElement('textarea')
    textarea.value = url
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('分享链接已复制')
  })
}

async function handleClose(row: ShareLinkVO) {
  try {
    await ElMessageBox.confirm('确定关闭此分享链接？关闭后外部用户将无法访问。', '确认关闭', {
      type: 'warning',
      confirmButtonText: '关闭',
      cancelButtonText: '取消',
    })
    await deleteShareLink(row.id)
    ElMessage.success('分享已关闭')
    row.status = 0
  } catch { /* 取消或失败 */ }
}

async function handleDelete(row: ShareLinkVO) {
  try {
    await ElMessageBox.confirm('确定永久删除此分享记录？删除后无法恢复。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await forceDeleteShareLink(row.id)
    ElMessage.success('分享记录已删除')
    const idx = shareList.value.indexOf(row)
    if (idx >= 0) shareList.value.splice(idx, 1)
  } catch { /* 取消或失败 */ }
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.share-manage {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 20px 40px;
}

.page-header {
  padding: 20px 0 16px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-title .el-icon {
  color: var(--el-color-primary);
}

.page-desc {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.share-card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
}

/* 统计卡片 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px 18px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  display: flex;
  align-items: center;
  gap: 12px;
  border: 2px solid #e8e8e8;
  transition: all 0.25s;
}
.stat-card:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,0.08) }
.stat-card.active { border-color: #4a90d9 }
.stat-card.exhausted { border-color: #b0b0b0 }
.stat-card.expired { border-color: #e6a23c }
.stat-card.closed { border-color: #f56c6c }

.stat-card::before {
  content: ''; width: 4px; height: 30px;
  border-radius: 2px; flex-shrink: 0;
}
.stat-card.active::before { background: #4a90d9 }
.stat-card.exhausted::before { background: #b0b0b0 }
.stat-card.expired::before { background: #e6a23c }
.stat-card.closed::before { background: #f56c6c }

.stat-num { font-size: 26px; font-weight: 700; line-height: 1; }
.stat-card.active .stat-num { color: #4a90d9 }
.stat-card.exhausted .stat-num { color: #606266 }
.stat-card.expired .stat-num { color: #e6a23c }
.stat-card.closed .stat-num { color: #f56c6c }

.stat-label { font-size: 12px; color: #909399; font-weight: 500 }

.share-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.share-count {
  font-size: 13px;
  color: #909399;
}

.access-info { width: 100% }
.access-text { font-size: 12px; color: #606266; font-variant-numeric: tabular-nums }

/* 筛选栏 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 0;
  border-top: 1px solid #f0f2f5;
  border-bottom: 1px solid #f0f2f5;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  min-width: 32px;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.no-pwd {
  color: #c0c4cc;
  font-size: 13px;
}

.pwd-text {
  font-family: monospace;
  font-size: 13px;
  color: #e6a23c;
  letter-spacing: 1px;
}

.file-name {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 14px;
}

.file-name:hover {
  text-decoration: underline;
}

@media (max-width: 1180px) {
  .share-manage {
    padding: 0 12px;
  }
  .filter-bar {
    gap: 10px;
  }
}

@media (max-width: 768px) {
  .stats-row { grid-template-columns: repeat(2, 1fr) }
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .filter-group {
    width: 100%;
  }
  .share-card {
    padding: 12px;
  }
  .page-title {
    font-size: 18px;
  }
}
</style>
