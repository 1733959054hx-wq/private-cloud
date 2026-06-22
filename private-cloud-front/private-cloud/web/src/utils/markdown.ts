/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-unused-vars */

// @ts-ignore - markdown-it-task-lists 没有类型声明
import taskLists from 'markdown-it-task-lists'
// @ts-ignore - markdown-it-footnote 没有类型声明
import footnote from 'markdown-it-footnote'
import MarkdownIt from 'markdown-it'
import katex from '@traptitech/markdown-it-katex'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

// 自己实现安全的 HTML 转义，免去对 md.utils 的依赖，彻底杜绝 utils 相关的类型警告
function escapeHtml(unsafe: string): string {
  return unsafe
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

// 采用最稳健的初始化传参，并将 md 显式声明为 any，彻底清除 renderer 与 highlight 的编辑器报错
const md: any = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight: function (str: string, lang: string): string {
    // 关键修正：强制将语言标识转化为小写（如 XML -> xml），解决 xml/json 等大写标签不触发高亮的问题
    const cleanLang = (lang || '').trim().toLowerCase()
    if (cleanLang && hljs.getLanguage(cleanLang)) {
      try {
        return hljs.highlight(str, { language: cleanLang }).value
      } catch (__) { /* ignore */ }
    }
    return ''
  }
} as any).enable('table')

// 加载插件
md.use(taskLists)
md.use(footnote)
md.use(katex)

// 只允许 http/https/mailto 协议的链接，其余一律不转为 <a>
md.validateLink = function (url: string): boolean {
  return /^(https?:|mailto:|#|\/)/i.test(url)
}

// 自定义代码块渲染规则（带语言标签和复制按钮）
md.renderer.rules.fence = (tokens: any[], idx: number, options: any, _env: any, _self: any): string => {
  const token = tokens[idx]
  const code = token.content.trim()
  const lang = token.info.trim() || 'txt'

  const highlightedCode = options.highlight
    ? options.highlight(token.content, lang, '')
    : escapeHtml(token.content)

  const safeCode = encodeURIComponent(code)

  return `<div class="code-block-wrapper">
    <div class="code-block-header">
      <span class="code-block-lang">${lang}</span>
      <button class="code-block-copy-btn" data-code="${safeCode}">复制</button>
    </div>
    <pre class="hljs"><code>${highlightedCode || escapeHtml(token.content)}</code></pre>
  </div>`
}

// 安全链接过滤：只允许 http/https/mailto 协议，其余一律渲染为纯文本
const defaultLinkOpenRender = md.renderer.rules.link_open
md.renderer.rules.link_open = (tokens: any[], idx: number, options: any, env: any, self: any): string => {
  const hrefIdx = tokens[idx].attrIndex('href')
  if (hrefIdx >= 0) {
    const href = tokens[idx].attrs[hrefIdx][1]
    if (!/^(https?:|mailto:|#|\/)/i.test(href)) {
      // 非安全协议：输出纯文本 span 代替 <a>
      return '<span class="unsafe-link">'
    }
  }
  if (defaultLinkOpenRender) {
    return defaultLinkOpenRender(tokens, idx, options, env, self)
  }
  return self.renderToken(tokens, idx, options)
}

// 对应的 link_close 也需要处理：当 link_open 被替换为 span 时，close 标签也要匹配
const defaultLinkCloseRender = md.renderer.rules.link_close
md.renderer.rules.link_close = (tokens: any[], idx: number, options: any, env: any, self: any): string => {
  // 向前查找匹配的 open 标签，判断是否被替换为了 span
  for (let i = idx - 1; i >= 0; i--) {
    if (tokens[i].type === 'link_open') {
      const hrefIdx = tokens[i].attrIndex('href')
      if (hrefIdx >= 0) {
        const href = tokens[i].attrs[hrefIdx][1]
        if (!/^(https?:|mailto:|#|\/)/i.test(href)) {
          return '</span>'
        }
      }
      break
    }
  }
  if (defaultLinkCloseRender) {
    return defaultLinkCloseRender(tokens, idx, options, env, self)
  }
  return self.renderToken(tokens, idx, options)
}

// ==========================================
// 核心：仅做 Markdown 结构规范化，不猜测内容
// ==========================================
function normalizeMarkdown(text: string): string {
  if (!text) return ''

  // 1. 统一换行符
  text = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

  // 2. ``` 紧贴文字时加换行
  text = text.replace(/([^\s\n])(```)/g, '$1\n$2')

  // 3. 标题前缺空格修复：##标题 → ## 标题
  text = text.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')

  return text
}

// ==========================================
// 辅助流式拼接：仅闭合未配对的代码块
// ==========================================
function patchIncompleteMarkdown(text: string): string {
  // 仅闭合未配对的 ``` 代码块，不做其他猜测性修改
  let fenceCount = 0
  for (let i = 0; i < text.length; i++) {
    if (text.substring(i, i + 3) === '```') {
      fenceCount++
      i += 2
    }
  }
  if (fenceCount % 2 !== 0) {
    text += '\n```'
  }
  return text
}

// ==========================================
// 统一渲染入口
// ==========================================
export function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    // 调试模式：输出原始内容
    if (import.meta.env.DEV) {
      console.log('[markdown] 原始内容:', text)
    }
    
    const normalized = normalizeMarkdown(text)
    
    // 调试模式：输出规范化后内容
    if (import.meta.env.DEV) {
      console.log('[markdown] 规范化后:', normalized)
    }
    
    return md.render(normalized)
  } catch {
    return escapeText(text)
  }
}

export function renderStreamingMarkdown(text: string): string {
  if (!text) return ''
  try {
    const patched = patchIncompleteMarkdown(text)
    const normalized = normalizeMarkdown(patched)
    return md.render(normalized)
  } catch {
    return escapeText(text)
  }
}

function escapeText(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}
