<template>
  <div class="system-action-card">
    <div class="system-card-header">
      <el-icon :size="16"><component :is="headerIcon" /></el-icon>
      <span>{{ headerTitle }}</span>
    </div>
    <div class="system-card-body">
      <!-- 最近文件 -->
       <template v-if="actionId === 'search_recent'">
        <div v-if="!recentFiles.length" class="system-empty">最近没有上传过文件</div>
        <div v-else class="system-list">
          <div
            v-for="item in recentFiles"
            :key="item.id"
            class="system-list-item"
            @click="goPreview(item.id)"
          >
            <div class="system-item-icon"><FileIcon :file-type="item.fileType" :size="18" /></div>
            <div class="system-item-main">
              <div class="system-item-title" :title="item.fileName">{{ item.fileName }}</div>
              <div class="system-item-meta">{{ item.fileSizeText }} · {{ item.createTime }}</div>
            </div>
          </div>
        </div>
        <div v-if="total > recentFiles.length" class="system-more">共 {{ total }} 个文档</div>
      </template>

      <!-- 存储配额 -->
      <template v-else-if="actionId === 'storage_quota'">
        <div class="quota-stats">
          <div class="quota-stat">
            <div class="quota-value">{{ quotaInfo.usedText }}</div>
            <div class="quota-label">已使用</div>
          </div>
          <div class="quota-stat">
            <div class="quota-value">{{ quotaInfo.remainingText }}</div>
            <div class="quota-label">剩余</div>
          </div>
          <div class="quota-stat">
            <div class="quota-value">{{ quotaInfo.quotaText }}</div>
            <div class="quota-label">总配额</div>
          </div>
        </div>
        <div class="quota-progress-wrap">
          <div class="quota-progress-text">
            <span>使用率</span>
            <span :class="`quota-status-${quotaInfo.status}`">{{ quotaInfo.usedPercent }}%</span>
          </div>
          <el-progress
            :percentage="quotaInfo.usedPercent"
            :status="quotaInfo.status"
            :stroke-width="10"
            :show-text="false"
          />
        </div>
        <div class="quota-tip" :class="`quota-status-${quotaInfo.status}`">{{ quotaTip }}</div>
      </template>

      <!-- 待审批 -->
      <template v-else-if="actionId === 'pending_approvals'">
        <div v-if="!approvals.length" class="system-empty">当前没有待审批的申请</div>
        <div v-else class="system-list">
          <div
            v-for="item in approvals"
            :key="item.id"
            class="system-list-item"
          >
            <div class="system-item-icon approval-icon"><el-icon :size="16"><Timer /></el-icon></div>
            <div class="system-item-main">
              <div class="system-item-title" :title="item.title">{{ item.title }}</div>
              <div class="system-item-meta">提交于 {{ item.createTime }}</div>
            </div>
          </div>
        </div>
        <div v-if="total > approvals.length" class="system-more">共 {{ total }} 条待审批</div>
      </template>

      <!-- 我的分享 -->
      <template v-else-if="actionId === 'my_shares'">
        <div v-if="!shares.length" class="system-empty">当前没有有效的分享链接</div>
        <div v-else class="system-list">
          <div
            v-for="item in shares"
            :key="item.id"
            class="system-list-item"
          >
            <div class="system-item-icon share-icon"><el-icon :size="16"><Share /></el-icon></div>
            <div class="system-item-main">
              <div class="system-item-title">分享链接 #{{ item.id }}</div>
              <div class="system-item-meta">已访问 {{ item.accessCount }} 次 · {{ item.createTime }}</div>
            </div>
          </div>
        </div>
        <div v-if="total > shares.length" class="system-more">共 {{ total }} 个有效分享</div>
      </template>

      <!-- 热门文件 -->
      <template v-else-if="actionId === 'hot_docs'">
        <div v-if="!hotDocs.length" class="system-empty">暂无文件数据</div>
        <div v-else class="rank-list">
          <div
            v-for="(item, index) in hotDocs"
            :key="item.id"
            class="rank-item"
            @click="goPreview(item.id)"
          >
            <div class="rank-number" :class="{ top: index < 3 }">{{ item.rank }}</div>
            <div class="rank-title" :title="item.fileName">{{ item.fileName }}</div>
            <div class="rank-count">{{ item.viewCount }} 浏览</div>
          </div>
        </div>
      </template>

      <!-- 文档统计 -->
      <template v-else-if="actionId === 'doc_stats'">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="stat-value">{{ stats.systemTotal }}</div>
            <div class="stat-label">系统文件总数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ stats.myDocs }}</div>
            <div class="stat-label">我的文件数量</div>
          </div>
          <div class="stat-card wide">
            <div class="stat-value">{{ stats.myTotalSizeText }}</div>
            <div class="stat-label">我的文件总大小</div>
          </div>
        </div>
      </template>

      <!-- 默认 -->
      <template v-else>
        <div class="system-default-text">{{ systemData?.content }}</div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Timer, Share, TrendCharts, DataAnalysis, Cpu } from '@element-plus/icons-vue'
