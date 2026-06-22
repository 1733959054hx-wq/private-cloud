<template>
  <div class="slider-captcha" :class="{ 'is-success': status === 'success', 'is-fail': status === 'fail' }">
    <!-- 图片区域 -->
    <div class="captcha-image-area" ref="imageArea">
      <!-- 背景图 -->
      <div class="captcha-bg" v-html="bgImage"></div>
      <!-- 滑块拼图块：固定在Y轴上，只沿X轴移动 -->
      <div
        class="captcha-piece"
        :style="{ transform: `translate(${currentX}px, ${y - 6}px)` }"
        v-html="sliderImage"
      ></div>
      <!-- 刷新按钮 -->
      <div class="captcha-refresh" @click="refresh" title="刷新验证码">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="white">
          <path d="M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
        </svg>
      </div>
      <!-- 加载状态 -->
      <div class="captcha-loading" v-if="loading">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
      <!-- 成功/失败遮罩 -->
      <transition name="fade">
        <div class="captcha-result" v-if="status">
          <span v-if="status === 'success'" class="result-success">
            <svg viewBox="0 0 24 24" width="32" height="32" fill="#52c41a">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
            </svg>
            验证成功
          </span>
          <span v-else class="result-fail">
            <svg viewBox="0 0 24 24" width="32" height="32" fill="#ff4d4f">
              <path d="M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z"/>
            </svg>
            验证失败，请重试
          </span>
        </div>
      </transition>
    </div>

    <!-- 滑动条 -->
    <div class="slider-track" ref="sliderTrack">
      <div class="slider-fill" :style="{ width: currentX + 'px' }"></div>
      <div
        class="slider-thumb"
        :class="{ dragging: isDragging, success: status === 'success', fail: status === 'fail' }"
        :style="{ transform: `translateX(${currentX}px)` }"
        @mousedown.prevent="startDrag"
        @touchstart.prevent="startDrag"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M8 5v14l11-7z" v-if="status !== 'success' && status !== 'fail'" />
          <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" v-else-if="status === 'success'" />
          <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" v-else />
        </svg>
      </div>
      <span class="slider-hint" v-if="!isDragging && !status && currentX === 0">
        &nbsp;&nbsp;向右拖动滑块完成验证
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'

const props = defineProps<{
  captchaKey: string
  bgImage: string
  sliderImage: string
  y: number
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'verify', x: number): void
  (e: 'refresh'): void
}>()

const status = ref<'success' | 'fail' | null>(null)
const isDragging = ref(false)
const currentX = ref(0)
const startX = ref(0)
const imageArea = ref<HTMLElement>()
const sliderTrack = ref<HTMLElement>()

// 背景宽320，拼图块宽56，所以最大滑动距离 = 320 - 56 = 264
const PIECE_WIDTH = 56
const CANVAS_WIDTH = 320
const MAX_X = CANVAS_WIDTH - PIECE_WIDTH

function startDrag(e: MouseEvent | TouchEvent) {
  if (status.value === 'success' || props.loading) return
  isDragging.value = true
  startX.value = getClientX(e) - currentX.value
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
  document.addEventListener('touchmove', onDrag, { passive: false })
  document.addEventListener('touchend', endDrag)
}

function onDrag(e: MouseEvent | TouchEvent) {
  if (!isDragging.value) return
  e.preventDefault()
  const clientX = getClientX(e)
  let x = clientX - startX.value
  x = Math.max(0, Math.min(x, MAX_X))
  currentX.value = x
}

function endDrag() {
  if (!isDragging.value) return
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)

  // 滑动距离太短视为无效
  if (currentX.value < 10) {
    currentX.value = 0
    return
  }

  // 发送X坐标给父组件验证
  emit('verify', Math.round(currentX.value))
}

function getClientX(e: MouseEvent | TouchEvent): number {
  return e instanceof MouseEvent ? e.clientX : e.touches[0].clientX
}

/** 由父组件调用设置验证结果 */
function setResult(result: 'success' | 'fail') {
  status.value = result
  if (result === 'fail') {
    setTimeout(() => {
      currentX.value = 0
      status.value = null
    }, 1200)
  }
}

/** 刷新验证码 */
function refresh() {
  currentX.value = 0
  status.value = null
  emit('refresh')
}

/** 重置组件状态 */
function reset() {
  currentX.value = 0
  status.value = null
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)
})

defineExpose({ setResult, reset, refresh })
</script>

<style scoped>
.slider-captcha {
  width: 320px;
  user-select: none;
}

.captcha-image-area {
  position: relative;
  width: 320px;
  height: 160px;
  border-radius: 8px;
  overflow: hidden;
  background: #e8e8e8;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.captcha-bg {
  position: absolute;
  inset: 0;
}

.captcha-bg :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.captcha-piece {
  position: absolute;
  top: 0;
  left: 0;
  will-change: transform;
}

.captcha-piece :deep(svg) {
  width: 56px;
  height: 56px;
  display: block;
  overflow: visible;
}

.captcha-refresh {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  z-index: 5;
}

.captcha-refresh:hover {
  background: rgba(0, 0, 0, 0.6);
  transform: rotate(180deg);
}

.captcha-loading {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: #909399;
  z-index: 10;
}

.loading-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid #e4e7ed;
  border-top-color: #409EFF;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.captcha-result {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 8;
}

.result-success,
.result-fail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  padding: 12px 24px;
  border-radius: 8px;
}

.result-success {
  background: rgba(255, 255, 255, 0.95);
  color: #52c41a;
}

.result-fail {
  background: rgba(255, 255, 255, 0.95);
  color: #ff4d4f;
  animation: shake 0.5s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-8px); }
  40% { transform: translateX(8px); }
  60% { transform: translateX(-4px); }
  80% { transform: translateX(4px); }
}

/* 滑动条 */
.slider-track {
  position: relative;
  height: 40px;
  margin-top: 12px;
  border-radius: 20px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  overflow: visible;
}

.slider-fill {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  border-radius: 20px 0 0 20px;
  background: linear-gradient(90deg, #409EFF, #66b1ff);
  transition: width 0.05s ease-out;
}

.is-success .slider-fill {
  background: linear-gradient(90deg, #52c41a, #73d13d);
}

.is-fail .slider-fill {
  background: linear-gradient(90deg, #ff4d4f, #ff7875);
}

.slider-thumb {
  position: absolute;
  left: 0;
  top: 50%;
  width: 40px;
  height: 40px;
  margin-top: -20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  transition: box-shadow 0.3s, background 0.3s;
  color: #409EFF;
  z-index: 2;
}

.slider-thumb:hover {
  box-shadow: 0 3px 12px rgba(64, 158, 255, 0.3);
}

.slider-thumb.dragging {
  cursor: grabbing;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  background: #409EFF;
  color: #fff;
}

.slider-thumb.success {
  background: #52c41a;
  color: #fff;
}

.slider-thumb.fail {
  background: #ff4d4f;
  color: #fff;
  animation: thumbShake 0.5s ease-in-out;
}

@keyframes thumbShake {
  0%, 100% { transform: translateX(var(--thumb-x, 0)); }
  20% { transform: translateX(calc(var(--thumb-x, 0) - 6px)); }
  40% { transform: translateX(calc(var(--thumb-x, 0) + 6px)); }
  60% { transform: translateX(calc(var(--thumb-x, 0) - 3px)); }
  80% { transform: translateX(calc(var(--thumb-x, 0) + 3px)); }
}

.slider-hint {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  font-size: 13px;
  color: #c0c4cc;
  white-space: nowrap;
  pointer-events: none;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
