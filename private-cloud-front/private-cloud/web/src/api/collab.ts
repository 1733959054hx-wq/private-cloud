import request from '@/api'

export interface CollabSessionVO {
  id: number
  documentId: number | null
  sessionId: string
  roomName: string
  status: number
  permissionMode: string
  ownerId: number | null
  createTime: string
  closeTime: string | null
}

export function createCollabSession(data: { roomName: string; permissionMode?: string }) {
  return request.post<{ code: number; message: string; data: CollabSessionVO }>('/front/collab', data)
}

export function getActiveSession(documentId: number) {
  return request.get<{ code: number; message: string; data: CollabSessionVO }>(`/front/collab/document/${documentId}/active`)
}

export function getDocumentSessions(documentId: number) {
  return request.get<{ code: number; message: string; data: CollabSessionVO[] }>(`/front/collab/document/${documentId}`)
}

export function getMySessions() {
  return request.get<{ code: number; message: string; data: CollabSessionVO[] }>('/front/collab/mine')
}

export function closeSession(sessionId: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/collab/${sessionId}`)
}

export function updateSessionPermission(sessionId: number, permissionMode: string) {
  return request.put<{ code: number; message: string; data: CollabSessionVO }>(`/front/collab/${sessionId}/permission`, { permissionMode })
}

export function getSessionById(id: number) {
  return request.get<{ code: number; message: string; data: CollabSessionVO }>(`/front/collab/${id}`)
}

export function getCollabContent(sessionId: string) {
  return request.get<{ code: number; message: string; data: string }>(`/front/collab/${sessionId}/content`)
}

export function saveCollabContent(sessionId: string, content: string) {
  return request.put<{ code: number; message: string; data: null }>(`/front/collab/${sessionId}/content`, { content })
}
