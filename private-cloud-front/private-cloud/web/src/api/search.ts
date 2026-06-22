import request from '@/api'

/** 搜索请求参数 */
export interface SearchRequest {
  keyword?: string
  uploader?: string
  startDate?: string
  endDate?: string
  fileTypes?: string[]
  page?: number
  size?: number
  orderBy?: string
  orderDir?: string
}

/** 搜索结果项 */
export interface SearchResultItem {
  documentId: number
  title: string
  summary: string
  uploader: string
  fileType: string
  fileSize: number
  uploadTime: string
  score: number
  highlights: string[]
}

/** 搜索建议 */
export interface SearchSuggestion {
  keyword: string
  count: number
  hot: boolean
}

/** 搜索响应 */
interface SearchResponse {
  code: number
  msg: string
  data: SearchResultItem[]
  total: number
  page: number
  size: number
}

/** 全文检索 */
export function fullTextSearch(params: SearchRequest) {
  return request.post<SearchResponse>('/search/engine/search', params)
}

/** 关键词自动补全 */
export function fetchSuggest(prefix: string) {
  return request.get<{ code: number; data: SearchSuggestion[] }>(
    '/search/engine/suggest',
    { params: { prefix } }
  )
}

/** 热门搜索词 */
export function fetchHotKeywords() {
  return request.get<{ code: number; data: string[] }>('/search/engine/hot')
}
