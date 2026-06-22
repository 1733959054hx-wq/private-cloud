import request from '@/api'

export interface NotificationDTO {
  id: number
  type: string
  title: string
  content: string
  fromUserId: number | null
  fromUsername: string | null
  fileId: number | null
  fileName: string | null
  commentId: number | null
  isRead: number
  createTime: string
}

export function getNotifications() {
  return request.get<{ code: number; message: string; data: NotificationDTO[] }>('/front/notifications')
}

export function getUnreadCount() {
  return request.get<{ code: number; message: string; data: { count: number } }>('/front/notifications/unread-count')
}

export function markAsRead(id: number) {
  return request.put<{ code: number; message: string; data: null }>(`/front/notifications/${id}/read`)
}

export function markAllAsRead() {
  return request.put<{ code: number; message: string; data: null }>('/front/notifications/read-all')
}

export function deleteNotification(id: number) {
  return request.delete<{ code: number; message: string; data: null }>(`/front/notifications/${id}`)
}

export function clearReadNotifications() {
  return request.delete<{ code: number; message: string; data: null }>('/front/notifications/clear-read')
}
