<template>
  <div class="advanced-search">
    <!-- ========== 页面头部 ========== -->
    <div class="page-header">
      <h2 class="page-title"><el-icon :size="20"><Search /></el-icon> 高级搜索引擎</h2>
      <span class="page-subtitle">全文检索 · BM25 排序 · 关键词高亮 · 多条件筛选</span>
    </div>

    <!-- ========== 搜索栏 ========== -->
    <div class="search-bar">
      <el-autocomplete
        v-model="keyword"
        :fetch-suggestions="querySuggestions"
        :trigger-on-focus="false"
        placeholder="搜索文档、文件名、内容..."
        clearable
        class="search-input"
        @select="handleSuggestSelect"
        @keyup.enter="doSearch"
        @clear="doSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #default="{ item }">
          <div class="suggest-item">
            <el-icon :size="14"><Search /></el-icon>
            <span class="suggest-text">{{ item.keyword }}</span>
            <el-tag
              v-if="item.source"
              :type="item.source === 'history' ? '' : 'success'"
              size="small"
              class="suggest-tag"
            >
              {{ item.source === 'history' ? '历史' : '文档' }}
            </el-tag>
          </div>
        </template>
      </el-autocomplete>
      <VoiceInput v-model="keyword" show-text @recognized="onVoiceRecognized" />
      <el-button type="primary" :icon="Search" @click="doSearch">搜索</el-button>
    </div>

    <!-- 标签筛选中提示 -->
    <div v-if="selectedTag" class="tag-filter-bar">
      <el-icon><Collection /></el-icon>
      <span class="tag-filter-text">正在筛选共用标签 <strong>「{{ selectedTag }}」</strong> 的文档</span>
      <el-tag type="warning" size="small" round>{{ total }} 个结果</el-tag>
      <el-button link type="primary" size="small" @click="onTagClear"><el-icon><Close /></el-icon> 清除</el-button>
    </div>

    <!-- ========== 热门搜索 ========== -->
    <div v-if="hotKeywords.length > 0" class="hot-keywords">
      <el-icon class="hot-icon"><TrendCharts /></el-icon>
      <span class="hot-label">热门搜索：</span>
      <el-tag
        v-for="kw in hotKeywords"
        :key="kw"
        size="small"
        class="hot-tag"
        @click="clickHotKeyword(kw)"
      >
        {{ kw }}
      </el-tag>
    </div>

    <!-- ========== 筛选条件面板 ========== -->
    <div class="filter-section">
      <div class="filter-header">
        <el-icon><Filter /></el-icon>
        <span>筛选条件</span>
        <el-button text type="primary" size="small" @click="resetFilters">重置</el-button>
      </div>
      <div class="filter-body">
        <el-form :inline="true" :model="filterForm" size="default" class="filter-form">
          <el-form-item label="上传者">
            <el-input
              v-model="filterForm.uploader"
              placeholder="全部上传者"
              clearable
              style="width: 140px"
              @change="doSearch"
            />
          </el-form-item>
          <el-form-item label="上传时间">
            <el-date-picker
              v-model="filterForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 240px"
              @change="doSearch"
            />
          </el-form-item>
          <el-form-item label="文件类型">
            <el-select
              v-model="filterForm.fileTypes"
              multiple
              placeholder="全部类型"
              clearable
              collapse-tags
              collapse-tags-tooltip
              style="width: 170px"
              @change="doSearch"
            >
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="docx" />
              <el-option label="Word 旧版" value="doc" />
              <el-option label="Excel" value="xlsx" />
              <el-option label="Excel 旧版" value="xls" />
              <el-option label="PPT" value="pptx" />
              <el-option label="PPT 旧版" value="ppt" />
              <el-option label="CSV" value="csv" />
              <el-option label="Markdown" value="md" />
              <el-option label="视频" value="mp4" />
              <el-option label="图片" value="png" />
              <el-option label="文本" value="txt" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- ========== 搜索结果 ========== -->
    <div class="search-results" v-loading="loading" element-loading-text="正在搜索...">
      <!-- 统计信息 -->
      <div v-if="hasSearched && !loading" class="stats-bar">
        <template v-if="total > 0">
          <span>
            找到约 <strong>{{ total }}</strong> 条结果
          </span>
          <el-tag
            v-if="keyword"
            closable
            size="small"
            type="warning"
            class="keyword-tag"
            @close="clearKeyword"
          >
            <el-icon :size="12"><Search /></el-icon>
            {{ keyword }}
          </el-tag>
          <span class="stats-duration">搜索耗时 {{ searchTime }}ms</span>
        </template>
        <span v-else>未找到相关结果，请尝试其他关键词或调整筛选条件</span>
      </div>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && hasSearched && total === 0"
        :image-size="180"
        description="没有找到匹配的文档"
      >
        <template #image>
          <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="180" height="180">
            <path d="M416 256h192L320 544 608 832H416L128 544z" fill="#E8E8E8"/>
            <circle cx="512" cy="512" r="320" fill="#F5F5F5" stroke="#D0D0D0" stroke-width="4"/>
            <path d="M716 716l128 128" stroke="#C0C0C0" stroke-width="6" stroke-linecap="round"/>
          </svg>
        </template>
      </el-empty>
      <el-empty
        v-if="!loading && !hasSearched"
        :image-size="180"
        description="输入关键词开始搜索"
      >
        <template #image>
          <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="180" height="180">
            <circle cx="448" cy="448" r="288" fill="#F0F2F5" stroke="#D0D0D0" stroke-width="4"/>
            <path d="M672 672l192 192" stroke="#B0B0B0" stroke-width="8" stroke-linecap="round"/>
          </svg>
        </template>
      </el-empty>

      <!-- 结果列表 -->
      <div class="result-list" v-if="results.length > 0">
        <transition-group name="result-enter">
          <el-card
            v-for="(item, index) in results"
            :key="item.documentId"
            shadow="hover"
            class="result-item"
            :style="{ '--delay': index * 0.05 + 's' }"
          >
            <div class="result-card-body">
              <div class="result-icon" :style="{ background: fileTypeBg(item.fileType) }">
                <el-icon :size="28" :color="fileTypeColor(item.fileType)">
                  <component :is="fileTypeIcon(item.fileType)" />
                </el-icon>
              </div>
              <div class="result-body">
                <div class="result-title-row">
                  <h3 class="result-title" @click="previewFile(item.documentId)" v-html="highlightText(item.title)"></h3>
                  <div class="score-badge" :style="{ background: scoreColor(item.score) }">
                    <el-icon :size="10" style="margin-right:2px"><Star /></el-icon>
                    {{ item.score?.toFixed(0) || 0 }}
                  </div>
                </div>
                <p class="result-summary" v-html="highlightSummary(item)"></p>
                <div v-if="item.spaceType != null" class="space-tag-row">
                  <el-tag v-if="item.spaceType === 0" size="small" type="info" effect="plain">👤 个人</el-tag>
                  <el-tag v-else-if="item.spaceType === 1" size="small" effect="plain" style="color:#409EFF;border-color:#409EFF">🏢 部门</el-tag>
                  <el-tag v-else-if="item.spaceType === 2" size="small" type="warning" effect="plain">🏭 企业</el-tag>
                </div>
                <div class="result-meta">
                  <span class="meta-item">
                    <el-icon><User /></el-icon>
                    {{ item.uploader }}
                  </span>
                  <span class="meta-item" :style="{ color: fileTypeColor(item.fileType) }">
                    <el-icon><Files /></el-icon>
                    {{ item.fileType?.toUpperCase() }}
                  </span>
                  <span class="meta-item">
                    <el-icon><Coin /></el-icon>
                    {{ formatFileSize(item.fileSize) }}
                  </span>
                  <span class="meta-item">
                    <el-icon><Clock /></el-icon>
                    {{ formatDate(item.uploadTime) }}
                  </span>
                </div>
              </div>
            </div>
          </el-card>
        </transition-group>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="total > size">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  Search, User, Files, Coin, Clock, Document,
  Star, TrendCharts, Filter, VideoCamera, Collection, Close
} from '@element-plus/icons-vue'
import {
  advFullTextSearch,
  advFetchSuggest,
  advFetchHotKeywords,
  type AdvSearchRequest,
  type AdvSearchResultItem,
  type AdvSearchSuggestion
} from '@/api/adv-search'
import VoiceInput from '@/components/VoiceInput.vue'

