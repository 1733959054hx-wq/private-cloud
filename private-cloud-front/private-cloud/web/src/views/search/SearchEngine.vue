<template>
  <div class="search-engine">
    <!-- ========== 搜索头部 ========== -->
    <div class="search-header">
      <div class="search-box-wrapper">
        <div class="search-box">
          <el-autocomplete
            v-model="keyword"
            :fetch-suggestions="querySuggestions"
            :trigger-on-focus="false"
            placeholder="搜索文档、文件名、内容..."
            clearable
            size="large"
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
                <el-icon><Search /></el-icon>
                <span>{{ item.keyword }}</span>
              </div>
            </template>
          </el-autocomplete>
          <el-button type="primary" size="large" :icon="Search" @click="doSearch">
            搜索
          </el-button>
        </div>
        <!-- 热门搜索 -->
        <div class="hot-keywords" v-if="hotKeywords.length > 0">
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
      </div>
    </div>

    <!-- ========== 筛选条件面板 ========== -->
    <div class="filter-panel" v-if="hasSearched">
      <el-form :inline="true" :model="filterForm" size="default">
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
            style="width: 180px"
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
        <el-form-item>
          <el-button type="primary" @click="doSearch">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ========== 搜索结果 ========== -->
    <div class="search-results" v-loading="loading">
      <!-- 统计信息 -->
      <div class="result-stats" v-if="hasSearched && !loading">
        <span v-if="total > 0">
          找到约 <strong>{{ total }}</strong> 条结果
          <span v-if="keyword" class="result-keyword">
            ，关键词：<el-tag size="small" type="warning" closable @close="clearKeyword">
              {{ keyword }}
            </el-tag>
          </span>
        </span>
        <span v-else>未找到相关结果，请尝试其他关键词或调整筛选条件</span>
      </div>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && hasSearched && total === 0"
        :image-size="200"
        description="没有找到匹配的文档"
      />
      <el-empty
        v-if="!loading && !hasSearched"
        :image-size="200"
        description="输入关键词开始搜索"
      >
        <template #image>
          <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="200" height="200">
            <path d="M464 512a48 48 0 1 0 96 0 48 48 0 1 0-96 0zm200-96a48 48 0 1 0 96 0 48 48 0 1 0-96 0zm-400 0a48 48 0 1 0 96 0 48 48 0 1 0-96 0zm464 192a160 160 0 0 1-256 0h256z" fill="#E0E0E0"/>
            <path d="M928 448a416 416 0 1 1-832 0 416 416 0 0 1 832 0z" fill="#F5F5F5" stroke="#D0D0D0" stroke-width="32"/>
            <path d="M832 832l160 160" stroke="#B0B0B0" stroke-width="64" stroke-linecap="round"/>
          </svg>
        </template>
      </el-empty>

      <!-- 结果列表 -->
      <div class="result-list" v-if="results.length > 0">
        <div
          v-for="item in results"
          :key="item.documentId"
          class="result-item"
        >
          <div class="result-icon">
            <el-icon :size="32" :color="fileTypeColor(item.fileType)">
              <component :is="fileTypeIcon(item.fileType)" />
            </el-icon>
          </div>
          <div class="result-body">
            <h3 class="result-title" @click="previewFile(item.documentId)">
              {{ item.title }}
            </h3>
            <p class="result-summary">{{ item.summary || '暂无摘要内容' }}</p>
            <div class="result-meta">
              <span class="meta-item">
                <el-icon><User /></el-icon>
                {{ item.uploader }}
              </span>
              <span class="meta-item">
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, User, Files, Coin, Clock, Document, VideoCamera } from '@element-plus/icons-vue'
import {
  fullTextSearch,
  fetchSuggest,
  fetchHotKeywords,
  type SearchRequest,
  type SearchResultItem,
  type SearchSuggestion
} from '@/api/search'

const router = useRouter()

// ========== 状态 ==========
const keyword = ref('')
const loading = ref(false)
const results = ref<SearchResultItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const size = ref(20)
const hasSearched = ref(false)
const hotKeywords = ref<string[]>([])

const filterForm = reactive({
  uploader: '',
  dateRange: null as [string, string] | null,
  fileTypes: [] as string[]
})

// ========== 生命周期 ==========
onMounted(() => {
  loadHotKeywords()
})

