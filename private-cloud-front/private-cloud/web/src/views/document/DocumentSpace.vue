<template>
  <div class="document-space">
    <!-- 顶部全局工具栏 -->
    <div class="doc-header">
      <div class="doc-header-left">
        <h2 class="doc-title"><el-icon :size="22"><FolderOpened /></el-icon> 文档</h2>
        <div class="header-divider"></div>
        <el-radio-group v-model="currentSpaceTab" class="space-segmented-control" @change="handleSpaceTabChange">
          <el-radio-button value="personal">个人空间</el-radio-button>
          <el-radio-button value="department">部门空间</el-radio-button>
          <el-radio-button value="enterprise">企业空间</el-radio-button>
        </el-radio-group>
      </div>
      <div class="doc-header-right">
        <el-input v-model="searchKeyword" placeholder="搜索当前目录文件..." :prefix-icon="Search" clearable class="doc-search">
          <template #suffix>
            <VoiceInput v-model="searchKeyword" />
          </template>
        </el-input>
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon> 上传文件
        </el-button>
        <el-button @click="showNewDirDialog = true">
          <el-icon><FolderAdd /></el-icon> 新建文件夹
        </el-button>
      </div>
    </div>

    <div class="doc-body">
      <div class="dir-sidebar">
        <div class="dir-sidebar-header">
          <span>目录结构</span>
          <el-button link type="primary" @click="showNewDirDialog = true" title="新建文件夹">
            <el-icon :size="16"><FolderAdd /></el-icon>
          </el-button>
        </div>
        <div class="dir-sidebar-tree">
          <div class="dir-root-item" :class="{ active: currentDirId === null, 'drag-over': isDraggingFile && dragOverTarget?.type === 'dir' && dragOverTarget?.id === null }" @click="navigateToDir(null)" @dragover="handleFileDragOverDir($event, null)" @dragleave="handleFileDragLeaveDir($event, null)" @drop="handleFileDropOnDir($event, null)">
            <el-icon :size="18"><FolderOpened /></el-icon>
            <span>全部文件</span>
          </div>
          <el-tree
            ref="dirTreeRef"
            :data="dirTree"
            :props="{ label: 'dirName', children: 'children' }"
            node-key="id"
            :expand-on-click-node="false"
            :default-expanded-keys="expandedDirIds"
            draggable
            :allow-drop="allowDirDrop"
            :allow-drag="allowDirDrag"
            @node-expand="handleDirNodeExpand"
            @node-collapse="handleDirNodeCollapse"
            @node-drop="handleDirDrop"
            @node-click="handleDirClick"
          >
            <template #default="{ node, data }">
              <div class="tree-node" :class="{ active: currentDirId === data.id, 'drag-over': isDraggingFile && dragOverTarget?.type === 'dir' && dragOverTarget?.id === data.id }" @dragover="handleFileDragOverDir($event, data.id)" @dragleave="handleFileDragLeaveDir($event, data.id)" @drop="handleFileDropOnDir($event, data.id)">
                <el-icon :size="18" class="tree-folder-icon"><FolderOpened /></el-icon>
                <span class="tree-node-label">{{ data.dirName }}</span>
                <el-dropdown trigger="click" @command="(cmd: string) => handleDirAction(cmd, data)" @click.stop>
                  <el-icon class="tree-node-more" @click.stop><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">重命名</el-dropdown-item>
                      <el-dropdown-item command="move">移动</el-dropdown-item>
                      <el-dropdown-item command="delete" style="color: #F56C6C;">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-tree>
        </div>
        <div v-if="isDraggingFile" class="cross-space-drop-zones">
          <div class="cross-space-drop-label">拖放到其他空间</div>
          <template v-for="tab in ['personal', 'department', 'enterprise']" :key="tab">
            <div v-if="tab !== currentSpaceTab && (tab === 'personal' || (tab === 'department' && userStore.departmentId) || (tab === 'enterprise' && userStore.companyId))" class="cross-space-section">
              <div class="cross-space-section-header" :class="{ 'drag-over': dragOverTarget?.type === 'space' && dragOverTarget?.spaceTab === tab && dragOverTarget?.id === null }" @dragover="handleFileDragOverSpace($event, tab)" @dragleave="handleFileDragLeaveSpace($event)" @drop="handleFileDropOnSpace($event, tab)">
                <el-icon :size="16"><FolderOpened /></el-icon> {{ spaceNameMap[tab] }}
              </div>
              <div v-if="crossSpaceDirTrees[tab]" class="cross-space-tree-wrapper">
                <el-tree
                  :ref="(el: any) => { if (el) crossSpaceTreeRefs[tab] = el }"
                  :data="crossSpaceDirTrees[tab]"
                  :props="{ label: 'dirName', children: 'children' }"
                  node-key="id"
                  :expand-on-click-node="false"
                  default-expand-all
                >
                  <template #default="{ data }">
                    <div class="cross-space-tree-node" :class="{ 'drag-over': dragOverTarget?.type === 'space' && dragOverTarget?.spaceTab === tab && dragOverTarget?.id === data.id }" @dragover="handleCrossSpaceDragOverDir($event, tab, data.id)" @dragleave="handleCrossSpaceDragLeaveDir($event)" @drop="handleCrossSpaceDropOnDir($event, tab, data.id)">
                      <el-icon :size="16" class="tree-folder-icon"><FolderOpened /></el-icon>
                      <span class="tree-node-label">{{ data.dirName }}</span>
                    </div>
                  </template>
                </el-tree>
              </div>
            </div>
          </template>
        </div>
        <div class="dir-sidebar-footer">
          <el-button text @click="showRecycleBin = true"><el-icon :size="16"><Delete /></el-icon> 回收站</el-button>
        </div>
      </div>

      <div class="file-main">
        <!-- 内容区工具栏：面包屑 + 上下文操作 -->
        <div class="file-toolbar">
          <div class="file-toolbar-left">
            <el-button v-if="currentDirId !== null" link type="primary" @click="goToParentDir" class="back-btn" title="返回上级目录">
              <el-icon :size="16"><ArrowLeft /></el-icon>
            </el-button>
            <el-breadcrumb separator="/" class="file-breadcrumb">
              <el-breadcrumb-item @click="navigateToDir(null)">全部文件</el-breadcrumb-item>
              <el-breadcrumb-item v-for="dir in breadcrumbPath" :key="dir.id" @click="navigateToDir(dir)">{{ dir.dirName }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="file-toolbar-right">
            <el-radio-group v-model="viewMode" class="view-mode-control" size="small">
              <el-radio-button value="table" title="列表视图">
                <el-icon><List /></el-icon>
              </el-radio-button>
              <el-radio-button value="thumbnail" title="缩略图视图">
                <el-icon><Picture /></el-icon>
              </el-radio-button>
            </el-radio-group>
            <template v-if="selectedFiles.length > 0">
              <el-button type="danger" size="small" @click="batchDelete">
                <el-icon><Delete /></el-icon> 批量删除 ({{ selectedFiles.length }})
              </el-button>
              <el-button type="success" size="small" @click="batchDownloadFiles">
                <el-icon><Download /></el-icon> 批量下载
              </el-button>
              <el-button type="info" size="small" @click="openBatchMoveCopy('copy')">
                <el-icon><CopyDocument /></el-icon> 批量复制
              </el-button>
            </template>
          </div>
        </div>

        <div v-if="!loading && currentFiles.length === 0" class="empty-state">
          <el-empty description="当前目录暂无文件">
            <el-button type="primary" @click="showUploadDialog = true">上传文件</el-button>
          </el-empty>
        </div>

        <template v-else>
          <div class="data-view-wrapper" style="min-height: 300px; position: relative;">
            <!-- 换页轻量加载覆盖层：保留旧数据可见，仅覆盖半透明遮罩 -->
            <div v-if="pageChanging" class="page-changing-overlay">
              <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            </div>
            <div v-if="loading" class="skeleton-wrapper">
              <template v-if="viewMode === 'table'">
                <div v-for="i in 8" :key="'sk-' + i" class="skeleton-table-row">
                  <el-skeleton-item variant="checkbox" style="width: 16px; margin-right: 12px;" />
                  <el-skeleton-item variant="circle" style="width: 20px; height: 20px; margin-right: 10px;" />
                  <el-skeleton-item variant="text" style="width: 40%; margin-right: auto;" />
                  <el-skeleton-item variant="text" style="width: 60px; margin-right: 24px;" />
                  <el-skeleton-item variant="text" style="width: 50px; margin-right: 24px;" />
                  <el-skeleton-item variant="text" style="width: 80px; margin-right: 24px;" />
                  <el-skeleton-item variant="text" style="width: 30px;" />
                </div>
              </template>
              <template v-else>
                <div class="file-thumbnail-grid">
                  <div v-for="i in 9" :key="'sk-thumb-' + i" class="thumbnail-item skeleton-thumbnail-item">
                    <el-skeleton-item variant="image" style="width: 100%; height: 100%; border-radius: 4px;" />
                  </div>
                </div>
              </template>
            </div>
            <template v-else>
            <div v-if="viewMode === 'table'" class="file-table" style="flex: 1; min-height: 0; overflow: hidden;">
              <el-table ref="tableRef" :data="tableData" height="100%" row-key="id" :default-sort="{ prop: 'updateTime', order: 'descending' }" @sort-change="handleSortChange" @selection-change="handleSelectionChange" style="width: 100%">
              <el-table-column type="selection" width="45" />
              <el-table-column label="名称" min-width="300">
                <template #default="{ row }">
                  <div class="file-name-cell" :class="{ 'is-dragging': isDraggingFile && draggedFileIds.includes(row.id) }" draggable="true" @dblclick="goPreview(row.id)" @dragstart="handleFileDragStart($event, row)" @dragend="handleFileDragEnd">
                    <span class="file-icon-cell">
                      <FileIcon :file-type="row.fileType" :size="18" />
                    </span>
                    <span class="file-name-text">{{ row.fileName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="大小" prop="fileSize" width="120" align="right" sortable="custom">
                <template #default="{ row }">
                  <span>{{ formatFileSize(row.fileSize) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="上传者" width="100">
                <template #default="{ row }">
                  <span>{{ row.uploaderName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="修改时间" prop="updateTime" width="170" sortable="custom">
                <template #default="{ row }">
                  <span>{{ formatDate(row.updateTime || row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="版本" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.version" size="small" type="success" effect="plain">v{{ row.version }}</el-tag>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button link :type="(row as any).isFavorited ? 'warning' : ''" @click="handleFavorite(row)" :title="(row as any).isFavorited ? '取消收藏' : '收藏'">
                    <el-icon><StarFilled v-if="(row as any).isFavorited" /><Star v-else /></el-icon>
                  </el-button>
                  <el-dropdown trigger="click" @command="(cmd: string) => handleFileCommand(cmd, row)">
                    <el-button link title="更多"><el-icon><MoreFilled /></el-icon></el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="preview"><el-icon><View /></el-icon> 预览</el-dropdown-item>
                        <el-dropdown-item command="download"><el-icon><Download /></el-icon> 下载</el-dropdown-item>
                        <el-dropdown-item command="rename"><el-icon><Edit /></el-icon> 重命名</el-dropdown-item>
                        <el-dropdown-item command="copy"><el-icon><CopyDocument /></el-icon> 复制</el-dropdown-item>
                        <el-dropdown-item v-if="userStore.canCrossDeptTransfer" command="transfer"><el-icon><FolderOpened /></el-icon> 跨部门转移</el-dropdown-item>
                        <el-dropdown-item command="delete" style="color:#F56C6C"><el-icon><Delete /></el-icon> 删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-table-column>
            </el-table>
            </div>
            <div v-if="viewMode === 'table' && totalItems > pageSize" class="pagination-wrapper">
              <el-pagination
                v-model:current-page="currentPage"
                :page-size="pageSize"
                :total="totalItems"
                layout="prev, pager, next, jumper, ->, total"
                @current-change="handlePageChange"
                background
                small
              />
            </div>

          <!-- 缩略图视图 -->
          <div v-if="viewMode === 'thumbnail'" ref="thumbnailRef" class="file-thumbnail-grid">
            <div
              v-for="file in paginatedFiles"
              :key="'thumb-' + file.id"
              class="thumbnail-item file-item"
              :class="{ 'is-dragging': isDraggingFile && draggedFileIds.includes(file.id), 'no-thumb': !getThumbnailUrl(file) }"
              draggable="true"
              @dblclick="goPreview(file.id)"
              @dragstart="handleFileDragStart($event, file)"
              @dragend="handleFileDragEnd"
            >
              <div class="thumbnail-preview">
                <img
                  v-if="getThumbnailUrl(file)"
                  :src="getThumbnailUrl(file)"
                  :alt="file.fileName"
                  loading="lazy"
                  @error="handleThumbnailError($event, file)"
                />
                <div v-else class="thumbnail-fallback"><FileIcon :file-type="file.fileType" :size="48" /></div>
                <div v-if="isVideo(file.fileType || '')" class="thumbnail-video-play">
                  <el-icon :size="28" color="#ffffff"><VideoPlay /></el-icon>
                </div>
                <div v-if="file.isFavorited" class="thumbnail-favorite"><el-icon :size="14" color="#f7ba2a"><StarFilled /></el-icon></div>
              </div>
              <div class="thumbnail-name" :title="file.fileName">{{ file.fileName }}</div>
              <div class="thumbnail-meta">{{ formatFileSize(file.fileSize) }}</div>
            </div>
          </div>
          <div v-if="viewMode === 'thumbnail' && totalItems > pageSize" class="pagination-wrapper">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="totalItems"
              layout="prev, pager, next, jumper, ->, total"
              @current-change="handlePageChange"
              background
              small
            />
          </div>
            </template>
          </div>
        </template>
      </div>
    </div>

    <el-dialog v-model="showUploadDialog" width="560px" :close-on-click-modal="false" @close="handleUploadDialogClose">
      <template #header>
        <el-icon :size="20"><Upload /></el-icon>
        <span style="margin-left: 8px;">文件上传</span>
      </template>
      <ChunkUploader
        ref="chunkUploaderRef"
        :directory-id="currentDirId"
        :directory-name="currentDirName"
        :space-type="spaceType"
        :space-id="spaceId"
        @complete="handleUploadComplete"
        @close="handleUploadDialogClose"
      />
    </el-dialog>

    <el-dialog v-model="showNewDirDialog" title="新建文件夹" width="480px">
      <el-form :model="newDirForm" :rules="[{ required: true, message: '请输入文件夹名称', trigger: 'blur' }]" ref="newDirFormRef" label-width="90px">
        <el-form-item label="文件夹名称" prop="dirName">
          <el-input v-model="newDirForm.dirName" placeholder="请输入文件夹名称" />
        </el-form-item>
        <el-form-item label="所在目录">
          <el-tree-select
            v-model="newDirForm.parentId"
            :data="dirTreeForSelect"
            :props="{ label: 'dirName', children: 'children', value: 'id' }"
            placeholder="根目录"
            clearable
            check-strictly
            default-expand-all
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewDirDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateDir">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRenameDialog" title="重命名" width="400px">
      <el-input v-model="renameValue" placeholder="请输入新名称">
        <template v-if="renameFileTarget && fileExtension" #append>
          <span style="color: var(--el-text-color-secondary)">.{{ fileExtension }}</span>
        </template>
      </el-input>
      <p v-if="renameFileTarget && fileExtension" style="margin-top: 8px; color: var(--el-color-warning); font-size: 12px;">
        文件后缀 .{{ fileExtension }} 不可修改
      </p>
      <template #footer>
        <el-button @click="showRenameDialog = false">取消</el-button>
        <el-button type="primary" @click="handleRename">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showMoveDialog" title="移动到" width="400px">
      <el-tree
        :data="dirTree"
        :props="{ label: 'dirName', children: 'children' }"
        node-key="id"
        highlight-current
        default-expand-all
        @node-click="handleMoveTargetClick"
      />
      <template #footer>
        <el-button @click="showMoveDialog = false">取消</el-button>
        <el-button type="primary" @click="handleMoveDir">移动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showFileMoveCopyDialog" :title="fileMoveCopyMode === 'move' ? '移动文件' : '复制文件'" width="500px">
      <p style="margin-bottom: 12px; color: var(--el-text-color-secondary);">
        已选择 {{ fileMoveCopyTargets.length }} 个文件
      </p>
      <div v-if="fileMoveCopyMode === 'copy'" style="margin-bottom: 12px;">
        <span style="font-size: 13px; color: var(--el-text-color-regular); margin-right: 8px;">目标空间：</span>
        <el-radio-group v-model="fileMoveCopyTargetSpace" size="small" @change="handleTargetSpaceChange">
          <el-radio-button v-if="currentSpaceTab !== 'personal'" value="personal">个人空间</el-radio-button>
          <el-radio-button v-if="currentSpaceTab !== 'department' && userStore.departmentId" value="department">部门空间</el-radio-button>
          <el-radio-button v-if="currentSpaceTab !== 'enterprise' && userStore.companyId" value="enterprise">企业空间</el-radio-button>
          <el-radio-button value="current">当前空间</el-radio-button>
        </el-radio-group>
      </div>
      <p style="margin-bottom: 8px; font-size: 13px; color: var(--el-text-color-secondary);">选择目标目录：</p>
      <el-tree
        ref="fileMoveCopyTreeRef"
        :data="fileMoveCopyDirTree"
        :props="{ label: 'dirName', children: 'children', value: 'id' }"
        node-key="id"
        highlight-current
        default-expand-all
        @node-click="handleFileMoveCopyTargetClick"
      />
      <div v-if="fileMoveCopyTargetDirId === null" style="margin-top: 8px; color: var(--el-color-warning); font-size: 12px;">
        未选择目录将{{ fileMoveCopyMode === 'move' ? '移动' : '复制' }}到根目录
      </div>
      <template #footer>
        <el-button @click="showFileMoveCopyDialog = false">取消</el-button>
        <el-button type="primary" :loading="fileMoveCopyLoading" @click="handleFileMoveCopy">确认{{ fileMoveCopyMode === 'move' ? '移动' : '复制' }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showRecycleBin" :size="500">
      <template #header>
        <span><el-icon :size="18"><Delete /></el-icon> 回收站</span>
      </template>
      <div v-if="recycleBinItems.length === 0" class="empty-state">
        <el-empty description="回收站为空" />
      </div>
      <div v-else>
        <div v-for="item in recycleBinItems" :key="item.id" class="recycle-item">
          <div class="recycle-info">
            <div class="recycle-name">{{ item.itemName }}</div>
            <div class="recycle-meta">{{ item.itemType }} · {{ formatDate(item.createTime) }}</div>
          </div>
          <div class="recycle-actions">
            <el-button link type="primary" @click="handleRestore(item)"><el-icon><RefreshLeft /></el-icon> 恢复</el-button>
            <el-button link type="danger" @click="handlePermanentDelete(item)"><el-icon><Delete /></el-icon> 彻底删除</el-button>
          </div>
        </div>
        <div class="recycle-footer">
          <el-button type="danger" @click="handleClearRecycleBin"><el-icon><Delete /></el-icon> 清空回收站</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="showTransferDialog" title="跨部门文件转移" width="500px">
      <el-form label-width="100px">
        <el-form-item label="文件名称">
          <el-input :model-value="transferTarget?.fileName" disabled />
        </el-form-item>
        <el-form-item label="当前部门">
          <el-input :model-value="transferTarget?.departmentId ? '部门' + transferTarget.departmentId : '未分配'" disabled />
        </el-form-item>
        <el-form-item label="目标部门">
          <el-tree-select
            v-model="transferForm.targetDepartmentId"
            :data="departmentTreeForTransfer"
            :props="{ label: 'deptName', children: 'children', value: 'id' }"
            placeholder="请选择目标部门"
            check-strictly
            default-expand-all
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="目标目录">
          <el-tree-select
            v-model="transferForm.targetDirectoryId"
            :data="dirTreeForSelect"
            :props="{ label: 'dirName', children: 'children', value: 'id' }"
            placeholder="请选择目标目录（可选）"
            clearable
            check-strictly
            default-expand-all
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTransferDialog = false">取消</el-button>
        <el-button type="primary" @click="handleTransfer">确认转移</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'DocumentSpace' })
import { ref, shallowRef, triggerRef, computed, onMounted, onUnmounted, onActivated, onDeactivated, watch, nextTick } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import axios from 'axios'
import { Search, List, Picture, Upload, FolderAdd, Delete, Download, UploadFilled, MoreFilled, FolderOpened, StarFilled, Star, ArrowDown, ArrowLeft, View, Edit, RefreshLeft, Rank, CopyDocument, Loading, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getDirectories, getFiles, getFilesPage, createDirectory, updateDirectory, deleteDirectory,
  moveDirectory, batchSortDirectories, deleteFile, downloadFile, searchFiles, batchDownload, downloadBatchFile,
  getRecycleBin, moveToRecycleBin, batchMoveToRecycleBin, restoreFromRecycleBin, permanentDelete, clearRecycleBin,
  transferFile, getDepartments, renameFile, renameDirectory, moveFiles, copyFiles, getFileCover,
  type DirectoryDTO, type FileDTO, type RecycleBinItem, type DepartmentDTO,
} from '@/api/document'
import { addFavorite, removeFavorite } from '@/api/workspace'
import { useUserStore } from '@/stores/user'
import { useDocumentStore } from '@/stores/document'
import { formatFileSize, getFileTypeColor, isImage, isVideo } from '@/utils/chunk-upload'
import ChunkUploader from '@/components/ChunkUploader.vue'
import VoiceInput from '@/components/VoiceInput.vue'
import type { ChunkUploadState } from '@/utils/chunk-upload'
import FileIcon from '@/components/FileIcon.vue'

const router = useRouter()
const userStore = useUserStore()
const docStore = useDocumentStore()

// 用户前缀的 sessionStorage key 工具函数（区分不同用户的状态）
function userKey(key: string): string {
  return `u${userStore.userId}:${key}`
}

// UI 状态
const searchKeyword = ref('')
const loading = ref(false)
// 换页加载状态：轻量加载指示器，保留旧数据可见
const pageChanging = ref(false)
// 视图模式：从 sessionStorage 恢复，离开前持久化，确保从详情页返回时仍是原视图
const STORED_VIEW_MODE = sessionStorage.getItem(userKey('doc_view_mode')) as 'table' | 'thumbnail' | null
const viewMode = ref<'table' | 'thumbnail'>(
  STORED_VIEW_MODE === 'thumbnail' ? 'thumbnail' : 'table'
)

// 保存和恢复滚动条高度所需的变量
const tableRef = ref<any>(null)
const thumbnailRef = ref<HTMLElement | null>(null)
const savedScrollTop = ref(0)

// 空间切换：从 store 恢复初始值，避免刷新时默认显示个人空间
const initialSpaceTab = (() => {
  const key = docStore.currentSpaceKey
  if (!key) return 'personal' as const
  const st = parseInt(key.split(':')[0])
  if (st === 1) return 'department' as const
  if (st === 2) return 'enterprise' as const
  return 'personal' as const
})()
const currentSpaceTab = ref<'personal' | 'department' | 'enterprise'>(initialSpaceTab)
const spaceType = computed(() => {
  const map: Record<string, number> = { personal: 0, department: 1, enterprise: 2 }
  return map[currentSpaceTab.value]
})
const spaceId = computed(() => {
  if (currentSpaceTab.value === 'personal') return userStore.userId
  if (currentSpaceTab.value === 'department') return userStore.departmentId
  return userStore.companyId
})

const spaceKey = computed(() => `${spaceType.value}:${spaceId.value}`)

function handleSpaceTabChange(newTab: string) {
  // 验证权限
  if (newTab === 'department' && !userStore.departmentId) {
    ElMessage.warning('您尚未分配部门，无法访问部门空间')
    currentSpaceTab.value = 'personal' // 回退到个人空间
    handleSpaceTabChange('personal') // 递归调用以加载个人空间
    return
  }
  if (newTab === 'enterprise' && !userStore.companyId) {
    ElMessage.warning('您尚未分配企业，无法访问企业空间')
    currentSpaceTab.value = 'personal' // 回退到个人空间
    handleSpaceTabChange('personal') // 递归调用以加载个人空间
    return
  }
  
  clearStoredScroll() // 切换空间时清空滚动记录
  docStore.setSpaceKey(spaceKey.value)
  currentPage.value = 1
  searchKeyword.value = ''
  // 切换空间时清空缓存池
  cachePoolFiles.value = []
  cachePoolStartIndex.value = 0
  docStore.setCurrentDirectory(null)
  breadcrumbPath.value = []
  loadExpandedDirs() // 加载目标空间的目录展开状态
  fetchDirectories()

  // 采用与 navigateToDir 一致的 stale-while-revalidate 策略：
  // 1. 命中缓存则秒开，仅在缓存过期时后台静默同步
  // 2. 未命中缓存才显示 loading 并请求
  const cached = docStore.getCachedFiles(null)
  if (cached) {
    loading.value = false
    // 立即从缓存恢复 totalElements，使分页按钮即时显示
    const cachedTotal = docStore.getCachedTotalElements(null)
    if (cachedTotal != null) totalElements.value = cachedTotal
    if (docStore.isCacheStale(null)) {
      fetchFiles(null, false)
    } else {
      // 缓存未过期，用 store 缓存数据临时填充缓存池
      cachePoolFiles.value = cached
      cachePoolStartIndex.value = 0
    }
  } else {
    loading.value = true
    fetchFiles(null, true)
  }
}

// currentDirId 指向 store
const currentDirId = computed({
  get: () => docStore.currentDirectoryId,
  set: (val: number | null) => docStore.setCurrentDirectory(val === null ? null : docStore.directories.find(d => d.id === val) || null)
})

const currentDirName = computed(() => {
  if (!currentDirId.value) return '根目录'
  const dir = docStore.directories.find(d => d.id === currentDirId.value)
  return dir?.dirName || '当前目录'
})

// 从 store 读取基础数据（normalized，增量更新）
const baseFiles = computed(() => docStore.currentFiles)

// 搜索过滤层：不修改原始数据，纯计算属性
const currentFiles = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return baseFiles.value
  return baseFiles.value.filter(f => f.fileName?.toLowerCase().includes(kw))
})

const selectedFiles = computed(() => docStore.selectedFiles)

// 记录正在预加载的目录，防止鼠标晃动导致重复请求
const prefetchingSet = new Set<number>()

// 预加载核心逻辑：写入 store 的 normalized cache
async function prefetchFiles(dirId: number) {
  const sKey = `${spaceKey.value}:${dirId === null ? '' : String(dirId)}`
  if (sKey in docStore.dirFileIds || prefetchingSet.has(dirId)) return
  
  // 捕获当前空间标识，防止切换空间后旧请求覆盖新数据（竞态条件）
  const requestSpaceKey = spaceKey.value
  prefetchingSet.add(dirId)
  try {
    const res = await getFiles(dirId, spaceType.value, spaceId.value)
    // 请求完成时如果空间已切换，丢弃结果
    if (requestSpaceKey !== spaceKey.value) return
    const data = res.data.data || res.data
    const files = data.filter((f: any) => !!f.fileType)
    // 写入 store 的 normalized cache（不切换 currentDirectoryId）
    docStore.patchFiles(files, dirId)
  } catch {
    // 预加载失败不报错
  } finally {
    prefetchingSet.delete(dirId)
  }
}

// 分页相关（双层分页：后端大分页 + 前端小分页）
// 前端每页展示 18 个文件；后端一次性拉取 180 条（约 10 页）放入缓存池
// 用户在前 10 页内翻页时 0 网络延迟，超出缓存池才请求下一个 chunk
const frontendPageSize = 18   // 前端每页展示数量
const backendPageSize = 180   // 后端一次拉取数量（约 10 页）
const pageSize = frontendPageSize // 兼容模板中引用的 pageSize
const currentPage = ref(1)    // 前端当前页（1-based）
const totalElements = ref(0)  // 目录文件总数（来自后端）

// 缓存池元数据：记录当前缓存池覆盖的全局起始索引和已缓存的文件列表
const cachePoolStartIndex = ref(0)         // 当前缓存池在全局数据中的起始索引
const cachePoolFiles = ref<FileDTO[]>([])  // 当前缓存池的文件列表（最多 backendPageSize 条）

// 排序状态：默认按修改时间（updateTime）降序
type SortField = 'updateTime' | 'fileSize' | null
const sortField = ref<SortField>('updateTime')
const sortOrder = ref<'desc' | 'asc'>('desc')

function handleSortChange({ prop, order }: { prop: string; order: 'ascending' | 'descending' | null }) {
  if (!order) {
    // 取消排序：回到默认（修改时间降序）
    sortField.value = 'updateTime'
    sortOrder.value = 'desc'
    currentPage.value = 1
    cachePoolFiles.value = []
    cachePoolStartIndex.value = 0
    return
  }
  if (prop === 'fileSize' || prop === 'updateTime') {
    sortField.value = prop
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  }
  currentPage.value = 1
  // 排序变化时清空缓存池，强制重新拉取
  cachePoolFiles.value = []
  cachePoolStartIndex.value = 0
}

// 右侧只显示文件，不显示子目录
const totalItems = computed(() => searchKeyword.value.trim() ? currentFiles.value.length : totalElements.value)

/**
 * 缓存池中的文件（应用前端排序）
 * 非搜索模式下，从缓存池取数据并排序
 */
const sortedCacheFiles = computed(() => {
  if (searchKeyword.value.trim()) return []
  const list = [...cachePoolFiles.value]
  const field = sortField.value
  if (!field) return list
  const dir = sortOrder.value === 'asc' ? 1 : -1
  return list.sort((a, b) => {
    let av: any, bv: any
    if (field === 'fileSize') {
      av = a.fileSize ?? 0
      bv = b.fileSize ?? 0
    } else {
      // updateTime：优先用 updateTime，没有则用 createTime
      av = new Date(a.updateTime || a.createTime || 0).getTime()
      bv = new Date(b.updateTime || b.createTime || 0).getTime()
    }
    if (av < bv) return -1 * dir
    if (av > bv) return 1 * dir
    return 0
  })
})

/**
 * 搜索模式下的排序结果（来自 currentFiles，即 store 中的数据）
 */
const sortedFiles = computed(() => {
  if (!searchKeyword.value.trim()) return []
  const list = [...currentFiles.value]
  const field = sortField.value
  if (!field) return list
  const dir = sortOrder.value === 'asc' ? 1 : -1
  return list.sort((a, b) => {
    let av: any, bv: any
    if (field === 'fileSize') {
      av = a.fileSize ?? 0
      bv = b.fileSize ?? 0
    } else {
      av = new Date(a.updateTime || a.createTime || 0).getTime()
      bv = new Date(b.updateTime || b.createTime || 0).getTime()
    }
    if (av < bv) return -1 * dir
    if (av > bv) return 1 * dir
    return 0
  })
})

/**
 * 最终展示的文件列表（双层分页核心）
 * - 非搜索模式：从缓存池按前端页码切片
 * - 搜索模式：从搜索结果按前端页码切片
 */
const paginatedFiles = computed(() => {
  if (searchKeyword.value.trim()) {
    // 搜索模式：前端分页
    const start = (currentPage.value - 1) * frontendPageSize
    return sortedFiles.value.slice(start, start + frontendPageSize)
  }
  // 非搜索模式：从缓存池切片
  // currentPage 是 1-based，缓存池中对应偏移量 = (currentPage - 1) * frontendPageSize - cachePoolStartIndex
  const globalStartIndex = (currentPage.value - 1) * frontendPageSize
  const localStartIndex = globalStartIndex - cachePoolStartIndex.value
  if (localStartIndex < 0 || localStartIndex >= sortedCacheFiles.value.length) {
    return []
  }
  return sortedCacheFiles.value.slice(localStartIndex, localStartIndex + frontendPageSize)
})

/**
 * 判断当前页数据是否已在缓存池中
 * 只需检查起始索引是否落在缓存池范围内：
 * - 若起始索引已超出总文件数，说明是空页，无需请求
 * - 若起始索引在缓存池范围内，数据已在本地（末尾页可能不足 frontendPageSize 条，
 *   但 slice 会自动截断，不会越界）
 */
function isDataInCachePool(page: number): boolean {
  const globalStartIndex = (page - 1) * frontendPageSize
  // 空页（超出总文件数）：无需请求
  if (totalElements.value > 0 && globalStartIndex >= totalElements.value) return true
  const poolStart = cachePoolStartIndex.value
  const poolEnd = poolStart + cachePoolFiles.value.length
  return globalStartIndex >= poolStart && globalStartIndex < poolEnd
}

/**
 * 从缓存池中移除指定文件（删除/移动文件后调用）
 * 同时更新 totalElements
 */
function removeFromCachePool(fileIds: number[]) {
  if (fileIds.length === 0) return
  const idSet = new Set(fileIds)
  const before = cachePoolFiles.value.length
  cachePoolFiles.value = cachePoolFiles.value.filter(f => !idSet.has(f.id))
  const removed = before - cachePoolFiles.value.length
  if (removed > 0) {
    totalElements.value = Math.max(0, totalElements.value - removed)
  }
}

/**
 * 重置缓存池到第一页（上传/排序变化等需要重新加载时调用）
 */
function resetCachePool() {
  cachePoolFiles.value = []
  cachePoolStartIndex.value = 0
  currentPage.value = 1
}

function handlePageChange(page: number) {
  currentPage.value = page

  // 双层分页核心：判断目标页是否在缓存池中
  if (!searchKeyword.value.trim() && isDataInCachePool(page)) {
    // 数据已在缓存池，0 网络延迟，直接由 computed 切片渲染
    return
  }

  // 超出缓存池，需要请求后端加载新的 chunk
  pageChanging.value = true
  fetchFiles(currentDirId.value, true)
}

// 缓存表格数据引用稳定
const tableData = computed(() => paginatedFiles.value)
const breadcrumbPath = ref<DirectoryDTO[]>([])

const showUploadDialog = ref(false)
const chunkUploaderRef = ref()
const showNewDirDialog = ref(false)
const showRenameDialog = ref(false)
const showMoveDialog = ref(false)
const showRecycleBin = ref(false)
const showTransferDialog = ref(false)
const showFileMoveCopyDialog = ref(false)
const fileMoveCopyMode = ref<'move' | 'copy'>('move')
const fileMoveCopyTargets = ref<FileDTO[]>([])
const fileMoveCopyTargetDirId = ref<number | null>(null)
const fileMoveCopyLoading = ref(false)
const fileMoveCopyTreeRef = ref<any>(null)
const fileMoveCopyTargetSpace = ref<'current' | 'personal' | 'department' | 'enterprise'>('current')
const fileMoveCopyTargetDirs = ref<DirectoryDTO[]>([])

const fileMoveCopyDirTree = computed(() => {
  if (fileMoveCopyMode.value === 'copy' && fileMoveCopyTargetSpace.value !== 'current') {
    const map = new Map<number, any>()
    const roots: any[] = []
    fileMoveCopyTargetDirs.value.forEach(d => {
      map.set(d.id, { ...d, children: [] })
    })
    map.forEach(node => {
      if (node.parentId && map.has(node.parentId)) {
        map.get(node.parentId).children.push(node)
      } else {
        roots.push(node)
      }
    })
    return [{ id: 0, dirName: '根目录', children: roots }]
  }
  return dirTreeForSelect.value
})

const fileMoveCopyTargetSpaceInfo = computed(() => {
  if (fileMoveCopyMode.value === 'move' || fileMoveCopyTargetSpace.value === 'current') {
    return { spaceType: spaceType.value, spaceId: spaceId.value }
  }
  const map: Record<string, number> = { personal: 0, department: 1, enterprise: 2 }
  const st = map[fileMoveCopyTargetSpace.value]
  let sid: number | null = null
  if (fileMoveCopyTargetSpace.value === 'personal') sid = userStore.userId
  else if (fileMoveCopyTargetSpace.value === 'department') sid = userStore.departmentId
  else if (fileMoveCopyTargetSpace.value === 'enterprise') sid = userStore.companyId
  return { spaceType: st, spaceId: sid }
})
const renameFileTarget = ref<FileDTO | null>(null)

const fileExtension = computed(() => {
  if (!renameFileTarget.value?.fileName) return ''
  const dotIdx = renameFileTarget.value.fileName.lastIndexOf('.')
  if (dotIdx > 0 && dotIdx < renameFileTarget.value.fileName.length - 1) {
    return renameFileTarget.value.fileName.substring(dotIdx + 1)
  }
  return ''
})

const newDirForm = ref({ dirName: '', parentId: null as number | null })
const newDirFormRef = ref<FormInstance>()
const renameValue = ref('')
const renameTarget = ref<DirectoryDTO | null>(null)
const moveTarget = ref<DirectoryDTO | null>(null)
const moveDirTarget = ref<number | null>(null)
const recycleBinItems = ref<RecycleBinItem[]>([])
const transferTarget = ref<FileDTO | null>(null)
const transferForm = ref<{ targetDepartmentId: number | null; targetDirectoryId: number | null }>({
  targetDepartmentId: null,
  targetDirectoryId: null,
})
const allDepartments = ref<DepartmentDTO[]>([])

// 文件拖拽移动状态
const draggedFileIds = ref<number[]>([])
const isDraggingFile = computed(() => draggedFileIds.value.length > 0)
const dragOverTarget = ref<{ type: 'dir' | 'space'; id: number | null; spaceTab?: string } | null>(null)
const crossSpaceDirs = ref<Record<string, DirectoryDTO[]>>({})
const crossSpaceDirTrees = computed(() => {
  const result: Record<string, any[]> = {}
  for (const tab of Object.keys(crossSpaceDirs.value)) {
    const dirs = crossSpaceDirs.value[tab]
    const map = new Map<number, any>()
    const roots: any[] = []
    dirs.forEach(d => map.set(d.id, { ...d, children: [] }))
    map.forEach(node => {
      if (node.parentId && map.has(node.parentId)) {
        map.get(node.parentId).children.push(node)
      } else {
        roots.push(node)
      }
    })
    result[tab] = [{ id: 0, dirName: '根目录', children: roots }]
  }
  return result
})
const dirTreeRef = ref<any>(null)
const crossSpaceTreeRefs = ref<Record<string, any>>({})
let autoExpandTimer: ReturnType<typeof setTimeout> | null = null

// 目录树展开状态持久化：按空间隔离，刷新或返回时恢复展开层级
const expandedDirIds = ref<number[]>([])

/** 从 sessionStorage 加载当前空间的展开目录 ID 列表 */
function loadExpandedDirs() {
  const key = userKey(`doc_expanded_dirs:${spaceKey.value}`)
  try {
    const raw = sessionStorage.getItem(key)
    expandedDirIds.value = raw ? JSON.parse(raw) : []
  } catch {
    expandedDirIds.value = []
  }
}

/** 持久化当前展开目录 ID 列表 */
function saveExpandedDirs() {
  sessionStorage.setItem(userKey(`doc_expanded_dirs:${spaceKey.value}`), JSON.stringify(expandedDirIds.value))
}

/** 节点展开：记录到展开列表 */
function handleDirNodeExpand(data: DirectoryDTO) {
  if (data.id != null && !expandedDirIds.value.includes(data.id)) {
    expandedDirIds.value.push(data.id)
    saveExpandedDirs()
  }
}

/** 节点折叠：从展开列表移除 */
function handleDirNodeCollapse(data: DirectoryDTO) {
  if (data.id != null) {
    expandedDirIds.value = expandedDirIds.value.filter(id => id !== data.id)
    saveExpandedDirs()
  }
}

watch(showFileMoveCopyDialog, (val) => {
  if (val) {
    fileMoveCopyTargetDirId.value = null
    fileMoveCopyLoading.value = false
    fileMoveCopyTargetSpace.value = 'current'
    fileMoveCopyTargetDirs.value = []
    setTimeout(() => {
      if (fileMoveCopyTreeRef.value) {
        fileMoveCopyTreeRef.value.setCurrentKey(null)
      }
    }, 0)
  }
})

watch(showNewDirDialog, (val) => {
  if (val) {
    newDirForm.value = { dirName: '', parentId: null }
  }
})

watch(showRenameDialog, (val) => {
  if (!val) {
    renameValue.value = ''
    renameFileTarget.value = null
    renameTarget.value = null
  }
})

// 所有目录引用 store
const allDirectories = computed(() => docStore.directories)

const dirTree = computed(() => {
  const map = new Map<number, any>()
  const roots: any[] = []
  allDirectories.value.forEach(d => {
    map.set(d.id, { ...d, children: [] })
  })
  map.forEach(node => {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId).children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
})

const dirTreeForSelect = computed(() => {
  return [{ id: 0, dirName: '根目录', children: dirTree.value }]
})

const departmentTreeForTransfer = computed(() => {
  const map = new Map<number, any>()
  const roots: any[] = []
  allDepartments.value.forEach(d => {
    map.set(d.id, { id: d.id, deptName: d.deptName, children: [] })
  })
  map.forEach(node => {
    const dept = allDepartments.value.find(d => d.id === node.id)
    if (dept && dept.parentId && map.has(dept.parentId)) {
      map.get(dept.parentId).children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
})

function buildBreadcrumb(dirId: number | null) {
  breadcrumbPath.value = []
  if (!dirId) return
  const dirs = docStore.directories
  const findPath = (id: number, path: DirectoryDTO[]): boolean => {
    const dir = dirs.find(d => d.id === id)
    if (!dir) return false
    path.unshift(dir)
    if (dir.parentId) return findPath(dir.parentId, path)
    return true
  }
  findPath(dirId, breadcrumbPath.value)
}

async function fetchDirectories() {
  // 捕获当前空间标识，防止切换空间后旧请求覆盖新数据（竞态条件）
  const requestSpaceKey = spaceKey.value
  try {
    const res = await getDirectories(undefined, spaceType.value, spaceId.value)
    // 请求完成时如果空间已切换，丢弃结果
    if (requestSpaceKey !== spaceKey.value) return
    const dirs = res.data.data || res.data
    docStore.setDirectories(dirs)
  } catch { /* ignore */ }
}

async function fetchFiles(dirId: number | null, showLoading = false, bypassCache = false) {
  // 捕获当前空间标识，防止切换空间后旧请求覆盖新数据（竞态条件）
  const requestSpaceKey = spaceKey.value
  if (showLoading && cachePoolFiles.value.length === 0) {
    loading.value = true
  }

  try {
    // 双层分页：后端大分页（一次拉取 backendPageSize 条），前端小分页（frontendPageSize 条）
    // 计算当前前端页对应的后端 chunk 索引
    const currentChunkIndex = Math.floor((currentPage.value - 1) * frontendPageSize / backendPageSize)
    const res = await getFilesPage(
      dirId,
      spaceType.value,
      spaceId.value,
      currentChunkIndex,
      backendPageSize,
      sortField.value === 'fileSize' ? 'fileSize' : (sortField.value === 'updateTime' ? 'updateTime' : 'createTime'),
      sortOrder.value
    )
    // 请求完成时如果空间已切换，丢弃结果避免污染新空间缓存
    if (requestSpaceKey !== spaceKey.value) return
    const pageData = res.data.data || res.data
    const files = (pageData.content || []).filter((f: any) => !!f.fileType)
    totalElements.value = pageData.totalElements || 0
    docStore.setCachedTotalElements(dirId, totalElements.value)

    // 填充缓存池：记录全局起始索引和文件列表
    cachePoolStartIndex.value = currentChunkIndex * backendPageSize
    cachePoolFiles.value = files

    // 同步到 store（保持与其他逻辑兼容，如上传、删除等操作）
    docStore.patchFiles(files, dirId)
  } catch (error) {
    console.error('获取文件列表失败:', error)
    if (currentDirId.value === dirId && requestSpaceKey === spaceKey.value) {
      cachePoolFiles.value = []
      cachePoolStartIndex.value = 0
      docStore.patchFiles([], dirId)
      totalElements.value = 0
      docStore.setCachedTotalElements(dirId, 0)
    }
  } finally {
    if (currentDirId.value === dirId && requestSpaceKey === spaceKey.value) {
      loading.value = false
      pageChanging.value = false
      restoreScrollPosition() // 数据加载完成后恢复滚动位置
    }
  }
}

// stale-while-revalidate：缓存秒出 + 后台静默同步
function navigateToDir(dir: DirectoryDTO | null) {
  const newDirId = dir?.id ?? null
  if (newDirId === currentDirId.value) return
  
  clearStoredScroll() // 切换目录时清空滚动记录
  
  // 切换目录（store 更新 currentDirectoryId）
  docStore.setCurrentDirectory(dir)
  buildBreadcrumb(newDirId)
  currentPage.value = 1
  searchKeyword.value = ''

  // 切换目录时清空缓存池
  cachePoolFiles.value = []
  cachePoolStartIndex.value = 0
  
  const cached = docStore.getCachedFiles(newDirId)
  if (cached) {
    // 存在 store 缓存，界面"秒开"，直接渲染
    loading.value = false
    // 立即从缓存恢复 totalElements，使分页按钮即时显示
    const cachedTotal = docStore.getCachedTotalElements(newDirId)
    if (cachedTotal != null) totalElements.value = cachedTotal
    // 双层分页：仍需从后端拉取第一个 chunk 填充缓存池
    // 仅在缓存过期时后台同步，避免频繁请求后端
    if (docStore.isCacheStale(newDirId)) {
      fetchFiles(newDirId, false)
    } else {
      // 缓存未过期，用 store 缓存数据临时填充缓存池（秒出效果）
      cachePoolFiles.value = cached
      cachePoolStartIndex.value = 0
    }
  } else {
    // 无缓存，显示加载动画
    loading.value = true
    totalElements.value = 0
    fetchFiles(newDirId, true)
  }
}

function goToParentDir() {
  if (!currentDirId.value) return
  const currentDir = docStore.directories.find(d => d.id === currentDirId.value)
  if (currentDir && currentDir.parentId) {
    const parentDir = docStore.directories.find(d => d.id === currentDir.parentId)
    navigateToDir(parentDir || null)
  } else {
    navigateToDir(null)
  }
}

function handleDirClick(data: DirectoryDTO) {
  navigateToDir(data)
}

function allowDirDrag(draggingNode: any) {
  return !!draggingNode.data?.id
}

function allowDirDrop(draggingNode: any, dropNode: any, type: string) {
  if (type === 'inner') {
    return true
  }
  const dragDept = draggingNode.data?.departmentId
  const dropDept = dropNode.data?.departmentId
  if (dragDept && dropDept && dragDept !== dropDept) {
    return false
  }
  return true
}

async function handleDirDrop(draggingNode: any, dropNode: any, dropType: string) {
  const dragId = draggingNode.data.id
  let newParentId: number | null = null
  let sortOrder = 0

  if (dropType === 'inner') {
    newParentId = dropNode.data.id
  } else {
    newParentId = dropNode.data.parentId || null
    const siblings = dropNode.parent?.childNodes || []
    sortOrder = siblings.findIndex((n: any) => n.data?.id === dragId)
    if (sortOrder < 0) sortOrder = 0
  }

  const dragDept = draggingNode.data.departmentId
  const dropDept = dropNode.data.departmentId
  if (dropType !== 'inner' && dragDept && dropDept && dragDept !== dropDept) {
    ElMessage.warning('不允许跨部门拖拽排序')
    fetchDirectories()
    return
  }

  try {
    await moveDirectory(dragId, { newParentId, sortOrder })
    ElMessage.success('目录移动成功')
    fetchDirectories()
  } catch {
    ElMessage.error('目录移动失败')
    fetchDirectories()
  }
}

// === 文件拖拽移动（IDEA 风格：拖文件到侧边栏目录） ===

const spaceNameMap: Record<string, string> = { personal: '个人空间', department: '部门空间', enterprise: '企业空间' }

function buildDirFullPath(dirId: number | null, spaceTab: string): string {
  const spaceName = spaceNameMap[spaceTab] || ''
  if (!dirId) return spaceName
  const dirs = spaceTab === currentSpaceTab.value ? docStore.directories : (crossSpaceDirs.value[spaceTab] || [])
  const path: string[] = []
  const findPath = (id: number): boolean => {
    const dir = dirs.find(d => d.id === id)
    if (!dir) return false
    path.unshift(dir.dirName)
    if (dir.parentId) return findPath(dir.parentId)
    return true
  }
  findPath(dirId)
  return spaceName + (path.length ? ' / ' + path.join(' / ') : '')
}

function buildSourcePath(): string {
  return buildDirFullPath(currentDirId.value, currentSpaceTab.value)
}

function getDraggedFileNames(): string[] {
  return draggedFileIds.value.map(id => {
    const f = docStore.filesById[id]
    return f?.fileName || `文件#${id}`
  })
}

function getFileNamesByIds(ids: number[]): string[] {
  return ids.map(id => {
    const f = docStore.filesById[id]
    return f?.fileName || `文件#${id}`
  })
}

function buildMoveConfirmMsg(targetPath: string, fileIds: number[], isCrossSpace: boolean): string {
  const sourcePath = buildSourcePath()
  const names = getFileNamesByIds(fileIds)
  const escapedSource = sourcePath.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const escapedTarget = targetPath.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  let fileHtml: string
  if (names.length === 1) {
    const escaped = names[0].replace(/</g, '&lt;').replace(/>/g, '&gt;')
    fileHtml = `<b style="color:#409EFF">${escaped}</b>`
  } else {
    const escaped = names.slice(0, 3).map(n => n.replace(/</g, '&lt;').replace(/>/g, '&gt;'))
    fileHtml = `<b style="color:#409EFF">${escaped.join('、')}</b>`
    if (names.length > 3) fileHtml += ` 等 ${names.length} 个文件`
  }
  const crossNote = isCrossSpace ? '<br><span style="color:#E6A23C;font-size:12px;">此操作将改变文件所属空间</span>' : ''
  return `将 ${fileHtml}<br>从 <code style="background:#f5f7fa;padding:2px 6px;border-radius:3px;color:#606266;">${escapedSource}</code> 移动到 <code style="background:#f0f9eb;padding:2px 6px;border-radius:3px;color:#67C23A;">${escapedTarget}</code>${crossNote}`
}

function handleFileDragStart(e: DragEvent, file: FileDTO) {
  const selectedIds = docStore.selectedFiles.map(f => f.id)
  if (selectedIds.includes(file.id)) {
    draggedFileIds.value = [...selectedIds]
  } else {
    draggedFileIds.value = [file.id]
  }
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(draggedFileIds.value.length))
  }
  // 预加载其他空间的目录结构
  loadCrossSpaceDirs()
}

function handleFileDragEnd() {
  // ⚠️ 关键：必须延迟清空 draggedFileIds.value
  // 因为 drop 事件处理函数 handleFileDropOnDir 是 async 的，
  // 它内部 await ElMessageBox.confirm() 时，dragend 会先于 await resolve 触发
  // 如果立即清空，drop 处理函数中的 fileIds 就会丢失（变成空数组）
  // 实际：drop 中已通过 [...draggedFileIds.value] 复制了本地变量，
  // 但弹窗显示的"文件名"等信息还会读 ref，所以这里也延迟一帧清空确保一致
  setTimeout(() => {
    draggedFileIds.value = []
  }, 0)
  dragOverTarget.value = null
  if (autoExpandTimer) { clearTimeout(autoExpandTimer); autoExpandTimer = null }
}

async function loadCrossSpaceDirs() {
  const tabs = ['personal', 'department', 'enterprise'].filter(t => t !== currentSpaceTab.value)
  for (const tab of tabs) {
    if (crossSpaceDirs.value[tab]) continue
    const map: Record<string, number> = { personal: 0, department: 1, enterprise: 2 }
    const st = map[tab]
    let sid: number | null = null
    if (tab === 'personal') sid = userStore.userId
    else if (tab === 'department') sid = userStore.departmentId
    else if (tab === 'enterprise') sid = userStore.companyId
    if (st != null && sid != null) {
      try {
        const res = await getDirectories(undefined, st, sid)
        crossSpaceDirs.value[tab] = res.data.data || res.data
      } catch { /* ignore */ }
    }
  }
}

function handleFileDragOverDir(e: DragEvent, dirId: number | null) {
  if (!isDraggingFile.value) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverTarget.value = { type: 'dir', id: dirId }
  // 自动展开：拖到未展开的目录节点上 600ms 后展开
  if (dirId !== null && dirTreeRef.value) {
    const node = dirTreeRef.value.getNode(dirId)
    if (node && !node.expanded && node.childNodes?.length) {
      if (autoExpandTimer) clearTimeout(autoExpandTimer)
      autoExpandTimer = setTimeout(() => {
        if (dragOverTarget.value?.type === 'dir' && dragOverTarget.value.id === dirId && node && !node.expanded) {
          node.expanded = true
        }
      }, 600)
    }
  }
}

function handleFileDragLeaveDir(e: DragEvent, dirId: number | null) {
  if (!isDraggingFile.value) return
  const el = e.currentTarget as HTMLElement
  const related = e.relatedTarget as HTMLElement | null
  if (related && el.contains(related)) return
  if (dragOverTarget.value?.type === 'dir' && dragOverTarget.value.id === dirId) {
    dragOverTarget.value = null
    if (autoExpandTimer) { clearTimeout(autoExpandTimer); autoExpandTimer = null }
  }
}

async function handleFileDropOnDir(e: DragEvent, dirId: number | null) {
  e.preventDefault()
  e.stopPropagation()
  // ⚠️ 关键：dragend 事件会在 drop 之后、await 弹窗期间触发并清空 draggedFileIds.value
  // 所以必须立即把 fileIds 复制到本地变量，不能依赖响应式 ref
  const fileIds = [...draggedFileIds.value]
  dragOverTarget.value = null
  if (autoExpandTimer) { clearTimeout(autoExpandTimer); autoExpandTimer = null }
  if (fileIds.length === 0) return

  if (dirId === currentDirId.value) {
    ElMessage.info('文件已在当前目录中')
    draggedFileIds.value = []
    return
  }

  const targetPath = buildDirFullPath(dirId, currentSpaceTab.value)

  try {
    await ElMessageBox.confirm(
      buildMoveConfirmMsg(targetPath, fileIds, false),
      '移动确认',
      { type: 'info', confirmButtonText: '移动', cancelButtonText: '取消', dangerouslyUseHTMLString: true }
    )
  } catch {
    draggedFileIds.value = []
    return
  }

  try {
    const res = await moveFiles(fileIds, dirId, spaceType.value, spaceId.value)
    if (!res.data.data || res.data.data.length === 0) {
      ElMessage.warning('后端未移动任何文件，请检查后端日志')
    } else {
      ElMessage.success(`移动成功，共 ${res.data.data.length} 个文件`)
    }
    docStore.clearSelection()
    // 乐观更新：从当前目录的列表中移除这些文件（不重新拉取，避免"全列表闪烁"）
    docStore.removeFiles(fileIds)
    removeFromCachePool(fileIds)
    // 清除目标目录缓存（用户切过去时能拿到最新数据）
    docStore.removeDirCache(dirId)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '移动失败')
    // 失败时强制重新拉取真实数据
    docStore.removeDirCache(currentDirId.value)
    await fetchFiles(currentDirId.value, true, true)
  } finally {
    draggedFileIds.value = []
  }
}

function handleFileDragOverSpace(e: DragEvent, spaceTab: string) {
  if (!isDraggingFile.value) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverTarget.value = { type: 'space', id: null, spaceTab }
}

function handleFileDragLeaveSpace(e: DragEvent) {
  if (!isDraggingFile.value) return
  const el = e.currentTarget as HTMLElement
  const related = e.relatedTarget as HTMLElement | null
  if (related && el.contains(related)) return
  if (dragOverTarget.value?.type === 'space') {
    dragOverTarget.value = null
    if (autoExpandTimer) { clearTimeout(autoExpandTimer); autoExpandTimer = null }
  }
}

async function handleFileDropOnSpace(e: DragEvent, spaceTab: string) {
  e.preventDefault()
  // ⚠️ 关键：立即复制 fileIds 到本地变量，避免被 dragend 清空
  const fileIds = [...draggedFileIds.value]
  dragOverTarget.value = null
  if (autoExpandTimer) { clearTimeout(autoExpandTimer); autoExpandTimer = null }
  if (fileIds.length === 0) return

  const map: Record<string, number> = { personal: 0, department: 1, enterprise: 2 }
  const targetSpaceType = map[spaceTab]
  let targetSpaceId: number | null = null
  if (spaceTab === 'personal') targetSpaceId = userStore.userId
  else if (spaceTab === 'department') targetSpaceId = userStore.departmentId
  else if (spaceTab === 'enterprise') targetSpaceId = userStore.companyId

  const targetPath = spaceNameMap[spaceTab] || spaceTab

  try {
    await ElMessageBox.confirm(
      buildMoveConfirmMsg(targetPath, fileIds, true),
      '跨空间移动确认',
      { type: 'warning', confirmButtonText: '移动', cancelButtonText: '取消', dangerouslyUseHTMLString: true }
    )
  } catch {
    draggedFileIds.value = []
    return
  }

  try {
    await moveFiles(fileIds, null, targetSpaceType, targetSpaceId)
    ElMessage.success('跨空间移动成功')
    docStore.clearSelection()
    // 乐观更新：直接从当前目录的列表中移除（避免全列表重拉）
    docStore.removeFiles(fileIds)
    removeFromCachePool(fileIds)
    // 关键修复：清除目标空间根目录的前端缓存，否则切换到目标空间时仍显示旧列表（无新移动过来的文件）
    docStore.removeDirCache(null, `${targetSpaceType}:${targetSpaceId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '跨空间移动失败')
    docStore.removeDirCache(currentDirId.value)
    await fetchFiles(currentDirId.value, true, true)
  } finally {
    draggedFileIds.value = []
  }
}

function handleCrossSpaceDragOverDir(e: DragEvent, spaceTab: string, dirId: number | null) {
  if (!isDraggingFile.value) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverTarget.value = { type: 'space', id: dirId, spaceTab }
  // 自动展开跨空间目录树
  if (dirId !== null) {
    const treeRef = crossSpaceTreeRefs.value[spaceTab]
    if (treeRef) {
      const node = treeRef.getNode(dirId)
      if (node && !node.expanded && node.childNodes?.length) {
        if (autoExpandTimer) clearTimeout(autoExpandTimer)
        autoExpandTimer = setTimeout(() => {
          if (dragOverTarget.value?.type === 'space' && dragOverTarget.value.id === dirId && node && !node.expanded) {
            node.expanded = true
          }
        }, 600)
      }
    }
  }
}

function handleCrossSpaceDragLeaveDir(e: DragEvent) {
  if (!isDraggingFile.value) return
  const el = e.currentTarget as HTMLElement
  const related = e.relatedTarget as HTMLElement | null
  if (related && el.contains(related)) return
  if (dragOverTarget.value?.type === 'space') {
    dragOverTarget.value = null
    if (autoExpandTimer) { clearTimeout(autoExpandTimer); autoExpandTimer = null }
  }
}

async function handleCrossSpaceDropOnDir(e: DragEvent, spaceTab: string, dirId: number | null) {
  e.preventDefault()
  e.stopPropagation()
  // ⚠️ 关键：立即复制 fileIds 到本地变量，避免被 dragend 清空
  const fileIds = [...draggedFileIds.value]
  dragOverTarget.value = null
  if (autoExpandTimer) { clearTimeout(autoExpandTimer); autoExpandTimer = null }
  if (fileIds.length === 0) return

  const map: Record<string, number> = { personal: 0, department: 1, enterprise: 2 }
  const targetSpaceType = map[spaceTab]
  let targetSpaceId: number | null = null
  if (spaceTab === 'personal') targetSpaceId = userStore.userId
  else if (spaceTab === 'department') targetSpaceId = userStore.departmentId
  else if (spaceTab === 'enterprise') targetSpaceId = userStore.companyId

  const targetPath = buildDirFullPath(dirId === 0 ? null : dirId, spaceTab)

  try {
    await ElMessageBox.confirm(
      buildMoveConfirmMsg(targetPath, fileIds, true),
      '跨空间移动确认',
      { type: 'warning', confirmButtonText: '移动', cancelButtonText: '取消', dangerouslyUseHTMLString: true }
    )
  } catch {
    draggedFileIds.value = []
    return
  }

  try {
    await moveFiles(fileIds, dirId === 0 ? null : dirId, targetSpaceType, targetSpaceId)
    ElMessage.success('跨空间移动成功')
    docStore.clearSelection()
    // 乐观更新：直接从当前目录的列表中移除（避免全列表重拉）
    docStore.removeFiles(fileIds)
    removeFromCachePool(fileIds)
    // 关键修复：清除目标空间目标目录的前端缓存，否则切换到目标空间时仍显示旧列表（无新移动过来的文件）
    docStore.removeDirCache(dirId === 0 ? null : dirId, `${targetSpaceType}:${targetSpaceId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '跨空间移动失败')
    docStore.removeDirCache(currentDirId.value)
    await fetchFiles(currentDirId.value, true, true)
  } finally {
    draggedFileIds.value = []
  }
}

function goPreview(fileId: number) {
  router.push(`/preview/${fileId}`)
}

const thumbnailErrorMap = ref<Record<number, boolean>>({})
const thumbnailBlobUrls = ref<Record<number, string>>({})

function releaseThumbnailUrls() {
  Object.values(thumbnailBlobUrls.value).forEach(url => URL.revokeObjectURL(url))
  thumbnailBlobUrls.value = {}
}

async function loadThumbnailBlobs() {
  releaseThumbnailUrls()
  if (viewMode.value !== 'thumbnail') return
  const thumbFiles = paginatedFiles.value.filter(f =>
    (isImage(f.fileType || '') || isVideo(f.fileType || '')) && !thumbnailErrorMap.value[f.id]
  )
  for (const file of thumbFiles) {
    try {
      const token = sessionStorage.getItem('token')
      let blob: Blob
      if (isVideo(file.fileType || '')) {
        const res = await getFileCover(file.id)
        blob = new Blob([res.data], { type: 'image/jpeg' })
      } else {
        const res = await axios.get(`/api/front/preview/${file.id}`, {
          responseType: 'blob',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
        blob = res.data
      }
      thumbnailBlobUrls.value[file.id] = URL.createObjectURL(blob)
    } catch {
      thumbnailErrorMap.value[file.id] = true
    }
  }
}

function getThumbnailUrl(file: FileDTO): string | null {
  if (thumbnailErrorMap.value[file.id]) return null
  if (!isImage(file.fileType || '') && !isVideo(file.fileType || '')) return null
  return thumbnailBlobUrls.value[file.id] || null
}

function handleThumbnailError(e: Event, file: FileDTO) {
  thumbnailErrorMap.value[file.id] = true
}

async function handleDownload(file: FileDTO) {
  try {
    const res = await downloadFile(file.id)
    const blob = new Blob([res.data])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.fileName
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}

// 收藏：直接 patch store 中的实体，所有引用处同步更新
async function handleFavorite(file: FileDTO) {
  try {
    if ((file as any).isFavorited) {
      await removeFavorite(file.id, 0)
      docStore.updateFileFavorite(file.id, false)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(file.id, 0)
      docStore.updateFileFavorite(file.id, true)
      ElMessage.success('已收藏')
    }
  } catch { /* ignore */ }
}

// 删除文件：store.removeFile 从 normalized store 移除，不替换数组
async function handleDeleteFile(file: FileDTO) {
  await ElMessageBox.confirm(`确定要删除文件 "${file.fileName}" 吗？删除后将移入回收站`, '确认删除', { type: 'warning' })
  try {
    await moveToRecycleBin('doc', file.id)
    ElMessage.success('已移入回收站')
    docStore.removeFile(file.id)
    removeFromCachePool([file.id])
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleDeleteDir(dir: DirectoryDTO) {
  await ElMessageBox.confirm(`确定要删除目录 "${dir.dirName}" 及其所有内容吗？删除后将移入回收站`, '确认删除', { type: 'warning' })
  try {
    await moveToRecycleBin('directory', dir.id)
    ElMessage.success('已移入回收站')
    docStore.removeDirectory(dir.id)
    fetchDirectories()
  } catch {
    ElMessage.error('删除失败')
  }
}

function handleFileCommand(cmd: string, file: FileDTO) {
  if (cmd === 'preview') goPreview(file.id)
  else if (cmd === 'download') handleDownload(file)
  else if (cmd === 'rename') {
    renameFileTarget.value = file
    renameTarget.value = null
    const dotIdx = file.fileName.lastIndexOf('.')
    renameValue.value = dotIdx > 0 ? file.fileName.substring(0, dotIdx) : file.fileName
    showRenameDialog.value = true
  }
  else if (cmd === 'move') {
    fileMoveCopyMode.value = 'move'
    fileMoveCopyTargets.value = [file]
    fileMoveCopyTargetDirId.value = null
    showFileMoveCopyDialog.value = true
  }
  else if (cmd === 'copy') {
    fileMoveCopyMode.value = 'copy'
    fileMoveCopyTargets.value = [file]
    fileMoveCopyTargetDirId.value = null
    showFileMoveCopyDialog.value = true
  }
  else if (cmd === 'transfer') handleOpenTransfer(file)
  else if (cmd === 'delete') handleDeleteFile(file)
}

function openBatchMoveCopy(mode: 'move' | 'copy') {
  fileMoveCopyMode.value = mode
  fileMoveCopyTargets.value = [...docStore.selectedFiles]
  fileMoveCopyTargetDirId.value = null
  showFileMoveCopyDialog.value = true
}

function handleFileMoveCopyTargetClick(data: any) {
  fileMoveCopyTargetDirId.value = data.id === 0 ? null : data.id
}

async function handleTargetSpaceChange(tab: string) {
  fileMoveCopyTargetDirId.value = null
  fileMoveCopyTargetDirs.value = []
  if (tab === 'current') return
  const map: Record<string, number> = { personal: 0, department: 1, enterprise: 2 }
  const st = map[tab]
  let sid: number | null = null
  if (tab === 'personal') sid = userStore.userId
  else if (tab === 'department') sid = userStore.departmentId
  else if (tab === 'enterprise') sid = userStore.companyId
  if (st != null && sid != null) {
    try {
      const res = await getDirectories(undefined, st, sid)
      fileMoveCopyTargetDirs.value = res.data.data || res.data
    } catch {
      fileMoveCopyTargetDirs.value = []
    }
  }
}

async function handleFileMoveCopy() {
  if (fileMoveCopyLoading.value) return
  const ids = fileMoveCopyTargets.value.map(f => f.id)
  const targetDirId = fileMoveCopyTargetDirId.value
  const { spaceType: tSpaceType, spaceId: tSpaceId } = fileMoveCopyTargetSpaceInfo.value
  fileMoveCopyLoading.value = true
  try {
    if (fileMoveCopyMode.value === 'move') {
      await moveFiles(ids, targetDirId, spaceType.value, spaceId.value)
      ElMessage.success(`已移动 ${ids.length} 个文件`)
    } else {
      await copyFiles(ids, targetDirId, tSpaceType, tSpaceId)
      ElMessage.success(`已复制 ${ids.length} 个文件`)
    }
    showFileMoveCopyDialog.value = false
    docStore.clearSelection()
    if (fileMoveCopyMode.value === 'copy' && fileMoveCopyTargetSpace.value !== 'current') {
      const targetSpaceKey = `${tSpaceType}:${tSpaceId}`
      docStore.removeDirCache(targetDirId, targetSpaceKey)
    } else {
      docStore.removeDirCache(targetDirId)
    }
    if (fileMoveCopyMode.value === 'move') {
      // 移动操作：从缓存池移除被移动的文件
      removeFromCachePool(ids)
    }
    fetchFiles(currentDirId.value, true)
  } catch (e: any) {
    const msg = e?.response?.data?.message || (fileMoveCopyMode.value === 'move' ? '移动失败' : '复制失败')
    ElMessage.error(msg)
  } finally {
    fileMoveCopyLoading.value = false
  }
}

function handleSelectionChange(rows: FileDTO[]) {
  docStore.clearSelection()
  rows.forEach(r => docStore.toggleFileSelection(r.id))
}

// 批量删除：store.removeFiles 一次性从 normalized store 移除
async function batchDelete() {
  const sel = docStore.selectedFiles
  await ElMessageBox.confirm(`确定要删除选中的 ${sel.length} 个文件吗？删除后将移入回收站`, '批量删除', { type: 'warning' })
  try {
    const items = sel.map(row => ({ itemType: 'doc' as const, itemId: row.id }))
    await batchMoveToRecycleBin(items)
    ElMessage.success('已批量移入回收站')
    const fileIds = sel.map(f => f.id)
    docStore.removeFiles(fileIds)
    removeFromCachePool(fileIds)
    docStore.clearSelection()
  } catch {
    ElMessage.error('批量删除失败')
  }
}

async function batchDownloadFiles() {
  try {
    const ids = docStore.selectedFiles.map(f => f.id)
    const res = await batchDownload(ids)
    const data = res.data.data || res.data
    const dlRes = await downloadBatchFile(data.fileName)
    const blob = new Blob([dlRes.data as BlobPart])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${data.fileName}.zip`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('批量下载失败')
  }
}

// 上传完成：store.addUploadingFile 插入 uploading 状态实体，fetchFiles patch 更新真实数据
function handleUploadComplete(state: ChunkUploadState) {
  const targetDirId = currentDirId.value
  const ext = state.file.name.split('.').pop()?.toLowerCase() || 'unknown'
  
  // 乐观更新：通过 store 插入 uploading 状态的文件实体
  docStore.addUploadingFile(state.file, targetDirId)
  
  // 上传后新文件通常在第一页（按时间降序），重置到第一页并重新拉取
  resetCachePool()
  fetchFiles(targetDirId, false)

  if (isImage(ext) || ext === 'pdf') {
    ElMessage.info({ message: `${state.file.name} 正在自动进行文字识别，请稍后在文件预览页查看识别结果`, duration: 5000 })
  }
}

function handleUploadDialogClose() {
  showUploadDialog.value = false
  if (chunkUploaderRef.value) {
    chunkUploaderRef.value.reset()
  }
  // 关闭弹窗时静默刷新，patchFiles 增量合并
  fetchFiles(currentDirId.value, false)
}

async function handleCreateDir() {
  if (!newDirForm.value.dirName.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  try {
    const parentId = newDirForm.value.parentId || currentDirId.value
    const res = await createDirectory({
      dirName: newDirForm.value.dirName.trim(),
      parentId: parentId === 0 ? null : parentId,
      spaceType: spaceType.value,
      spaceId: spaceId.value,
    })
    ElMessage.success('文件夹创建成功')
    showNewDirDialog.value = false
    newDirForm.value.dirName = ''
    newDirForm.value.parentId = null
    // 新目录加入 store
    const newDir = res.data.data || res.data
    docStore.addDirectory(newDir)
    if ((parentId || null) === currentDirId.value) {
      fetchFiles(currentDirId.value, true)
    }
  } catch {
    ElMessage.error('创建文件夹失败')
  }
}

function handleDirAction(cmd: string, data: DirectoryDTO) {
  if (cmd === 'rename') {
    renameTarget.value = data
    renameValue.value = data.dirName
    showRenameDialog.value = true
  } else if (cmd === 'move') {
    moveTarget.value = data
    moveDirTarget.value = null
    showMoveDialog.value = true
  } else if (cmd === 'delete') {
    handleDeleteDir(data)
  }
}

async function handleRename() {
  if (!renameValue.value.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    if (renameFileTarget.value) {
      let finalName = renameValue.value.trim()
      if (fileExtension.value) {
        finalName = finalName + '.' + fileExtension.value
      }
      const res = await renameFile(renameFileTarget.value.id, finalName)
      ElMessage.success('重命名成功')
      showRenameDialog.value = false
      fetchFiles(currentDirId.value, false)
      renameFileTarget.value = null
    } else if (renameTarget.value) {
      const res = await renameDirectory(renameTarget.value.id, renameValue.value.trim())
      ElMessage.success('重命名成功')
      showRenameDialog.value = false
      docStore.updateDirectory(renameTarget.value.id, { dirName: renameValue.value.trim() })
      renameTarget.value = null
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || '重命名失败'
    ElMessage.error(msg)
  }
}

function handleMoveTargetClick(data: DirectoryDTO) {
  moveDirTarget.value = data.id
}

async function handleMoveDir() {
  if (!moveTarget.value || moveDirTarget.value === null) {
    ElMessage.warning('请选择目标目录')
    return
  }
  try {
    await moveDirectory(moveTarget.value.id, { newParentId: moveDirTarget.value })
    ElMessage.success('移动成功')
    showMoveDialog.value = false
    docStore.moveDirectory(moveTarget.value.id, moveDirTarget.value)
    fetchDirectories()
  } catch {
    ElMessage.error('移动失败')
  }
}

async function handleOpenTransfer(file: FileDTO) {
  transferTarget.value = file
  transferForm.value = { targetDepartmentId: null, targetDirectoryId: null }
  try {
    const res = await getDepartments()
    allDepartments.value = res.data.data || res.data
  } catch { /* ignore */ }
  showTransferDialog.value = true
}

async function handleTransfer() {
  if (!transferTarget.value) return
  if (!transferForm.value.targetDepartmentId) {
    ElMessage.warning('请选择目标部门')
    return
  }
  const isCrossDept = transferTarget.value.departmentId !== transferForm.value.targetDepartmentId
  if (isCrossDept) {
    try {
      await ElMessageBox.confirm(
        `确定将文件 "${transferTarget.value.fileName}" 转移至目标部门？此操作将更改文件的所属部门。`,
        '跨部门转移确认',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  try {
    await transferFile(transferTarget.value.id, {
      targetDepartmentId: transferForm.value.targetDepartmentId,
      targetDirectoryId: transferForm.value.targetDirectoryId,
    })
    ElMessage.success('文件转移成功')
    showTransferDialog.value = false
    // 从 store 移除已转移的文件
    docStore.removeFile(transferTarget.value.id)
    removeFromCachePool([transferTarget.value.id])
  } catch (e: any) {
    const msg = e?.response?.data?.message || '文件转移失败'
    if (msg.includes('无跨部门转移权限')) {
      ElMessage.error('您没有跨部门转移权限，请联系管理员开通')
    } else if (msg.includes('只能转移本部门')) {
      ElMessage.error('部门管理员只能转移本部门的文件到其他部门')
    } else {
      ElMessage.error(msg)
    }
  }
}

async function fetchRecycleBin() {
  try {
    const res = await getRecycleBin()
    recycleBinItems.value = res.data.data || res.data
  } catch { /* ignore */ }
}

async function handleRestore(item: RecycleBinItem) {
  try {
    await restoreFromRecycleBin(item.itemType, item.itemId)
    ElMessage.success('已恢复')
    fetchRecycleBin()
    fetchFiles(currentDirId.value, true)
  } catch {
    ElMessage.error('恢复失败')
  }
}

async function handlePermanentDelete(item: RecycleBinItem) {
  await ElMessageBox.confirm('彻底删除后将无法恢复，确定继续？', '警告', { type: 'warning' })
  try {
    await permanentDelete(item.itemType, item.itemId)
    ElMessage.success('已彻底删除')
    fetchRecycleBin()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleClearRecycleBin() {
  await ElMessageBox.confirm('清空回收站后所有文件将无法恢复，确定继续？', '警告', { type: 'warning' })
  try {
    await clearRecycleBin()
    ElMessage.success('系统正在后台清空回收站...')
    recycleBinItems.value = []
  } catch {
    ElMessage.error('清空请求失败')
  }
}

// 搜索已改为 computed 属性自动过滤，无需 handleSearch 函数

function formatDate(timeStr: string) {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

watch(showRecycleBin, (val) => {
  if (val) fetchRecycleBin()
})

// 保存当前的滚动高度
function saveScrollPosition() {
  let top = 0
  if (viewMode.value === 'table') {
    // 兼容可能发生变化的 Element Plus 滚动条包装器
    const scrollEl = tableRef.value?.$el?.querySelector('.el-scrollbar__wrap') || tableRef.value?.$el?.querySelector('.el-table__body-wrapper')
    top = scrollEl ? scrollEl.scrollTop : 0
  } else if (viewMode.value === 'thumbnail') {
    top = thumbnailRef.value ? thumbnailRef.value.scrollTop : 0
  }
  savedScrollTop.value = top
  // 用 sessionStorage 备份，即使组件没有使用 keep-alive，重新挂载也能成功还原位置
  sessionStorage.setItem(userKey('doc_scroll_top'), String(top))
  sessionStorage.setItem(userKey('doc_scroll_mode'), viewMode.value)
  // 同步持久化当前视图模式，回到页面时按相同视图还原
  sessionStorage.setItem(userKey('doc_view_mode'), viewMode.value)
}

// 还原滚动高度
function restoreScrollPosition() {
  const storedTop = sessionStorage.getItem(userKey('doc_scroll_top'))
  const storedMode = sessionStorage.getItem(userKey('doc_scroll_mode'))

  if (!storedTop || storedMode !== viewMode.value) return

  const topVal = parseInt(storedTop, 10)
  savedScrollTop.value = topVal

  // 多次重试：表格/网格在异步加载完成前 scrollHeight 可能还未撑开
  // 失败不清理 sessionStorage，保留到下次重试；只有真正成功后才清理
  let attempts = 0
  const maxAttempts = 6
  const tryRestore = () => {
    attempts++
    let success = false
    if (viewMode.value === 'table' && tableRef.value) {
      tableRef.value.doLayout() // 重新布局表格，防止尺寸未计算完毕导致还原失败
      tableRef.value.setScrollTop(topVal)
      // 校验：再读一次当前 scrollTop 是否已就位
      const wrap = tableRef.value?.$el?.querySelector('.el-scrollbar__wrap')
      if (wrap && wrap.scrollTop === topVal) {
        success = true
      }
    } else if (viewMode.value === 'thumbnail' && thumbnailRef.value) {
      thumbnailRef.value.scrollTop = topVal
      if (thumbnailRef.value.scrollTop === topVal) success = true
    }

    if (success) {
      sessionStorage.removeItem(userKey('doc_scroll_top'))
      sessionStorage.removeItem(userKey('doc_scroll_mode'))
    } else if (attempts < maxAttempts) {
      setTimeout(tryRestore, 120)
    }
    // 重试耗尽：保留 sessionStorage 留给下次 onActivated / 下次进入
  }

  nextTick(() => {
    setTimeout(tryRestore, 60)
  })
}

// 清空滚动记录（用于切换目录、切换空间、切换视图等场景）
function clearStoredScroll() {
  savedScrollTop.value = 0
  sessionStorage.removeItem(userKey('doc_scroll_top'))
  sessionStorage.removeItem(userKey('doc_scroll_mode'))
}

// 监听视图模式切换，切换时重置滚动历史（同时持久化当前视图模式）
watch(viewMode, (mode) => {
  clearStoredScroll()
  sessionStorage.setItem(userKey('doc_view_mode'), mode)
  if (mode === 'thumbnail') {
    loadThumbnailBlobs()
  } else {
    releaseThumbnailUrls()
  }
})

// 缩略图视图下，翻页或文件列表变化时重新加载缩略图
watch(paginatedFiles, () => {
  if (viewMode.value === 'thumbnail') {
    loadThumbnailBlobs()
  }
})

onUnmounted(() => {
  releaseThumbnailUrls()
})

// 针对 keep-alive 缓存停用时的监听
// 【修复说明】此处不要再次调用 saveScrollPosition()
// 原因：路由离开时，onBeforeRouteLeave 已经捕获并保存了精确的 scrollTop。
// 随后 keep-alive 挂起组件时会触发 onDeactivated，此时 DOM 已经被隐藏或尺寸归零，
// 在此处调用 saveScrollPosition() 会读取到 0，并错误地覆盖掉前面已经保存的正确高度。
onDeactivated(() => {
  // 故意留空，避免 0 高度覆盖 bug
})

// 实时同步 currentSpaceTab 到 store，确保导航离开时状态不丢失
watch(currentSpaceTab, () => {
  docStore.setSpaceKey(spaceKey.value)
})

// 针对普通路由切换（非 keep-alive）时的监听
onBeforeRouteLeave((to, from, next) => {
  saveScrollPosition()
  docStore.setSpaceKey(spaceKey.value)
  next()
})

onMounted(async () => {
  if (docStore.currentSpaceKey) {
    const parts = docStore.currentSpaceKey.split(':')
    const st = parseInt(parts[0])
    if (st === 1) currentSpaceTab.value = 'department'
    else if (st === 2) currentSpaceTab.value = 'enterprise'
    else currentSpaceTab.value = 'personal'
  }
  docStore.setSpaceKey(spaceKey.value)
  loadExpandedDirs() // 恢复当前空间的目录展开状态
  // 1. 先加载目录结构到 store
  await fetchDirectories()
  
  // 2. 获取当前被持久化的目录 ID（确保刷新后依然留在原目录）
  const dirId = currentDirId.value
  
  // 3. 重新构建面包屑路径，防止刷新页面后面包屑丢失
  buildBreadcrumb(dirId)
  
  // 4. 读取缓存并进行静默更新
  const cached = docStore.getCachedFiles(dirId)
  if (cached) {
    loading.value = false
    // 立即从缓存恢复 totalElements，使分页按钮即时显示（不必等 API 返回）
    const cachedTotal = docStore.getCachedTotalElements(dirId)
    if (cachedTotal != null) totalElements.value = cachedTotal
    // 只有在缓存真正过期时，才在后台静默请求，避免频繁无意义地调用慢接口
    if (docStore.isCacheStale(dirId)) {
      fetchFiles(dirId, false)
    } else {
      // 缓存未过期，用 store 缓存数据临时填充缓存池（秒出效果）
      cachePoolFiles.value = cached
      cachePoolStartIndex.value = 0
    }
    restoreScrollPosition() // 首屏挂载且使用缓存时还原滚动位置
  } else {
    loading.value = true
    fetchFiles(dirId, true)
  }
})

// keep-alive 缓存后，组件被激活时：
// 1. 恢复离开时的滚动位置
// 2. 若缓存过期则后台静默同步（stale-while-revalidate），保持数据新鲜
onActivated(() => {
  restoreScrollPosition()
  const dirId = currentDirId.value
  // 若缓存池为空但 store 有缓存，临时填充缓存池（秒出效果）
  if (cachePoolFiles.value.length === 0) {
    const cached = docStore.getCachedFiles(dirId)
    if (cached) {
      cachePoolFiles.value = cached
      cachePoolStartIndex.value = 0
      const cachedTotal = docStore.getCachedTotalElements(dirId)
      if (cachedTotal != null) totalElements.value = cachedTotal
    }
  }
  if (docStore.isCacheStale(dirId)) {
    fetchFiles(dirId, false)
  }
})
</script>

<style scoped>
/* ==================== 1. 全局悬浮岛布局 ==================== */
.document-space {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background-color: #f7f8fa; /* 统一全局灰蓝底色 */
  padding: 16px;
  box-sizing: border-box;
  gap: 16px; /* 卡片之间的呼吸间距 */
}

/* 顶部操作岛 */
.doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.03);
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.doc-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-divider {
  width: 1px;
  height: 20px;
  background-color: var(--el-border-color);
  margin: 0 8px;
}

/* ==================== 2. 深度定制：苹果风分段控制器 ==================== */
/* 让空间切换按钮（个人/部门/企业）变成类似 iOS 系统的丝滑切换器 */
.doc-header-left :deep(.el-radio-group) {
  background: #f0f2f5;
  padding: 4px;
  border-radius: 4px;
  gap: 4px;
}
.doc-header-left :deep(.el-radio-button__inner) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  border-radius: 4px !important;
  color: #606266;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  padding: 6px 16px;
}
.doc-header-left :deep(.el-radio-button__inner:hover) {
  color: #1d1e23;
}
.doc-header-left :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: #ffffff !important;
  color: #1d1e23;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08) !important;
  font-weight: 600;
}

/* ==================== 3. 深度定制：全局按钮与输入框 ==================== */
.document-space :deep(.el-button) {
  border-radius: 4px;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  font-weight: 500;
}
.document-space :deep(.el-button:active) {
  transform: scale(0.96);
}
.document-space :deep(.el-input__wrapper) {
  border-radius: 4px;
  box-shadow: 0 0 0 1px #e4e7ed inset;
}
.document-space :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset, 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.doc-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.doc-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.doc-search {
  width: 240px;
}

/* 主体区域 */
.doc-body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}

/* 左侧目录岛 */
.dir-sidebar {
  width: 260px; /* 稍微加宽，避免目录过长截断 */
  background: #ffffff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.03);
  flex-shrink: 0;
  overflow: hidden;
}

.dir-sidebar-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dir-sidebar-tree {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.dir-sidebar-tree::-webkit-scrollbar {
  width: 4px;
}

.dir-sidebar-tree::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 2px;
}

.dir-sidebar-tree::-webkit-scrollbar-track {
  background: transparent;
}

.dir-root-item .el-icon {
  color: var(--el-color-warning);
}

.dir-root-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-regular);
  transition: all 0.2s;
}

.dir-root-item:hover {
  background: var(--el-fill-color-light);
}

.dir-root-item:hover .el-icon {
  color: var(--el-color-warning);
}

.dir-root-item.active {
  background-color: var(--el-fill-color);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.dir-root-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 15%;
  height: 70%;
  width: 3px;
  background-color: var(--el-color-primary);
  border-radius: 0 3px 3px 0;
}

.dir-root-item.active span {
  color: var(--el-text-color-primary) !important;
  font-weight: 600;
}

.dir-root-item.active .el-icon {
  color: var(--el-color-primary) !important;
}

.dir-sidebar :deep(.el-tree) {
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding-right: 4px;
}

.tree-folder-icon {
  color: var(--el-color-warning) !important;
  flex-shrink: 0;
}

.tree-node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  color: var(--el-text-color-regular);
}

/* 当前选中的目录：通过响应式 .active 精确控制，避免 is-current 误高亮 */
.tree-node.active {
  background-color: var(--el-fill-color);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.tree-node.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 15%;
  height: 70%;
  width: 3px;
  background-color: var(--el-color-primary);
  border-radius: 0 3px 3px 0;
}

.tree-node.active .tree-node-label {
  color: var(--el-text-color-primary) !important;
  font-weight: 600;
}

.tree-node.active .tree-folder-icon {
  color: var(--el-color-primary) !important;
}

.tree-node-more {
  display: none;
  cursor: pointer;
  color: var(--el-text-color-secondary);
}

.tree-node:hover .tree-node-more {
  display: inline-flex;
}

.dir-sidebar-footer {
  padding: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  text-align: center;
  flex-shrink: 0;
}

/* 文件拖拽高亮 */
.dir-root-item.drag-over,
.tree-node.drag-over {
  background: var(--el-color-primary-light-8) !important;
  outline: 2px dashed var(--el-color-primary);
  outline-offset: -2px;
  border-radius: 4px;
}

.tree-node.drag-over .tree-node-label {
  color: var(--el-color-primary) !important;
  font-weight: 600;
}

.file-name-cell.is-dragging,
.thumbnail-item.is-dragging {
  opacity: 0.45;
}

/* 跨空间拖放区域 */
.cross-space-drop-zones {
  padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  animation: crossSpaceFadeIn 0.2s ease;
}

@keyframes crossSpaceFadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.cross-space-drop-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
  padding-left: 2px;
}

.cross-space-section {
  margin-bottom: 6px;
}

.cross-space-section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  border: 1.5px dashed var(--el-border-color);
  transition: all 0.2s;
  background: var(--el-fill-color-lighter);
}

.cross-space-section-header:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-dark);
}

.cross-space-section-header.drag-over {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  border-style: solid;
  color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}

.cross-space-tree-wrapper {
  max-height: 180px;
  overflow-y: auto;
  margin: 2px 0 4px 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.cross-space-tree-wrapper::-webkit-scrollbar {
  width: 4px;
}

.cross-space-tree-wrapper::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 2px;
}

.cross-space-tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 12px;
}

.cross-space-tree-node.drag-over {
  background: var(--el-color-primary-light-8) !important;
  outline: 2px dashed var(--el-color-primary);
  outline-offset: -2px;
  border-radius: 3px;
}

.cross-space-tree-node.drag-over .tree-node-label {
  color: var(--el-color-primary) !important;
  font-weight: 600;
}

/* 右侧文件岛 */
.file-main {
  flex: 1;
  background: #ffffff;
  border-radius: 8px;
  padding: 20px 24px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* 内容区工具栏 */
.file-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.file-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-breadcrumb {
  font-size: 13px;
}

.back-btn {
  padding: 4px;
  margin-right: 4px;
  margin-top: -2px; /* 微调视觉重心垂直对齐 */
}

/* 文件列表区域 */
.file-table {
  flex: 1;
  min-height: 0;
}

/* ==================== 4. 表格去线框化设计 ==================== */
/* 隐藏繁杂的表格边框，增加呼吸感 */
.file-table :deep(.el-table) {
  border-radius: 4px;
}
.file-table :deep(.el-table th.el-table__cell) {
  background-color: #fafbfc;
  color: #8a8f99;
  font-weight: 500;
  border-bottom: none;
}
.file-table :deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid #f5f6f7;
  transition: background-color 0.2s;
}
.file-table :deep(.el-table__row:hover td) {
  background-color: #f4f5f9 !important; /* 悬浮时变成淡淡的灰蓝 */
}
.file-table :deep(.el-table::before) {
  display: none; /* 去除表格底部自带的白线 */
}

/* 目录树悬浮优化 */
.dir-sidebar-tree :deep(.el-tree-node__content) {
  border-radius: 4px;
  margin: 2px 8px;
  height: 34px;
  transition: all 0.2s;
}
.dir-sidebar-tree :deep(.el-tree-node__content:hover) {
  background-color: #f4f5f9;
}

.loading-state,
.empty-state {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.file-name-cell:hover .file-name-text {
  color: var(--el-color-primary);
}

.file-name-cell:hover .file-icon-cell {
  transform: scale(1.05);
}

.file-icon-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  flex-shrink: 0;
  background-color: var(--el-fill-color-light);
  transition: all 0.2s ease;
}

.file-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

/* ==================== 缩略图视图 ==================== */
.file-thumbnail-grid {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
  align-content: start;
  padding: 4px;
}

.thumbnail-item {
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-card, 6px);
  cursor: pointer;
  transition: all var(--duration-normal, 300ms) var(--ease-out, cubic-bezier(0.2,0.8,0.2,1));
  border: 1px solid transparent;
  overflow: hidden;
  background: var(--color-surface, var(--el-fill-color-light));
}

.thumbnail-item:hover {
  border-color: var(--color-border, var(--el-border-color));
  transform: translateY(-4px);
  box-shadow: var(--shadow-m, 0 4px 12px rgba(0,0,0,0.08));
}

.thumbnail-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  overflow: hidden;
}

.thumbnail-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.thumbnail-item:hover .thumbnail-preview img {
  transform: scale(1.05);
}

.thumbnail-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--el-text-color-secondary);
}

.thumbnail-item.no-thumb .thumbnail-preview {
  background: var(--el-fill-color-light);
}

.thumbnail-favorite {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.thumbnail-video-play {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  pointer-events: none;
}

.thumbnail-name {
  font-size: 13px;
  padding: 10px 12px 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
}

.thumbnail-meta {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  padding: 0 12px 12px;
}

.recycle-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 12px;
}

.recycle-item:last-child {
  border-bottom: none;
}

.recycle-info {
  flex: 1;
  min-width: 0; /* 允许flex子元素缩小到比内容更小 */
  overflow: hidden;
}

.recycle-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-all;
}

.recycle-meta {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recycle-actions {
  flex-shrink: 0; /* 防止按钮被挤压 */
  display: flex;
  align-items: center;
  gap: 8px;
}

.recycle-actions .el-button {
  font-size: 13px;
}

.recycle-actions .el-icon {
  margin-right: 2px;
}

.recycle-footer {
  margin-top: 20px;
  text-align: center;
}

.recycle-footer .el-icon {
  margin-right: 4px;
}

.empty-state {
  padding: 60px 0;
  text-align: center;
}

.loading-state {
  padding: 20px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 16px 0;
  flex-shrink: 0;
}

.data-view-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 换页轻量加载覆盖层：半透明遮罩 + 居中旋转图标 */
.page-changing-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  pointer-events: none;
  color: #409eff;
}

.skeleton-wrapper {
  padding: 16px 0;
}

.skeleton-table-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.skeleton-thumbnail-item {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  overflow: hidden;
}

@media (max-width: 1180px) {
  .document-space {
    padding: 0 16px;
  }
  .dir-sidebar {
    width: 200px;
  }
  .doc-search {
    width: 200px;
  }
}

@media (max-width: 992px) {
  .doc-header {
    padding: 12px 0;
  }
  .doc-title {
    font-size: 18px;
  }
  .doc-body {
    flex-direction: column;
    gap: 12px;
  }
  .dir-sidebar {
    width: 100%;
    max-height: 220px;
  }
  .doc-search {
    width: 180px;
  }
}

@media (max-width: 768px) {
  .document-space {
    padding: 0 12px;
  }
  .doc-header {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .doc-header-left,
  .doc-header-right {
    width: 100%;
    justify-content: flex-start;
  }
  .doc-header-right {
    justify-content: flex-end;
  }
  .doc-search {
    width: 100%;
  }
  .file-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .file-toolbar-left,
  .file-toolbar-right {
    width: 100%;
    flex-wrap: wrap;
  }
  .pagination-wrapper {
    flex-direction: column;
    gap: 8px;
  }
}

@media (max-width: 480px) {
  .doc-title {
    font-size: 16px;
  }
  .doc-header-left {
    flex-wrap: wrap;
  }
  .header-divider {
    display: none;
  }
}

/* ==================== 5. 视图切换器（悬浮胶囊风格） ==================== */
.view-mode-control.el-radio-group {
  background: #f0f2f5;
  padding: 4px;
  border-radius: 4px;
  gap: 2px;
  border: 1px solid rgba(0, 0, 0, 0.03);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.02);
}
.view-mode-control :deep(.el-radio-button__inner) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  border-radius: 4px !important;
  color: #909399;
  padding: 6px 12px;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.view-mode-control :deep(.el-radio-button__inner:hover) {
  color: #1d1e23;
}
.view-mode-control :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: #ffffff !important;
  color: #1d1e23;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08), 0 0 1px rgba(0,0,0,0.05) !important;
}
</style>
