<template>
  <div class="voice-input-wrapper">
    <el-button v-if="showText" :class="['voice-btn', { recording: isRecording }]" :icon="Microphone" @click="start">语音录入</el-button>
    <el-button v-else :class="['voice-btn', { recording: isRecording }]" :icon="Microphone" circle size="small"
      :title="hint" @click="start" />

    <el-dialog v-model="visible" title="语音录入" width="400px" :close-on-click-modal="false"
      :show-close="status === 'success' || status === 'error' || status === 'manual'" destroy-on-close center>
      <div class="body">
        <!-- 录音中 -->
        <div v-if="status === 'recording'" class="st">
          <div class="icon recording"><div class="ring"></div><el-icon :size="48"><Microphone /></el-icon></div>
          <div class="wave"><span v-for="i in 5" :key="i" class="bar" :style="{ animationDelay: i * 0.1 + 's' }"></span></div>
          <p class="hint red">正在聆听…</p>
        </div>
        <!-- 识别中 -->
        <div v-if="status === 'recognizing'" class="st">
          <div class="icon processing"><el-icon :size="48" class="is-loading"><Loading /></el-icon></div>
          <p class="hint">正在识别…</p>
        </div>
        <!-- 成功 -->
        <div v-if="status === 'success'" class="st">
          <div class="icon success"><el-icon :size="48" color="#67C23A"><CircleCheckFilled /></el-icon></div>
          <p class="hint">识别完成</p>
          <div class="result"><el-input v-model="text" type="textarea" :rows="3" placeholder="识别结果（可手动修改）" /></div>
          <div class="btns">
            <el-button @click="retry"><el-icon><RefreshRight /></el-icon>重录</el-button>
            <el-button type="primary" @click="confirm"><el-icon><Check /></el-icon>确认</el-button>
          </div>
        </div>
        <!-- 失败：降级为手动输入 -->
        <div v-if="status === 'error'" class="st">
          <div class="icon error"><el-icon :size="48" color="#F56C6C"><CircleCloseFilled /></el-icon></div>
          <p class="hint red">{{ errMsg || '识别失败' }}</p>
          <p class="sub-hint">语音识别需要连接外部服务，国内网络可能不可用。<br>您可以直接在下方输入文字：</p>
          <div class="result"><el-input v-model="text" type="textarea" :rows="3" placeholder="请输入内容" /></div>
          <div class="btns">
            <el-button @click="retry"><el-icon><RefreshRight /></el-icon>重试语音</el-button>
            <el-button type="primary" @click="confirm"><el-icon><Check /></el-icon>使用此文字</el-button>
          </div>
        </div>
        <!-- 不支持：直接手动输入 -->
        <div v-if="status === 'manual'" class="st">
          <div class="icon info"><el-icon :size="48" color="#909399"><InfoFilled /></el-icon></div>
          <p class="hint">手动输入</p>
          <p class="sub-hint">当前浏览器不支持语音识别，请直接输入：</p>
          <div class="result"><el-input v-model="text" type="textarea" :rows="3" placeholder="请输入内容" /></div>
          <div class="btns">
            <el-button type="primary" @click="confirm"><el-icon><Check /></el-icon>确认</el-button>
          </div>
        </div>
      </div>
      <template v-if="status === 'recording'" #footer>
        <el-button type="danger" @click="stop" size="large" :icon="VideoPause">停止录音</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { Microphone, Loading, CircleCheckFilled, CircleCloseFilled, RefreshRight, Check, VideoPause, InfoFilled } from '@element-plus/icons-vue'

