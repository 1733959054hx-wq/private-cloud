import request from '@/api'

export interface ShareLinkVO {
  id: number
  fileId: number
  fileName?: string
  creatorId: number | null
  token: string
  password: string | null
  expireTime: string | null
  maxAccess: number
  accessCount: number
  permissionType: string
  status: number
  createTime: string
}

export function createShareLink(data: { fileId: number; permissionType?: string; expireTime?: string | null; maxAccess?: number; password?: string | null }) {
  return request.post<{ code: number; message: string; data: ShareLinkVO }>('/front/share-links', data)
}

export function getShareLinkByToken(token: string) {
  return request.get<{ code: number; message: string; data: ShareLinkVO }>(`/front/share-links/token/${token}`)
}

export function getFileShareLinks(fileId: number) {
  return request.get<{ code: number; message: string; data: ShareLinkVO[] }>(`/front/share-links/file/${fileId}`)
}

export function getMyShareLinks() {
  return request.get<{ code: number; message: string; data: ShareLinkVO[] }>('/front/share-links/mine')
}

export function getAllMyShareLinks() {
  return request.get<{ code: number; message: string; data: ShareLinkVO[] }>('/front/share-links/mine/all')
}

export function deleteShareLink(linkId: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/share-links/${linkId}`)
}

export function forceDeleteShareLink(linkId: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/share-links/${linkId}/force`)
}

export function recordShareAccess(token: string) {
  return request.post<{ code: number; message: string; data: null }>(`/front/share-links/access/${token}`)
}
