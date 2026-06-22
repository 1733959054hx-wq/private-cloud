import request from '@/api'

/** 搜索请求参数 */
export interface AdvSearchRequest {
  keyword?: string
  uploader?: string
  startDate?: string
  endDate?: string
  fileTypes?: string[]
  tag?: string
  page?: number
  size?: number
  orderBy?: string
  orderDir?: string
}

/** 搜索结果项 */
export interface AdvSearchResultItem {
  documentId: number
  title: string
  summary: string
  uploader: string
  fileType: string
  fileSize: number
  uploadTime: string
  score: number
  highlights: string[]
  tags?: string[]
}

/** 搜索建议（带来源标签） */
export interface AdvSearchSuggestion {
  keyword: string
  count: number
  hot: boolean
  source: string   // 'history' | 'document'
}

/** 搜索响应 */
interface AdvSearchResponse {
  code: number
  msg: string
  data: AdvSearchResultItem[]
  total: number
  page: number
  size: number
}

/** 全文检索 */
export function advFullTextSearch(params: AdvSearchRequest) {
  return request.post<AdvSearchResponse>('/search/engine/search', params)
}

/** 关键词自动补全 */
export function advFetchSuggest(prefix: string) {
  return request.get<{ code: number; data: AdvSearchSuggestion[] }>(
    '/search/engine/suggest',
    { params: { prefix } }
  )
}

/** 热门搜索词 */
export function advFetchHotKeywords() {
  return request.get<{ code: number; data: string[] }>('/search/engine/hot')
}

/** 重建 ES 索引 */
export function rebuildIndex() {
  return request.post('/search/engine/index/rebuild')
}

/** 根据部门ID查询文档 */
export function getByDepartment(deptId: number) {
  return request.get<{ code: number; data: any[] }>('/search/engine/department', {
    params: { deptId }
  })
}
