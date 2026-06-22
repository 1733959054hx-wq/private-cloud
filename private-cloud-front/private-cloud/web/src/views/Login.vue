<template>
  <div class="login-page">
    <div
      class="presentation-area"
      ref="presentationRef"
    >
      <!-- 局部粒子背景：AI 知识网格 -->
      <div class="particle-bg-local">
        <VueParticles
          id="tsparticles"
          :options="particlesOptions"
        />
      </div>

      <!-- 动态流体呼吸光晕 -->
      <div class="glow-layer glow-1"></div>
      <div class="glow-layer glow-2"></div>

      <div class="presentation-content">
        <div class="hero-section">
          <div class="brand-icon"><el-icon :size="40"><Cloudy /></el-icon></div>
          <p class="feature-subtitle">Cloud Document System</p>
          <h1 class="feature-title">私有云文档<br/>管理系统</h1>
          <div class="brand-divider"></div>
          <p class="feature-desc hero-desc">集中管控企业数字资产，赋能团队高效协同与知识沉淀。</p>
        </div>

        <div class="feature-cards">
          <div class="feature-card">
            <div class="feature-icon-large">01</div>
            <div>
              <h2 class="feature-title">毫秒级实时协同</h2>
              <p class="feature-desc">基于 Yjs CRDT 算法构建，支持多人实时协作编辑富文本与 Markdown，彻底告别文档冲突。</p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon-large">02</div>
            <div>
              <h2 class="feature-title">全场景 AI 赋能</h2>
              <p class="feature-desc">集成 DeepSeek/GLM 等多模型，基于 RAG 架构实现企业知识问答、自动生成与智能总结。</p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon-large">03</div>
            <div>
              <h2 class="feature-title">金融级安全防护</h2>
              <p class="feature-desc">全链路 RSA 加密传输，结合细粒度 RBAC 权限与内网 IP 准入控制，保障核心数据资产绝对安全。</p>
            </div>
          </div>
        </div>
      </div><!-- /presentation-content -->
    </div>

    <div class="login-form-area">
      <div class="login-card">
        <AnimatedAvatar :isBlind="isPasswordFocus" />
        <h2 class="login-title">欢迎登录</h2>
        <p class="login-desc">请使用企业工号登录系统</p>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent.stop="handleLogin" @keyup.enter.prevent="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入企业工号" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @focus="isPasswordFocus = true" @blur="isPasswordFocus = false" />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="rememberMe">记住密码</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-btn" :loading="loading" @click.prevent="handleLogin">
              <el-icon><Promotion /></el-icon> 登 录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="login-tip">
          <el-icon><InfoFilled /></el-icon> 无需注册，请使用预置账号
        </div>
      </div>
      <div class="login-footer">© 2026 私有云知识库</div>
    </div>

    <!-- 点选文字验证码弹窗 -->
    <el-dialog
      v-model="captchaDialogVisible"
      title="安全验证"
      width="400px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="true"
      align-center
      @open="onCaptchaDialogOpen"
    >
      <div class="captcha-dialog-body">
        <TextCaptcha
          ref="captchaRef"
          :image="captchaData?.image || ''"
          :prompt="captchaData?.prompt || ''"
          :loading="captchaLoading"
          @verify="onCaptchaVerify"
          @refresh="loadCaptcha"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Cloudy, Promotion, InfoFilled, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getCaptcha, type CaptchaData, type CaptchaClick } from '@/api/auth'
import { fetchRsaPublicKey, encryptPassword } from '@/utils/rsa'
import TextCaptcha from '@/components/TextCaptcha.vue'
import AnimatedAvatar from '@/components/AnimatedAvatar.vue'

// --- 登录业务逻辑变量 ---
const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const captchaRef = ref<InstanceType<typeof TextCaptcha>>()
const loading = ref(false)
const rememberMe = ref(false)
const isPasswordFocus = ref(false)

// RSA公钥缓存
const rsaPublicKey = ref('')
// 安全模块加载失败原因
const securityError = ref('')
// 安全模块是否加载中
const rsaLoading = ref(true)
// 验证码数据
const captchaData = ref<CaptchaData | null>(null)
const captchaLoading = ref(false)
// 验证码弹窗
const captchaDialogVisible = ref(false)

