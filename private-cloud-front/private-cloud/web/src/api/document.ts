import request from '@/api'
import axios from 'axios'

export interface DirectoryDTO {
  id: number
  dirName: string
  parentId: number | null
  departmentId: number | null
  spaceType: number | null
  spaceId: number | null
  sortOrder: number
}

export interface FileDTO {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  filePath: string
  storageType: string
  md5: string
  directoryId: number | null
  directoryName: string | null
  departmentId: number | null
  spaceType: number | null
  spaceId: number | null
  uploaderId: number | null
  uploaderName: string
  version: number
  viewCount: number
  downloadCount: number
  status: number
  isFavorited?: boolean
  previewStatus?: string
  previewPdfPath?: string
  createTime: string
  updateTime: string
}

export interface VersionDTO {
  id: number
  fileId: number
  version: number
  filePath: string
  fileSize: number
  operatorId: number | null
  changeNote: string | null
  createTime: string
}

export interface RecycleBinItem {
  id: number
  itemType: string
  itemId: number
  itemName: string
  deletedBy: number | null
  expireTime: string
  createTime: string
}

export function getDirectories(departmentId?: number, spaceType?: number, spaceId?: number) {
  const params: Record<string, any> = {}
  if (departmentId != null) params.departmentId = departmentId
  if (spaceType != null) params.spaceType = spaceType
  if (spaceId != null) params.spaceId = spaceId
  return request.get<{ code: number; message: string; data: DirectoryDTO[] }>('/front/directories', { params })
}

export function createDirectory(data: { dirName: string; parentId?: number | null; departmentId?: number | null; spaceType?: number | null; spaceId?: number | null; sortOrder?: number }) {
  return request.post<{ code: number; message: string; data: DirectoryDTO }>('/front/directories', data)
}

export function updateDirectory(id: number, data: { dirName?: string; sortOrder?: number }) {
  return request.put<{ code: number; message: string; data: DirectoryDTO }>(`/front/directories/${id}`, data)
}

export function renameDirectory(id: number, newName: string) {
  return request.put<{ code: number; message: string; data: DirectoryDTO }>(`/front/directories/${id}/rename`, { newName })
}

export function deleteDirectory(id: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/directories/${id}`)
}

export function moveDirectory(id: number, data: { newParentId: number | null; sortOrder?: number }) {
  return request.put<{ code: number; message: string; data: DirectoryDTO }>(`/front/directories/${id}/move`, data)
}

export function batchSortDirectories(sortItems: Array<{ id: number; sortOrder?: number; parentId?: number }>) {
  return request.put<{ code: number; message: string; data: null }>('/front/directories/batch-sort', sortItems)
}

export function getFiles(directoryId?: number | null, spaceType?: number | null, spaceId?: number | null, extraParams?: Record<string, any>) {
  const params: Record<string, any> = {}
  if (directoryId != null) params.directoryId = directoryId
  if (spaceType != null) params.spaceType = spaceType
  if (spaceId != null) params.spaceId = spaceId
  if (extraParams) Object.assign(params, extraParams)
  return request.get<{ code: number; message: string; data: FileDTO[] }>('/front/files', { params })
}

export interface FilePageResult {
  content: FileDTO[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function getFilesPage(
  directoryId?: number | null,
  spaceType?: number | null,
  spaceId?: number | null,
  page = 0,
  size = 20,
  sortField = 'createTime',
  sortOrder: 'asc' | 'desc' = 'desc',
) {
  const params: Record<string, any> = { page, size, sortField, sortOrder }
  if (directoryId != null) params.directoryId = directoryId
  if (spaceType != null) params.spaceType = spaceType
  if (spaceId != null) params.spaceId = spaceId
  return request.get<{ code: number; message: string; data: FilePageResult }>('/front/files/page', { params })
}

export function getFileDetail(id: number) {
  return request.get<{ code: number; message: string; data: FileDTO }>(`/front/files/${id}`)
}

export function updateFile(id: number, data: { fileName?: string; fileType?: string }) {
  return request.put<{ code: number; message: string; data: FileDTO }>(`/front/files/${id}`, data)
}

export function renameFile(id: number, newName: string) {
  return request.put<{ code: number; message: string; data: FileDTO }>(`/front/files/${id}/rename`, { newName })
}

export function deleteFile(id: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/files/${id}`)
}

export function restoreFile(id: number) {
  return request.put<{ code: number; message: string; data: FileDTO }>(`/front/files/${id}/restore`)
}

export function downloadFile(id: number) {
  return request.get(`/front/files/${id}/download`, { responseType: 'blob' })
}

export function searchFiles(keyword: string) {
  return request.get<{ code: number; message: string; data: FileDTO[] }>('/front/files/search', { params: { keyword } })
}

export function getRelatedFiles(fileId: number) {
  return request.get<{ code: number; message: string; data: FileDTO[] }>(`/front/files/${fileId}/related`)
}

export function recordFileView(fileId: number) {
  return request.post<{ code: number; message: string; data: null }>(`/front/files/${fileId}/view`)
}