const router = useRouter()
const route = useRoute()

// ========== 状态 ==========
const keyword = ref('')
const loading = ref(false)
const all = ref<AdvSearchResultItem[]>([])
const results = computed(() => all.value.slice((currentPage.value - 1) * size.value, currentPage.value * size.value))
const total = ref(0)
const currentPage = ref(1)
const size = ref(10)
const hasSearched = ref(false)
const hotKeywords = ref<string[]>([])
const searchTime = ref(0)
const selectedTag = ref('')

const filterForm = reactive({
  uploader: '',
  dateRange: null as [string, string] | null,
  fileTypes: [] as string[]
})

// ========== 生命周期 ==========
onMounted(() => {
  loadHotKeywords()
  // 从 URL 参数中读取标签和关键词（如从文件详情页跳转）
  if (route.query.tag) {
    selectedTag.value = route.query.tag as string
    currentPage.value = 1
    doSearch()
  }
  if (route.query.keyword) {
    keyword.value = route.query.keyword as string
    currentPage.value = 1
    doSearch()
  }
})

// ========== API 调用 ==========
async function doSearch() {
  loading.value = true
  hasSearched.value = true
  const startTime = Date.now()

  const params: AdvSearchRequest = {
    keyword: keyword.value || undefined,
    uploader: filterForm.uploader || undefined,
    fileTypes: filterForm.fileTypes.length > 0 ? filterForm.fileTypes : undefined,
    tag: selectedTag.value || undefined,
  }

  if (filterForm.dateRange) {
    params.startDate = filterForm.dateRange[0] + 'T00:00:00'
    params.endDate = filterForm.dateRange[1] + 'T23:59:59'
  }

  try {
    const res = await advFullTextSearch(params)
    all.value = res.data.data || []
    total.value = all.value.length
    currentPage.value = 1
  } catch {
    all.value = []
    total.value = 0
  } finally {
    loading.value = false
    searchTime.value = Date.now() - startTime
  }
}

