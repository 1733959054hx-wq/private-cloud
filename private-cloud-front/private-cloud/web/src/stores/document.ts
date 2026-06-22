import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { DirectoryDTO, FileDTO } from '@/api/document'

// 文件状态机类型
export type FileStatus = 'uploading' | 'processing' | 'ready' | 'failed'

// 扩展 FileDTO，添加前端状态字段（用 fileStatus 避免与后端 status:number 冲突）
export interface FileEntity extends FileDTO {
  fileStatus: FileStatus
  _rowKey?: string
  _tempId?: number // 临时ID，用于上传中的文件
}

// 目录ID转字符串key（包含空间信息，避免不同空间缓存冲突）
// spaceKey: "spaceType:spaceId" 格式
function dirKey(dirId: number | null, spaceKey?: string): string {
  const d = dirId === null ? '' : String(dirId)
  return spaceKey ? `${spaceKey}:${d}` : d
}

export const useDocumentStore = defineStore('document', () => {
  // 规范化存储：文件实体映射（Record 替代 Map，Vue 响应式更好，可序列化持久化）
  const filesById = ref<Record<number, FileEntity>>({})
  // 目录文件ID映射：目录ID字符串 -> 文件ID列表
  const dirFileIds = ref<Record<string, number[]>>({})

  // 目录存储
  const directories = ref<DirectoryDTO[]>([])
  const currentDirectoryId = ref<number | null>(null)

  // 当前空间标识（"spaceType:spaceId" 格式），用于缓存隔离
  const currentSpaceKey = ref<string>('')

  // 上传队列和处理队列（不持久化）
  const uploadQueue = ref<FileEntity[]>([])
  const processingQueue = ref<FileEntity[]>([])

  // 缓存版本号，用于 stale-while-revalidate
  const syncVersion = ref<Record<string, number>>({})

  // 每个目录的文件总数缓存（用于分页按钮即时显示，避免等 API 返回才渲染）
  const totalElementsByDir = ref<Record<string, number>>({})

  // 选中的文件ID（不持久化）
  const selectedFileIds = ref<number[]>([])

  // 视图模式（不持久化）
  const viewMode = ref<'table' | 'grid'>('table')

  function setSpaceKey(spaceKey: string) {
    currentSpaceKey.value = spaceKey
  }

  // 当前目录
  const currentDirectory = computed(() => {
    if (currentDirectoryId.value === null) return null
    return directories.value.find(d => d.id === currentDirectoryId.value) || null
  })

  // 当前目录下的文件列表（从规范化存储中获取）
  const currentFiles = computed(() => {
    const key = dirKey(currentDirectoryId.value, currentSpaceKey.value)
    const fileIds = dirFileIds.value[key] || []
    return fileIds
      .map(id => filesById.value[id])
      .filter((f): f is FileEntity => f !== undefined)
  })

  // 当前目录下的子目录
  const currentDirs = computed(() => {
    return directories.value.filter(d => d.parentId === currentDirectoryId.value)
  })

  // 选中的文件对象
  const selectedFiles = computed(() => {
    return selectedFileIds.value
      .map(id => filesById.value[id])
      .filter((f): f is FileEntity => f !== undefined)
  })

  // 设置当前目录
  function setCurrentDirectory(dir: DirectoryDTO | null) {
    currentDirectoryId.value = dir?.id ?? null
  }

  // 增量更新文件（核心：diff patch，只增删变化项，不替换数组引用）
  function patchFiles(files: FileDTO[], dirId: number | null) {
    const key = dirKey(dirId, currentSpaceKey.value)
    const oldIds = dirFileIds.value[key] || []
    const newIdSet = new Set(files.map(f => f.id))

    // 1. 更新或新增文件实体
    for (const file of files) {
      const existing = filesById.value[file.id]
      if (existing) {
        // 更新现有文件，保留状态
        Object.assign(existing, file)
      } else {
        // 添加新文件
        const entity: FileEntity = {
          ...file,
          fileStatus: 'ready',
          _rowKey: `file-${file.id}`
        }
        filesById.value[file.id] = entity
      }
    }

    // 2. 删除后端已不存在的文件实体
    for (const oldId of oldIds) {
      if (!newIdSet.has(oldId)) {
        delete filesById.value[oldId]
      }
    }

    // 3. 增量更新 dirFileIds 数组（只在有变化时重建引用）
    const newIds = files.map(f => f.id)
    
    // 【核心修复】：检查当前目录 key 是否已经初始化在缓存字典中
    const hasKey = key in dirFileIds.value
    
    const isSame =
      hasKey && // 必须已经存在 key，且数组长度和内容完全一致，才判定为相同
      oldIds.length === newIds.length &&
      oldIds.every((id, i) => id === newIds[i])

    if (!isSame) {
      dirFileIds.value[key] = newIds // 确保空文件夹也能正确写入 [] 以占位缓存
    }

    // 4. 更新同步版本
    syncVersion.value[key] = Date.now()
  }

  // 添加上传中的文件
  function addUploadingFile(file: File, dirId: number | null) {
    const tempId = -Date.now()
    const ext = file.name.split('.').pop()?.toLowerCase() || 'unknown'
    const key = dirKey(dirId, currentSpaceKey.value)

    const sk = currentSpaceKey.value ? currentSpaceKey.value.split(':') : []
    const entity: FileEntity = {
      id: tempId,
      fileName: file.name,
      fileSize: file.size,
      fileType: ext,
      filePath: '',
      storageType: '',
      md5: '',
      directoryId: dirId,
      departmentId: null,
      spaceType: sk[0] != null ? Number(sk[0]) : null,
      spaceId: sk[1] != null ? Number(sk[1]) : null,
      uploaderId: null,
      uploaderName: '当前用户',
      version: 1,
      viewCount: 0,
      downloadCount: 0,
      status: 0,
      fileStatus: 'uploading',
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
      _rowKey: `file-temp-${tempId}`,
      _tempId: tempId
    }

    // 添加到文件映射
    filesById.value[tempId] = entity

    // 添加到目录文件ID映射（添加到开头）
    const fileIds = dirFileIds.value[key] || []
    fileIds.unshift(tempId)
    dirFileIds.value[key] = [...fileIds] // 触发响应式

    // 添加到上传队列
    uploadQueue.value.push(entity)

    return tempId
  }

  // 更新上传进度
  function updateUploadProgress(tempId: number, _progress: number) {
    const entity = filesById.value[tempId]
    if (entity) {
      entity.fileStatus = 'uploading'
    }
  }

  // 上传完成，转为处理中
  function markAsProcessing(tempId: number, realFile: FileDTO) {
    const entity = filesById.value[tempId]
    if (entity) {
      // 更新为真实文件数据
      Object.assign(entity, realFile)
      entity.fileStatus = 'processing'
      entity._rowKey = `file-${realFile.id}`

      // 更新文件映射（从临时ID切换到真实ID）
      delete filesById.value[tempId]
      filesById.value[realFile.id] = entity

      // 更新目录文件ID映射
      const dirId = entity.directoryId
      const key = dirKey(dirId, currentSpaceKey.value)
      const fileIds = dirFileIds.value[key] || []
      const idx = fileIds.indexOf(tempId)
      if (idx !== -1) {
        fileIds[idx] = realFile.id
        dirFileIds.value[key] = [...fileIds] // 触发响应式
      }

      // 从上传队列移到处理队列
      uploadQueue.value = uploadQueue.value.filter(f => f._tempId !== tempId)
      processingQueue.value.push(entity)
    }
  }

  // 标记为就绪
  function markAsReady(fileId: number) {
    const entity = filesById.value[fileId]
    if (entity) {
      entity.fileStatus = 'ready'
      processingQueue.value = processingQueue.value.filter(f => f.id !== fileId)
    }
  }

  // 标记为失败
  function markAsFailed(fileId: number) {
    const entity = filesById.value[fileId]
    if (entity) {
      entity.fileStatus = 'failed'
      uploadQueue.value = uploadQueue.value.filter(f => f.id !== fileId)
      processingQueue.value = processingQueue.value.filter(f => f.id !== fileId)
    }
  }

  // 删除文件（从规范化存储中移除）
  function removeFile(fileId: number) {
    const entity = filesById.value[fileId]
    if (entity) {
      // 从目录文件ID映射中移除
      const dirId = entity.directoryId
      const key = dirKey(dirId, currentSpaceKey.value)
      const fileIds = dirFileIds.value[key] || []
      const idx = fileIds.indexOf(fileId)
      if (idx !== -1) {
        fileIds.splice(idx, 1)
        dirFileIds.value[key] = [...fileIds] // 触发响应式
      }

      // 从文件映射中移除
      delete filesById.value[fileId]

      // 从选中列表中移除
      selectedFileIds.value = selectedFileIds.value.filter(id => id !== fileId)
    }
  }

  // 批量删除文件
  function removeFiles(fileIds: number[]) {
    fileIds.forEach(id => removeFile(id))
  }

  // 设置目录列表
  function setDirectories(list: DirectoryDTO[]) {
    directories.value = list
  }

  // 添加目录
  function addDirectory(dir: DirectoryDTO) {
    directories.value.push(dir)
  }

  // 更新目录
  function updateDirectory(dirId: number, updates: Partial<DirectoryDTO>) {
    const dir = directories.value.find(d => d.id === dirId)
    if (dir) {
      Object.assign(dir, updates)
    }
  }

  // 删除目录
  function removeDirectory(dirId: number) {
    directories.value = directories.value.filter(d => d.id !== dirId)
    // 同时删除该目录下的所有文件映射
    const key = dirKey(dirId, currentSpaceKey.value)
    delete dirFileIds.value[key]
  }

  // 移动目录
  function moveDirectory(dirId: number, newParentId: number | null) {
    const dir = directories.value.find(d => d.id === dirId)
    if (dir) {
      dir.parentId = newParentId
    }
  }

  // 切换文件选中状态
  function toggleFileSelection(fileId: number) {
    const idx = selectedFileIds.value.indexOf(fileId)
    if (idx >= 0) {
      selectedFileIds.value.splice(idx, 1)
    } else {
      selectedFileIds.value.push(fileId)
    }
  }

  // 清空选中
  function clearSelection() {
    selectedFileIds.value = []
  }

  // 设置视图模式
  function setViewMode(mode: 'table' | 'grid') {
    viewMode.value = mode
  }

  // 检查缓存是否过期（stale-while-revalidate）
  function isCacheStale(dirId: number | null, maxAge: number = 5 * 60 * 1000): boolean {
    const key = dirKey(dirId, currentSpaceKey.value)
    const lastSync = syncVersion.value[key] || 0
    return Date.now() - lastSync > maxAge
  }

  // 获取缓存数据（只要本地存在就先返回展示，用于实现真正的 stale-while-revalidate）
  function getCachedFiles(dirId: number | null): FileEntity[] | null {
    const key = dirKey(dirId, currentSpaceKey.value)
    if (!(key in dirFileIds.value)) return null
    
    const fileIds = dirFileIds.value[key] || []
    return fileIds
      .map(id => filesById.value[id])
      .filter((f): f is FileEntity => f !== undefined)
  }

  // 强制刷新缓存
  function invalidateCache(dirId: number | null, spaceKey?: string) {
    const key = dirKey(dirId, spaceKey || currentSpaceKey.value)
    delete syncVersion.value[key]
  }

  // 获取目录的文件总数缓存（用于分页按钮即时显示）
  function getCachedTotalElements(dirId: number | null): number | null {
    const key = dirKey(dirId, currentSpaceKey.value)
    return totalElementsByDir.value[key] ?? null
  }

  // 设置目录的文件总数缓存
  function setCachedTotalElements(dirId: number | null, total: number) {
    const key = dirKey(dirId, currentSpaceKey.value)
    totalElementsByDir.value[key] = total
  }

  function removeDirCache(dirId: number | null, spaceKey?: string) {
    const key = dirKey(dirId, spaceKey || currentSpaceKey.value)
    const ids = dirFileIds.value[key]
    if (ids) {
      ids.forEach(id => delete filesById.value[id])
      delete dirFileIds.value[key]
    }
    delete syncVersion.value[key]
  }

  // 更新文件收藏状态
  function updateFileFavorite(fileId: number, isFavorited: boolean) {
    const entity = filesById.value[fileId]
    if (entity) {
      entity.isFavorited = isFavorited
    }
  }

  // 重置整个 store 状态（setup store 不支持 $reset，需手动实现）
  function resetState() {
    filesById.value = {}
    dirFileIds.value = {}
    directories.value = []
    currentDirectoryId.value = null
    currentSpaceKey.value = ''
    uploadQueue.value = []
    processingQueue.value = []
    syncVersion.value = {}
    totalElementsByDir.value = {}
    selectedFileIds.value = []
    viewMode.value = 'table'
  }

  return {
    // 状态
    filesById,
    dirFileIds,
    directories,
    currentDirectoryId,
    currentSpaceKey,
    uploadQueue,
    processingQueue,
    syncVersion,
    totalElementsByDir,
    selectedFileIds,
    viewMode,

    // 计算属性
    currentDirectory,
    currentFiles,
    currentDirs,
    selectedFiles,

    // 方法
    setCurrentDirectory,
    setSpaceKey,
    patchFiles,
    addUploadingFile,
    updateUploadProgress,
    markAsProcessing,
    markAsReady,
    markAsFailed,
    removeFile,
    removeFiles,
    setDirectories,
    addDirectory,
    updateDirectory,
    removeDirectory,
    moveDirectory,
    toggleFileSelection,
    clearSelection,
    setViewMode,
    isCacheStale,
    getCachedFiles,
    invalidateCache,
    getCachedTotalElements,
    setCachedTotalElements,
    removeDirCache,
    updateFileFavorite,
    resetState
  }
}, {
  persist: {
    storage: sessionStorage,
    paths: [
      'filesById',
      'dirFileIds',
      'directories',
      'currentDirectoryId',
      'currentSpaceKey',
      'syncVersion',
      'totalElementsByDir'
    ]
  }
})