export function getFileCover(fileId: number) {
  return axios.get<ArrayBuffer>(`/api/front/files/${fileId}/cover`, {
    responseType: 'arraybuffer',
    headers: { Authorization: `Bearer ${sessionStorage.getItem('token') || ''}` },
  })
}

export function fulltextSearchFiles(keyword: string) {
  return request.get<{ code: number; message: string; data: FileDTO[] }>('/front/files/fulltext-search', { params: { keyword } })
}

export function moveFiles(fileIds: number[], targetDirectoryId: number | null, targetSpaceType?: number | null, targetSpaceId?: number | null) {
  return request.put<{ code: number; message: string; data: FileDTO[] }>('/front/files/move', {
    fileIds, targetDirectoryId, targetSpaceType, targetSpaceId
  })
}

export function copyFiles(fileIds: number[], targetDirectoryId: number | null, targetSpaceType?: number | null, targetSpaceId?: number | null) {
  return request.post<{ code: number; message: string; data: FileDTO[] }>('/front/files/copy', {
    fileIds, targetDirectoryId, targetSpaceType, targetSpaceId
  })
}

export function getFileVersions(fileId: number) {
  return request.get<{ code: number; message: string; data: VersionDTO[] }>(`/front/files/${fileId}/versions`)
}

export function uploadNewVersion(fileId: number, formData: FormData) {
  return request.post<{ code: number; message: string; data: VersionDTO }>(`/front/files/${fileId}/versions`, formData)
}

export function rollbackVersion(fileId: number, targetVersion: number) {
  return request.post<{ code: number; message: string; data: VersionDTO }>(`/front/files/${fileId}/versions/rollback`, { targetVersion })
}

export function batchDownload(fileIds: number[]) {
  return request.post<{ code: number; message: string; data: { fileName: string; expireTime: string } }>('/front/files/batch-download', fileIds)
}

export function downloadBatchFile(fileName: string) {
  return request.get(`/front/files/batch-download/${fileName}`, { responseType: 'blob' })
}

export function getRecycleBin() {
  return request.get<{ code: number; message: string; data: RecycleBinItem[] }>('/front/recycle-bin')
}

export function moveToRecycleBin(itemType: string, itemId: number) {
  return request.post<{ code: number; message: string; data: null }>('/front/recycle-bin/move', { itemType, itemId })
}

export function batchMoveToRecycleBin(items: Array<{ itemType: string; itemId: number }>) {
  return request.post<{ code: number; message: string; data: null }>('/front/recycle-bin/batch-move', items)
}

export function restoreFromRecycleBin(itemType: string, itemId: number) {
  return request.post<{ code: number; message: string; data: null }>('/front/recycle-bin/restore', { itemType, itemId })
}

export function permanentDelete(itemType: string, itemId: number) {
  return request.delete<{ code: number; message: string; data: null }>('/front/recycle-bin/permanent', { data: { itemType, itemId } })
}

export function clearRecycleBin() {
  return request.delete<{ code: number; message: string; data: null }>('/front/recycle-bin')
}

export function transferFile(id: number, data: { targetDirectoryId?: number | null; targetDepartmentId?: number | null }) {
  return request.put<{ code: number; message: string; data: FileDTO }>(`/front/files/${id}/transfer`, data)
}

export interface DepartmentDTO {
  id: number
  deptName: string
  parentId: number
  sortOrder: number
  companyId: number
}

export function getDepartments() {
  return request.get<{ code: number; message: string; data: DepartmentDTO[] }>('/front/users/departments')
}

export interface SensitiveWordDTO {
  id: number
  word: string
  category: string | null
  level: number | null
  enabled: number
  createBy: number | null
  createTime: string
  updateTime: string
}

export function getSensitiveWords() {
  return request.get<{ code: number; message: string; data: SensitiveWordDTO[] }>('/front/sensitive-words')
}

export function searchSensitiveWords(keyword: string) {
  return request.get<{ code: number; message: string; data: SensitiveWordDTO[] }>('/front/sensitive-words/search', { params: { keyword } })
}

export function addSensitiveWord(data: { word: string; category?: string; level?: number }) {
  return request.post<{ code: number; message: string; data: SensitiveWordDTO }>('/front/sensitive-words', data)
}

export function batchAddSensitiveWords(data: { words: string[]; category?: string; level?: number }) {
  return request.post<{ code: number; message: string; data: null }>('/front/sensitive-words/batch', data)
}

export function updateSensitiveWord(id: number, data: { word?: string; category?: string; level?: number; enabled?: number }) {
  return request.put<{ code: number; message: string; data: SensitiveWordDTO }>(`/front/sensitive-words/${id}`, data)
}

export function deleteSensitiveWord(id: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/sensitive-words/${id}`)
}

export function checkSensitiveText(text: string) {
  return request.post<{ code: number; message: string; data: { contains: boolean; sensitiveWords: string[]; filteredText: string } }>('/front/sensitive-words/check', { text })
}

export function rebuildSensitiveWordDfa() {
  return request.post<{ code: number; message: string; data: null }>('/front/sensitive-words/rebuild')
}
