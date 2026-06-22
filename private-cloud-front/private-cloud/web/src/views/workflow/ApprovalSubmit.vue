<template>
  <div class="approval-submit">
    <div class="page-header">
      <h2 class="page-title"><el-icon :size="22"><DocumentChecked /></el-icon> 申请签章</h2>
      <p class="page-desc">选择可转换为 PDF 的文件，提交电子签章审批</p>
    </div>

    <!-- 可转换文件列表 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="card-header">
          <span>可签章文件</span>
          <div class="card-header-right">
            <el-select v-model="typeFilter" size="small" style="width:120px" placeholder="全部类型">
              <el-option label="全部类型" value="" />
              <el-option v-for="t in fileTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-button size="small" @click="loadFiles" :loading="loading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>
      <div v-loading="loading">
        <el-empty v-if="!loading && filteredFiles.length === 0" description="没有可签章的文件" />
        <el-table v-else :data="filteredFiles" stripe @row-click="handleSelectFile">
          <el-table-column prop="fileName" label="文件名" min-width="250" />
          <el-table-column prop="fileType" label="类型" width="80">
            <template #default="{ row }"><el-tag size="small">{{ row.fileType?.toUpperCase() }}</el-tag></template>
          </el-table-column>
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="uploaderName" label="上传者" width="120" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click.stop="openSubmitDialog(row)">申请签章</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 审批记录 -->
    <el-card shadow="hover" class="section-card" style="margin-top:16px;">
      <template #header>
        <div class="card-header">
          <span>审批记录</span>
          <el-button size="small" @click="loadMyRequests" :loading="reqLoading">刷新</el-button>
        </div>
      </template>
      <div v-loading="reqLoading">
        <el-empty v-if="!reqLoading && myRequests.length === 0" description="暂无审批记录" />
        <el-table v-else :data="myRequests" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="盖章文件" min-width="180">
            <template #default="{ row }">
              <span v-if="row.stampedFileId" class="stamped-link" @click.stop="previewStamped(row.stampedFileId)">
                {{ row.stampedFileName || '已签章文件' }}
              </span>
              <span v-else-if="row.status === 2" class="approved-no-file">{{ row.fileName || '已签章' }}</span>
              <span v-else class="no-pwd">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="提交时间" width="150">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 0"
                type="success" size="small"
                @click.stop="openApproveDialog(row)"
              >通过</el-button>
              <el-button
                v-if="row.stampedFileId"
                type="primary" size="small" link
                @click.stop="previewStamped(row.stampedFileId)"
              >预览</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 提交申请弹窗 -->
    <el-dialog v-model="showSubmitDialog" title="申请电子签章" width="420px">
      <el-form label-width="80px">
        <el-form-item label="文件名"><span class="dialog-info">{{ selectedFile?.fileName }}</span></el-form-item>
        <el-form-item label="审批标题">
          <el-input v-model="approvalTitle" placeholder="请输入审批标题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 审批通过 → 选择目录 -->
    <el-dialog v-model="showApproveDialog" title="审批通过 - 选择保存目录" width="500px">
      <el-form label-width="80px">
        <el-form-item label="审批标题">
          <span class="dialog-info">{{ approveTarget?.title }}</span>
        </el-form-item>
        <el-form-item label="签署人">
          <el-input v-model="signerName" placeholder="签署人姓名" />
        </el-form-item>
        <el-form-item label="保存目录">
          <el-tree
            :data="dirTree"
            :props="{ label: 'dirName', children: 'children' }"
            node-key="id"
            highlight-current
            default-expand-all
            @node-click="handleDirSelect"
          />
          <div class="selected-dir" v-if="selectedDir">
            已选择：<strong>{{ selectedDir.dirName }}</strong> (ID: {{ selectedDir.id }})
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showApproveDialog = false">取消</el-button>
        <el-button type="success" :loading="approving" :disabled="!selectedDir" @click="handleApprove">
          确认通过并盖章
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentChecked, Refresh } from '@element-plus/icons-vue'
import {
  getConvertibleFiles,
  submitApproval,
  getMyApprovalRequests,
  getDirectoryTree,
  approveAndStamp,
  type ConvertibleFile,
} from '@/api/approval'

const router = useRouter()

// ========== 状态 ==========
const loading = ref(false)
const reqLoading = ref(false)
const files = ref<ConvertibleFile[]>([])
const myRequests = ref<any[]>([])
const typeFilter = ref('')

const fileTypeOptions = [
  { label: 'PDF', value: 'pdf' }, { label: 'Word', value: 'docx' },
  { label: 'Word旧版', value: 'doc' }, { label: 'Excel', value: 'xlsx' },
  { label: 'PPT', value: 'pptx' }, { label: '文本', value: 'txt' },
  { label: 'Markdown', value: 'md' }, { label: 'CSV', value: 'csv' },
]

const filteredFiles = computed(() => {
  if (!typeFilter.value) return files.value
  return files.value.filter(f => f.fileType?.toLowerCase() === typeFilter.value)
})