const form = reactive({
  username: localStorage.getItem('remembered_username') || '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// --- UI交互相关变量 ---
const presentationRef = ref<HTMLElement | null>(null)

// --- tsparticles 配置 (明亮模式下的 AI 脑图星轨) ---
const particlesOptions = ref<any>({
  fullScreen: { enable: false },
  background: { color: 'transparent' },
  fpsLimit: 60,
  particles: {
    // 稍微减少数量，增加呼吸感
    number: { value: 60, density: { enable: true, area: 800 } },
    // 采用品牌蓝
    color: { value: '#409eff' },
    links: {
      enable: true,
      color: '#409eff',
      distance: 160,
      opacity: 0.45,
      width: 1.5,
    },
    move: {
      enable: true,
      speed: 0.4,
      direction: 'none',
      random: true,
      outModes: 'out',
    },
    size: { value: { min: 1.5, max: 3.5 } },
    opacity: {
      value: { min: 0.3, max: 0.8 },
      animation: { enable: true, speed: 0.5, minimumValue: 0.3 },
    },
  },
  interactivity: {
    // 鼠标靠近时产生连线，契合"知识连接"的隐喻
    events: { onHover: { enable: true, mode: 'grab' } },
    modes: { grab: { distance: 180, links: { opacity: 0.4 } } },
  },
  detectRetina: true,
})

// --- 生命周期函数 ---
onMounted(async () => {
  // 原生捕获阶段拦截表单提交
  document.querySelector('.login-card form')?.addEventListener('submit', e => e.preventDefault(), { capture: true })

  // 读取记住密码状态（仅记住用户名，不保存密码）
  rememberMe.value = localStorage.getItem('remember_me') === 'true'

  // 1. 初始化 RSA
  await loadRsaPublicKey()
  rsaLoading.value = false
  if (!rsaPublicKey.value) {
    ElMessage.warning('安全服务连接失败，登录功能不可用，请检查网络连接')
  }

})

// --- 登录业务方法 ---

/** 获取RSA公钥 */
async function loadRsaPublicKey() {
  try {
    rsaPublicKey.value = await fetchRsaPublicKey()
    securityError.value = ''
  } catch (err: any) {
    console.error('获取RSA公钥失败', err)
    securityError.value = err?.message || '安全模块加载失败，请检查网络'
  }
}

/** 获取验证码 */
async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const res = await getCaptcha()
    captchaData.value = res.data.data
  } catch (err) {
    console.error('获取验证码失败', err)
    ElMessage.error('验证码加载失败，请重试')
  } finally {
    captchaLoading.value = false
  }
}

/** 弹窗打开时加载验证码 */
async function onCaptchaDialogOpen() {
  captchaRef.value?.reset()
  await Promise.all([loadCaptcha(), loadRsaPublicKey()])
}

/** 点选验证通过回调 → 发送登录请求 */
async function onCaptchaVerify(clicks: CaptchaClick[]) {
  if (!captchaData.value || !rsaPublicKey.value) {
    captchaRef.value?.setResult('fail')
    ElMessage.error(securityError.value || '安全模块未就绪，请刷新重试')
    return
  }

  loading.value = true
  try {
    const encryptedPassword = encryptPassword(form.password, rsaPublicKey.value)

    await userStore.loginWithCredentials({
      username: form.username,
      encryptedPassword,
      captchaKey: captchaData.value.captchaKey,
      captchaClicks: clicks,
    })

    // ✅ 登录成功：保存记住用户名
    if (rememberMe.value) {
      localStorage.setItem('remember_me', 'true')
      localStorage.setItem('remembered_username', form.username)
    } else {
      localStorage.removeItem('remember_me')
      localStorage.removeItem('remembered_username')
    }

    captchaRef.value?.setResult('success')
    await new Promise(resolve => setTimeout(resolve, 400))
    captchaDialogVisible.value = false
    loading.value = false
    ElMessage.success('登录成功')
    router.push('/dashboard').catch(() => {
      window.location.href = '/'
    })
  } catch (err: any) {
    loading.value = false
    const msg = err?.message || '登录失败'
    ElMessage.error(msg)
    // ❌ 登录失败 → 显示失败动画并刷新验证码
    captchaRef.value?.setResult('fail')
    await Promise.all([loadCaptcha(), loadRsaPublicKey()])
  }
}

