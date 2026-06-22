<template>
  <div class="chunk-uploader">
    <div v-if="uploadStates.length === 0 && !uploading" class="upload-drop-zone" @dragover.prevent @drop.prevent="handleDrop">
      <el-upload
        ref="uploadRef"
        drag
        multiple
        :auto-upload="false"
        :on-change="handleFileChange"
        :accept="accept"
        :show-file-list="false"
        class="upload-area"
      >
        <el-icon :size="48" class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持所有文件类型，大文件将自动分片上传（5MB/片）</div>
        </template>
      </el-upload>
    </div>

    <div v-else class="upload-tasks">
      <div v-for="(state, idx) in uploadStates" :key="idx" class="upload-task">
        <div v-if="state.status === 'done'" class="task-done">
          <div class="done-icon"><el-icon :size="36" color="#67C23A"><CircleCheckFilled /></el-icon></div>
          <div class="done-title">上传成功！</div>
          <div class="done-info">
            <div class="done-row"><el-icon><Document /></el-icon> {{ state.file.name }}</div>
            <div class="done-row"><el-icon><Coin /></el-icon> {{ formatFileSize(state.file.size) }} | 耗时 {{ formatTime(state.elapsed) }} | MD5 校验通过</div>
            <div class="done-row"><el-icon><Folder /></el-icon> 存储位置：{{ directoryName || '当前目录' }}</div>
            <div v-if="isOcrSupported(fileExt(state.file.name))" class="done-row ocr-hint">
              <el-icon><Reading /></el-icon> 上传完成后已自动触发文字识别
            </div>
          </div>
          <div class="done-actions">
            <el-button size="small" @click="handleViewFile(state)" v-if="state.fileRecordId"><el-icon><View /></el-icon> 查看文件</el-button>
            <el-button size="small" type="primary" @click="handleClose">关闭</el-button>
          </div>
        </div>

        <div v-else class="task-progress">
          <div class="task-header">
            <div class="task-file-info">
              <el-icon :size="18"><Document /></el-icon>
              <span class="task-filename">{{ state.file.name }}</span>
            </div>
            <div class="task-meta">
              {{ formatFileSize(state.file.size) }} | 分片 5MB/片 | 共 {{ state.totalChunks }} 片
            </div>
          </div>

          <div v-if="state.status === 'md5'" class="md5-section">
            <div class="md5-label"><el-icon><Lock /></el-icon> MD5 校验计算中...</div>
            <el-progress :percentage="state.md5Progress" :stroke-width="8" :show-text="true" status="" />
          </div>

          <div v-else class="progress-section">
            <el-progress
              :percentage="state.percent"
              :stroke-width="12"
              :format="() => `${state.uploadedChunks}/${state.totalChunks}  ${state.percent}%`"
              :status="state.status === 'error' ? 'exception' : undefined"
            />

            <div class="stats-row">
              <div class="stat-item">
                <span class="stat-icon">⚡</span>
                <span class="stat-label">上传速度</span>
                <span class="stat-value">{{ formatSpeed(state.speed) }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-icon">⏱️</span>
                <span class="stat-label">已用时间</span>
                <span class="stat-value">{{ formatTime(state.elapsed) }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-icon">⏳</span>
                <span class="stat-label">预计剩余</span>
                <span class="stat-value">{{ formatTime(state.remaining) }}</span>
              </div>
            </div>

            <div class="chunks-grid">
              <div class="chunks-label">📦 分片进度：</div>
              <div class="chunks-visual">
                <div
                  v-for="(cs, ci) in state.chunkStatuses"
                  :key="ci"
                  class="chunk-cell"
                  :class="cs"
                  :title="`分片 ${ci + 1}: ${chunkStatusText(cs)}`"
                >
                  <span v-if="cs === 'done'">✅</span>
                  <span v-else-if="cs === 'uploading'">⬆️</span>
                  <span v-else-if="cs === 'error'">❌</span>
                  <span v-else>⬜</span>
                </div>
              </div>
              <div class="chunks-numbers">
                <span v-for="(cs, ci) in state.chunkStatuses" :key="ci" class="chunk-num" :class="cs">
                  {{ ci + 1 }}
                </span>
              </div>
            </div>

            <div v-if="state.md5" class="md5-display">
              🔐 MD5 校验: {{ state.md5.substring(0, 16) }}... (✅ 计算完成)
            </div>

            <div v-if="state.status === 'error'" class="error-msg">
              ❌ {{ state.errorMessage }}
            </div>
          </div>

          <div class="task-actions">
            <el-button
              v-if="state.status === 'uploading'"
              size="small"
              @click="handlePause(idx)"
            >
              ⏸️ 暂停
            </el-button>
            <el-button
              v-if="state.status === 'paused'"
              size="small"
              type="primary"
              @click="handleResume(idx)"
            >
              ▶️ 继续
            </el-button>
            <el-button
              v-if="state.status === 'error'"
              size="small"
              type="danger"
              @click="handleCancel(idx)"
            >
              🗑️ 取消上传
            </el-button>
          </div>
        </div>
      </div>

      <div v-if="!allDone" class="add-more">
        <el-button text @click="triggerAddFiles"><el-icon><Plus /></el-icon> 继续添加文件</el-button>
      </div>
    </div>

    <el-upload
      ref="hiddenUploadRef"
      multiple
      :auto-upload="false"
      :on-change="handleFileChange"
      :show-file-list="false"
      style="display:none;"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { UploadFilled, Document, Lock, CircleCheckFilled, Coin, Folder, Reading, View, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  createUploadState,
  executeUpload,
  formatFileSize,
  formatSpeed,
  formatTime,
  isOcrSupported,
  type ChunkUploadState,
} from '@/utils/chunk-upload'

const props = defineProps<{
  directoryId?: number | null
  directoryName?: string
  spaceType?: number | null
  spaceId?: number | null
  mode?: 'upload' | 'updateVersion'
  updateFileId?: number | null
  accept?: string
}>()

const emit = defineEmits<{
  (e: 'complete', state: ChunkUploadState): void
  (e: 'close'): void
}>()

const uploadStates = ref<ChunkUploadState[]>([])
const uploading = ref(false)
const abortControllers = ref<(AbortController | null)[]>([])
const uploadRef = ref()
const hiddenUploadRef = ref()

const allDone = computed(() => uploadStates.value.length > 0 && uploadStates.value.every(s => s.status === 'done'))

function fileExt(name: string): string {
  return name.split('.').pop()?.toLowerCase() || ''
}

function chunkStatusText(cs: string): string {
  const map: Record<string, string> = { pending: '等待中', uploading: '上传中', done: '已完成', error: '失败' }
  return map[cs] || cs
}

function handleFileChange(file: UploadFile) {
  if (!file.raw) return
  // 文件类型一致性校验（仅版本更新模式下生效）
  if (props.mode === 'updateVersion' && props.accept) {
    const acceptedExts = props.accept.split(',').map(s => s.trim().toLowerCase()).filter(s => s.startsWith('.'))
    const currentFileExt = '.' + fileExt(file.name)
    if (!acceptedExts.includes(currentFileExt)) {
      ElMessage.error(`文件类型不匹配，仅支持上传 ${props.accept} 格式的文件`)
      return
    }
  }
  const state = createUploadState(file.raw)
  uploadStates.value.push(state)
  abortControllers.value.push(null)
  startUpload(uploadStates.value.length - 1)
}

function handleDrop(e: DragEvent) {
  const files = e.dataTransfer?.files
  if (!files) return
  for (let i = 0; i < files.length; i++) {
    const state = createUploadState(files[i])
    uploadStates.value.push(state)
    abortControllers.value.push(null)
    startUpload(uploadStates.value.length - 1)
  }
}

async function startUpload(idx: number) {
  uploading.value = true
  const controller = new AbortController()
  abortControllers.value[idx] = controller

  const deptId = props.spaceType != null && props.spaceType > 0 ? props.spaceId : undefined
  await executeUpload(
    uploadStates.value[idx],
    props.directoryId,
    deptId,
    controller.signal,
    props.mode,
    props.updateFileId,
    props.spaceType,
    props.spaceId,
  )

  if (uploadStates.value[idx].status === 'done') {
    emit('complete', uploadStates.value[idx])
  }

  uploading.value = uploadStates.value.some(s => s.status === 'uploading' || s.status === 'md5')
}

function handlePause(idx: number) {
  const controller = abortControllers.value[idx]
  if (controller) {
    controller.abort()
    abortControllers.value[idx] = null
  }
}

function handleResume(idx: number) {
  const state = uploadStates.value[idx]
  if (state.status !== 'paused') return
  const controller = new AbortController()
  abortControllers.value[idx] = controller
  startUpload(idx)
}

function handleCancel(idx: number) {
  const controller = abortControllers.value[idx]
  if (controller) {
    controller.abort()
    abortControllers.value[idx] = null
  }
  uploadStates.value.splice(idx, 1)
  abortControllers.value.splice(idx, 1)
  uploading.value = uploadStates.value.some(s => s.status === 'uploading' || s.status === 'md5')
}

function handleViewFile(state: ChunkUploadState) {
  if (state.fileRecordId) {
    window.open(`/preview/${state.fileRecordId}`, '_blank')
  }
}

function handleClose() {
  emit('close')
}

function triggerAddFiles() {
  hiddenUploadRef.value?.$el?.querySelector('input')?.click()
}

function reset() {
  uploadStates.value = []
  abortControllers.value = []
  uploading.value = false
}

defineExpose({ reset, allDone })
</script>

<style scoped>
.chunk-uploader {
  min-height: 120px;
}

.upload-drop-zone :deep(.el-upload) {
  width: 100%;
}

.upload-drop-zone :deep(.el-upload-dragger) {
  width: 100%;
  padding: 32px 20px;
}

.upload-icon {
  color: #c0c4cc;
  margin-bottom: 8px;
}

.upload-tasks {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-task {
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 16px;
  background: #fafbfc;
}

.task-done {
  text-align: center;
  padding: 12px 0;
}

.done-icon {
  margin-bottom: 8px;
}

.done-title {
  font-size: 16px;
  font-weight: 600;
  color: #67C23A;
  margin-bottom: 12px;
}

.done-info {
  text-align: left;
  max-width: 380px;
  margin: 0 auto 16px;
}

.done-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}

.done-row .el-icon {
  color: #909399;
  flex-shrink: 0;
}

.ocr-hint {
  color: #409eff !important;
  font-weight: 500;
  margin-top: 4px;
  padding: 6px 10px;
  background: #ecf5ff;
  border-radius: 6px;
}

.ocr-hint .el-icon {
  color: #409eff !important;
}

.done-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.task-progress .task-header {
  margin-bottom: 12px;
}

.task-file-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.task-filename {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  word-break: break-all;
}

.task-meta {
  font-size: 12px;
  color: #909399;
  padding-left: 24px;
}

.md5-section {
  margin-bottom: 12px;
}

.md5-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.progress-section :deep(.el-progress) {
  margin-bottom: 12px;
}

.stats-row {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.stat-icon {
  font-size: 14px;
}

.stat-label {
  color: #909399;
}

.stat-value {
  font-weight: 600;
  color: #303133;
}

.chunks-grid {
  margin-bottom: 10px;
}

.chunks-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}

.chunks-visual {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  margin-bottom: 4px;
}

.chunk-cell {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  border-radius: 3px;
  transition: all 0.2s;
}

.chunk-cell.done { background: #f0f9eb; }
.chunk-cell.uploading { background: #ecf5ff; animation: pulse 1s infinite; }
.chunk-cell.error { background: #fef0f0; }
.chunk-cell.pending { background: #f5f7fa; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.chunks-numbers {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.chunk-num {
  width: 20px;
  text-align: center;
  font-size: 9px;
  color: #c0c4cc;
}

.chunk-num.done { color: #67C23A; }
.chunk-num.uploading { color: #409eff; font-weight: 600; }
.chunk-num.error { color: #F56C6C; }

.md5-display {
  font-size: 12px;
  color: #67C23A;
  margin-bottom: 8px;
  font-family: 'Consolas', 'Monaco', monospace;
}

.error-msg {
  font-size: 13px;
  color: #F56C6C;
  margin-bottom: 8px;
}

.task-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 8px;
}

.add-more {
  text-align: center;
  padding: 8px 0;
}
</style>
