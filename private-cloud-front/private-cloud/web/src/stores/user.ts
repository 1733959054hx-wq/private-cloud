import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, logout as logoutApi, getUserInfo, type LoginRequest, type UserInfo, type CaptchaClick } from '@/api/auth'
import { useAiStore } from '@/stores/ai'
import { useDocumentStore } from '@/stores/document'

export const useUserStore = defineStore('user', () => {
  const token = ref(sessionStorage.getItem('token') || '')

  const cachedUserInfo = sessionStorage.getItem('userInfo')
  const userInfo = ref<UserInfo | null>(cachedUserInfo ? JSON.parse(cachedUserInfo) : null)

  const isLoggedIn = computed(() => !!token.value)

  const realName = computed(() => userInfo.value?.realName || sessionStorage.getItem('realName') || '用户')
  const username = computed(() => userInfo.value?.username || '')
  const userId = computed(() => userInfo.value?.userId || 0)
  const departmentName = computed(() => userInfo.value?.departmentName || '')
  const departmentId = computed(() => userInfo.value?.departmentId ?? null)
  const companyId = computed(() => userInfo.value?.companyId ?? null)
  const roles = computed(() => userInfo.value?.roles || [])
  const permissions = computed(() => userInfo.value?.permissions || [])

  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))
  const isDeptAdmin = computed(() => roles.value.includes('ROLE_DEPT_ADMIN'))
  const canCrossDeptTransfer = computed(() =>
    permissions.value.includes('file:transfer:cross_department') || isAdmin.value
  )
  const canDeleteAnyComment = computed(() =>
    permissions.value.includes('comment:delete') || isAdmin.value
  )
  const canDeleteDeptComment = computed(() =>
    permissions.value.includes('comment:delete:department') || isDeptAdmin.value || isAdmin.value
  )

  async function login(data: LoginRequest) {
    const res = await loginApi(data)
    const loginData = res.data.data || res.data
    token.value = loginData.token
    sessionStorage.setItem('token', loginData.token)
    sessionStorage.setItem('realName', loginData.realName || loginData.username)
    sessionStorage.setItem('userInfo', JSON.stringify(loginData))
    userInfo.value = {
      userId: loginData.userId,
      username: loginData.username,
      realName: loginData.realName,
      email: null,
      phone: null,
      avatar: null,
      departmentId: loginData.departmentId ?? null,
      departmentName: loginData.departmentName ?? null,
      companyId: loginData.companyId ?? null,
      roles: loginData.roles || [],
      permissions: loginData.permissions || [],
    }
    return loginData
  }

  /** 兼容旧调用：接收明文密码+验证码信息，内部调用登录API */
  async function loginWithCredentials(params: {
    encryptedPassword: string
    captchaKey: string
    captchaClicks: CaptchaClick[]
    username: string
  }) {
    return login({
      username: params.username,
      encryptedPassword: params.encryptedPassword,
      captchaKey: params.captchaKey,
      captchaClicks: params.captchaClicks,
    })
  }

  async function fetchUserInfo() {
    try {
      const res = await getUserInfo()
      userInfo.value = res.data.data || res.data
    } catch (err) {
      const cached = sessionStorage.getItem('userInfo')
      if (cached) {
        try { userInfo.value = JSON.parse(cached) } catch { /* ignore */ }
      }
      // 重新抛出异常，让调用方（如路由守卫）能捕获认证失败
      throw err
    }
  }

  async function logout() {
    // 通知后端将 Token 加入黑名单（不阻塞，失败也继续清理本地）
    try {
      if (token.value) await logoutApi()
    } catch { /* 忽略网络错误，本地仍需清理 */ }

    // 清理当前用户的 AI 聊天记录（防止切换用户后看到前一个用户的记录）
    const aiStore = useAiStore()
    aiStore.clearForLogout()

    // 清理当前用户的文档空间状态（sessionStorage 中以 doc_ 开头的 key）
    const uid = userId.value
    if (uid) {
      const prefix = `u${uid}:`
      // 清理带 userId 前缀的 key
      for (let i = sessionStorage.length - 1; i >= 0; i--) {
        const key = sessionStorage.key(i)
        if (key && key.startsWith(prefix)) {
          sessionStorage.removeItem(key)
        }
      }
      // 清理旧的无前缀 key（兼容历史数据）
      ;['doc_view_mode', 'doc_scroll_top', 'doc_scroll_mode', 'last_doc_route'].forEach(k => sessionStorage.removeItem(k))
      // 清理 AI 助手位置
      localStorage.removeItem(`u${uid}:ai-assistant-position`)
      localStorage.removeItem('ai-assistant-position')
    }

    // 清理 document store 状态（setup store 不支持 $reset，使用自定义 resetState）
    const docStore = useDocumentStore()
    docStore.resetState()

    token.value = ''
    userInfo.value = null
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userInfo')
    sessionStorage.removeItem('realName')
  }

  return { token, userInfo, isLoggedIn, realName, username, userId, departmentName, departmentId, companyId, roles, permissions, isAdmin, isDeptAdmin, canCrossDeptTransfer, canDeleteAnyComment, canDeleteDeptComment, login, loginWithCredentials, fetchUserInfo, logout }
})
