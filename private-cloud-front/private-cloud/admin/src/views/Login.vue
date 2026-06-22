<template>
  <div class="admin-login">
    <div class="login-card">
      <div class="login-header">
        <div class="login-icon"><el-icon :size="32"><Monitor /></el-icon></div>
        <h1>私有云管理后台</h1>
        <p>管理员登录</p>
      </div>
      <el-form :model="form" label-width="0" size="large">
        <el-form-item><el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" /></el-form-item>
        <el-form-item><el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" /></el-form-item>
        <el-form-item><el-button type="primary" style="width:100%" :loading="loading" @click="handleLogin">登 录</el-button></el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) { ElMessage.warning('请输入用户名和密码'); return }
  loading.value = true
  try {
    // TODO: 对接真实登录 API
    // const res = await request.post('/auth/login', form)
    // sessionStorage.setItem('token', res.data.token)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch { ElMessage.error('登录失败') } finally { loading.value = false }
}
</script>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}
.login-card {
  background: #fff; border-radius: 20px; padding: 40px; width: 380px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.login-header { text-align: center; margin-bottom: 28px }
.login-icon { width: 64px; height: 64px; border-radius: 16px; background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px }
.login-header h1 { font-size: 20px; font-weight: 700; color: #303133; margin: 0 0 4px }
.login-header p { font-size: 13px; color: #909399; margin: 0 }
</style>
