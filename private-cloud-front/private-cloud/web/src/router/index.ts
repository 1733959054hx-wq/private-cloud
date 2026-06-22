import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/s/:token',
    name: 'SharePreview',
    component: () => import('@/views/share/SharePreview.vue'),
    meta: { title: '外部共享文档' },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Workbench.vue'),
        meta: { title: '个人工作台' },
      },
      {
        path: 'document',
        name: 'Document',
        component: () => import('@/views/document/DocumentSpace.vue'),
        meta: { title: '文档空间' },
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/search/AdvancedSearchEngine.vue'),
        meta: { title: '高级搜索' },
      },
      {
        path: 'qa',
        name: 'QA',
        component: () => import('@/views/search/KnowledgeQA.vue'),
        meta: { title: '知识问答' },
      },
      {
        path: 'collab',
        name: 'Collab',
        component: () => import('@/views/collab/Collaboration.vue'),
        meta: { title: '外部协作' },
      },
      {
        path: 'collab/editor/:id',
        name: 'CollabEditor',
        component: () => import('@/views/collab/CollabEditor.vue'),
        meta: { title: '协同编辑' },
      },
      {
        path: 'share/manage',
        name: 'ShareManage',
        component: () => import('@/views/share/ShareManage.vue'),
        meta: { title: '分享管理' },
      },
      {
        path: 'workflow/approval',
        name: 'ApprovalSubmit',
        component: () => import('@/views/workflow/ApprovalSubmit.vue'),
        meta: { title: '申请签章' },
      },
      {
        path: 'ai/generate',
        name: 'DocGenerate',
        component: () => import('@/views/ai/DocumentGenerate.vue'),
        meta: { title: '智能文档生成' },
      },
      {
        path: 'ai/generated-docs',
        name: 'GeneratedDocs',
        component: () => import('@/views/ai/GeneratedDocs.vue'),
        meta: { title: '生成文档管理' },
      },
      {
        path: 'generate',
        redirect: { name: 'DocGenerate' },
      },
      {
        path: 'preview/:id',
        name: 'Preview',
        component: () => import('@/views/document/FilePreview.vue'),
        meta: { title: '文件预览' },
      },
      {
        path: 'admin/sensitive-words',
        name: 'SensitiveWords',
        component: () => import('@/views/admin/SensitiveWords.vue'),
        meta: { title: '敏感词管理' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const publicRoutes = ['Login', 'SharePreview']
// 标记用户信息是否已加载，避免每次导航都发请求
let userInfoLoaded = false

router.beforeEach(async (to, _from, next) => {
  const token = sessionStorage.getItem('token')
  if (publicRoutes.includes(to.name as string)) {
    next()
    return
  }
  if (!token) {
    userInfoLoaded = false
    next({ name: 'Login' })
  } else if (to.name === 'Login' && token) {
    next({ name: 'Dashboard' })
  } else {
    // 仅在首次导航时获取用户信息，后续导航跳过
    if (token && !userInfoLoaded) {
      const userStore = useUserStore()
      // 如果登录时已存入用户信息，直接标记为已加载，跳过冗余请求
      if (userStore.userInfo) {
        userInfoLoaded = true
      } else {
        try {
          await userStore.fetchUserInfo()
          userInfoLoaded = true
        } catch {
          userInfoLoaded = false
          userStore.logout()
          next({ name: 'Login' })
          return
        }
      }
    }
    next()
  }
})

// 忽略重复导航错误（快速点击同一菜单项时触发）
const originalPush = router.push
router.push = function (to: any) {
  return originalPush.call(this, to).catch((err: any) => {
    if (err?.name !== 'NavigationDuplicated') {
      throw err
    }
  }) as any
}

export default router
