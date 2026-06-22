<template>
  <div class="share-preview">
    <!-- 密码验证 -->
    <div v-if="step === 'password'" class="share-auth">
      <div class="auth-card">
        <div class="auth-icon">
          <el-icon :size="48"><Lock /></el-icon>
        </div>
        <h2>加密分享</h2>
        <p class="auth-desc">此文件已加密，请输入访问密码</p>
        <el-input
          v-model="passwordInput"
          type="password"
          placeholder="请输入分享密码"
          size="large"
          show-password
          @keyup.enter="verifyPassword"
        />
        <el-button type="primary" size="large" :loading="authLoading" class="auth-btn" @click="verifyPassword">
          验证
        </el-button>
        <p v-if="authError" class="auth-error">{{ authError }}</p>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-else-if="step === 'loading'" class="share-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>正在加载分享内容...</p>
    </div>

    <!-- 错误 -->
    <div v-else-if="step === 'error'" class="share-error">
      <el-result icon="error" title="分享已失效" :sub-title="errorMessage">
        <template #extra>
          <p class="error-hint">该分享链接可能已过期、被关闭或访问次数已达上限</p>
        </template>
      </el-result>
    </div>

    <!-- 文件预览 -->
    <div v-else-if="step === 'preview'" class="preview-container">
      <div class="preview-header">
        <div class="preview-header-left">
          <span class="preview-filename">{{ fileInfo?.fileName || '文件预览' }}</span>
          <el-tag v-if="fileInfo?.fileType" size="small" :color="getFileTypeColor(fileInfo.fileType)" effect="dark" style="border:none">
            {{ fileInfo.fileType.toUpperCase() }}
          </el-tag>
          <span class="share-info-badge">外部共享</span>
        </div>
        <div class="preview-header-right">
          <el-button @click="handleDownload">
            <el-icon><Download /></el-icon> 下载
          </el-button>
        </div>
      </div>

      <div ref="previewBodyRef" class="preview-body">
        <div v-if="previewLoading" class="preview-loading">
          <el-skeleton :rows="10" animated />
        </div>

        <div v-else-if="!fileInfo" class="preview-empty">
          <el-empty description="文件不存在或无权访问" />
        </div>

        <template v-else>
          <!-- PDF: pdf.js canvas渲染 -->
          <div v-if="ft === 'pdf'" class="preview-pdf">
            <div class="pdf-viewer-container">
              <div class="pdf-toolbar">
                <el-button-group>
                  <el-button size="small" :disabled="currentPage <= 1" @click="currentPage--"><el-icon><ArrowLeft /></el-icon></el-button>
                  <el-button size="small" disabled>{{ currentPage }} / {{ pdfTotalPages }}</el-button>
                  <el-button size="small" :disabled="currentPage >= pdfTotalPages" @click="currentPage++"><el-icon><ArrowRight /></el-icon></el-button>
                </el-button-group>
              </div>
              <div class="pdf-canvas-container">
                <canvas ref="pdfCanvasRef"></canvas>
              </div>
            </div>
            <div v-if="pdfLoading" class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在加载PDF...</span>
            </div>
          </div>

          <!-- Word: 异步转PDF + pdf.js canvas渲染 -->
          <div v-else-if="ft === 'docx' || ft === 'doc'" class="preview-word">
            <div v-if="wordConvertStatus === 'COMPLETED'" class="pdf-viewer-container">
              <div class="pdf-toolbar">
                <el-button-group>
                  <el-button size="small" :disabled="currentWordPage <= 1" @click="currentWordPage--"><el-icon><ArrowLeft /></el-icon></el-button>
                  <el-button size="small" disabled>{{ currentWordPage }} / {{ wordTotalPages }}</el-button>
                  <el-button size="small" :disabled="currentWordPage >= wordTotalPages" @click="currentWordPage++"><el-icon><ArrowRight /></el-icon></el-button>
                </el-button-group>
              </div>
              <div class="pdf-canvas-container">
                <canvas ref="wordCanvasRef"></canvas>
              </div>
            </div>
            <div v-else-if="wordConvertStatus === 'PROCESSING'" class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>文档转换中，请稍候... (首次转换可能需要1-2分钟)</span>
            </div>
            <div v-else-if="wordConvertStatus === 'FAILED'" class="preview-loading">
              <span>文档预览失败：{{ wordConvertMessage }}，请确认LibreOffice已安装</span>
            </div>
            <div v-else class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在准备文档环境...</span>
            </div>
          </div>

          <!-- PPT: 异步转PDF + pdf.js canvas渲染 -->
          <div v-else-if="ft === 'pptx' || ft === 'ppt'" class="preview-pptx">
            <div v-if="convertStatus === 'COMPLETED'" class="pdf-viewer-container">
              <div class="pdf-toolbar">
                <el-button-group>
                  <el-button size="small" :disabled="currentPptPage <= 1" @click="currentPptPage--"><el-icon><ArrowLeft /></el-icon></el-button>
                  <el-button size="small" disabled>{{ currentPptPage }} / {{ pptTotalPages }}</el-button>
                  <el-button size="small" :disabled="currentPptPage >= pptTotalPages" @click="currentPptPage++"><el-icon><ArrowRight /></el-icon></el-button>
                </el-button-group>
              </div>
              <div class="pdf-canvas-container">
                <canvas ref="pptCanvasRef"></canvas>
              </div>
            </div>
            <div v-else-if="convertStatus === 'PROCESSING'" class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>文档转换中，请稍候... (首次转换可能需要1-2分钟)</span>
            </div>
            <div v-else-if="convertStatus === 'FAILED'" class="preview-loading">
              <span>PPT预览加载失败：{{ convertMessage }}，请确认LibreOffice已安装</span>
            </div>
            <div v-else class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在准备文档环境...</span>
            </div>
          </div>

          <!-- 视频/音频 -->
          <div v-else-if="isAudioVideo(ft)" class="preview-media">
            <video
              v-if="ft === 'mp4' || ft === 'webm' || ft === 'avi' || ft === 'mov'"
              :src="mediaUrl" controls class="preview-video"
            />
            <audio v-else :src="mediaUrl" controls class="preview-audio" />
          </div>

          <!-- 图片 -->
          <div v-else-if="isImage(ft)" class="preview-image">
            <img :src="imageUrl" :alt="fileInfo.fileName" class="preview-img" />
          </div>

          <!-- Excel -->
          <div v-else-if="ft === 'xlsx' || ft === 'xls'" class="preview-excel">
            <div v-if="excelLoading" class="excel-loading">
              <el-skeleton :rows="8" animated />
            </div>
            <div v-else-if="excelSheets.length > 0" class="excel-container">
              <div class="excel-tabs">
                <el-radio-group v-model="excelActiveSheet" size="small">
                  <el-radio-button v-for="(sheet, idx) in excelSheets" :key="idx" :value="idx">{{ sheet.name }}</el-radio-button>
                </el-radio-group>
              </div>
              <div class="excel-table-wrapper">
                <table v-if="excelSheets[excelActiveSheet]" class="excel-table">
                  <thead>
                    <tr>
                      <th class="row-num">#</th>
                      <th v-for="(_, ci) in excelSheets[excelActiveSheet].rows[0]" :key="ci">{{ getColumnName(ci) }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, ri) in excelSheets[excelActiveSheet].rows" :key="ri">
                      <td class="row-num">{{ ri + 1 }}</td>
                      <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-if="excelSheets[excelActiveSheet] && excelSheets[excelActiveSheet].totalRows > 100" class="excel-notice">
                仅显示前100行，共 {{ excelSheets[excelActiveSheet].totalRows }}{{ excelSheets[excelActiveSheet].totalRowsApprox ? '+' : '' }} 行。请下载查看完整文件
              </div>
            </div>
            <div v-else class="excel-empty">
              <el-empty description="Excel文件解析失败" :image-size="40">
                <el-button type="primary" @click="handleDownload">下载文件</el-button>
              </el-empty>
            </div>
          </div>

          <!-- 文本 -->
          <div v-else-if="isTextFile(ft)" class="preview-text">
            <div v-if="ft === 'md'" class="markdown-body" v-html="renderedMarkdown"></div>
            <div v-else-if="ft === 'csv'" class="markdown-body csv-preview" v-html="renderedMarkdown"></div>
            <pre v-else class="text-content">{{ textContent }}</pre>
          </div>

          <!-- 不支持 -->
          <div v-else class="preview-unsupported">
            <el-empty description="暂不支持在线预览此文件类型">
              <el-button type="primary" @click="handleDownload">下载文件</el-button>
            </el-empty>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Download, Loading, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import * as pdfjsLib from 'pdfjs-dist'
