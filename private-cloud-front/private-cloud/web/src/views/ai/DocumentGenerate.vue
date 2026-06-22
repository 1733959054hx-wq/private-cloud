<template>
  <div class="doc-generate">
    <div class="gen-header">
      <h2 class="gen-title"><el-icon :size="22"><MagicStick /></el-icon> 智能文档生成</h2>
      <p class="gen-desc">选择模板，填写参数，AI自动生成专业文档</p>
    </div>

    <div class="generate-workbench">
      <!-- 左侧：智能创作配置 -->
      <div class="workbench-left">
        <div class="template-selector">
          <div class="template-title"><el-icon :size="16"><Tickets /></el-icon> 文档模板</div>
          <div class="template-grid">
            <div
              v-for="tpl in templates"
              :key="tpl.id"
              class="template-chip"
              :class="{ active: selectedTemplate?.id === tpl.id }"
              @click="selectTemplate(tpl)"
            >
              <el-icon :size="18"><component :is="getTemplateIcon(tpl.icon)" /></el-icon>
              <span>{{ tpl.name }}</span>
            </div>
          </div>
        </div>

        <div v-if="selectedTemplate" class="config-panel">
          <h3 class="config-title">
            <el-icon :size="18"><component :is="getTemplateIcon(selectedTemplate.icon)" /></el-icon>
            {{ selectedTemplate.name }}
          </h3>
          <p class="config-desc">{{ selectedTemplate.desc }}</p>

          <!-- 参考文档上传 -->
          <el-upload
            drag
            accept=".doc,.docx,.md,.txt"
            :auto-upload="false"
            :show-file-list="false"
            @change="handleRefFileChange"
            class="ref-upload"
          >
            <el-icon :size="28"><UploadFilled /></el-icon>
            <div class="upload-text">
              <span v-if="referenceContent">已加载参考文档</span>
              <span v-else>上传参考源文档 (doc/docx/md/txt)</span>
            </div>
          </el-upload>

          <el-alert v-if="extractingFields" type="warning" show-icon :closable="false" style="margin-bottom: 12px;">
            <template #title>AI 正在分析参考文档，智能提取表单字段...</template>
          </el-alert>
          <el-alert v-else-if="referenceContent" type="success" show-icon :closable style="margin-bottom: 12px;" @close="referenceContent = ''">
            <template #title>已加载参考文档，可点击"一键智能填充"提取字段</template>
          </el-alert>

          <!-- 表单 -->
          <el-form :model="formData" label-position="top" class="config-form">
            <el-form-item v-for="field in selectedTemplate.fields" :key="field.key" :label="field.label">
              <el-input v-if="field.type === 'text'" v-model="formData[field.key]" :placeholder="field.placeholder" />
              <el-input v-else-if="field.type === 'textarea'" v-model="formData[field.key]" type="textarea" :rows="3" :placeholder="field.placeholder" />
              <el-select v-else-if="field.type === 'select'" v-model="formData[field.key]" :placeholder="field.placeholder">
                <el-option v-for="opt in field.options" :key="opt" :label="opt" :value="opt" />
              </el-select>
              <el-date-picker v-else-if="field.type === 'date'" v-model="formData[field.key]" type="date" :placeholder="field.placeholder" value-format="YYYY-MM-DD" style="width: 100%;" />
            </el-form-item>
          </el-form>

          <!-- 操作栏 -->
          <div class="config-actions">
            <div class="actions-row-top">
              <div class="model-select-group">
                <el-select v-model="selectedModel" placeholder="选择AI模型" style="width: 180px;">
                  <el-option v-for="m in modelOptions" :key="m.value" :label="m.label" :value="m.value" />
                </el-select>
                <el-button v-if="referenceContent" type="success" plain :loading="extractingFields" @click="handleAutoExtract">
                  <el-icon><MagicStick /></el-icon> 智能填充
                </el-button>
              </div>
              <el-button @click="resetForm">重置</el-button>
            </div>
            <el-button type="primary" :loading="isGenerating" @click="startAsyncGeneration" class="generate-btn">
              <el-icon><MagicStick /></el-icon> 立即生成文档
            </el-button>
          </div>
        </div>

        <div v-else class="config-empty">
          <el-empty description="请选择一个文档模板开始">
            <template #image>
              <el-icon :size="64" color="#C0C4CC"><EditPen /></el-icon>
            </template>
          </el-empty>
        </div>
      </div>

      <!-- 右侧：实时预览区 -->
      <div class="workbench-right">
        <div class="preview-header">
          <span class="preview-label"><el-icon :size="16"><Document /></el-icon> 生成预览</span>
          <span v-if="currentStatusMsg" class="status-msg" :class="{ active: isGenerating }">{{ currentStatusMsg }}</span>
          <div v-if="generatedContent && !isGenerating" class="preview-actions">
            <el-button size="small" @click="handleCopyResult"><el-icon><CopyDocument /></el-icon> 复制</el-button>
            <el-button size="small" type="primary" @click="handleDownloadResult"><el-icon><Download /></el-icon> 下载</el-button>
            <el-button size="small" type="danger" @click="handleDeleteResult"><el-icon><Delete /></el-icon> 删除</el-button>
          </div>
        </div>

        <div class="preview-content">
          <el-skeleton v-if="isGenerating && !generatedContent" :rows="12" animated />
          <div v-else-if="generatedContent" class="markdown-body" v-html="renderedResult"></div>
          <div v-else class="preview-placeholder">
            <el-icon :size="48" color="#DCDFE6"><Document /></el-icon>
            <p>填写左侧参数后，点击"立即生成文档"</p>
            <p class="placeholder-hint">AI 将实时生成并在此处预览</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'DocGenerate' })
