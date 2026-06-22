<template>
  <div class="sensitive-words-page">
    <div class="page-header">
      <h2><el-icon :size="22"><Warning /></el-icon> 敏感词管理</h2>
      <div class="header-actions">
        <el-input v-model="searchKeyword" placeholder="搜索敏感词..." :prefix-icon="Search" clearable class="search-input" />
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon> 添加敏感词
        </el-button>
        <el-button @click="openBatchAddDialog">
          <el-icon><DocumentAdd /></el-icon> 批量添加
        </el-button>
        <el-button @click="handleRebuildDfa" :loading="rebuilding">
          <el-icon><Refresh /></el-icon> 重建词典
        </el-button>
      </div>
    </div>

    <div class="page-body">
      <el-table :data="filteredWords" stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="word" label="敏感词" min-width="200" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small">{{ row.category }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.level === 1" type="warning" size="small">低</el-tag>
            <el-tag v-else-if="row.level === 2" type="danger" size="small">中</el-tag>
            <el-tag v-else-if="row.level === 3" type="danger" effect="dark" size="small">高</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="handleToggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showAddDialog" title="添加敏感词" width="450px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input v-model="addForm.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="addForm.category" placeholder="请选择分类" clearable style="width: 100%;">
            <el-option label="政治" value="政治" />
            <el-option label="色情" value="色情" />
            <el-option label="暴力" value="暴力" />
            <el-option label="赌博" value="赌博" />
            <el-option label="毒品" value="毒品" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="addForm.level">
            <el-radio :value="1">低</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">高</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showBatchAddDialog" title="批量添加敏感词" width="500px">
      <el-form label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input v-model="batchWords" type="textarea" :rows="6" placeholder="每行一个敏感词" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="batchForm.category" placeholder="请选择分类" clearable style="width: 100%;">
            <el-option label="政治" value="政治" />
            <el-option label="色情" value="色情" />
            <el-option label="暴力" value="暴力" />
            <el-option label="赌博" value="赌博" />
            <el-option label="毒品" value="毒品" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="batchForm.level">
            <el-radio :value="1">低</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">高</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleBatchAdd" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑敏感词" width="450px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input v-model="editForm.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" placeholder="请选择分类" clearable style="width: 100%;">
            <el-option label="政治" value="政治" />
            <el-option label="色情" value="色情" />
            <el-option label="暴力" value="暴力" />
            <el-option label="赌博" value="赌博" />
            <el-option label="毒品" value="毒品" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="editForm.level">
            <el-radio :value="1">低</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">高</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleEdit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCheckDialog" title="文本检测" width="500px">
      <el-input v-model="checkText" type="textarea" :rows="4" placeholder="请输入要检测的文本" />
      <div v-if="checkResult" class="check-result">
        <el-alert v-if="checkResult.contains" type="warning" :closable="false" show-icon>
          <template #title>检测到敏感词：{{ checkResult.sensitiveWords.join('、') }}</template>
        </el-alert>
        <el-alert v-else type="success" :closable="false" show-icon title="未检测到敏感词" />
        <div v-if="checkResult.filteredText" class="filtered-text">
          <span class="label">过滤后文本：</span>
          <span>{{ checkResult.filteredText }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="showCheckDialog = false">关闭</el-button>
        <el-button type="primary" @click="handleCheckText" :loading="checking">检测</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search, Plus, DocumentAdd, Refresh, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSensitiveWords, addSensitiveWord, batchAddSensitiveWords,
  updateSensitiveWord, deleteSensitiveWord, checkSensitiveText,
  rebuildSensitiveWordDfa, type SensitiveWordDTO,
} from '@/api/document'

const loading = ref(false)
const submitting = ref(false)
const rebuilding = ref(false)
const checking = ref(false)
const searchKeyword = ref('')
const words = ref<SensitiveWordDTO[]>([])

const filteredWords = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return words.value
  return words.value.filter(w => w.word.toLowerCase().includes(kw))
})

const showAddDialog = ref(false)
const addForm = ref({ word: '', category: '', level: 1 })

const showBatchAddDialog = ref(false)
const batchWords = ref('')
const batchForm = ref({ category: '', level: 1 })