const p = defineProps<{ modelValue?: string; showText?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', t: string): void; (e: 'recognized', t: string): void }>()

const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
const isSupported = ref(!!SR)
const visible = ref(false)
const status = ref<'recording' | 'recognizing' | 'success' | 'error' | 'manual'>('recording')
const text = ref('')
const errMsg = ref('')
const isRecording = ref(false)

let recog: any = null

const hint = computed(() => !isSupported.value ? '语音录入（点击手动输入）' : '语音录入')

function start() {
  if (!SR) {
    // 浏览器不支持 → 直接打开手动输入弹窗
    text.value = ''
    errMsg.value = ''
    status.value = 'manual'
    visible.value = true
    return
  }
  // 清理旧的 recognition 实例
  if (recog) { try { recog.abort() } catch (_: any) {}; recog = null }
  visible.value = true; text.value = ''; errMsg.value = ''; status.value = 'recording'
  recog = new SR(); recog.lang = 'zh-CN'; recog.interimResults = false; recog.continuous = false
  recog.onresult = (e: any) => { text.value = e.results[0][0].transcript; status.value = 'success' }
  recog.onerror = (e: any) => {
    const err = e.error
    if (err === 'not-allowed' || err === 'service-not-allowed') {
      errMsg.value = '麦克风权限被拒绝，请在浏览器中允许使用麦克风'
    } else if (err === 'no-speech') {
      errMsg.value = '未检测到语音，请大声说话'
    } else if (err === 'audio-capture') {
      errMsg.value = '没有可用的麦克风设备'
    } else if (err === 'network') {
      errMsg.value = '语音识别服务连接失败（国内网络环境通常无法访问）'
    } else {
      errMsg.value = '识别失败: ' + err
    }
    status.value = 'error'; isRecording.value = false; recog = null
  }
  recog.onend = () => { isRecording.value = false; if (status.value === 'recording') status.value = 'recognizing' }
  try {
    recog.start()
    isRecording.value = true
  } catch (e: any) {
    // start() 在权限被拒绝或其他原因下可能直接抛异常
    errMsg.value = '启动语音识别失败：' + (e?.message || '未知错误')
    status.value = 'error'
    isRecording.value = false
  }
}

function stop() {
  if (!recog) return
  try { recog.stop() } catch (_: any) {}
}

function confirm() {
  const t = text.value.trim()
  if (t) { emit('update:modelValue', p.modelValue ? p.modelValue + t : t); emit('recognized', t) }
  visible.value = false; recog = null
}

function retry() { visible.value = false; recog = null; setTimeout(start, 200) }

onUnmounted(() => { recog?.abort() })
</script>

<style scoped>
.voice-input-wrapper { display: inline-flex; align-items: center }
.voice-btn { transition: all .3s }
.voice-btn.recording { color: #fff !important; background: #F56C6C !important; border-color: #F56C6C !important; animation: mic-pulse 1.2s infinite }
@keyframes mic-pulse { 0%,100%{ box-shadow: 0 0 0 0 rgba(245,108,108,.5) } 50%{ box-shadow: 0 0 0 8px rgba(245,108,108,0) } }
.body { padding: 20px 0; text-align: center }
.st { display: flex; flex-direction: column; align-items: center; gap: 12px }
.icon { width: 80px; height: 80px; border-radius: 50%; display: flex; align-items: center; justify-content: center; position: relative }
.icon.recording { background: #FEF0F0; color: #F56C6C }
.icon.processing { background: #FDF6EC; color: #E6A23C }
.icon.success { background: #F0F9EB }
.icon.error { background: #FEF0F0 }
.icon.info { background: #F4F4F5 }
.ring { position: absolute; inset: -6px; border-radius: 50%; border: 2px solid #F56C6C; animation: ring-pulse 1.5s ease-out infinite }
@keyframes ring-pulse { 0%{ transform: scale(.8); opacity: 1 } 100%{ transform: scale(1.3); opacity: 0 } }
.wave { display: flex; align-items: center; gap: 3px; height: 30px }
.bar { width: 3px; height: 10px; background: #F56C6C; border-radius: 2px; animation: wave .8s ease-in-out infinite alternate }
@keyframes wave { from{ height: 6px } to{ height: 24px } }
.hint { font-size: 15px; color: #303133; margin: 0; font-weight: 500 }
.hint.red { color: #F56C6C }
.sub-hint { font-size: 12px; color: #909399; margin: 0; line-height: 1.6 }
.result { width: 100%; max-width: 350px }
.btns { display: flex; gap: 12px; margin-top: 8px }
</style>