/** 点击登录按钮 → 先校验表单，再弹出验证码弹窗 */
async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (rsaLoading.value) {
    ElMessage.warning('安全模块正在初始化，请稍后再试')
    return
  }

  if (!rsaPublicKey.value) {
    ElMessage.error(securityError.value || '安全模块未就绪，请刷新页面')
    return
  }

  // 弹出点选文字验证码弹窗
  captchaDialogVisible.value = true
}
</script>

<style scoped>
/* 整体布局 */
.login-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #f0f7ff 0%, #ffffff 100%);
  position: relative;
}

/* 局部粒子背景：限定在左侧展示区 */
.particle-bg-local {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
}
.particle-bg-local :deep(#tsparticles) {
  width: 100%;
  height: 100%;
}

/* --- 左侧展示区：现代 Light SaaS 明亮风格 --- */
.presentation-area {
  flex: 6;
  position: relative;
  overflow: hidden;
  z-index: 1;
  /* 极浅的蓝灰渐变，与全局 f7f8fa 呼应 */
  background: linear-gradient(135deg, #f5f7fa 0%, #e8f0f9 100%);
}

/* 动态流体呼吸光晕 (Vibe Motion 核心) */
.glow-layer {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  z-index: 1;
  opacity: 0.9;
  animation: floatGlow 6s infinite alternate cubic-bezier(0.4, 0, 0.2, 1);
}
.glow-1 {
  width: 400px;
  height: 400px;
  background: rgba(64, 158, 255, 0.4);
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}
.glow-2 {
  width: 500px;
  height: 500px;
  background: rgba(51, 204, 204, 0.3);
  bottom: -150px;
  right: -100px;
  animation-delay: -5s;
}
@keyframes floatGlow {
  0%   { transform: translate(0, 0) scale(1); }
  50%  { transform: translate(150px, 80px) scale(1.15); }
  100% { transform: translate(-80px, 150px) scale(0.85); }
}

/* 几何网格背景层：浅色科技网格 */
.presentation-area::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-image:
    linear-gradient(rgba(0, 0, 0, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
  z-index: 0;
}

/* 右下角版本标识 */
.presentation-area::after {
  content: 'v2.6 · PRIVATE CLOUD';
  position: absolute;
  bottom: 28px;
  right: 36px;
  font-family: 'Courier New', 'Consolas', monospace;
  font-size: 11px;
  letter-spacing: 2px;
  color: rgba(0, 0, 0, 0.25);
  z-index: 1;
}

/* 内容层 */
.presentation-content {
  position: relative;
  z-index: 10;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px 64px;
  box-sizing: border-box;
  overflow-y: auto;
  scrollbar-width: none;
  color: #1d1e23;
}
.presentation-content::-webkit-scrollbar {
  display: none;
}

/* 顶部品牌区 */
.hero-section {
  margin-bottom: 48px;
  position: relative;
}
.hero-section::before {
  content: 'CLOUD DOCUMENT SYSTEM';
  display: block;
  font-family: 'Courier New', 'Consolas', monospace;
  font-size: 12px;
  letter-spacing: 3px;
  color: rgba(0, 0, 0, 0.4);
  margin-bottom: 20px;
}
.hero-section .feature-title {
  font-size: 40px;
  color: #1d1e23;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0;
  margin-bottom: 0;
}
.hero-desc {
  max-width: 460px;
  margin-top: 16px;
  color: #606266;
  font-size: 15px;
}

/* 特性列表：半透明浅色毛玻璃卡片 */
.feature-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 520px;
}

.feature-card {
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: var(--radius-card, 16px);
  padding: 20px 24px;
  text-align: left;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  transition: all var(--duration-normal, 300ms) var(--ease-out, cubic-bezier(0.34, 1.56, 0.64, 1));
  display: flex;
  align-items: flex-start;
  gap: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
  /* 卡片层级与进场动画 */
  z-index: 2;
  position: relative;
  animation: cardFadeInUp 0.8s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
.feature-card:nth-child(1) { animation-delay: 0.1s; }
.feature-card:nth-child(2) { animation-delay: 0.2s; }
.feature-card:nth-child(3) { animation-delay: 0.3s; }
@keyframes cardFadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to   { opacity: 1; transform: translateY(0); }
}
.feature-card:hover {
  background: rgba(255, 255, 255, 0.85);
  border-color: #ffffff;
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.06);
}