const showEditDialog = ref(false)
const editForm = ref<{ id: number; word: string; category: string; level: number }>({ id: 0, word: '', category: '', level: 1 })

const showCheckDialog = ref(false)
const checkText = ref('')
const checkResult = ref<{ contains: boolean; sensitiveWords: string[]; filteredText: string } | null>(null)

async function fetchWords() {
  loading.value = true
  try {
    const res = await getSensitiveWords()
    words.value = res.data.data || res.data
  } catch {
    ElMessage.error('加载敏感词列表失败')
  } finally {
    loading.value = false
  }
}

function openAddDialog() {
  addForm.value = { word: '', category: '', level: 1 }
  showAddDialog.value = true
}

async function handleAdd() {
  if (!addForm.value.word.trim()) {
    ElMessage.warning('请输入敏感词')
    return
  }
  submitting.value = true
  try {
    await addSensitiveWord({
      word: addForm.value.word.trim(),
      category: addForm.value.category || undefined,
      level: addForm.value.level,
    })
    ElMessage.success('添加成功')
    showAddDialog.value = false
    fetchWords()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '添加失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

function openBatchAddDialog() {
  batchWords.value = ''
  batchForm.value = { category: '', level: 1 }
  showBatchAddDialog.value = true
}

async function handleBatchAdd() {
  const wordList = batchWords.value.split('\n').map(w => w.trim()).filter(w => w)
  if (wordList.length === 0) {
    ElMessage.warning('请输入至少一个敏感词')
    return
  }
  submitting.value = true
  try {
    await batchAddSensitiveWords({
      words: wordList,
      category: batchForm.value.category || undefined,
      level: batchForm.value.level,
    })
    ElMessage.success('批量添加成功')
    showBatchAddDialog.value = false
    fetchWords()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '批量添加失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

function openEditDialog(row: SensitiveWordDTO) {
  editForm.value = {
    id: row.id,
    word: row.word,
    category: row.category || '',
    level: row.level || 1,
  }
  showEditDialog.value = true
}

async function handleEdit() {
  if (!editForm.value.word.trim()) {
    ElMessage.warning('请输入敏感词')
    return
  }
  submitting.value = true
  try {
    await updateSensitiveWord(editForm.value.id, {
      word: editForm.value.word.trim(),
      category: editForm.value.category || undefined,
      level: editForm.value.level,
    })
    ElMessage.success('更新成功')
    showEditDialog.value = false
    fetchWords()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '更新失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

async function handleToggleEnabled(row: SensitiveWordDTO) {
  try {
    await updateSensitiveWord(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch {
    row.enabled = row.enabled === 1 ? 0 : 1
    ElMessage.error('操作失败')
  }
}

async function handleDelete(row: SensitiveWordDTO) {
  await ElMessageBox.confirm(`确定要删除敏感词 "${row.word}" 吗？`, '确认删除', { type: 'warning' })
  try {
    await deleteSensitiveWord(row.id)
    ElMessage.success('删除成功')
    fetchWords()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleRebuildDfa() {
  rebuilding.value = true
  try {
    await rebuildSensitiveWordDfa()
    ElMessage.success('DFA词典重建成功')
  } catch {
    ElMessage.error('重建失败')
  } finally {
    rebuilding.value = false
  }
}

async function handleCheckText() {
  if (!checkText.value.trim()) {
    ElMessage.warning('请输入要检测的文本')
    return
  }
  checking.value = true
  try {
    const res = await checkSensitiveText(checkText.value)
    checkResult.value = res.data.data || res.data
  } catch {
    ElMessage.error('检测失败')
  } finally {
    checking.value = false
  }
}

function formatDate(timeStr: string) {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  fetchWords()
})
</script>

<style scoped>
.sensitive-words-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  padding: 0 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input {
  width: 200px;
}

.page-body {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  min-height: 0;
  overflow: auto;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

.check-result {
  margin-top: 12px;
}

.filtered-text {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
}

.filtered-text .label {
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

@media (max-width: 1180px) {
  .sensitive-words {
    padding: 0 12px;
  }
}

@media (max-width: 768px) {
  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-row .el-input,
  .filter-row .el-button {
    width: 100%;
  }
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
