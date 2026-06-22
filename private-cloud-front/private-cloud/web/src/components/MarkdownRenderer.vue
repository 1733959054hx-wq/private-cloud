<template>
  <div
    class="ai-studio-markdown-body"
    v-html="renderedHtml"
    @click="handleContainerClick"
  ></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown, renderStreamingMarkdown } from '@/utils/markdown'

const props = defineProps<{
  content: string
  isStreaming?: boolean // 是否正在流式输出
}>()

// 根据状态调用不同渲染器
const renderedHtml = computed(() => {
  if (props.isStreaming) {
    return renderStreamingMarkdown(props.content)
  }
  return renderMarkdown(props.content)
})

// 高效事件委托：捕获代码块 header 里的复制按钮点击 + 拦截链接跳转
function handleContainerClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (target && target.classList.contains('code-block-copy-btn')) {
    const encodedCode = target.getAttribute('data-code')
    if (encodedCode) {
      const originalCode = decodeURIComponent(encodedCode)
      navigator.clipboard.writeText(originalCode).then(() => {
        target.textContent = '已复制'
        target.classList.add('copied')
        setTimeout(() => {
          target.textContent = '复制'
          target.classList.remove('copied')
        }, 2000)
      })
    }
    return
  }
  // 拦截所有 <a> 标签的默认跳转行为，仅高亮显示不导航
  const anchor = target.closest('a')
  if (anchor) {
    event.preventDefault()
    event.stopPropagation()
  }
}
</script>

<style scoped>
/* ==================== 仿 AI Studio 排版与代码样式 ==================== */
.ai-studio-markdown-body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-size: 14px;
  line-height: 1.65;
  color: #1f1f1f;
}

/* 列表样式 */
.ai-studio-markdown-body :deep(ul),
.ai-studio-markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 10px 0;
}
.ai-studio-markdown-body :deep(li) {
  margin: 4px 0;
}

/* 引用 */
.ai-studio-markdown-body :deep(blockquote) {
  border-left: 4px solid #409eff;
  padding: 8px 16px;
  margin: 12px 0;
  background: #ecf5ff;
  color: #606266;
  border-radius: 0 6px 6px 0;
}

/* 行内代码 */
.ai-studio-markdown-body :deep(code:not(.hljs code)) {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #409eff;
  font-family: monospace;
}

/* ==================== 仿 Google AI Studio 代码块 ==================== */
.ai-studio-markdown-body :deep(.code-block-wrapper) {
  margin: 14px 0;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid #2d2d2d;
}

/* 代码头部栏 */
.ai-studio-markdown-body :deep(.code-block-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #1e1e1e;
  padding: 6px 14px;
  border-bottom: 1px solid #2d2d2d;
  user-select: none;
}

.ai-studio-markdown-body :deep(.code-block-lang) {
  font-size: 11px;
  font-weight: 600;
  color: #858585;
  text-transform: uppercase;
}

/* 复制代码按钮 */
.ai-studio-markdown-body :deep(.code-block-copy-btn) {
  background: transparent;
  border: 1px solid #434343;
  color: #a6a6a6;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ai-studio-markdown-body :deep(.code-block-copy-btn:hover) {
  background: #2d2d2d;
  color: #ffffff;
  border-color: #409eff;
}

.ai-studio-markdown-body :deep(.code-block-copy-btn.copied) {
  background: #409eff;
  color: #ffffff;
  border-color: #409eff;
}

/* 覆盖 highlight.js 背景 */
.ai-studio-markdown-body :deep(pre.hljs) {
  margin: 0 !important;
  padding: 14px !important;
  background: #151515 !important;
  overflow-x: auto;
}

.ai-studio-markdown-body :deep(pre.hljs code) {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
}

/* 链接样式：高亮显示但不可点击跳转 */
.ai-studio-markdown-body :deep(a) {
  color: #409eff;
  text-decoration: none;
  background: #ecf5ff;
  padding: 1px 4px;
  border-radius: 3px;
  cursor: default;
  pointer-events: auto;
}
.ai-studio-markdown-body :deep(a:hover) {
  background: #d9ecff;
}
</style>
