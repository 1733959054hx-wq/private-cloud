<template>
  <div class="avatar-container" ref="containerRef">
    <svg viewBox="0 0 120 120" class="avatar-svg">
      <!-- 身体 -->
      <rect x="20" y="20" width="80" height="80" rx="20" fill="#E4E7ED" />
      <!-- 面部底色 -->
      <rect x="30" y="35" width="60" height="40" rx="8" fill="#1A1A2E" />

      <!-- 左眼白 -->
      <ellipse cx="45" cy="55" rx="8" ry="10" fill="#FFFFFF" />
      <!-- 右眼白 -->
      <ellipse cx="75" cy="55" rx="8" ry="10" fill="#FFFFFF" />

      <!-- 瞳孔（跟随鼠标） -->
      <g :style="pupilStyle" class="pupils" :class="{ 'hidden': isBlind }">
        <circle cx="45" cy="55" r="4" fill="#409EFF" />
        <circle cx="75" cy="55" r="4" fill="#409EFF" />
      </g>

      <!-- 闭眼弧线 -->
      <g :class="{ 'visible': isBlind }" class="closed-eyes">
        <path d="M 40 55 Q 45 50 50 55" stroke="#FFFFFF" stroke-width="2" fill="none" />
        <path d="M 70 55 Q 75 50 80 55" stroke="#FFFFFF" stroke-width="2" fill="none" />
      </g>

      <!-- 手臂（捂眼时抬起） -->
      <g :class="{ 'arms-up': isBlind }" class="arms">
        <rect x="35" y="90" width="15" height="25" rx="5" fill="#409EFF" />
        <rect x="70" y="90" width="15" height="25" rx="5" fill="#409EFF" />
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  isBlind: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)
const pupilOffsetX = ref(0)
const pupilOffsetY = ref(0)

const MAX_RADIUS = 3

const pupilStyle = computed(() => ({
  transform: `translate(${pupilOffsetX.value}px, ${pupilOffsetY.value}px)`,
  transition: props.isBlind ? 'transform 0.3s' : 'transform 0.1s ease-out'
}))

const handleMouseMove = (e: MouseEvent) => {
  if (props.isBlind || !containerRef.value) {
    pupilOffsetX.value = 0
    pupilOffsetY.value = 0
    return
  }

  const rect = containerRef.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2

  const deltaX = e.clientX - centerX
  const deltaY = e.clientY - centerY

  const angle = Math.atan2(deltaY, deltaX)
  const distance = Math.min(Math.hypot(deltaX, deltaY) / 30, MAX_RADIUS)

  pupilOffsetX.value = Math.cos(angle) * distance
  pupilOffsetY.value = Math.sin(angle) * distance
}

onMounted(() => {
  document.addEventListener('mousemove', handleMouseMove)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove)
})
</script>

<style scoped>
.avatar-container {
  width: 120px;
  height: 120px;
  margin: 0 auto;
  position: relative;
}
.avatar-svg {
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.pupils {
  transition: opacity 0.2s;
}
.pupils.hidden {
  opacity: 0;
}
.closed-eyes {
  opacity: 0;
  transition: opacity 0.2s;
}
.closed-eyes.visible {
  opacity: 1;
}
.arms {
  transform: translateY(0);
  transition: transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}
.arms.arms-up {
  transform: translateY(-40px);
}
</style>
