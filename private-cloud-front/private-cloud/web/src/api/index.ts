import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

request.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code && res.code !== 200) {
      if (res.code === 401) {
        sessionStorage.removeItem('token')
        sessionStorage.removeItem('userInfo')
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('userInfo')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else if (error.response?.status === 400) {
      const msg = error.response?.data?.message || '请求参数错误'
      ElMessage.error(msg)
    } else if (error.response?.status === 404) {
      // 404不弹提示（可能是IP准入拦截的伪装响应），由调用方自行处理
    } else if (error.response?.status === 500) {
      ElMessage.error('服务器内部错误，请稍后重试')
    } else if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络后重试')
    } else {
      ElMessage.error(error.response?.data?.message || '网络错误，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
