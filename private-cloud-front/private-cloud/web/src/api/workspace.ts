import request from '@/api'

export interface StorageByType {
  type: string
  size: number
}

export interface StorageStats {
  totalQuota: number
  totalUsed: number
  byType: StorageByType[]
}

export interface ContributionDay {
  date: string
  count: number
}

export interface DashboardVO {
  totalDocs: number
  monthlyUploads: number
  pendingApprovals: number
  favoriteCount: number
  unreadNotifications: number
  recentFiles: RecentFile[]
  pendingApprovalList: PendingApproval[]
  storageStats: StorageStats
  contributionHeatmap: ContributionDay[]
}

export interface RecentFile {
  fileId: number
  fileName: string
  fileType: string
  fileSize: number
  accessTime: string
}

export interface PendingApproval {
  approvalId: number
  title: string
  type: string
  status: string
  createTime: string
}

export interface FavoriteVO {
  id: number
  targetId: number
  targetType: number
  targetName: string
  fileType: string | null
  createTime: string
}

export interface NotificationVO {
  id: number
  receiverId: number
  type: string
  title: string
  content: string
  relatedId: number | null
  isRead: number
  readTime: string | null
  createTime: string
}

export function getDashboard() {
  return request.get<{ code: number; message: string; data: DashboardVO }>('/front/dashboard')
}

export function getFavorites() {
  return request.get<{ code: number; message: string; data: FavoriteVO[] }>('/front/favorites')
}

export function addFavorite(targetId: number, targetType: number) {
  return request.post<{ code: number; message: string; data: FavoriteVO }>('/front/favorites', { targetId, targetType })
}

export function removeFavorite(targetId: number, targetType: number) {
  return request.delete<{ code: number; message: string; data: null }>('/front/favorites', { params: { targetId, targetType } })
}

export function checkFavorite(targetId: number, targetType: number) {
  return request.get<{ code: number; message: string; data: { favorited: boolean } }>('/front/favorites/check', { params: { targetId, targetType } })
}

export function getNotifications() {
  return request.get<{ code: number; message: string; data: NotificationVO[] }>('/front/notifications')
}

export function getUnreadNotifications() {
  return request.get<{ code: number; message: string; data: NotificationVO[] }>('/front/notifications/unread')
}

export function getUnreadCount() {
  return request.get<{ code: number; message: string; data: { unreadCount: number } }>('/front/notifications/unread-count')
}

export function markNotificationRead(id: number) {
  return request.put<{ code: number; message: string; data: null }>(`/front/notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return request.put<{ code: number; message: string; data: null }>('/front/notifications/read-all')
}

// ==================== 工作台布局 ====================

export interface LayoutItem {
  i: string
  x: number
  y: number
  w: number
  h: number
}

export function getWorkspaceLayout() {
  return request.get<{ code: number; message: string; data: { workspaceLayout: string | null } }>('/front/users/layout')
}

export function saveWorkspaceLayout(layout: LayoutItem[]) {
  return request.put<{ code: number; message: string; data: null }>('/front/users/layout', {
    workspaceLayout: JSON.stringify(layout),
  })
}

// ==================== 团队动态 ====================

export interface TeamUpdate {
  type: string
  title: string
  operator: string
  time: string
  fileId?: number
  notificationId?: number
}

export function getTeamUpdates() {
  return request.get<{ code: number; message: string; data: TeamUpdate[] }>('/front/dashboard/team-updates')
}
