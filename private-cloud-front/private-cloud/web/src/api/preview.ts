import request from '@/api'

// ==================== 1. 统一的通用响应结构 ====================
export interface Result<T> {
  code: number
  message: string
  data: T
}

// ==================== 2. 接口类型定义 ====================
export interface PreviewInfo {
  id: number
  fileName: string
  fileType: string
  extractedText: string | null
  status: number
}

export interface ExcelSheetData {
  name: string
  totalRows: number
  totalRowsApprox?: boolean // 修复：补全可选属性，解决 FilePreview.vue 中的编译报错
  rows: string[][]
}

export interface ExcelData {
  sheets: ExcelSheetData[]
  error?: string
}

export interface ConvertStatus {
  status: 'NOT_STARTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  message?: string
  pdfUrl?: string
}

export interface TextPreviewData {
  content: string
  truncated: boolean
  fileSize: number
  previewSize: number
}

// ==================== 3. 静态流/原生资源 URL（供 DOM 元素直接使用，带 /api 前缀） ====================
export function getPreviewUrl(fileId: number): string {
  return `/api/front/preview/${fileId}`
}

export function getPdfStreamUrl(fileId: number): string {
  return `/api/front/preview/${fileId}/pdf-stream`
}

export function getConvertedPdfUrl(fileId: number): string {
  return `/api/front/preview/${fileId}/converted-pdf`
}

// ==================== 4. 异步转换相关 API ====================
export function getConvertStatus(fileId: number) {
  return request.get<Result<ConvertStatus>>(`/front/preview/${fileId}/convert-status`)
}

export function triggerConvert(fileId: number, force: boolean = false) {
  return request.post<Result<ConvertStatus>>(`/front/preview/${fileId}/trigger-convert`, null, {
    params: force ? { force: true } : undefined
  })
}

// ==================== 5. 文本/表格数据解析 API ====================
export function getExcelData(fileId: number) {
  return request.get<Result<ExcelData>>(`/front/preview/${fileId}/excel-data`)
}

export function getTextPreview(fileId: number) {
  return request.get<Result<TextPreviewData>>(`/front/preview/${fileId}/text-preview`)
}

// ==================== 6. OCR 文本提取 API ====================
export function triggerOcr(fileId: number, page?: number) {
  const params = page !== undefined ? { page } : {}
  return request.post<Result<{ fileId: number; status: string; message: string; page?: number }>>(
    `/front/ocr/${fileId}`,
    null,
    { params }
  )
}

export function getOcrResult(fileId: number, page?: number) {
  const params = page !== undefined ? { page } : {}
  return request.get<Result<{ fileId: number; status: string; ocrText: string | null; errorMessage: string | null; hasText: boolean; page?: number }>>(
    `/front/ocr/${fileId}/result`,
    { params }
  )
}

// ==================== 7. 阅读进度云端同步 API ====================
export function saveProgressApi(data: { fileId: number; progressType: number; progressValue: number }) {
  return request.post<Result<string>>(`/front/preview/progress/save`, data)
}

export function getProgressApi(fileId: number, progressType: number) {
  return request.get<Result<number>>(`/front/preview/progress/get`, { params: { fileId, progressType } })
}

// ==================== 8. 备用或外部调用的信息 API ====================
export function getPreviewInfo(fileId: number) {
  return request.get<Result<PreviewInfo>>(`/front/preview/${fileId}/info`)
}
