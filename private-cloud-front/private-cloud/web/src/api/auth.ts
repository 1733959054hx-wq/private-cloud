import request from '@/api'

export interface CaptchaClick {
  x: number
  y: number
}

export interface LoginRequest {
  username: string
  encryptedPassword: string
  captchaKey: string
  captchaClicks: CaptchaClick[]
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  realName: string
  departmentId: number | null
  departmentName: string | null
  companyId: number | null
  roles: string[]
  permissions: string[]
}

export interface UserInfo {
  userId: number
  username: string
  realName: string
  email: string | null
  phone: string | null
  avatar: string | null
  departmentId: number | null
  departmentName: string | null
  companyId: number | null
  roles: string[]
  permissions: string[]
}

export interface CaptchaData {
  captchaKey: string
  image: string
  prompt: string
}

/** 获取RSA公钥 */
export function getRsaPublicKey() {
  return request.get<{ code: number; message: string; data: { publicKey: string } }>(
    '/front/auth/rsa-public-key'
  )
}

/** 获取点选文字验证码 */
export function getCaptcha() {
  return request.get<{ code: number; message: string; data: CaptchaData }>(
    '/front/auth/captcha'
  )
}

/** 登录（增强版：RSA加密密码 + 点选文字验证码） */
export function login(data: LoginRequest) {
  return request.post<{ code: number; message: string; data: LoginResponse }>('/front/auth/login', data)
}

/** 登出：通知后端将 Token 加入黑名单 */
export function logout() {
  return request.post<{ code: number; message: string; data: null }>('/front/auth/logout')
}

export function getUserInfo() {
  return request.get<{ code: number; message: string; data: UserInfo }>('/front/auth/info')
}

export interface SimpleUser {
  id: number
  username: string
  realName: string
  departmentName: string | null
}

export function getAllUsers() {
  return request.get<{ code: number; message: string; data: SimpleUser[] }>('/front/users')
}

export function getMentionableUsers(fileId: number) {
  return request.get<{ code: number; message: string; data: SimpleUser[] }>('/front/users/mentionable', { params: { fileId } })
}
