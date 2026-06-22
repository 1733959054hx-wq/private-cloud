import { initUpload, uploadChunk } from '@/api/upload'
import { generateId } from '@/utils'

const CHUNK_SIZE = 5 * 1024 * 1024

export type ChunkStatus = 'pending' | 'uploading' | 'done' | 'error'

export interface ChunkUploadState {
  file: File
  fileId: string
  taskId: number
  totalChunks: number
  uploadedChunks: number
  chunkStatuses: ChunkStatus[]
  percent: number
  md5: string
  md5Progress: number
  speed: number
  elapsed: number
  remaining: number
  status: 'md5' | 'uploading' | 'paused' | 'done' | 'error'
  errorMessage: string
  startTime: number
  lastBytesTime: number
  lastUploadedChunks: number
  fileRecordId: number | null
}

async function calculateMD5(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<string> {
  const SparkMD5 = (await import('spark-md5')).default
  return new Promise((resolve, reject) => {
    const blobSlice = File.prototype.slice || (File.prototype as any).mozSlice || (File.prototype as any).webkitSlice
    const chunkSize = 2 * 1024 * 1024
    const chunks = Math.ceil(file.size / chunkSize)
    let currentChunk = 0
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()

    reader.onload = (e) => {
      spark.append(e.target?.result as ArrayBuffer)
      currentChunk++
      if (onProgress) onProgress(Math.round((currentChunk / chunks) * 100))
      if (currentChunk < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }

    reader.onerror = () => reject(new Error('文件读取失败'))

    function loadNext() {
      const start = currentChunk * chunkSize
      const end = Math.min(start + chunkSize, file.size)
      reader.readAsArrayBuffer(blobSlice.call(file, start, end))
    }

    loadNext()
  })
}

export function createUploadState(file: File): ChunkUploadState {
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  return {
    file,
    fileId: '',
    taskId: 0,
    totalChunks,
    uploadedChunks: 0,
    chunkStatuses: new Array(totalChunks).fill('pending'),
    percent: 0,
    md5: '',
    md5Progress: 0,
    speed: 0,
    elapsed: 0,
    remaining: 0,
    status: 'md5',
    errorMessage: '',
    startTime: 0,
    lastBytesTime: 0,
    lastUploadedChunks: 0,
    fileRecordId: null,
  }
}

export async function executeUpload(
  state: ChunkUploadState,
  directoryId?: number | null,
  departmentId?: number | null,
  abortSignal?: AbortSignal,
  mode?: 'upload' | 'updateVersion',
  updateFileId?: number | null,
  spaceType?: number | null,
  spaceId?: number | null,
): Promise<void> {
  state.status = 'md5'
  state.md5Progress = 0

  try {
    state.md5 = await calculateMD5(state.file, (p) => { state.md5Progress = p })
  } catch {
    state.status = 'error'
    state.errorMessage = 'MD5计算失败'
    return
  }

  if (abortSignal?.aborted) { state.status = 'paused'; return }

  state.status = 'uploading'
  state.startTime = Date.now()
  state.lastBytesTime = Date.now()
  state.lastUploadedChunks = 0

  const totalChunks = state.totalChunks
  const fileId = generateId()
  state.fileId = fileId

  const initForm = new FormData()
  initForm.append('fileId', fileId)
  initForm.append('fileName', state.file.name)
  initForm.append('totalChunks', String(totalChunks))
  initForm.append('fileSize', String(state.file.size))
  if (directoryId) initForm.append('directoryId', String(directoryId))
  if (departmentId) initForm.append('departmentId', String(departmentId))
  if (spaceType != null) initForm.append('spaceType', String(spaceType))
  if (spaceId != null) initForm.append('spaceId', String(spaceId))
  if (mode) initForm.append('mode', mode)
  if (updateFileId) initForm.append('updateFileId', String(updateFileId))

  try {
    const initRes = await initUpload(initForm)
    const initData = initRes.data.data || initRes.data
    state.taskId = initData.taskId
    if (initData.fileRecordId) state.fileRecordId = initData.fileRecordId
  } catch (err) {
    state.status = 'error'
    state.errorMessage = '初始化上传失败'
    return
  }

  for (let i = 0; i < totalChunks; i++) {
    if (abortSignal?.aborted) { state.status = 'paused'; return }

    state.chunkStatuses[i] = 'uploading'

    const start = i * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, state.file.size)
    const chunkBlob = state.file.slice(start, end)

    const chunkForm = new FormData()
    chunkForm.append('fileId', fileId)
    chunkForm.append('chunkIndex', String(i))
    chunkForm.append('chunk', chunkBlob)

    // 分片上传重试机制（最多3次）
    let chunkSuccess = false
    const maxRetries = 3
    for (let retry = 0; retry < maxRetries && !chunkSuccess; retry++) {
      try {
        const chunkRes = await uploadChunk(chunkForm)
        const chunkData = chunkRes.data.data || chunkRes.data
        state.uploadedChunks = chunkData.uploadedChunks || chunkData.receivedChunks || (i + 1)
        state.chunkStatuses[i] = 'done'

        // 如果后端返回了 fileRecordId，保存到 state 中
        if (chunkData.fileRecordId) {
          state.fileRecordId = chunkData.fileRecordId
        }

        const now = Date.now()
        const dt = (now - state.lastBytesTime) / 1000
        if (dt > 0.3 || state.speed === 0) {
          const dChunks = state.uploadedChunks - state.lastUploadedChunks
          if (dChunks > 0 && dt > 0) {
            state.speed = (dChunks * CHUNK_SIZE) / dt
          }
          state.lastBytesTime = now
          state.lastUploadedChunks = state.uploadedChunks
        }

        state.elapsed = (now - state.startTime) / 1000
        if (state.speed > 0) {
          const remainingBytes = (totalChunks - state.uploadedChunks) * CHUNK_SIZE
          state.remaining = remainingBytes / state.speed
        }

        state.percent = Math.round((state.uploadedChunks / totalChunks) * 100)
        chunkSuccess = true
      } catch (err) {
        if (retry < maxRetries - 1) {
          // 等待一段时间后重试
          await new Promise(r => setTimeout(r, 1000 * (retry + 1)))
        } else {
          state.chunkStatuses[i] = 'error'
          state.status = 'error'
          state.errorMessage = `分片 ${i + 1} 上传失败（已重试${maxRetries}次）`
          return
        }
      }
    }
  }

  state.status = 'done'
  state.percent = 100
  state.elapsed = (Date.now() - state.startTime) / 1000
  state.remaining = 0
  state.speed = 0
}

export function formatFileSize(bytes: number): string {
  if (!bytes && bytes !== 0) return '未知'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

export function formatSpeed(bytesPerSec: number): string {
  if (!bytesPerSec || bytesPerSec <= 0) return '0 B/s'
  return formatFileSize(bytesPerSec) + '/s'
}

export function formatTime(seconds: number): string {
  if (!seconds || seconds <= 0) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function getFileTypeColor(fileType: string): string {
  const map: Record<string, string> = {
    pdf: '#F40F02', doc: '#2B579A', docx: '#2B579A',
    xls: '#217346', xlsx: '#217346', ppt: '#D04525', pptx: '#D04525',
    mp4: '#7B2FF7', avi: '#7B2FF7', mov: '#7B2FF7',
    mp3: '#FF6B35', wav: '#FF6B35', png: '#9B59B6',
    jpg: '#9B59B6', jpeg: '#9B59B6', md: '#607D8B', txt: '#607D8B',
  }
  return map[fileType?.toLowerCase()] || '#909399'
}

export function isPreviewable(fileType: string): boolean {
  const types = ['pdf', 'docx', 'mp4', 'mp3', 'wav', 'avi', 'mov', 'webm', 'ogg', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'txt', 'md']
  return types.includes(fileType?.toLowerCase())
}

export function isAudioVideo(fileType: string): boolean {
  const types = ['mp4', 'mp3', 'wav', 'avi', 'mov', 'webm', 'ogg']
  return types.includes(fileType?.toLowerCase())
}

export function isImage(fileType: string): boolean {
  const types = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg']
  return types.includes(fileType?.toLowerCase())
}

export function isVideo(fileType: string): boolean {
  const types = ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'flv', 'm4v', '3gp']
  return types.includes(fileType?.toLowerCase())
}

export function isOcrSupported(fileType: string): boolean {
  const types = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf', 'bmp', 'tiff']
  return types.includes(fileType?.toLowerCase())
}
