import request from './index'

export interface DashboardStats { totalDocs: number; totalUsers: number; totalStorage: number; usedStorage: number; todayUploads: number; todayDownloads: number; monthlyUploads: number; approvalPassRate: number }
export interface DocTypeStat { type: string; count: number }
export interface StorageTrend { date: string; size: number }
export interface HotDoc { docId: number; fileName: string; viewCount: number; fileType?: string }
export interface HotKeyword { keyword: string; count: number }
export interface PendingApproval { id: number; title: string; fileName?: string; applicant?: number; createTime: string }

export function getDashboardStats() { return request.get('/dashboard/stats') }
export function getDocTypeStats() { return request.get('/dashboard/doc-types') }
export function getStorageTrend() { return request.get('/dashboard/storage-trend') }
export function getHotDocs() { return request.get('/dashboard/hot-docs') }
export function getHotKeywords() { return request.get('/dashboard/hot-keywords') }
export function getPendingApprovals() { return request.get('/dashboard/pending-approvals') }
