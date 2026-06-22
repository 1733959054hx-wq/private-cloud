import request from '@/api'

export interface UploadInitResponse {
  fileId: string
  taskId: number
  fileRecordId?: number
}

export interface ChunkUploadResponse {
  fileId: string
  uploadedChunks?: number
  receivedChunks?: number
  totalChunks: number
  status?: string
  fileRecordId?: number
}

export function initUpload(formData: FormData) {
  return request.post<{ code: number; message: string; data: UploadInitResponse }>('/front/files/upload/init', formData)
}

export function uploadChunk(formData: FormData) {
  return request.post<{ code: number; message: string; data: ChunkUploadResponse }>('/front/files/upload/chunk', formData, {
    timeout: 120000, // 分片上传超时2分钟，避免最后一个分片触发合并时超时
  })
}

export function getOcrStatus(fileId: number) {
  return request.get<{ code: number; message: string; data: { fileId: number; status: string; ocrText: string | null; hasText: boolean } }>(`/front/ocr/${fileId}/result`)
}