/* --- 左侧内容样式细节 --- */
.brand-icon {
  font-size: 40px;
  margin-bottom: 16px;
  color: #409eff;
}
.feature-icon-large {
  font-size: 16px;
  font-weight: 700;
  color: #409eff;
  background: #ffffff;
  width: 40px;
  height: 40px;
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-btn, 10px);
  border: 1px solid rgba(64, 158, 255, 0.2);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
  font-family: 'Courier New', 'Consolas', monospace;
}
.feature-title {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 6px;
  color: #1d1e23;
}
.hero-section .feature-title {
  font-size: 40px;
  margin-bottom: 0;
}
.feature-subtitle {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.4);
  letter-spacing: 3px;
  margin-bottom: 12px;
  margin-top: 0;
  font-family: 'Courier New', 'Consolas', monospace;
  text-transform: uppercase;
}
.feature-desc {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  margin: 0;
}
.brand-divider {
  width: 48px;
  height: 3px;
  background: #409eff;
  border-radius: 2px;
  margin: 20px 0 0 0;
}

/* 右侧登录表单区 */
.login-form-area {
  flex: 4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  padding: 40px;
  position: relative;
  box-shadow: -10px 0 40px rgba(0, 0, 0, 0.06);
  border-left: 1px solid rgba(0, 0, 0, 0.04);
  z-index: 1;
}

/* ==================== 登录页深度美化 ==================== */
.login-card {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-dialog, 20px);
  padding: 40px;
  box-shadow: var(--shadow-l, 0 12px 40px rgba(0, 0, 0, 0.12));
  transition: all var(--duration-normal, 300ms) var(--ease-out, cubic-bezier(0.2,0.8,0.2,1));
  /* 增强毛玻璃的模糊度和边框高光，形成更强的立体剥离感 */
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 1);
}
.login-card:hover {
  box-shadow: 0 20px 60px rgba(0,0,0,0.14);
  border-color: var(--el-border-color);
}

/* 1. 输入框重塑：去边框、大圆角、软阴影 */
.login-card :deep(.el-input__wrapper) {
  border-radius: 12px !important;
  background-color: #f5f7fa !important;
  box-shadow: none !important; /* 彻底去掉默认边框 */
  border: 1px solid transparent;
  padding: 8px 16px;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* 输入框悬浮与聚焦：微浮雕发光效果 */
.login-card :deep(.el-input__wrapper:hover) {
  background-color: #f0f2f5 !important;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  background-color: #ffffff !important;
  border-color: #409eff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.12), 0 0 0 2px rgba(64, 158, 255, 0.1) !important;
  transform: translateY(-1px);
}
.login-card :deep(.el-input__inner) {
  height: 28px;
  font-size: 15px;
  color: #1d1e23;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
  text-align: center;
}

.login-desc {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 36px;
  text-align: center;
}

/* 2. 登录大按钮：光晕与按压反馈 */
.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px !important;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #409eff, #337ecc);
  border: none;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.25);
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.35);
  background: linear-gradient(135deg, #66b1ff, #409eff);
}
.login-btn:active {
  transform: translateY(1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.login-tip {
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.login-footer {
  position: absolute;
  bottom: 24px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.captcha-dialog-body {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

/* 表单元素跟随 Element Plus 默认浅色主题 */
.login-card :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

@media (max-width: 768px) {
  .presentation-area {
    display: none;
  }
  .login-form-area {
    flex: 1;
    background: rgba(255, 255, 255, 0.92);
  }
}
</style>
