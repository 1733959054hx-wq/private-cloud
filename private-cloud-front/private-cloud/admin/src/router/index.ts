import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '管理员登录' },
  },
  {
    path: '/',
    component: () => import('@/views/MainLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardContent.vue'),
        meta: { title: '数据仪表盘' },
      },
      {
        path: 'system',
        meta: { title: '系统管理' },
        children: [
          { path: 'user', name: 'UserManage', component: () => import('@/views/system/UserManage.vue'), meta: { title: '用户管理' } },
          { path: 'dept', name: 'DeptManage', component: () => import('@/views/system/DeptManage.vue'), meta: { title: '部门管理' } },
          { path: 'role', name: 'RoleManage', component: () => import('@/views/system/RoleManage.vue'), meta: { title: '角色管理' } },
          { path: 'dict', name: 'DictManage', component: () => import('@/views/system/DictManage.vue'), meta: { title: '字典管理' } },
          { path: 'sensitive', name: 'SensitiveWords', component: () => import('@/views/system/SensitiveWords.vue'), meta: { title: '敏感词管理' } },
          { path: 'storage', name: 'StorageConfig', component: () => import('@/views/system/StorageConfig.vue'), meta: { title: '存储配置' } },
          { path: 'cache', name: 'CacheMonitor', component: () => import('@/views/system/CacheMonitor.vue'), meta: { title: '缓存监控' } },
        ],
      },
      {
        path: 'workflow',
        meta: { title: '工作流管理' },
        children: [
          { path: 'approval', name: 'ApprovalManage', component: () => import('@/views/workflow/ApprovalManage.vue'), meta: { title: '审批管理' } },
          { path: 'monitor', name: 'ApprovalMonitor', component: () => import('@/views/workflow/ApprovalMonitor.vue'), meta: { title: '审批监控' } },
        ],
      },
      {
        path: 'security',
        meta: { title: '安全审计' },
        children: [
          { path: 'online', name: 'OnlineUsers', component: () => import('@/views/security/OnlineUsers.vue'), meta: { title: '在线用户' } },
          { path: 'operation-log', name: 'OperationLog', component: () => import('@/views/security/OperationLog.vue'), meta: { title: '操作日志' } },
          { path: 'access-log', name: 'AccessLog', component: () => import('@/views/security/AccessLog.vue'), meta: { title: '文件访问日志' } },
        ],
      },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })
export default router