import {
  getPreviewUrl,
  getExcelData,
  getPdfStreamUrl,
  getConvertedPdfUrl,
  getConvertStatus,
  triggerConvert,
  getTextPreview,
  type ExcelData,
  type ConvertStatus,
  type TextPreviewData,
} from '@/api/share-preview'
import { useWatermark } from '@/composables/useWatermark'
import { renderMarkdown } from '@/utils/markdown'
import { formatFileSize, getFileTypeColor, isAudioVideo, isImage } from '@/utils/chunk-upload'

const route = useRoute()
const { setWatermark, clearWatermark, enableScreenshotProtection } = useWatermark()

// 预览容器 ref（用于水印挂载目标）
const previewBodyRef = ref<HTMLElement | null>(null)

// 设置 pdf.js worker
pdfjsLib.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version}/pdf.worker.min.js`

// ========== 分享验证 ==========
const step = ref<'password' | 'loading' | 'preview' | 'error'>('loading')
const passwordInput = ref('')
const authLoading = ref(false)
const authError = ref('')
const errorMessage = ref('')
const fileId = ref<number | null>(null)

// ========== 文件预览 ==========
const fileInfo = ref<any>(null)
const previewLoading = ref(false)
const textContent = ref('')
const renderedMarkdown = ref('')

// Excel
const excelSheets = ref<ExcelData['sheets']>([])
const excelActiveSheet = ref(0)
const excelLoading = ref(false)

// ==================== pdf.js 渲染相关 ====================
const pdfCanvasRef = ref<HTMLCanvasElement>()
const pptCanvasRef = ref<HTMLCanvasElement>()
const wordCanvasRef = ref<HTMLCanvasElement>()
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let pptDoc: pdfjsLib.PDFDocumentProxy | null = null
let wordDoc: pdfjsLib.PDFDocumentProxy | null = null

// PDF 预览状态
const pdfTotalPages = ref(0)
const currentPage = ref(1)
const pdfLoading = ref(false)
const pdfScale = ref(1.5)

// PPT 预览状态（异步转换 + pdf.js）
const pptTotalPages = ref(0)
const currentPptPage = ref(1)
const pptLoading = ref(false)
const convertStatus = ref<ConvertStatus['status']>('NOT_STARTED')
const convertMessage = ref('')

// Word 预览状态（异步转换 + pdf.js）
const wordTotalPages = ref(0)
const currentWordPage = ref(1)
const wordLoading = ref(false)
const wordConvertStatus = ref<ConvertStatus['status']>('NOT_STARTED')
const wordConvertMessage = ref('')

// 转换轮询定时器
let convertPollTimer: ReturnType<typeof setInterval> | null = null
let wordConvertPollTimer: ReturnType<typeof setInterval> | null = null

const ft = computed(() => fileInfo.value?.fileType?.toLowerCase() || '')
const mediaUrl = computed(() => fileId.value ? getPreviewUrl(fileId.value) : '')
const imageUrl = computed(() => fileId.value ? getPreviewUrl(fileId.value) : '')
const canDownload = computed(() => shareData?.permissionType === 'DOWNLOAD')

// ========== 文本文件类型 ==========
const TEXT_FILE_TYPES = new Set(['txt', 'md', 'csv', 'log', 'json', 'xml', 'html', 'htm', 'css', 'js', 'java', 'py', 'c', 'cpp', 'h', 'go', 'rs', 'ts', 'sql', 'yaml', 'yml', 'ini', 'conf', 'cfg', 'sh', 'bat', 'properties'])

function isTextFile(type: string) {
  return TEXT_FILE_TYPES.has(type?.toLowerCase())
}

// ========== CSV → HTML 表格 ==========
function csvToHtmlTable(csvText: string): string {
  const MAX_ROWS = 2000
  const lines = csvText.split('\n').filter(l => l.trim())
  if (lines.length === 0) return '<p>空文件</p>'
  const displayLines = lines.length > MAX_ROWS ? lines.slice(0, MAX_ROWS) : lines
  const parseRow = (line: string): string[] => {
    const cells: string[] = []
    let cell = ''
    let inQuotes = false
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      if (inQuotes) {
        if (ch === '"') {
          if (i + 1 < line.length && line[i + 1] === '"') { cell += '"'; i++ }
          else inQuotes = false
        } else cell += ch
      } else {
        if (ch === '"') inQuotes = true
        else if (ch === ',') { cells.push(cell); cell = '' }
        else cell += ch
      }
    }
    cells.push(cell)
    return cells
  }
  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  let html = '<div class="csv-table-wrapper"><table><thead><tr>'
  const headerCells = parseRow(displayLines[0])
  for (const h of headerCells) html += `<th>${esc(h)}</th>`
  html += '</tr></thead><tbody>'
  for (let r = 1; r < displayLines.length; r++) {
    html += '<tr>'
    for (const c of parseRow(displayLines[r])) html += `<td>${esc(c)}</td>`
    html += '</tr>'
  }
  html += '</tbody></table>'
  if (lines.length > MAX_ROWS) html += `<p style="text-align:center;color:#909399;margin-top:12px;">仅显示前 ${MAX_ROWS} 行</p>`
  html += '</div>'
  return html
}

// ========== Excel 列名 ==========
function getColumnName(idx: number): string {
  let name = ''
  while (idx >= 0) {
    name = String.fromCharCode(65 + (idx % 26)) + name
    idx = Math.floor(idx / 26) - 1
  }
  return name
}

// ========== 分享生命周期 ==========
interface ShareData {
  id: number
  fileId: number
  token: string
  password: string | null
  expireTime: string | null
  maxAccess: number
  accessCount: number
  status: number
  permissionType: string
  createTime: string
}

let shareData: ShareData | null = null

const shareToken = ref(route.params.token as string)

onMounted(async () => {
  const token = shareToken.value
  if (!token) {
    step.value = 'error'
    errorMessage.value = '无效的分享链接'
    return
  }

  try {
    const res = await fetch(`/api/front/share-links/token/${token}`)
    const json = await res.json()

    if (json.code !== 200) {
      step.value = 'error'
      errorMessage.value = json.message || '分享已失效'
      return
    }

    shareData = json.data
    if (!shareData || shareData.status !== 1) {
      step.value = 'error'
      errorMessage.value = '分享已关闭或不存在'
      return
    }

    if (shareData.password) {
      step.value = 'password'
    } else {
      await doAccess()
    }
  } catch {
    step.value = 'error'
    errorMessage.value = '加载分享信息失败'
  }
})

onUnmounted(() => {
  clearWatermark()
  pdfDoc?.destroy()
  pptDoc?.destroy()
  wordDoc?.destroy()
  pdfDoc = null
  pptDoc = null
  wordDoc = null
  if (convertPollTimer) { clearInterval(convertPollTimer); convertPollTimer = null }
  if (wordConvertPollTimer) { clearInterval(wordConvertPollTimer); wordConvertPollTimer = null }
})

async function verifyPassword() {
  if (!passwordInput.value.trim()) {
    authError.value = '请输入密码'
    return
  }
  authLoading.value = true
  authError.value = ''
  try {
    if (shareData && passwordInput.value === shareData.password) {
      await doAccess()
    } else {
      authError.value = '密码错误，请重试'
    }
  } catch {
    authError.value = '密码错误，请重试'
  } finally {
    authLoading.value = false
  }
}

async function doAccess() {
  if (!shareData) {
    step.value = 'error'
    errorMessage.value = '分享信息加载失败'
    return
  }

  fileId.value = shareData.fileId

  try {
    await fetch(`/api/front/share-links/access/${shareData.token}`, { method: 'POST' })
  } catch { /* ignore */ }

  enableScreenshotProtection()

  step.value = 'preview'
  await nextTick()
  setWatermark('访客', previewBodyRef.value)

  loadFilePreview(shareData.fileId)
}

// ==================== pdf.js 渲染函数 ====================

/** 渲染PDF页面到canvas */
async function renderPdfPageToCanvas(
  doc: pdfjsLib.PDFDocumentProxy,
  pageNum: number,
  canvasRef: Ref<HTMLCanvasElement | undefined>,
  scale: number = 1.5
) {
  if (!canvasRef.value) return
  const page = await doc.getPage(pageNum)
  const viewport = page.getViewport({ scale })
  const canvas = canvasRef.value
  const context = canvas.getContext('2d')
  if (!context) return

  // 1. 设置 canvas 缓冲区尺寸
  canvas.width = viewport.width
  canvas.height = viewport.height

  // 2. 显式设置 canvas CSS 显示尺寸（像素），避免依赖 CSS 百分比
  canvas.style.width = viewport.width + 'px'
  canvas.style.height = viewport.height + 'px'

  // 3. 设置 wrapper 尺寸
  const wrapper = canvas.parentElement
  if (wrapper) {
    wrapper.style.width = `${viewport.width}px`
    wrapper.style.height = `${viewport.height}px`
  }

  // 4. 仅首次渲染时等待布局完成
  const needsLayoutWait = !canvas.style.width || canvas.style.width === '0px'
  if (needsLayoutWait) {
    await new Promise<void>(resolve => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => resolve())
      })
    })
  }

  await page.render({ canvasContext: context, viewport }).promise
}

/** 加载PDF文件（直接流） */
async function loadPdfWithPdfJs(id: number) {
  pdfLoading.value = true
  currentPage.value = 1
  try {
    const url = getPdfStreamUrl(id)
    const loadingTask = pdfjsLib.getDocument(url)
    pdfDoc = await loadingTask.promise
    pdfTotalPages.value = pdfDoc.numPages
    await nextTick()
    await renderPdfPageToCanvas(pdfDoc, 1, pdfCanvasRef, pdfScale.value)
  } catch (e) {
    console.error('PDF加载失败:', e)
  }
  pdfLoading.value = false
}

/** 加载已转换的PDF（Word/PPT转PDF后） */
async function loadConvertedPdfWithPdfJs(id: number, target: 'ppt' | 'word') {
  const canvasRef = target === 'ppt' ? pptCanvasRef : wordCanvasRef
  const loading = target === 'ppt' ? pptLoading : wordLoading
  loading.value = true
  try {
    const url = getConvertedPdfUrl(id)
    const loadingTask = pdfjsLib.getDocument(url)
    const doc = await loadingTask.promise
    if (target === 'ppt') {
      pptDoc = doc
      pptTotalPages.value = doc.numPages
      currentPptPage.value = 1
    } else {
      wordDoc = doc
      wordTotalPages.value = doc.numPages
      currentWordPage.value = 1
    }
    await nextTick()
    await renderPdfPageToCanvas(doc, 1, canvasRef, pdfScale.value)
  } catch (e) {
    console.error('转换PDF加载失败:', e)
  }
  loading.value = false
}

/** PPT: 触发异步转换 */
async function startPptConversion() {
  if (!fileId.value) return
  convertStatus.value = 'PROCESSING'
  convertMessage.value = '文档转换已启动，请稍候...'
  try {
    await triggerConvert(fileId.value)
    startConvertPolling('ppt')
  } catch {
    convertStatus.value = 'FAILED'
    convertMessage.value = '触发转换失败'
  }
}

/** Word: 触发异步转换 */
async function startWordConversion() {
  if (!fileId.value) return
  wordConvertStatus.value = 'PROCESSING'
  wordConvertMessage.value = '文档转换已启动，请稍候...'
  try {
    await triggerConvert(fileId.value)
    startConvertPolling('word')
  } catch {
    wordConvertStatus.value = 'FAILED'
    wordConvertMessage.value = '触发转换失败'
  }
}

/** 轮询转换状态 */
function startConvertPolling(target: 'ppt' | 'word') {
  const statusRef = target === 'ppt' ? convertStatus : wordConvertStatus
  const messageRef = target === 'ppt' ? convertMessage : wordConvertMessage

  const stopPolling = () => {
    if (target === 'ppt' && convertPollTimer) {
      clearInterval(convertPollTimer)
      convertPollTimer = null
    } else if (target === 'word' && wordConvertPollTimer) {
      clearInterval(wordConvertPollTimer)
      wordConvertPollTimer = null
    }
  }

  stopPolling()
  const timer = setInterval(async () => {
    if (!fileId.value) { stopPolling(); return }
    try {
      const res = await getConvertStatus(fileId.value)
      const data = res.data.data || res.data
      statusRef.value = data.status
      messageRef.value = data.message || ''

      if (data.status === 'COMPLETED') {
        stopPolling()
        await loadConvertedPdfWithPdfJs(fileId.value, target)
      } else if (data.status === 'FAILED') {
        stopPolling()
      }
    } catch {
      stopPolling()
      statusRef.value = 'FAILED'
      messageRef.value = '查询转换状态失败'
    }
  }, 3000)

  if (target === 'ppt') convertPollTimer = timer
  else wordConvertPollTimer = timer
}

/** 检查转换状态并决定是否需要触发 */
async function checkAndStartConversion(target: 'ppt' | 'word') {
  if (!fileId.value) return
  const statusRef = target === 'ppt' ? convertStatus : wordConvertStatus

  // 【安全拦截】如果当前已经是 PROCESSING（转换中）或 COMPLETED（已完成），直接拦截
  // 防止并发调用覆盖状态，与 FilePreview.vue 保持一致的防御性编程
  if (statusRef.value === 'PROCESSING' || statusRef.value === 'COMPLETED') {
    return
  }

  try {
    const res = await getConvertStatus(fileId.value)
    const data = res.data.data || res.data
    statusRef.value = data.status

    if (data.status === 'COMPLETED') {
      await loadConvertedPdfWithPdfJs(fileId.value, target)
    } else if (data.status === 'PROCESSING') {
      startConvertPolling(target)
    } else if (data.status === 'NOT_STARTED' || data.status === 'FAILED') {
      // 静默触发转换，不需要用户点击
      if (target === 'ppt') {
        await startPptConversion()
      } else {
        await startWordConversion()
      }
    }
  } catch {
    // 接口报错时的降级处理
    statusRef.value = 'FAILED'
  }
}

// ========== 加载文件预览 ==========
async function loadFilePreview(id: number) {
  previewLoading.value = true
  try {
    const infoRes = await fetch(`/api/front/preview/${id}/info`)
    if (!infoRes.ok) {
      previewLoading.value = false
      return
    }
    const docFile = await infoRes.json()
    fileInfo.value = {
      id: docFile.id,
      fileName: docFile.fileName,
      fileType: docFile.fileType,
      fileSize: docFile.fileSize,
      uploaderName: docFile.uploaderName,
      createTime: docFile.createTime,
    }

    const type = fileInfo.value.fileType?.toLowerCase()

    if (type === 'pdf') {
      await loadPdfWithPdfJs(id)
    } else if (type === 'docx' || type === 'doc') {
      await checkAndStartConversion('word')
    } else if (type === 'xlsx' || type === 'xls') {
      await loadExcelData(id)
    } else if (type === 'pptx' || type === 'ppt') {
      await checkAndStartConversion('ppt')
    } else if (isTextFile(type)) {
      await loadTextContent(id, type)
    }
  } catch (e) {
    console.error('加载预览失败:', e)
  }
  previewLoading.value = false
}

async function loadExcelData(id: number) {
  excelLoading.value = true
  try {
    const res = await getExcelData(id)
    if (res.data.data?.sheets) {
      excelSheets.value = res.data.data.sheets
    }
  } catch { /* ignore */ }
  excelLoading.value = false
}

/** 文本预览：使用服务端截断API */
async function loadTextContent(id: number, type: string) {
  try {
    const res = await getTextPreview(id)
    const data: TextPreviewData = res.data.data || res.data
    if (data.error) {
      textContent.value = '文件读取失败'
      return
    }
    textContent.value = data.content
    if (data.truncated) {
      textContent.value += '\n\n... 文件过大，仅显示前512KB内容，请下载查看完整文件'
    }
    if (type === 'csv') {
      renderedMarkdown.value = csvToHtmlTable(textContent.value)
    } else if (type === 'md') {
      renderedMarkdown.value = renderMarkdown(textContent.value)
    }
  } catch {
    // 降级：直接下载文件
    try {
      const res = await fetch(getPreviewUrl(id))
      const fullText = await res.text()
      const MAX_PREVIEW_BYTES = 512 * 1024
      textContent.value = fullText.length > MAX_PREVIEW_BYTES
        ? fullText.substring(0, MAX_PREVIEW_BYTES) + '\n\n... 文件过大，仅显示前512KB内容'
        : fullText
      if (type === 'csv') renderedMarkdown.value = csvToHtmlTable(textContent.value)
      else if (type === 'md') renderedMarkdown.value = renderMarkdown(textContent.value)
    } catch { /* ignore */ }
  }
}

function handleDownload() {
  if (!canDownload.value) {
    ElMessage.warning('此链接仅允许预览，不支持下载')
    return
  }
  window.open(`/api/front/share-links/download/${shareToken.value}`, '_blank')
}

// PDF翻页
watch(currentPage, async (newPage) => {
  if (!pdfDoc || newPage < 1 || newPage > pdfTotalPages.value) return
  await renderPdfPageToCanvas(pdfDoc, newPage, pdfCanvasRef, pdfScale.value)
})

// PPT翻页
watch(currentPptPage, async (newPage) => {
  if (!pptDoc || newPage < 1 || newPage > pptTotalPages.value) return
  await renderPdfPageToCanvas(pptDoc, newPage, pptCanvasRef, pdfScale.value)
})

// Word翻页
watch(currentWordPage, async (newPage) => {
  if (!wordDoc || newPage < 1 || newPage > wordTotalPages.value) return
  await renderPdfPageToCanvas(wordDoc, newPage, wordCanvasRef, pdfScale.value)
})
</script>

<style scoped>
.share-preview {
  min-height: 100vh;
  background: #f0f2f5;
}

/* ========== 密码验证 ========== */
.share-auth {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.auth-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px 32px;
  width: 380px;
  max-width: 90vw;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid #ebeef5;
}

.auth-icon {
  margin-bottom: 12px;
  color: var(--el-color-primary);
}

.auth-card h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 6px;
  color: #303133;
}

.auth-desc {
  font-size: 14px;
  color: #909399;
  margin: 0 0 20px;
}

.auth-btn {
  width: 100%;
  margin-top: 12px;
}

.auth-error {
  color: #f56c6c;
  font-size: 13px;
  margin-top: 10px;
}

/* ========== 加载/错误 ========== */
.share-loading,
.share-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  color: #909399;
}

.share-loading p {
  margin-top: 16px;
}

.error-hint {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

/* ========== 文件预览 ========== */
.preview-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;
}

.preview-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.preview-filename {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-info-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  font-size: 11px;
  color: #fff;
  background: #e6a23c;
  border-radius: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.preview-header-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.preview-body {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  min-height: 400px;
  padding: 20px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  gap: 12px;
  color: #909399;
}

.preview-empty {
  padding: 80px 0;
}

/* PDF/PPT/Word pdf.js canvas 预览 */
.pdf-viewer-container {
  width: 100%;
  height: calc(100vh - 200px);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.pdf-toolbar {
  padding: 8px 0;
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-shrink: 0;
}

.pdf-canvas-container {
  width: 100%;
  flex: 1;
  display: flex;
  justify-content: center;
  overflow: auto;
}

.pdf-canvas-container canvas {
  max-width: 100%;
  height: auto;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.preview-pdf,
.preview-pptx,
.preview-word {
  min-height: 60vh;
}

/* 媒体 */
.preview-media {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.preview-video {
  max-width: 100%;
  max-height: 70vh;
  border-radius: 8px;
}

.preview-audio {
  width: 100%;
  max-width: 600px;
}

/* 图片 */
.preview-image {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.preview-img {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
  border-radius: 8px;
}

/* Excel */
.excel-container {
  padding: 20px;
}

.excel-tabs {
  margin-bottom: 12px;
}

.excel-table-wrapper {
  overflow-x: auto;
}

.excel-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.excel-table th {
  background: #f5f7fa;
  padding: 8px 12px;
  border: 1px solid #e4e7ed;
  text-align: left;
  font-weight: 600;
  color: #606266;
}

.excel-table td {
  padding: 6px 12px;
  border: 1px solid #ebeef5;
  color: #303133;
}

.excel-table tr:hover td {
  background: #f5f7fa;
}

.row-num {
  color: #c0c4cc;
  width: 40px;
  text-align: center;
}

.excel-notice {
  padding: 12px;
  text-align: center;
  font-size: 13px;
  color: #909399;
  background: #fafafa;
  border-radius: 0 0 8px 8px;
}

.excel-loading,
.excel-empty {
  padding: 60px 20px;
}

/* 文本 */
.preview-text {
  padding: 24px;
}

.text-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  margin: 0;
  font-family: 'SF Mono', 'Monaco', 'Menlo', monospace;
}

.csv-preview {
  padding: 0;
}

/* 不支持 */
.preview-unsupported {
  padding: 60px 0;
}

/* markdown 样式继承 */
:deep(.markdown-body) {
  padding: 24px;
}

@media (max-width: 1180px) {
  .preview-header {
    padding: 10px 14px;
  }
}

@media (max-width: 768px) {
  .preview-header {
    flex-wrap: wrap;
    gap: 8px;
  }
  .preview-header-left {
    flex: 1 1 100%;
    min-width: 0;
  }
  .preview-filename {
    font-size: 14px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
  .preview-header-right {
    flex: 1 1 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
  .pdf-toolbar {
    flex-wrap: wrap;
    gap: 6px;
  }
  .excel-table {
    font-size: 12px;
  }
  .excel-table th,
  .excel-table td {
    padding: 6px 8px;
  }
}

@media (max-width: 480px) {
  .preview-header-right .el-button span {
    display: none;
  }
  .preview-header-right .el-button .el-icon {
    margin: 0;
  }
}

:deep(.csv-table-wrapper) {
  overflow-x: auto;
  padding: 16px;
}

:deep(.csv-table-wrapper table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

:deep(.csv-table-wrapper th),
:deep(.csv-table-wrapper td) {
  padding: 6px 10px;
  border: 1px solid #e0e0e0;
  text-align: left;
}

:deep(.csv-table-wrapper th) {
  background: #f5f7fa;
  font-weight: 600;
}
</style>
