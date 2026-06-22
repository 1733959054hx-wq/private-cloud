import request from '@/api'

export interface CommentDTO {
  id: number
  fileId: number
  userId: number
  username: string
  content: string
  parentId: number | null
  mentions: string | null
  departmentId: number | null
  createTime: string
  updateTime: string
  canDelete: boolean
  isFileOwner: boolean
  quoteText: string | null
}

export function getComments(fileId: number) {
  return request.get<{ code: number; message: string; data: CommentDTO[] }>('/front/comments', { params: { fileId } })
}

export function addComment(data: { fileId: number; content: string; parentId?: number | null; mentions?: string | null; quoteText?: string | null }) {
  return request.post<{ code: number; message: string; data: CommentDTO }>('/front/comments', data)
}

export function updateComment(id: number, content: string) {
  return request.put<{ code: number; message: string; data: CommentDTO }>(`/front/comments/${id}`, { content })
}

export function deleteComment(id: number, isOwner = false) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/comments/${id}`, { params: { isOwner } })
}