import FileIcon from '@/components/FileIcon.vue'
import type { SystemActionData } from '@/api/ai'
import { formatFileSize } from '@/utils/chunk-upload'

const props = defineProps<{
  data: SystemActionData
}>()

const router = useRouter()
const systemData = computed(() => props.data)
const actionId = computed(() => props.data.actionId)
const rawData = computed(() => props.data.data || {})

const headerTitle = computed(() => {
  const map: Record<string, string> = {
    search_recent: '最近文件',
    storage_quota: '存储配额',
    pending_approvals: '待审批申请',
    my_shares: '我的分享',
    hot_docs: '热门文件 TOP10',
    doc_stats: '文件统计',
  }
  return map[actionId.value] || '系统查询'
})

const headerIcon = computed(() => {
  const map: Record<string, any> = {
    search_recent: Document,
    storage_quota: DataAnalysis,
    pending_approvals: Timer,
    my_shares: Share,
    hot_docs: TrendCharts,
    doc_stats: Cpu,
  }
  return map[actionId.value] || Cpu
})

const recentFiles = computed(() => {
  const list = (rawData.value.files || []) as any[]
  return list.map(f => ({
    id: f.id,
    fileName: f.fileName,
    fileType: f.fileType,
    fileSizeText: f.fileSizeText || formatFileSize(f.fileSize || 0),
    createTime: f.createTime,
  }))
})

const quotaInfo = computed(() => {
  return {
    quota: rawData.value.quota || 0,
    quotaText: rawData.value.quotaText || '0 B',
    used: rawData.value.used || 0,
    usedText: rawData.value.usedText || '0 B',
    remaining: rawData.value.remaining || 0,
    remainingText: rawData.value.remainingText || '0 B',
    usedPercent: rawData.value.usedPercent || 0,
    status: rawData.value.status || 'success',
  }
})

const quotaTip = computed(() => {
  const status = quotaInfo.value.status
  if (status === 'danger') return '⚠️ 存储空间即将用尽，建议清理或扩容'
  if (status === 'warning') return '存储空间使用较多，请注意管理'
  return '存储空间充足，请放心使用'
})

const approvals = computed(() => (rawData.value.approvals || []) as any[])
const shares = computed(() => (rawData.value.shares || []) as any[])
const hotDocs = computed(() => (rawData.value.docs || []) as any[])
const stats = computed(() => ({
  systemTotal: rawData.value.systemTotal || 0,
  myDocs: rawData.value.myDocs || 0,
  myTotalSizeText: rawData.value.myTotalSizeText || '0 B',
}))
const total = computed(() => rawData.value.total || 0)

function goPreview(fileId: number) {
  if (!fileId) return
  router.push({ name: 'Preview', params: { id: String(fileId) } })
}
</script>

<style scoped>
.system-action-card {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  min-width: 280px;
  max-width: 420px;
}

.system-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
  font-weight: 600;
  color: #1d1e23;
}

.system-card-header .el-icon {
  color: #409eff;
}

.system-card-body {
  padding: 12px 14px;
}

.system-empty {
  color: #909399;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}

.system-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.system-list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.system-list-item:hover {
  background: #f5f7fa;
}

.system-item-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #f5f7fa;
  flex-shrink: 0;
}

.approval-icon {
  background: #fdf6ec;
  color: #e6a23c;
}

.share-icon {
  background: #f0f9eb;
  color: #67c23a;
}

.system-item-main {
  flex: 1;
  min-width: 0;
}

.system-item-title {
  font-size: 13px;
  font-weight: 500;
  color: #1d1e23;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.system-item-meta {
  font-size: 11px;
  color: #8a8f99;
  margin-top: 2px;
}

.system-more {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.quota-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.quota-stat {
  text-align: center;
  padding: 10px 6px;
  background: #f7f8fa;
  border-radius: 8px;
}

.quota-value {
  font-size: 14px;
  font-weight: 600;
  color: #1d1e23;
}

.quota-label {
  font-size: 11px;
  color: #8a8f99;
  margin-top: 4px;
}

.quota-progress-wrap {
  margin-bottom: 10px;
}

.quota-progress-text {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}

.quota-tip {
  font-size: 12px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f7f8fa;
}

.quota-status-success {
  color: #67c23a;
}

.quota-status-warning {
  color: #e6a23c;
}

.quota-status-danger {
  color: #f56c6c;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.rank-item:hover {
  background: #f5f7fa;
}

.rank-number {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f2f3f5;
  color: #606266;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.rank-number.top {
  background: #409eff;
  color: #fff;
}

.rank-title {
  flex: 1;
  font-size: 13px;
  color: #1d1e23;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-count {
  font-size: 11px;
  color: #8a8f99;
  flex-shrink: 0;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.stat-card {
  padding: 14px 10px;
  background: #f7f8fa;
  border-radius: 8px;
  text-align: center;
}

.stat-card.wide {
  grid-column: span 2;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 12px;
  color: #606266;
  margin-top: 6px;
}

.system-default-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