import { ref, computed, onMounted, onActivated, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { MagicStick, Tickets, EditPen, Document, CopyDocument, Download, DataAnalysis, Monitor, Notebook, Promotion, Delete, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { generateDocument, extractTemplateFields, submitGenerateTask, fetchGenerateStream, getGenerateTaskStatus } from '@/api/ai'
import { renderMarkdown } from '@/utils/markdown'
import { useAiStore } from '@/stores/ai'

interface TemplateField {
  key: string
  label: string
  type: 'text' | 'textarea' | 'select' | 'date'
  placeholder: string
  options?: string[]
}

interface DocTemplate {
  id: string
  name: string
  icon: string
  desc: string
  fields: TemplateField[]
}

const templates: DocTemplate[] = [
  {
    id: 'meeting-minutes',
    name: '会议纪要',
    icon: 'Tickets',
    desc: '自动生成规范的会议纪要文档',
    fields: [
      { key: 'meetingTitle', label: '会议主题', type: 'text', placeholder: '请输入会议主题' },
      { key: 'meetingDate', label: '会议日期', type: 'date', placeholder: '请选择会议日期' },
      { key: 'attendees', label: '参会人员', type: 'text', placeholder: '如：张三、李四、王五' },
      { key: 'meetingContent', label: '会议内容摘要', type: 'textarea', placeholder: '请简要描述会议讨论的主要内容' },
      { key: 'decisions', label: '决议事项', type: 'textarea', placeholder: '请列出会议达成的决议' },
      { key: 'todos', label: '后续待办', type: 'textarea', placeholder: '请列出需要跟进的事项' },
    ],
  },
  {
    id: 'project-proposal',
    name: '项目方案',
    icon: 'DataAnalysis',
    desc: '生成完整的项目方案文档',
    fields: [
      { key: 'projectName', label: '项目名称', type: 'text', placeholder: '请输入项目名称' },
      { key: 'background', label: '项目背景', type: 'textarea', placeholder: '请描述项目背景和需求' },
      { key: 'objectives', label: '项目目标', type: 'textarea', placeholder: '请列出项目要达成的目标' },
      { key: 'scope', label: '项目范围', type: 'textarea', placeholder: '请描述项目范围和边界' },
      { key: 'timeline', label: '时间规划', type: 'text', placeholder: '如：3个月' },
      { key: 'teamSize', label: '团队规模', type: 'text', placeholder: '如：5人' },
    ],
  },
  {
    id: 'technical-doc',
    name: '技术文档',
    icon: 'Monitor',
    desc: '生成技术架构或API文档',
    fields: [
      { key: 'docTitle', label: '文档标题', type: 'text', placeholder: '请输入文档标题' },
      { key: 'systemName', label: '系统名称', type: 'text', placeholder: '请输入系统名称' },
      { key: 'techStack', label: '技术栈', type: 'text', placeholder: '如：Spring Boot + Vue3 + MySQL' },
      { key: 'description', label: '系统描述', type: 'textarea', placeholder: '请描述系统功能和架构' },
      { key: 'modules', label: '核心模块', type: 'textarea', placeholder: '请列出核心模块及其功能' },
      { key: 'apiList', label: '接口列表', type: 'textarea', placeholder: '请列出主要API接口' },
    ],
  },
  {
    id: 'work-report',
    name: '工作汇报',
    icon: 'Notebook',
    desc: '生成周报/月报/年度汇报',
    fields: [
      { key: 'reportType', label: '汇报类型', type: 'select', placeholder: '请选择', options: ['周报', '月报', '季度汇报', '年度汇报'] },
      { key: 'period', label: '汇报周期', type: 'text', placeholder: '如：2026年5月第2周' },
      { key: 'completedWork', label: '已完成工作', type: 'textarea', placeholder: '请列出本周期已完成的工作' },
      { key: 'ongoingWork', label: '进行中工作', type: 'textarea', placeholder: '请列出正在进行的工作' },
      { key: 'issues', label: '遇到的问题', type: 'textarea', placeholder: '请列出遇到的问题和困难' },
      { key: 'nextPlan', label: '下期计划', type: 'textarea', placeholder: '请列出下一周期的工作计划' },
    ],
  },
  {
    id: 'contract',
    name: '合同模板',
    icon: 'Document',
    desc: '生成标准合同文档',
    fields: [
      { key: 'contractType', label: '合同类型', type: 'select', placeholder: '请选择', options: ['服务合同', '采购合同', '劳动合同', '保密协议', '合作协议'] },
      { key: 'partyA', label: '甲方名称', type: 'text', placeholder: '请输入甲方名称' },
      { key: 'partyB', label: '乙方名称', type: 'text', placeholder: '请输入乙方名称' },
      { key: 'contractContent', label: '合同主要内容', type: 'textarea', placeholder: '请描述合同主要内容' },
      { key: 'amount', label: '合同金额', type: 'text', placeholder: '如：100,000元' },
      { key: 'duration', label: '合同期限', type: 'text', placeholder: '如：1年' },
    ],
  },
]

const selectedTemplate = ref<DocTemplate | null>(null)
const formData = ref<Record<string, string>>({})
const isGenerating = ref(false)
const generatedContent = ref('')
const referenceContent = ref('')
const extractingFields = ref(false)
const currentStatusMsg = ref('')

const route = useRoute()
const aiStore = useAiStore()
const selectedModel = ref('glm-4.7-flash')
const modelOptions = aiStore.modelOptions

// AI标签键 → 模板表单字段键的映射
const aiTagToFieldMap: Record<string, Record<string, string>> = {
  'meeting-minutes': {
    'subject': 'meetingTitle', 'meeting_title': 'meetingTitle', 'title': 'meetingTitle',
    'meeting_date': 'meetingDate', 'contract_date': 'meetingDate', 'date': 'meetingDate', 'time': 'meetingDate',
    'attendees': 'attendees', 'participants': 'attendees',
    'meeting_content': 'meetingContent', 'content': 'meetingContent', 'summary': 'meetingContent', 'description': 'meetingContent',
    'decisions': 'decisions', 'decision': 'decisions',
    'todos': 'todos', 'action_items': 'todos', 'todo': 'todos',
  },
  'project-proposal': {
    'subject': 'projectName', 'project_name': 'projectName', 'title': 'projectName',
    'background': 'background', 'objectives': 'objectives', 'objective': 'objectives', 'goals': 'objectives',
    'scope': 'scope', 'timeline': 'timeline', 'duration': 'timeline', 'contract_date': 'timeline',
    'team_size': 'teamSize', 'teamSize': 'teamSize',
  },
  'technical-doc': {
    'subject': 'docTitle', 'title': 'docTitle', 'doc_title': 'docTitle',
    'system_name': 'systemName', 'systemName': 'systemName',
    'tech_stack': 'techStack', 'techStack': 'techStack', 'technology': 'techStack',
    'description': 'description', 'content': 'description',
    'modules': 'modules', 'core_modules': 'modules',
    'api_list': 'apiList', 'apiList': 'apiList', 'apis': 'apiList',
  },
  'work-report': {
    'report_type': 'reportType', 'reportType': 'reportType', 'type': 'reportType', 'document_type': 'reportType',
    'period': 'period', 'contract_date': 'period', 'date': 'period',
    'completed_work': 'completedWork', 'completedWork': 'completedWork', 'completed': 'completedWork',
    'ongoing_work': 'ongoingWork', 'ongoingWork': 'ongoingWork', 'ongoing': 'ongoingWork',
    'issues': 'issues', 'problems': 'issues',
    'next_plan': 'nextPlan', 'nextPlan': 'nextPlan', 'plan': 'nextPlan',
  },
  'contract': {
    'contract_type': 'contractType', 'contractType': 'contractType', 'document_type': 'contractType', 'type': 'contractType',
    'party_a': 'partyA', 'partyA': 'partyA', 'party_b': 'partyB', 'partyB': 'partyB',
    'contract_content': 'contractContent', 'contractContent': 'contractContent', 'content': 'contractContent', 'subject': 'contractContent', 'description': 'contractContent',
    'contract_amount': 'amount', 'contractAmount': 'amount', 'amount': 'amount', 'price': 'amount', 'total': 'amount',
    'contract_date': 'duration', 'contractDate': 'duration', 'duration': 'duration', 'period': 'duration', 'term': 'duration', 'deadline': 'duration',
  },
}

// 处理参考文件上传（限制 2MB，仅文本文件）
function handleRefFileChange(file: any) {
  const raw = file.raw || file
  const maxSize = 2 * 1024 * 1024
  if (raw.size > maxSize) {
    ElMessage.warning('参考文档大小不能超过 2MB')
    return
  }
  const allowedTypes = ['text/plain', 'text/markdown', 'application/json', 'text/csv']
  const ext = (raw.name?.split('.').pop() || '').toLowerCase()
  if (!allowedTypes.includes(raw.type) && !['txt', 'md', 'json', 'csv'].includes(ext)) {
    ElMessage.warning('仅支持上传文本类参考文档（txt/md/json/csv）')
    return
  }
  const reader = new FileReader()
  reader.onload = (e) => {
    referenceContent.value = e.target?.result as string
    ElMessage.success('参考文档已加载')
    if (selectedTemplate.value) {
      handleAutoExtract()
    }
  }
  reader.readAsText(raw)
}

// AI一键智能填充函数
async function handleAutoExtract() {
  if (!referenceContent.value || !selectedTemplate.value) return
  extractingFields.value = true
  try {
    const res = await extractTemplateFields({
      templateId: selectedTemplate.value.id,
      referenceContent: referenceContent.value,
      model: selectedModel.value
    })
    const data = res.data.data || res.data
    let fillCount = 0
    for (const [key, val] of Object.entries(data)) {
      if (selectedTemplate.value.fields.some(f => f.key === key)) {
        formData.value[key] = val || ''
        if (val) fillCount++
      }
    }
    if (fillCount > 0) {
      ElMessage.success(`AI 已成功提取并自动填充了 ${fillCount} 个表单字段！`)
    } else {
      ElMessage.warning('未从参考文档中提取到匹配的表单信息')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '智能提取填充失败')
  } finally {
    extractingFields.value = false
  }
}

// 异步SSE生成文档（核心新逻辑）
async function startAsyncGeneration() {
  if (!selectedTemplate.value) return

  const emptyFields = selectedTemplate.value.fields.filter(f => !formData.value[f.key]?.trim())
  if (emptyFields.length > 0) {
    ElMessage.warning(`请填写：${emptyFields.map(f => f.label).join('、')}`)
    return
  }

  isGenerating.value = true
  generatedContent.value = ''
  currentStatusMsg.value = '正在提交任务...'

  try {
    const reqData: any = {
      templateId: selectedTemplate.value.id,
      params: { ...formData.value },
      model: selectedModel.value,
    }
    if (referenceContent.value) {
      reqData.referenceContent = referenceContent.value
    }

    const res = await submitGenerateTask(reqData)
    const taskInfo = res.data.data
    if (!taskInfo || !taskInfo.taskId) {
      throw new Error('任务提交失败：未返回 taskId')
    }

    // 根据 mode 分支处理
    if (taskInfo.mode === 'mq') {
      // MQ 模式：后端异步处理，前端轮询 status 接口
      currentStatusMsg.value = 'AI 正在撰写文档...'
      pollTaskStatus(taskInfo.docId)
    } else {
      // SSE 降级模式：通过 SSE 流接收进度
      fetchGenerateStream(
        taskInfo.taskId,
        (status, msg) => {
          currentStatusMsg.value = msg
          if (status === 'CHUNK') {
            generatedContent.value += msg
          }
        },
        (finalResult) => {
          isGenerating.value = false
          currentStatusMsg.value = '生成完成！'
          if (finalResult.content) {
            generatedContent.value = finalResult.content
          }
          ElMessage.success('文档生成成功')
        },
        (err) => {
          isGenerating.value = false
          currentStatusMsg.value = '生成失败'
          ElMessage.error(err.message || '文档生成失败')
        }
      )
    }
  } catch (err: any) {
    isGenerating.value = false
    currentStatusMsg.value = '提交失败'
    ElMessage.error(err?.message || '任务提交失败')
  }
}

/**
 * MQ 模式：轮询任务状态
 */
let pollStatusTimer: ReturnType<typeof setInterval> | null = null
function pollTaskStatus(docId: number) {
  if (pollStatusTimer) clearInterval(pollStatusTimer)
  pollStatusTimer = setInterval(async () => {
    try {
      const res = await getGenerateTaskStatus(docId)
      const data = res.data.data
      if (!data) return
      if (data.status === 1) {
        // 生成成功
        if (pollStatusTimer) { clearInterval(pollStatusTimer); pollStatusTimer = null }
        isGenerating.value = false
        currentStatusMsg.value = '生成完成！'
        if (data.content) {
          generatedContent.value = data.content
        }
        ElMessage.success('文档生成成功')
      } else if (data.status === 2) {
        // 生成失败
        if (pollStatusTimer) { clearInterval(pollStatusTimer); pollStatusTimer = null }
        isGenerating.value = false
        currentStatusMsg.value = '生成失败'
        ElMessage.error(data.failReason || '文档生成失败')
      }
      // status === 0 继续轮询
    } catch (e) {
      // 网络异常不中断轮询
    }
  }, 3000)
}

// 组件卸载前清理轮询定时器，避免内存泄漏和无效请求
onBeforeUnmount(() => {
  if (pollStatusTimer) {
    clearInterval(pollStatusTimer)
    pollStatusTimer = null
  }
})

// 接收路由参数，自动选择模板并加载参考内容
// 抽取为独立函数，供 onMounted 和 onActivated 共用
// 标记位：防止 onMounted + onActivated 重复执行（keep-alive 首次挂载时两者都会触发）
let templateLoaded = false

async function loadTemplateFromRoute() {
  const templateType = route.query.templateType as string
  const fileName = route.query.fileName as string
  const hasReference = route.query.hasReference === '1'
  const hasAiTags = route.query.hasAiTags === '1'

  // 没有模板参数时，不覆盖已有状态（保留 keep-alive 缓存的页面状态）
  if (!templateType) return

  // 有模板参数：说明是从预览页面跳转过来的，需要加载新模板
  // 先重置已有状态，避免残留
  generatedContent.value = ''
  currentStatusMsg.value = ''

  const tpl = templates.find(t => t.id === templateType)
  if (tpl) selectTemplate(tpl)

  const refFileId = sessionStorage.getItem('ref_file_id')

  if (hasReference && refFileId) {
    const stored = sessionStorage.getItem(`ref_content_${refFileId}`)
    if (stored) {
      referenceContent.value = stored
      sessionStorage.removeItem(`ref_content_${refFileId}`)
    }
  }

  if (referenceContent.value && selectedTemplate.value) {
    await nextTick()
    handleAutoExtract()
  }

  if (hasAiTags && refFileId && selectedTemplate.value) {
    const metaRaw = sessionStorage.getItem(`ai_meta_${refFileId}`)
    if (metaRaw) {
      try {
        const metaMap: Record<string, string> = JSON.parse(metaRaw)
        const tplId = selectedTemplate.value.id
        const fieldMap = aiTagToFieldMap[tplId] || {}
        for (const [tagKey, tagValue] of Object.entries(metaMap)) {
          const fieldKey = fieldMap[tagKey]
            || fieldMap[tagKey.toLowerCase()]
            || fieldMap[tagKey.replace(/([A-Z])/g, '_$1').toLowerCase()]
          if (fieldKey && selectedTemplate.value.fields.some(f => f.key === fieldKey)) {
            if (!formData.value[fieldKey]?.trim()) {
              formData.value[fieldKey] = tagValue
            }
          }
        }
      } catch { /* ignore parse error */ }
      sessionStorage.removeItem(`ai_meta_${refFileId}`)
    }
  }

  sessionStorage.removeItem('ref_file_id')

  if (fileName && selectedTemplate.value) {
    const nameWithoutExt = fileName.replace(/\.[^.]+$/, '')
    if (templateType === 'meeting-minutes' && !formData.value.meetingTitle) formData.value.meetingTitle = nameWithoutExt
    else if (templateType === 'project-proposal' && !formData.value.projectName) formData.value.projectName = nameWithoutExt
    else if (templateType === 'technical-doc' && !formData.value.docTitle) formData.value.docTitle = nameWithoutExt
    else if (templateType === 'work-report' && !formData.value.period) formData.value.period = nameWithoutExt
    else if (templateType === 'contract' && !formData.value.partyA) formData.value.partyA = nameWithoutExt
  }
}

onMounted(async () => {
  await loadTemplateFromRoute()
  templateLoaded = true
})

// keep-alive 激活时：如果有新的路由参数（从预览页跳来），重新加载模板
// 否则保留已有状态（用户从其他页面切回来时恢复离开时的样子）
onActivated(async () => {
  if (!templateLoaded) return // 首次挂载时 onMounted 已处理，跳过
  const hasNewTemplate = !!route.query.templateType
  if (hasNewTemplate) {
    await loadTemplateFromRoute()
  }
  // 没有 templateType 参数时，保留 keep-alive 缓存的状态，不做任何操作
})

const renderedResult = computed(() => {
  if (!generatedContent.value) return ''
  return renderMarkdown(generatedContent.value)
})

const iconMap: Record<string, any> = { Tickets, DataAnalysis, Monitor, Notebook, Document }

function getTemplateIcon(name: string) { return iconMap[name] || Document }

function selectTemplate(tpl: DocTemplate) {
  selectedTemplate.value = tpl
  formData.value = {}
  generatedContent.value = ''
  currentStatusMsg.value = ''
  tpl.fields.forEach(f => { formData.value[f.key] = '' })
}

function resetForm() {
  if (selectedTemplate.value) {
    selectedTemplate.value.fields.forEach(f => { formData.value[f.key] = '' })
  }
  generatedContent.value = ''
  currentStatusMsg.value = ''
}

function handleCopyResult() {
  navigator.clipboard.writeText(generatedContent.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => { ElMessage.error('复制失败') })
}

function handleDownloadResult() {
  const htmlContent = renderMarkdown(generatedContent.value)
  const docHtml = `
<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">
<head><meta charset="utf-8"><style>
  body { font-family: 'SimSun', serif; font-size: 12pt; line-height: 1.5; padding: 20px; }
  h1 { font-size: 16pt; font-weight: bold; font-family: 'SimSun', serif; margin: 20px 0 10px; }
  h2 { font-size: 14pt; font-weight: bold; font-family: 'SimSun', serif; margin: 18px 0 8px; }
  h3 { font-size: 12pt; font-weight: bold; font-family: 'SimSun', serif; margin: 14px 0 6px; }
  img + em, img + caption, .figure-caption { font-size: 10.5pt; font-family: 'SimHei', sans-serif; text-align: center; display: block; margin-top: 4px; }
  table { border-collapse: collapse; width: 100%; margin: 10px 0; }
  th, td { border: 1px solid #999; padding: 6px 10px; text-align: left; }
  th { background-color: #f0f0f0; font-weight: bold; }
  p { margin: 6px 0; } ul, ol { padding-left: 20px; } li { margin: 4px 0; }
  code { background: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-size: 11pt; }
  pre { background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto; }
  pre code { background: none; padding: 0; }
</style></head><body>${htmlContent}</body></html>`
  const blob = new Blob(['\uFEFF' + docHtml], { type: 'application/msword;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${selectedTemplate.value?.name || '文档'}.doc`
  a.click()
  window.URL.revokeObjectURL(url)
}

function handleDeleteResult() {
  generatedContent.value = ''
  currentStatusMsg.value = ''
  ElMessage.success('已删除生成内容')
}
</script>

<style scoped>
.doc-generate {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px;
}
.gen-header { margin-bottom: 20px; }
.gen-title {
  font-size: 22px; font-weight: 700; color: #303133;
  margin-bottom: 6px; display: flex; align-items: center; gap: 8px;
}
.gen-desc { font-size: 14px; color: #909399; }

/* 分屏工作台 */
.generate-workbench {
  display: flex; gap: 20px; min-height: calc(100vh - 220px);
}
.workbench-left {
  width: 480px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px;
}
.workbench-right {
  flex: 1; min-width: 0; display: flex; flex-direction: column;
  background: #fff; border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  overflow: hidden;
}

/* 模板选择器 */
.template-selector {
  background: #fff; border-radius: 12px; padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.template-title {
  font-size: 14px; font-weight: 600; color: #303133;
  margin-bottom: 10px; display: flex; align-items: center; gap: 6px;
}
.template-grid {
  display: flex; flex-wrap: wrap; gap: 8px;
}
.template-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 20px; cursor: pointer;
  background: #f5f7fa; font-size: 13px; color: #606266;
  transition: all 0.2s; border: 1px solid transparent;
}
.template-chip:hover { background: #ecf5ff; color: #409EFF; }
.template-chip.active {
  background: #ecf5ff; border-color: #b3d8ff; color: #409EFF; font-weight: 600;
}

/* 配置面板 */
.config-panel {
  background: #fff; border-radius: 12px; padding: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04); flex: 1;
  display: flex; flex-direction: column;
}
.config-title {
  font-size: 17px; font-weight: 600; color: #303133;
  margin-bottom: 4px; display: flex; align-items: center; gap: 8px;
}
.config-desc { font-size: 13px; color: #909399; margin-bottom: 16px; }

.ref-upload { margin-bottom: 12px; }
.ref-upload :deep(.el-upload-dragger) {
  padding: 16px; border-radius: 8px; border-style: dashed;
}
.upload-text { font-size: 13px; color: #909399; margin-top: 4px; }

.config-form { flex: 1; overflow-y: auto; }
.config-actions {
  display: flex; flex-direction: column; gap: 12px;
  padding-top: 16px; border-top: 1px solid #f0f0f0;
}
.actions-row-top {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
}
.model-select-group {
  display: flex; align-items: center; gap: 8px;
}
.generate-btn {
  width: 100%; font-size: 15px; padding: 12px 0;
  border-radius: 8px;
}

.config-empty {
  background: #fff; border-radius: 12px; padding: 60px 40px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04); flex: 1;
  display: flex; align-items: center; justify-content: center;
}

/* 右侧预览区 */
.preview-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 20px; border-bottom: 1px solid #f0f0f0;
}
.preview-label {
  font-size: 15px; font-weight: 600; color: #303133;
  display: flex; align-items: center; gap: 6px;
}
.status-msg {
  font-size: 13px; color: #67C23A; font-weight: 500;
}
.status-msg.active { color: #E6A23C; }
.preview-actions { margin-left: auto; display: flex; gap: 6px; }

.preview-content {
  flex: 1; padding: 24px; overflow-y: auto;
}
.preview-placeholder {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; height: 100%; gap: 12px; color: #909399;
}
.placeholder-hint { font-size: 12px; color: #C0C4CC; }

@media (max-width: 1180px) {
  .generate-workbench { flex-direction: column; }
  .workbench-left { width: 100%; }
  .workbench-right { min-height: 400px; }
}
@media (max-width: 768px) {
  .doc-generate { padding: 12px; }
  .actions-row-top { flex-direction: column; align-items: stretch; }
  .model-select-group { flex-direction: column; }
  .model-select-group .el-select { width: 100% !important; }
}
</style>