async function loadHotKeywords() {
  try {
    const res = await advFetchHotKeywords()
    hotKeywords.value = res.data.data || []
  } catch {
    hotKeywords.value = []
  }
}

async function querySuggestions(query: string, cb: (items: any[]) => void) {
  if (!query.trim()) {
    cb([])
    return
  }
  try {
    const res = await advFetchSuggest(query.trim())
    const list: AdvSearchSuggestion[] = res.data.data || []
    cb(list.map(item => ({
      value: item.keyword,
      keyword: item.keyword,
      source: item.source
    })))
  } catch {
    cb([])
  }
}

// ========== 事件处理 ==========
function handleSuggestSelect(item: any) {
  keyword.value = item.keyword
  currentPage.value = 1
  doSearch()
}

function clickHotKeyword(kw: string) {
  keyword.value = kw
  currentPage.value = 1
  doSearch()
}

function clearKeyword() {
  keyword.value = ''
  currentPage.value = 1
  doSearch()
}

function onTagClick(tagName: string) {
  selectedTag.value = tagName
  currentPage.value = 1
  doSearch()
}

function onTagClear() {
  selectedTag.value = ''
  currentPage.value = 1
  doSearch()
}

function onVoiceRecognized(text: string) {
  keyword.value = text
  currentPage.value = 1
  doSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function resetFilters() {
  filterForm.uploader = ''
  filterForm.dateRange = null
  filterForm.fileTypes = []
  currentPage.value = 1
  doSearch()
}

function previewFile(id: number) {
  router.push(`/preview/${id}`)
}

// ========== 高亮渲染 ==========
function highlightText(text: string): string {
  if (!text) return ''
  return text.replace(
    /<em class='hl-keyword'>(.*?)<\/em>/g,
    '<em class="hl-match">$1</em>'
  )
}

function highlightSummary(item: AdvSearchResultItem): string {
  if (item.highlights && item.highlights.length > 0) {
    return item.highlights.map(h =>
      h.replace(/<em class='hl-keyword'>(.*?)<\/em>/g, '<em class="hl-match">$1</em>')
    ).join('<span class="hl-sep"> ... </span>') || '暂无摘要内容'
  }
  return item.summary || '暂无摘要内容'
}

// ========== 分数颜色 ==========
function scoreColor(score: number): string {
  if (score == null) return '#909399'
  const s = Math.min(score, 100)
  if (s >= 80) return '#67C23A'
  if (s >= 60) return '#E6A23C'
  if (s >= 40) return '#F56C6C'
  return '#909399'
}

// ========== 工具函数 ==========
function formatFileSize(bytes: number): string {
  if (!bytes) return '未知'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let s = bytes
  while (s >= 1024 && i < units.length - 1) {
    s /= 1024
    i++
  }
  return `${s.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '未知'
  const d = new Date(dateStr)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function fileTypeIcon(type: string): string {
  const map: Record<string, string> = {
    pdf: 'Document', doc: 'Document', docx: 'Document',
    xls: 'Files', xlsx: 'Files',
    ppt: 'Document', pptx: 'Document',
    csv: 'Files', md: 'Document',
    mp4: 'VideoCamera', png: 'Files', gif: 'Files', webp: 'Files',
    txt: 'Document'
  }
  return map[type?.toLowerCase()] || 'Document'
}

function fileTypeColor(type: string): string {
  const map: Record<string, string> = {
    pdf: '#F40F02', doc: '#2B579A', docx: '#2B579A',
    xls: '#217346', xlsx: '#217346',
    ppt: '#D04525', pptx: '#D04525',
    csv: '#1FA55F', md: '#083FA1',
    mp4: '#FF6B35'
  }
  return map[type?.toLowerCase()] || '#909399'
}

function fileTypeBg(type: string): string {
  const map: Record<string, string> = {
    pdf: '#FFF1F0', doc: '#EBF0FA', docx: '#EBF0FA',
    xls: '#E8F5ED', xlsx: '#E8F5ED',
    ppt: '#FDEAE8', pptx: '#FDEAE8',
    csv: '#E8F8EE', md: '#E8EEFA',
    mp4: '#FFF0E8'
  }
  return map[type?.toLowerCase()] || '#F5F7FA'
}
</script>

<style scoped>
.advanced-search {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 20px 40px;
  min-height: calc(100vh - 80px);
}

/* ========== 页面头部 ========== */
.page-header {
  padding: 20px 0 16px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-title .el-icon {
  color: var(--el-color-primary);
}

.page-subtitle {
  font-size: 13px;
  color: #909399;
}

/* 标签筛选提示 */
.tag-filter-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; margin-bottom: 12px;
  background: linear-gradient(135deg, #fef5e7, #fdf2e9);
  border: 1px solid #f5d5a0; border-radius: 10px; font-size: 13px; color: #7d5e00;
}
.tag-filter-text strong { color: #e6a23c }
.tag-filter-bar .el-icon { color: #e6a23c }

/* 热门搜索 */
.hot-keywords {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  overflow-x: auto;
  white-space: nowrap;
  margin-bottom: 16px;
  padding-bottom: 2px;
}

.hot-icon {
  font-size: 16px;
  color: #c0c4cc;
}

.hot-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.hot-tag {
  cursor: pointer;
  border-radius: 10px;
  transition: all 0.2s;
}

.hot-tag:hover {
  transform: scale(1.05);
}

/* ========== 搜索栏 ========== */
.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.2s;
  height: 40px;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 1px 8px rgba(64, 158, 255, 0.15);
  border-color: var(--el-color-primary);
}

.search-bar .el-button {
  border-radius: 8px;
  height: 40px;
}

/* ========== 筛选条件 ========== */
.filter-section {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 16px;
  overflow: hidden;
}

.filter-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  background: #fafafa;
  border-bottom: 1px solid #f0f2f5;
}

.filter-header .el-icon {
  font-size: 14px;
  color: var(--el-color-primary);
}

.filter-header .el-button {
  margin-left: auto;
}

.filter-body {
  padding: 12px 16px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.filter-form .el-form-item {
  margin-bottom: 0;
}

/* ========== 搜索结果 ========== */
.search-results {
  margin-top: 4px;
}

/* 统计信息 */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0 16px;
  font-size: 14px;
  color: #606266;
}

.stats-bar strong {
  color: var(--el-color-primary);
  font-weight: 600;
}

.keyword-tag {
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.stats-duration {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}

/* 结果列表 */
.result-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-item {
  border-radius: 8px;
  border: 1px solid #f0f2f5;
  transition: all 0.2s;
}

.result-item:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.result-card-body {
  display: flex;
  gap: 14px;
  padding: 4px 0;
}

.result-icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.result-body {
  flex: 1;
  min-width: 0;
}

.result-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 4px;
}

.result-title {
  font-size: 15px;
  font-weight: 500;
  color: #1a1a2e;
  cursor: pointer;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
  line-height: 1.4;
  transition: color 0.2s;
}

.result-title:hover {
  color: var(--el-color-primary);
}

.score-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  min-width: 36px;
  justify-content: center;
  line-height: 1.6;
}

.space-tag-row { display: flex; gap: 6px; margin-bottom: 6px }
.result-summary {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  margin: 0 0 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.meta-item .el-icon {
  font-size: 12px;
}

/* 搜索结果标签 */
.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.mini-tag {
  cursor: pointer;
  font-size: 11px;
  border-radius: 10px;
  transition: all 0.2s;
}

.mini-tag:hover {
  transform: scale(1.05);
  filter: brightness(0.95);
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 28px 0 16px;
}

/* 空状态 */
.el-empty {
  padding: 60px 0;
}

/* 建议下拉 */
.suggest-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.suggest-item .el-icon {
  font-size: 14px;
  color: #909399;
}

.suggest-text {
  flex: 1;
}

.suggest-tag {
  flex-shrink: 0;
  font-size: 11px;
  border-radius: 8px;
}

/* ========== 关键词高亮 ========== */
:deep(.hl-match) {
  color: #d4380d;
  font-weight: 700;
  font-style: normal;
  background: linear-gradient(180deg, transparent 60%, rgba(212, 56, 13, 0.12) 60%);
}

:deep(.hl-sep) {
  color: #c0c4cc;
  font-weight: 300;
  letter-spacing: 2px;
}

@media (max-width: 1180px) {
  .search-page {
    padding: 0 12px;
  }
  .filter-panel {
    width: 220px;
  }
}

@media (max-width: 992px) {
  .search-body {
    flex-direction: column;
    gap: 12px;
  }
  .filter-panel {
    width: 100%;
    position: static;
    max-height: none;
  }
  .page-title {
    font-size: 18px;
  }
}

@media (max-width: 768px) {
  .page-header {
    padding: 14px 0 12px;
  }
  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .search-actions {
    width: 100%;
    justify-content: flex-end;
  }
  .result-card {
    padding: 12px;
  }
  .result-title {
    font-size: 15px;
  }
  .pager-bar {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
