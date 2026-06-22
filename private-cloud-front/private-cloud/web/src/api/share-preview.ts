import request from '@/api'

/** 分享信息 */
export interface ShareInfo {
  id: number
  documentId: number
  token: string
  shareUrl: string
  password: boolean
  expireTime: string | null
  maxViews: number
  currentViews: number
  status: number
  createTime: string
}

/** 分享访问请求 */
export interface ShareAccessRequest {
  token: string
  password?: string
}

/** 分享访问响应 */
export interface ShareAccessResponse {
  fileId: number
  fileName: string
  fileType: string
  fileSize: number
  token: string
}

/** 文件预览信息 */
export interface PreviewFileInfo {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  uploaderName: string
  createTime: string
  extractedText: string | null
}

/**
 * 检查分享链接状态（不需要密码）
 */
export function checkShare(token: string) {
  return request.get<{ code: number; message: string; data: ShareInfo }>(`/share/check/${token}`)
}

/**
 * 验证密码并获取文件访问权限
 */
export function accessShare(data: ShareAccessRequest) {
  return request.post<{ code: number; message: string; data: ShareAccessResponse }>('/share/access', data)
}

/**
 * 获取文件预览信息
 */
export function getPreviewInfo(fileId: number) {
  return request.get<{ code: number; message: string; data: PreviewFileInfo }>(`/front/preview/${fileId}/info`)
}

/**
 * 获取预览 URL
 */
export function getPreviewUrl(fileId: number): string {
  return `/api/front/preview/${fileId}`
}

/**
 * 获取 Excel 数据
 */
export interface ExcelSheetData {
  name: string
  totalRows: number
  rows: string[][]
}

export interface ExcelData {
  sheets: ExcelSheetData[]
  error?: string
}

export interface PdfPageData {
  totalPages: number
  renderedPages: number
  pages: string[]
}

export interface PptPageData {
  totalPages: number
  renderedPages: number
  pages: string[]
  error?: string
}

export function getPdfPages(fileId: number) {
  return request.get<{ data: PdfPageData }>(`/front/preview/${fileId}/pdf-pages`)
}

export function getPptPages(fileId: number) {
  return request.get<{ data: PptPageData }>(`/front/preview/${fileId}/ppt-pages`)
}

export function getExcelData(fileId: number) {
  return request.get<{ data: ExcelData }>(`/front/preview/${fileId}/excel-data`)
}

// ==================== pdf.js 流式渲染相关 API ====================

/** 获取PDF原始文件流URL（供pdf.js直接加载） */
export function getPdfStreamUrl(fileId: number): string {
  return `/api/front/preview/${fileId}/pdf-stream`
}

/** 获取已转换的PDF文件流URL（Word/PPT转PDF后供pdf.js加载） */
export function getConvertedPdfUrl(fileId: number): string {
  return `/api/front/preview/${fileId}/converted-pdf`
}

/** 获取转换状态 */
export interface ConvertStatus {
  status: 'NOT_STARTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  message?: string
  pdfUrl?: string
}

export function getConvertStatus(fileId: number) {
  return request.get<{ data: ConvertStatus }>(`/front/preview/${fileId}/convert-status`)
}

/** 触发异步转换 */
export function triggerConvert(fileId: number) {
  return request.post<{ data: ConvertStatus }>(`/front/preview/${fileId}/trigger-convert`)
}

/** 文本预览（服务端截断） */
export interface TextPreviewData {
  content: string
  truncated: boolean
  fileSize: number
  previewSize: number
}

export function getTextPreview(fileId: number) {
  return request.get<{ data: TextPreviewData }>(`/front/preview/${fileId}/text-preview`)
}