// ========== 提交审批 ==========
const showSubmitDialog = ref(false)
const submitting = ref(false)
const selectedFile = ref<ConvertibleFile | null>(null)
const approvalTitle = ref('')

// ========== 审批通过 ==========
const showApproveDialog = ref(false)
const approving = ref(false)
const approveTarget = ref<any>(null)
const signerName = ref('系统管理员')
const dirTree = ref<any[]>([])
const selectedDir = ref<{ id: number; dirName: string } | null>(null)

onMounted(() => {
  loadFiles()
  loadMyRequests()
})

async function loadFiles() {
  loading.value = true
  try {
    const res = await getConvertibleFiles()
    files.value = res.data.data || []
  } catch { files.value = [] }
  finally { loading.value = false }
}

async function loadMyRequests() {
  reqLoading.value = true
  try {
    const res = await getMyApprovalRequests()
    myRequests.value = res.data.data || []
  } catch { myRequests.value = [] }
  finally { reqLoading.value = false }
}

function handleSelectFile(row: ConvertibleFile) {
  openSubmitDialog(row)
}

function openSubmitDialog(file: ConvertibleFile) {
  selectedFile.value = file
  approvalTitle.value = (file.fileName || '').replace(/\.[^.]+$/, '') + ' - 签章申请'
  showSubmitDialog.value = true
}

async function handleSubmit() {
  if (!selectedFile.value || !approvalTitle.value.trim()) {
    ElMessage.warning('请填写审批标题')
    return
  }
  submitting.value = true
  try {
    await submitApproval({ documentId: selectedFile.value.id, title: approvalTitle.value, type: 'stamp' })
    ElMessage.success('审批请求已提交')
    showSubmitDialog.value = false
    await loadMyRequests()
  } catch { ElMessage.error('提交失败') }
  finally { submitting.value = false }
}

// ========== 审批通过 ==========

async function openApproveDialog(row: any) {
  approveTarget.value = row
  signerName.value = '系统管理员'
  selectedDir.value = null
  // 加载目录树
  try {
    const res = await getDirectoryTree()
    dirTree.value = res.data.data || []
  } catch { dirTree.value = [] }
  showApproveDialog.value = true
}

function handleDirSelect(data: any) {
  selectedDir.value = { id: data.id, dirName: data.dirName }
}

async function handleApprove() {
  if (!approveTarget.value || !selectedDir.value) return
  approving.value = true
  try {
    const res = await approveAndStamp(approveTarget.value.id, selectedDir.value.id, signerName.value)
    const data = res.data.data
    ElMessage.success('审批通过，签章完成')
    showApproveDialog.value = false
    // 更新本地记录
    const found = myRequests.value.find(r => r.id === approveTarget.value.id)
    if (found) {
      found.status = 2
      found.stampedFileId = data.stampedFileId
      found.stampedFileName = data.stampedFileName
    }
  } catch (e: any) { ElMessage.error(e.message || '审批失败') }
  finally { approving.value = false }
}

function previewStamped(fileId: number) {
  router.push(`/preview/${fileId}`)
}

// ========== 工具 ==========

function statusType(s: number): string {
  return ['warning', 'primary', 'success', 'danger', 'info'][s] || 'info'
}
function statusLabel(s: number): string {
  return ['待审批', '审批中', '已通过', '已驳回', '已终止'][s] || '未知'
}
function formatSize(bytes: number): string {
  if (!bytes) return '未知'
  const u = ['B', 'KB', 'MB', 'GB']
  let i = 0, s = bytes
  while (s >= 1024 && i < u.length - 1) { s /= 1024; i++ }
  return `${s.toFixed(i === 0 ? 0 : 1)} ${u[i]}`
}
function formatDate(d: string): string {
  if (!d) return ''
  const t = new Date(d)
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')} ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.approval-submit {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 20px 40px;
}
.page-header {
  padding: 20px 0 16px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px; font-weight: 600; color: #303133; margin: 0 0 4px;
  display: flex; align-items: center; gap: 8px;
}
.page-title .el-icon { color: var(--el-color-primary); }
.page-desc { font-size: 13px; color: #909399; margin: 0; }
.section-card { border-radius: 12px; border: 1px solid #ebeef5; }
.card-header {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 15px; font-weight: 600;
}
.card-header-right { display: flex; align-items: center; gap: 8px; }
.dialog-info { font-size: 14px; color: #303133; }
.no-pwd { color: #c0c4cc; font-size: 13px; }
.approved-no-file { color: #909399; font-size: 13px; }
.stamped-link {
  color: var(--el-color-primary); cursor: pointer; font-size: 13px;
}
.stamped-link:hover { text-decoration: underline; }
.selected-dir {
  margin-top: 8px; font-size: 13px; color: #606266;
  padding: 6px 10px; background: #f0f9eb; border-radius: 6px;
}

@media (max-width: 1180px) {
  .approval-submit {
    padding: 0 12px;
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .section-card {
    padding: 12px;
  }
  .card-header {
    flex-wrap: wrap;
    gap: 8px;
  }
  .page-title {
    font-size: 18px;
  }
}
</style>
