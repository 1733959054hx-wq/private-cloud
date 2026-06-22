<template>
  <div class="text-captcha">
    <!-- 验证码图片区域 -->
    <div class="captcha-image-wrapper" @click="onImageClick">
      <img
        v-if="image"
        :src="image"
        alt="验证码"
        class="captcha-img"
        draggable="false"
        @dragstart.prevent
      />
      <!-- 加载中 -->
      <div v-else class="captcha-loading">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <!-- 点击标记 -->
      <div
        v-for="(click, idx) in clicks"
        :key="idx"
        class="click-marker"
        :style="{ left: click.x + 'px', top: click.y + 'px' }"
      >
        <span class="marker-index">{{ idx + 1 }}</span>
      </div>
      <!-- 结果反馈蒙层 -->
      <div v-if="resultState" class="result-overlay" :class="resultState">
        <el-icon :size="32">
          <SuccessFilled v-if="resultState === 'success'" />
          <CircleCloseFilled v-else />
        </el-icon>
        <span>{{ resultState === 'success' ? '验证成功' : '验证失败' }}</span>
      </div>
    </div>

    <!-- 提示文字 -->
    <div class="captcha-prompt" v-if="prompt">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ prompt }}</span>
    </div>

    <!-- 已点击序列显示 -->
    <div class="click-sequence" v-if="clicks.length > 0">
      <div class="sequence-label">已点击 {{ clicks.length }} 个位置</div>
    </div>

    <!-- 操作栏 -->
    <div class="captcha-actions">
      <el-button size="small" :icon="RefreshRight" @click="handleReset" :disabled="loading">
        重新选择
      </el-button>
      <el-button size="small" :icon="Refresh" @click="$emit('refresh')" :disabled="loading">
        换一张
      </el-button>
      <el-button
        size="small"
        type="primary"
        :disabled="clicks.length === 0 || loading"
        @click="handleSubmit"
      >
        确认验证
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { RefreshRight, Refresh, InfoFilled, Loading, SuccessFilled, CircleCloseFilled } from '@element-plus/icons-vue'

interface CaptchaClick {
  x: number
  y: number
}

const props = defineProps<{
  image: string
  prompt: string
  loading: boolean
}>()

const emit = defineEmits<{
  verify: [clicks: CaptchaClick[]]
  refresh: []
}>()

const clicks = ref<CaptchaClick[]>([])
const resultState = ref<'success' | 'fail' | null>(null)

/** 监听图片变化时重置点击记录 */
watch(() => props.image, () => {
  clicks.value = []
  resultState.value = null
})

/** 图片点击处理 */
function onImageClick(e: MouseEvent) {
  if (resultState.value) return

  const target = e.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const x = Math.round(e.clientX - rect.left)
  const y = Math.round(e.clientY - rect.top)

  clicks.value.push({ x, y })
}

/** 重置点击 */
function handleReset() {
  clicks.value = []
  resultState.value = null
}

/** 提交验证 */
function handleSubmit() {
  emit('verify', [...clicks.value])
}

/** 外部调用：设置验证结果 */
function setResult(state: 'success' | 'fail') {
  resultState.value = state
  if (state === 'fail') {
    setTimeout(() => {
      clicks.value = []
      resultState.value = null
    }, 1200)
  }
}

/** 外部调用：重置状态 */
function reset() {
  clicks.value = []
  resultState.value = null
}

defineExpose({ setResult, reset })
</script>

<style scoped>
.text-captcha {
  width: 100%;
  user-select: none;
}

.captcha-image-wrapper {
  position: relative;
  width: 320px;
  height: 160px;
  margin: 0 auto;
  border-radius: 8px;
  overflow: hidden;
  cursor: crosshair;
  border: 2px solid #e4e7ed;
  background: #f5f7fa;
  transition: border-color 0.3s;
}

.captcha-image-wrapper:hover {
  border-color: #409eff;
}

.captcha-img {
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
}

.captcha-loading {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
}

.click-marker {
  position: absolute;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.85);
  border: 2px solid #fff;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
  animation: markerPop 0.2s ease-out;
  z-index: 2;
}

@keyframes markerPop {
  0% { transform: translate(-50%, -50%) scale(0); }
  70% { transform: translate(-50%, -50%) scale(1.2); }
  100% { transform: translate(-50%, -50%) scale(1); }
}

.marker-index {
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.result-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  z-index: 10;
  animation: overlayFade 0.3s ease;
}

@keyframes overlayFade {
  from { opacity: 0; }
  to { opacity: 1; }
}

.result-overlay.success {
  background: rgba(103, 194, 58, 0.85);
  color: #fff;
}

.result-overlay.fail {
  background: rgba(245, 108, 108, 0.85);
  color: #fff;
}

.captcha-prompt {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 12px;
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}

.click-sequence {
  margin-top: 8px;
  text-align: center;
}

.sequence-label {
  font-size: 12px;
  color: #909399;
}

.captcha-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 12px;
}
</style>