// ========== API 调用 ==========
/** 执行搜索 */
async function doSearch() {
  loading.value = true
  hasSearched.value = true

  const params: SearchRequest = {
    keyword: keyword.value || undefined,
    uploader: filterForm.uploader || undefined,
    fileTypes: filterForm.fileTypes.length > 0 ? filterForm.fileTypes : undefined,
    page: currentPage.value,
    size: size.value
  }

  if (filterForm.dateRange) {
    params.startDate = filterForm.dateRange[0] + 'T00:00:00'
    params.endDate = filterForm.dateRange[1] + 'T23:59:59'
  }

  try {
    const res = await fullTextSearch(params)
    results.value = res.data.data || []
    total.value = res.data.total || 0
  } catch {
    results.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 加载热门搜索词 */
async function loadHotKeywords() {
  try {
    const res = await fetchHotKeywords()
    hotKeywords.value = res.data.data || []
  } catch {
    hotKeywords.value = []
  }
}

/** 关键词自动补全 */
async function querySuggestions(query: string, cb: (items: { value: string; keyword: string }[]) => void) {
  if (!query.trim()) {
    cb([])
    return
  }
  try {
    const res = await fetchSuggest(query.trim())
    const list: SearchSuggestion[] = res.data.data || []
    cb(list.map(item => ({ value: item.keyword, keyword: item.keyword })))
  } catch {
    cb([])
  }
}

// ========== 事件处理 ==========
function handleSuggestSelect(item: { keyword: string }) {
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

function handlePageChange(page: number) {
  currentPage.value = page
  doSearch()
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

// ========== 工具函数 ==========
function formatFileSize(bytes: number): string {
  if (!bytes) return '未知'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '未知'
  const d = new Date(dateStr)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 根据文件类型返回图标组件 */
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

/** 根据文件类型返回颜色 */
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
</script>

<style scoped>
.search-engine {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 20px;
  min-height: calc(100vh - 80px);
}

/* ---- 搜索头部 ---- */
.search-header {
  text-align: center;
  margin-bottom: 8px;
}

.search-box-wrapper {
  display: inline-block;
  width: 100%;
  max-width: 720px;
}

.search-box {
  display: flex;
  gap: 12px;
}

.search-input {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.search-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.2);
}

.search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.3);
  border-color: var(--el-color-primary);
}

.search-box .el-button {
  border-radius: 24px;
  padding: 0 28px;
  font-size: 15px;
}

/* 热门搜索 */
.hot-keywords {
  margin-top: 12px;
  text-align: left;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.hot-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.hot-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.hot-tag:hover {
  transform: scale(1.05);
}

/* ---- 筛选面板 ---- */
.filter-panel {
  margin: 20px 0 16px;
  padding: 16px 20px;
  background: #f5f7fa;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
}

.filter-panel .el-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.filter-panel .el-form-item {
  margin-bottom: 0;
}

/* ---- 搜索结果 ---- */
.search-results {
  margin-top: 8px;
}

.result-stats {
  font-size: 14px;
  color: #606266;
  padding: 0 4px 16px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 4px;
}

.result-stats strong {
  color: var(--el-color-primary);
  font-weight: 600;
}

.result-keyword {
  margin-left: 4px;
}

.result-keyword .el-tag {
  cursor: default;
}

/* 结果列表 */
.result-list {
  display: flex;
  flex-direction: column;
}

.result-item {
  display: flex;
  gap: 16px;
  padding: 16px 12px;
  border-bottom: 1px solid #f0f2f5;
  transition: background 0.2s;
}

.result-item:hover {
  background: #f5f7fa;
}

.result-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 10px;
}

.result-body {
  flex: 1;
  min-width: 0;
}

.result-title {
  font-size: 16px;
  font-weight: 500;
  color: #1a1a2e;
  cursor: pointer;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-title:hover {
  color: var(--el-color-primary);
}

.result-summary {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
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
  gap: 4px;
}

.meta-item .el-icon {
  font-size: 13px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 32px 0 16px;
}

/* 空状态 */
.el-empty {
  padding: 80px 0;
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

@media (max-width: 1180px) {
  .search-engine {
    padding: 0 12px;
  }
  .filter-panel {
    width: 220px;
  }
}

@media (max-width: 992px) {
  .search-engine {
    flex-direction: column;
    gap: 12px;
  }
  .filter-panel {
    width: 100%;
    position: static;
    max-height: none;
  }
}

@media (max-width: 768px) {
  .search-header {
    padding: 16px 12px;
  }
  .search-box {
    flex-direction: column;
    align-items: stretch;
  }
  .result-item {
    padding: 10px;
  }
  .result-title {
    font-size: 15px;
  }
}
</style>
