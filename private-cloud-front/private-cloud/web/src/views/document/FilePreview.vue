<template>
  <div class="file-preview" :class="{ 'embed-mode': isEmbedMode }">
    <div v-if="!isEmbedMode" class="preview-header">
      <div class="preview-header-left">
        <el-button text @click="handleGoBack">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <span class="preview-filename">{{ fileData?.fileName || '文件预览' }}</span>
        <el-tag v-if="fileData?.fileType" size="small" :color="getFileTypeColor(fileData.fileType)" effect="dark" style="border:none;">{{ fileData.fileType.toUpperCase() }}</el-tag>
        <el-tag v-if="fileData?.version" size="small" type="success" effect="dark" class="version-tag">v{{ fileData.version }}</el-tag>
      </div>
      <div class="preview-header-right">
        <el-button v-if="canGenerateTemplate" type="primary" @click="handleGenerateFromTemplate">
          <el-icon><MagicStick /></el-icon> 使用此模板生成
        </el-button>
        <el-button v-if="canShowMindmap" type="primary" plain @click="toggleMindmapSidebar">
          <el-icon><Coordinate /></el-icon> 脑图
        </el-button>
        <el-button @click="handleFavorite">
          <el-icon><StarFilled v-if="isFavorited" /><Star v-else /></el-icon>
          {{ isFavorited ? '已收藏' : '收藏' }}
        </el-button>
        <el-button @click="handleShare"><el-icon><Share /></el-icon> 分享</el-button>
        <el-dropdown trigger="click">
          <el-button>更多 <el-icon><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleDownload"><el-icon><Download /></el-icon> 下载</el-dropdown-item>
              <el-dropdown-item @click="triggerTagExtractionAction"><el-icon><CollectionTag /></el-icon> AI标签提取</el-dropdown-item>
              <el-dropdown-item @click="showVersionUpload = true"><el-icon><Upload /></el-icon> 上传新版本</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="preview-body">
      <div ref="previewMainRef" class="preview-main" @mouseup="handleTextSelection">
        <div v-if="showAnnotationBtn" class="annotation-float-btn" :style="annotationBtnStyle" @mousedown.prevent @click="handleAnnotateSelection">
          <el-icon><ChatLineSquare /></el-icon> 批注
        </div>
        <div v-if="!fileData && loading" class="preview-skeleton">
          <el-skeleton :rows="10" animated />
        </div>

        <div v-if="!fileData && !loading" class="preview-empty">
          <el-empty description="文件不存在或无权访问" />
        </div>

        <div ref="previewContentRef" class="preview-main-content">
          <template v-if="fileData">
          <!-- PDF/Word/PPT 使用 v-show 保持 canvas DOM，避免 pdf.js 上下文丢失 -->
          <div v-show="fileData.fileType === 'pdf'" class="preview-pdf">
            <div class="pdf-viewer-container">
              <div class="pdf-toolbar">
                <el-button-group>
                  <el-button size="small" circle :disabled="currentPage <= 1" @click="currentPage--"><el-icon><ArrowLeft /></el-icon></el-button>
                  <el-popover placement="bottom" :width="180" trigger="click">
                    <template #reference>
                      <el-button size="small">{{ currentPage }} / {{ pdfTotalPages }}</el-button>
                    </template>
                    <div class="page-jump-popover">
                      <span>跳转到</span>
                      <el-input-number v-model="jumpPageNum" :min="1" :max="Math.max(1, pdfTotalPages)" size="small" controls-position="right" style="width:100px;" />
                      <el-button size="small" type="primary" @click="handleJumpPage">确定</el-button>
                    </div>
                  </el-popover>
                  <el-button size="small" circle :disabled="currentPage >= pdfTotalPages" @click="currentPage++"><el-icon><ArrowRight /></el-icon></el-button>
                </el-button-group>
              </div>
              <div class="pdf-canvas-container">
                <div class="pdf-page-wrapper">
                  <canvas ref="pdfCanvasRef"></canvas>
                  <div ref="pdfTextLayerRef" class="textLayer"></div>
                </div>
              </div>
            </div>
            <div v-if="pdfLoading" class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在加载PDF...</span>
            </div>
          </div>

          <div v-show="fileData.fileType === 'docx' || fileData.fileType === 'doc'" class="preview-word">
            <div v-if="wordConvertStatus === 'COMPLETED'" class="pdf-viewer-container">
              <div class="pdf-toolbar">
                <el-button-group>
                  <el-button size="small" circle :disabled="currentWordPage <= 1" @click="currentWordPage--"><el-icon><ArrowLeft /></el-icon></el-button>
                  <el-popover placement="bottom" :width="180" trigger="click">
                    <template #reference>
                      <el-button size="small">{{ currentWordPage }} / {{ wordTotalPages }}</el-button>
                    </template>
                    <div class="page-jump-popover">
                      <span>跳转到</span>
                      <el-input-number v-model="jumpWordPageNum" :min="1" :max="Math.max(1, wordTotalPages)" size="small" controls-position="right" style="width:100px;" />
                      <el-button size="small" type="primary" @click="handleJumpWordPage">确定</el-button>
                    </div>
                  </el-popover>
                  <el-button size="small" circle :disabled="currentWordPage >= wordTotalPages" @click="currentWordPage++"><el-icon><ArrowRight /></el-icon></el-button>
                </el-button-group>
              </div>
              <div class="pdf-canvas-container">
                <div class="pdf-page-wrapper">
                  <canvas ref="wordCanvasRef"></canvas>
                  <div ref="wordTextLayerRef" class="textLayer"></div>
                </div>
              </div>
            </div>
            <div v-else-if="wordConvertStatus === 'PROCESSING'" class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>文档转换中，请稍候... (首次转换可能需要1-2分钟)</span>
            </div>
            <div v-else-if="wordConvertStatus === 'FAILED'" class="preview-loading">
              <span>文档预览失败：{{ wordConvertMessage || '转换失败' }}，请确认LibreOffice已安装或联系管理员</span>
              <el-button type="primary" size="small" style="margin-top: 8px;" @click="retryWordConvert">重新转换</el-button>
            </div>
            <div v-else class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在准备文档环境...</span>
            </div>
          </div>

          <div v-show="fileData.fileType === 'pptx' || fileData.fileType === 'ppt'" class="preview-pptx">
            <div v-if="convertStatus === 'COMPLETED'" class="pdf-viewer-container">
              <div class="pdf-toolbar">
                <el-button-group>
                  <el-button size="small" circle :disabled="currentPptPage <= 1" @click="currentPptPage--"><el-icon><ArrowLeft /></el-icon></el-button>
                  <el-popover placement="bottom" :width="180" trigger="click">
                    <template #reference>
                      <el-button size="small">{{ currentPptPage }} / {{ pptTotalPages }}</el-button>
                    </template>
                    <div class="page-jump-popover">
                      <span>跳转到</span>
                      <el-input-number v-model="jumpPptPageNum" :min="1" :max="Math.max(1, pptTotalPages)" size="small" controls-position="right" style="width:100px;" />
                      <el-button size="small" type="primary" @click="handleJumpPptPage">确定</el-button>
                    </div>
                  </el-popover>
                  <el-button size="small" circle :disabled="currentPptPage >= pptTotalPages" @click="currentPptPage++"><el-icon><ArrowRight /></el-icon></el-button>
                </el-button-group>
              </div>
              <div class="pdf-canvas-container">
                <div class="pdf-page-wrapper">
                  <canvas ref="pptCanvasRef"></canvas>
                  <div ref="pptTextLayerRef" class="textLayer"></div>
                </div>
              </div>
            </div>
            <div v-else-if="convertStatus === 'PROCESSING'" class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>文档转换中，请稍候... (首次转换可能需要1-2分钟)</span>
            </div>
            <div v-else-if="convertStatus === 'FAILED'" class="preview-loading">
              <span>PPT预览加载失败：{{ convertMessage || '转换失败' }}，请确认LibreOffice已安装或联系管理员</span>
              <el-button type="primary" size="small" style="margin-top: 8px;" @click="retryPptConvert">重新转换</el-button>
            </div>
            <div v-else class="preview-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在准备文档环境...</span>
            </div>
          </div>

          <!-- 其他文件类型使用 v-if/v-else-if 链 -->
          <template v-if="!['pdf','docx','doc','pptx','ppt'].includes(fileData.fileType)">
            <div v-if="isAudioVideo(fileData.fileType)" class="preview-media">
              <video v-if="fileData.fileType === 'mp4' || fileData.fileType === 'webm' || fileData.fileType === 'avi' || fileData.fileType === 'mov'"
                ref="mediaRef"
                :src="mediaBlobUrl" controls class="preview-video"
                @timeupdate="handleMediaTimeUpdate"
                @loadedmetadata="handleMediaLoaded"
                @pause="handleMediaPause"
                @ended="handleMediaPause" />
              <audio v-else ref="mediaRef" :src="mediaBlobUrl" controls class="preview-audio"
                @timeupdate="handleMediaTimeUpdate"
                @loadedmetadata="handleMediaLoaded"
                @pause="handleMediaPause"
                @ended="handleMediaPause" />
            </div>

            <div v-else-if="isImage(fileData.fileType)" class="preview-image">
              <img :src="imageUrl" :alt="fileData.fileName" class="preview-img" />
            </div>

            <div v-else-if="fileData.fileType === 'xlsx' || fileData.fileType === 'xls'" class="preview-excel">
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

            <div v-else-if="isTextFile(fileData.fileType)" class="preview-text">
              <div v-if="fileData.fileType === 'md'" class="markdown-body" v-html="renderedMarkdown"></div>
              <div v-else-if="fileData.fileType === 'csv'" class="markdown-body csv-preview" v-html="renderedMarkdown"></div>
              <pre v-else class="text-content">{{ textContent }}</pre>
            </div>

            <div v-else class="preview-unsupported">
              <el-empty description="该文件类型暂不支持在线预览">
                <el-button type="primary" @click="handleDownload">下载文件</el-button>
              </el-empty>
            </div>
          </template>
          </template>
        </div>

        <div v-if="!isEmbedMode" class="sidebar-toggle-zone" :class="{ 'is-collapsed': !showRightSidebar }">
          <button
            class="sidebar-toggle-fab"
            :class="{ 'is-collapsed': !showRightSidebar }"
            :aria-label="showRightSidebar ? '收起信息栏' : '展开信息栏'"
            @click="showRightSidebar = !showRightSidebar"
          >
            <span class="toggle-arrow" aria-hidden="true">
              <svg viewBox="0 0 8 12" width="6" height="10">
                <path
                  d="M1.5 1L6.5 6L1.5 11"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span class="toggle-tip">{{ showRightSidebar ? '收起' : '展开' }}</span>
          </button>
        </div>
      </div>

      <div v-show="showRightSidebar && !isEmbedMode" class="preview-sidebar" :class="{ 'sidebar-expanded': isMindmapExpanded }">
        <!-- 脑图侧边栏模块（三状态：未生成/生成中/已生成） -->
        <div v-show="mindmapSidebarVisible" class="mindmap-section" :class="{ 'mindmap-expanded': isMindmapExpanded }">
          <div class="mindmap-toolbar">
            <div class="mindmap-title">
              <el-icon><Connection /></el-icon> 文档脑图
            </div>
            <div class="mindmap-actions">
              <el-button size="small" plain :title="isMindmapExpanded ? '退出宽屏' : '宽屏模式'" @click="isMindmapExpanded = !isMindmapExpanded">
                <el-icon><FullScreen v-if="!isMindmapExpanded" /><Aim v-else /></el-icon>
              </el-button>
              <el-select v-model="mindmapModel" size="small" placeholder="选择模型" style="width: 130px">
                <el-option v-for="m in mindmapModelOptions" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
              <el-button
                v-if="mindmapContent"
                size="small"
                type="primary"
                :loading="mindmapLoading"
                @click="handleRegenerateMindmap"
              >
                <el-icon><RefreshRight /></el-icon> 重新生成
              </el-button>
            </div>
          </div>

          <!-- 状态 B：生成中 -->
          <div v-if="mindmapLoading" class="mindmap-loading">
            <el-icon class="is-loading" :size="28"><Loading /></el-icon>
            <p>{{ mindmapLoadingText }}</p>
            <p class="mindmap-loading-hint">大文档可能需要 1-5 分钟，AI 正在分析中，请勿关闭页面</p>
          </div>

          <!-- 状态 D：生成失败 -->
          <div v-else-if="mindmapError" class="mindmap-empty">
            <el-empty :description="mindmapError" :image-size="60" />
            <el-button type="primary" @click="requestGenerateMindmap">
              <el-icon><RefreshRight /></el-icon> 重新生成
            </el-button>
          </div>

          <!-- 状态 A：未生成 -->
          <div v-else-if="!mindmapContent" class="mindmap-empty">
            <el-empty description="尚未生成脑图" :image-size="60" />
            <el-button type="primary" @click="requestGenerateMindmap">
              <el-icon><Cpu /></el-icon> AI 智能生成脑图
            </el-button>
          </div>

          <!-- 状态 C：已生成 -->
          <div v-else ref="mindmapContainerRef" class="mindmap-canvas"></div>
        </div>

        <!-- 脑图多页码选择浮窗 -->
        <div v-if="showPageSelect" class="mindmap-page-popover" :style="pageSelectPos" @click.stop>
          <div class="popover-title">请选择跳转页码</div>
          <div class="popover-buttons">
            <el-button
              v-for="p in availablePages" :key="p"
              size="small" class="page-jump-btn"
              @click="() => { jumpToMindmapPage(p); showPageSelect = false }"
            >
              第 {{ p }} 页
            </el-button>
          </div>
        </div>

        <el-tabs v-model="sidebarTab" class="sidebar-tabs">
          <el-tab-pane label="信息" name="info">
            <div class="info-section">
              <div class="info-row"><span class="info-label">文件名</span><span class="info-value">{{ fileData?.fileName }}</span></div>
              <div class="info-row"><span class="info-label">类型</span><span class="info-value">{{ fileData?.fileType?.toUpperCase() }}</span></div>
              <div class="info-row"><span class="info-label">大小</span><span class="info-value">{{ formatFileSize(fileData?.fileSize || 0) }}</span></div>
              <div class="info-row"><span class="info-label">上传者</span><span class="info-value">{{ fileData?.uploaderName || '-' }}</span></div>
              <div class="info-row"><span class="info-label">版本</span><span class="info-value version-badge">v{{ fileData?.version || 1 }}</span></div>
              <div class="info-row"><span class="info-label">浏览次数</span><span class="info-value">{{ fileData?.viewCount || 0 }}</span></div>
              <div class="info-row"><span class="info-label">下载次数</span><span class="info-value">{{ fileData?.downloadCount || 0 }}</span></div>
              <div class="info-row"><span class="info-label">上传时间</span><span class="info-value">{{ formatDate(fileData?.createTime) }}</span></div>
              <div class="info-row"><span class="info-label">更新时间</span><span class="info-value">{{ formatDate(fileData?.updateTime) }}</span></div>
            </div>

            <div class="tag-section">
              <div class="section-title"><el-icon :size="16"><CollectionTag /></el-icon> 标签</div>
              <div v-if="docTags.length === 0" class="tag-empty-hint">暂无标签</div>
              <div v-else class="tag-cloud-container">
                <div
                  v-for="(tag, index) in docTags"
                  :key="tag.id"
                  class="tag-chip"
                  :class="{ 'tag-chip-ai': tag.tagSource === 'AI' }"
                  :style="getTagChipStyle(index)"
                  :title="tag.tagName"
                >
                  <span class="tag-chip-text" @click="handleFileTagClick(tag.tagName)">{{ tag.tagName }}</span>
                  <el-icon class="tag-chip-edit" @click="handleEditTag(tag)" title="编辑"><Edit /></el-icon>
                  <el-icon class="tag-chip-delete" @click="handleDeleteTagConfirm(tag)" title="删除"><Close /></el-icon>
                </div>
              </div>
              <div class="tag-actions">
                <el-button size="small" @click="showAddTag = true">+ 添加</el-button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="识别文本" name="ocr">
            <div class="ocr-section">
              <div v-if="!canShowOcr" class="ocr-empty">
                <el-empty :description="ocrPendingOffice ? '文档转换中，转换完成后即可进行文字识别' : '当前文件类型不支持文字识别'" :image-size="60" />
              </div>
              <template v-else>
                <div v-if="ocrLoading" class="ocr-loading">
                  <el-skeleton :rows="5" animated />
                </div>
                <div v-else-if="ocrStatus === 'NOT_STARTED'" class="ocr-empty">
                  <el-empty description="尚未进行文字识别" :image-size="40">
                    <el-button type="primary" size="small" @click="triggerOcrExtraction">开始识别当前页</el-button>
                  </el-empty>
                </div>
                <div v-else-if="ocrStatus === 'PROCESSING' || ocrStatus === 'PENDING'" class="ocr-processing">
                  <el-icon :size="24" class="ocr-spin"><Loading /></el-icon>
                  <span>文字识别中，请稍候...</span>
                </div>
                <div v-else-if="ocrStatus === 'COMPLETED' && ocrText" class="ocr-result">
                  <div class="ocr-toolbar">
                    <span v-if="ocrCurrentPage" class="ocr-page-info">第 {{ ocrCurrentPage }} 页</span>
                    <el-button size="small" @click="handleCopyOcrText"><el-icon><CopyDocument /></el-icon> 复制文本</el-button>
                    <el-button size="small" @click="triggerOcrExtraction"><el-icon><Refresh /></el-icon> 重新识别</el-button>
                  </div>
                  <div class="ocr-text-content">{{ ocrText }}</div>
                </div>
                <div v-else-if="ocrStatus === 'FAILED'" class="ocr-failed">
                  <el-empty description="文字识别失败" :image-size="40">
                    <el-button type="primary" size="small" @click="triggerOcrExtraction">重新识别</el-button>
                  </el-empty>
                </div>
                <div v-else class="ocr-empty">
                  <el-empty description="暂无文字识别结果" :image-size="40" />
                </div>
              </template>
            </div>
          </el-tab-pane>

          <el-tab-pane label="评论" name="comments">
            <div class="comment-section">
              <div v-if="selectedQuoteText" class="quote-preview-bar">
                <div class="quote-preview-label">引用文本：</div>
                <div class="quote-preview-text">"{{ selectedQuoteText.length > 80 ? selectedQuoteText.substring(0, 80) + '...' : selectedQuoteText }}"</div>
                <el-button link type="danger" size="small" @click="clearQuoteSelection"><el-icon><Delete /></el-icon></el-button>
              </div>
              <div class="comment-input">
                <div class="comment-input-wrapper">
                  <el-input
                    v-model="commentText"
                    :placeholder="selectedQuoteText ? '对选中内容发表批注...' : '发表评论... 输入@提及用户'"
                    :rows="2"
                    type="textarea"
                    @input="handleCommentInput"
                    @keydown="handleCommentKeydown"
                  />
                  <div v-if="showMentionList && filteredMentionUsers.length > 0" class="mention-dropdown">
                    <div
                      v-for="(user, idx) in filteredMentionUsers"
                      :key="user.id"
                      class="mention-item"
                      :class="{ active: idx === mentionIndex }"
                      @click="selectMentionUser(user)"
                      @mouseenter="mentionIndex = idx"
                    >
                      <el-avatar :size="24" class="mention-avatar">{{ user.realName?.charAt(0) || user.username?.charAt(0) }}</el-avatar>
                      <span class="mention-name">{{ user.realName || user.username }}</span>
                      <span v-if="user.departmentName" class="mention-dept">{{ user.departmentName }}</span>
                    </div>
                  </div>
                </div>
                <div class="comment-input-actions">
                  <VoiceInput v-model="commentText" @recognized="onCommentVoiceRecognized" />
                  <el-button type="primary" size="small" @click="handleAddComment" :loading="commentLoading">发送</el-button>
                </div>
              </div>
              <div class="comment-list">
                <div v-if="comments.length === 0" class="empty-comments">
                  <el-empty description="暂无评论" :image-size="40" />
                </div>
                <div v-for="comment in comments" :key="comment.id" class="comment-item">
                  <div class="comment-header">
                    <el-avatar :size="28" class="comment-avatar">{{ comment.username?.charAt(0) }}</el-avatar>
                    <span class="comment-user">{{ comment.username }}</span>
                    <span class="comment-time">{{ formatDate(comment.createTime) }}</span>
                    <el-button
                      v-if="canDeleteComment(comment)"
                      link
                      type="danger"
                      size="small"
                      class="comment-delete-btn"
                      @click="handleDeleteComment(comment)"
                    >
                      <el-icon><Delete /></el-icon> 删除
                    </el-button>
                  </div>
                  <div v-if="comment.quoteText" class="comment-quote">
                    <span class="quote-icon">❝</span>
                    <span class="quote-text">{{ comment.quoteText.length > 120 ? comment.quoteText.substring(0, 120) + '...' : comment.quoteText }}</span>
                  </div>
                  <div class="comment-content" v-html="renderCommentContent(comment.content)"></div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="版本" name="versions">
            <div class="version-section">
              <div v-if="versions.length === 0" class="empty-comments">
                <el-empty description="暂无版本历史" :image-size="40" />
              </div>
              <el-timeline>
                <el-timeline-item v-for="ver in versions" :key="ver.id" :timestamp="formatDate(ver.createTime)" placement="top">
                  <el-card shadow="never" class="version-card" :class="{ 'version-current': ver.version === fileData?.version }">
                    <div class="version-header">
                      <span class="version-num">v{{ ver.version }}</span>
                      <el-tag v-if="ver.version === fileData?.version" size="small" type="success" effect="dark">当前版本</el-tag>
                      <el-button v-else link type="primary" size="small" @click="handleRollback(ver.version)">回滚</el-button>
                    </div>
                    <div class="version-note">{{ ver.changeNote || '无变更说明' }}</div>
                    <div class="version-meta">{{ formatFileSize(ver.fileSize) }}</div>
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <el-dialog v-model="showShareDialog" title="创建分享链接" width="440px">
      <el-form label-width="80px">
        <el-form-item label="权限类型">
          <el-select v-model="shareForm.permissionType">
            <el-option label="仅查看" value="VIEW" />
            <el-option label="可下载" value="DOWNLOAD" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问密码">
          <el-input v-model="shareForm.password" placeholder="留空则无需密码" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker
            v-model="shareForm.expireTime"
            type="datetime"
            placeholder="留空则永不过期"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="最大访问">
          <el-input-number v-model="shareForm.maxAccess" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showShareDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateShare">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAddTag" title="添加标签" width="420px">
      <div class="tag-form-preview">
        预览：<code>{{ newTagKey || '标签键' }}: {{ newTagValue || '标签值' }}</code>
      </div>
      <el-form label-position="top" class="tag-form">
        <el-form-item label="标签键">
          <el-input v-model="newTagKey" placeholder="如：subject" />
        </el-form-item>
        <el-form-item label="标签值">
          <el-input v-model="newTagValue" placeholder="如：基于Flink的实时推荐系统" @keyup.enter="handleAddTag" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddTag = false">取消</el-button>
        <el-button type="primary" @click="handleAddTag">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditTag" title="编辑标签" width="420px">
      <div class="tag-form-preview">
        预览：<code>{{ editTagKey || '标签键' }}: {{ editTagValue || '标签值' }}</code>
      </div>
      <el-form label-position="top" class="tag-form">
        <el-form-item label="标签键">
          <el-input v-model="editTagKey" placeholder="如：subject" />
        </el-form-item>
        <el-form-item label="标签值">
          <el-input v-model="editTagValue" placeholder="如：基于Flink的实时推荐系统" @keyup.enter="handleUpdateTag" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditTag = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateTag">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showVersionUpload" title="上传新版本" width="560px" :close-on-click-modal="false" @close="handleVersionUploadClose">
      <ChunkUploader
        ref="versionUploaderRef"
        mode="updateVersion"
        :update-file-id="fileData?.id"
        :accept="fileData?.fileType ? '.' + fileData.fileType : ''"
        @complete="handleVersionUploadComplete"
        @close="handleVersionUploadClose"
      />
    </el-dialog>

    <el-dialog v-model="showAiTagConfirm" title="AI标签提取结果" width="480px" :close-on-click-modal="false">
      <p style="color:#606266;margin-bottom:12px;">AI已为本文档提取以下标签，是否添加到文档标签？</p>
      <div class="ai-tag-confirm-list">
        <div v-for="(meta, i) in aiExtractedTags" :key="i" class="ai-tag-confirm-item">
          <el-tag type="warning" size="default">{{ meta.tagKey }}: {{ meta.tagValue }}</el-tag>
          <span class="ai-tag-confidence">{{ ((meta.confidence || 0) * 100).toFixed(0) }}%</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="handleDismissAiTags">丢弃</el-button>
        <el-button type="primary" @click="handleConfirmAiTags">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 相关文件推荐悬浮框 -->
    <div
      v-if="showRelatedFilesFloat && relatedFiles.length > 0 && !isEmbedMode"
      ref="relatedFloatRef"
      class="related-files-float"
      :class="{ collapsed: relatedFilesCollapsed, 'is-dragging': isDraggingFloat }"
      :style="floatStyle"
    >
      <div class="related-files-header" @mousedown="startDragFloat" @click="handleFloatHeaderClick">
        <div class="related-files-title">
          <span>相关文件推荐</span>
          <el-tag size="small" type="info" effect="plain" class="related-count">{{ relatedFiles.length }}</el-tag>
        </div>
        <div class="related-files-actions">
          <el-button
            link
            size="small"
            :title="relatedFilesCollapsed ? '展开' : '收起'"
            @click.stop="relatedFilesCollapsed = !relatedFilesCollapsed"
          >
            <el-icon><ArrowDown v-if="!relatedFilesCollapsed" /><ArrowUp v-else /></el-icon>
          </el-button>
          <el-button
            link
            size="small"
            title="关闭"
            @click.stop="handleCloseRelatedFiles"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
      <div v-show="!relatedFilesCollapsed" class="related-files-body">
        <div
          v-for="file in relatedFiles"
          :key="file.id"
          class="related-file-item"
          @click="goPreview(file.id)"
        >
          <div class="related-file-icon"><FileIcon :file-type="file.fileType" :size="22" /></div>
          <div class="related-file-info">
            <div class="related-file-name" :title="file.fileName">{{ file.fileName }}</div>
            <div class="related-file-meta">
              <span>{{ file.directoryName || '根目录' }}</span>
              <span class="meta-divider">·</span>
              <span>{{ file.uploaderName }}</span>
              <span class="meta-divider">·</span>
              <span>{{ formatFileSize(file.fileSize) }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-show="!relatedFilesCollapsed" class="related-files-footer">
        <el-checkbox v-model="doNotShowToday" size="small" @change="handleDoNotShowTodayChange">
          今日不再显示
        </el-checkbox>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick, onUnmounted, onActivated, onDeactivated, type Ref } from 'vue'
defineOptions({ name: 'Preview' })
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Download, Share, ArrowDown, ArrowUp, UploadFilled, StarFilled, Star, Reading, CollectionTag, Upload, Loading, CopyDocument, Refresh, RefreshRight, Delete, MagicStick, Coordinate, Connection, Cpu, ChatLineSquare, Edit, Close, FullScreen, Aim } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as pdfjsLib from 'pdfjs-dist'
import * as d3 from 'd3'
import { getFileDetail, downloadFile, getFileVersions, uploadNewVersion, rollbackVersion, getFiles, getRelatedFiles, recordFileView, type FileDTO, type VersionDTO } from '@/api/document'
import { getPreviewUrl, getPreviewInfo, triggerOcr, getOcrResult, getExcelData, getPdfStreamUrl, getConvertedPdfUrl, getConvertStatus, triggerConvert, getTextPreview, saveProgressApi, getProgressApi, type ExcelSheetData, type ConvertStatus, type TextPreviewData } from '@/api/preview'
import { triggerTagExtraction, getDocTags, addDocTag, deleteDocTag, updateDocTag, getFileTags, confirmAiTags, dismissAiTags, generateMindmap, getSavedMindmap, submitMindmapTask, getMindmapTaskStatus, type DocTag, type DocMetadata } from '@/api/ai'
import { getComments, addComment, deleteComment, type CommentDTO } from '@/api/comment'
import { getMentionableUsers, type SimpleUser } from '@/api/auth'
import { createShareLink, type ShareLinkVO } from '@/api/share'
import { addFavorite, removeFavorite, checkFavorite } from '@/api/workspace'
import { useDocumentStore } from '@/stores/document'
import { useUserStore } from '@/stores/user'
import { useWatermark } from '@/composables/useWatermark'
import { formatFileSize, getFileTypeColor, isAudioVideo, isImage } from '@/utils/chunk-upload'
import request from '@/api'
import { renderMarkdown } from '@/utils/markdown'
import { debounce } from 'lodash-es'
import ChunkUploader from '@/components/ChunkUploader.vue'
import VoiceInput from '@/components/VoiceInput.vue'
import FileIcon from '@/components/FileIcon.vue'
import type { ChunkUploadState } from '@/utils/chunk-upload'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const docStore = useDocumentStore()
const { setWatermark, clearWatermark } = useWatermark()

const fileId = computed(() => {
  const id = Number(route.params.id)
  return isNaN(id) ? 0 : id
})
const isEmbedMode = computed(() => route.query.embed === 'true')
const loading = ref(true)
const fileData = ref<FileDTO | null>(null)
const isFavorited = ref(false)
const textContent = ref('')
const excelSheets = ref<ExcelSheetData[]>([])
const excelActiveSheet = ref(0)
const excelLoading = ref(false)
const renderedMarkdown = ref('')

const sidebarTab = ref(route.query.tab === 'comments' ? 'comments' : 'info')
const showRightSidebar = ref(true)
const docTags = ref<DocTag[]>([])
const comments = ref<CommentDTO[]>([])
const versions = ref<VersionDTO[]>([])
const commentText = ref('')
const commentLoading = ref(false)
const allUsers = ref<SimpleUser[]>([])
const showMentionList = ref(false)
const mentionSearch = ref('')
const mentionIndex = ref(0)
const mentionedUserIds = ref<Set<number>>(new Set())

const selectedQuoteText = ref<string | null>(null)
const pendingQuoteText = ref<string | null>(null)
const showAnnotationBtn = ref(false)
const annotationBtnStyle = ref<{ top: string; left: string }>({ top: '0px', left: '0px' })
const previewMainRef = ref<HTMLElement | null>(null)
const previewContentRef = ref<HTMLElement | null>(null)

const filteredMentionUsers = computed(() => {
  if (!mentionSearch.value) return allUsers.value.slice(0, 8)
  const keyword = mentionSearch.value.toLowerCase()
  return allUsers.value.filter(u =>
    (u.realName && u.realName.toLowerCase().includes(keyword)) ||
    u.username.toLowerCase().includes(keyword)
  ).slice(0, 8)
})

const showShareDialog = ref(false)
const shareForm = ref({ permissionType: 'VIEW', password: '', maxAccess: 100, expireTime: null as string | null })
const shareResult = ref<ShareLinkVO | null>(null)

const showAddTag = ref(false)
const newTagKey = ref('')
const newTagValue = ref('')
const showEditTag = ref(false)
const editTagKey = ref('')
const editTagValue = ref('')
const editingTagId = ref<number | null>(null)

const showVersionUpload = ref(false)
const versionUploaderRef = ref()

// 相关文件推荐
const relatedFiles = ref<FileDTO[]>([])
const showRelatedFilesFloat = ref(true)
const relatedFilesCollapsed = ref(false)
const doNotShowToday = ref(false)
const relatedFilesLoading = ref(false)
const RELATED_HIDE_KEY = computed(() => `related_files_hide_until_${userStore.userId || 'guest'}`)

// 悬浮框拖拽
const relatedFloatRef = ref<HTMLElement | null>(null)
const floatDragOffset = ref({ x: 0, y: 0 })
const isDraggingFloat = ref(false)
const floatDragStart = ref({ mouseX: 0, mouseY: 0, offsetX: 0, offsetY: 0, hasMoved: false })
const floatStyle = computed(() => ({
  transform: `translate(${floatDragOffset.value.x}px, calc(-50% + ${floatDragOffset.value.y}px))`,
  cursor: isDraggingFloat.value ? 'grabbing' : 'default',
}))

// 判断当前文件是否可以用于模板生成
const canGenerateTemplate = computed(() => {
  if (!fileData.value) return false
  // 条件一：文件类型必须是可编辑文档
  const ft = fileData.value.fileType?.toLowerCase()
  if (!['docx', 'doc', 'pdf', 'pptx', 'ppt'].includes(ft)) return false
  // 条件二：AI标签中包含与智能文档生成模板相关的关键词
  const templateKeywords = [
    '模板', '表单', '可生成',
    '纪要', '会议', '方案', '项目',
    '技术文档', '汇报', '周报', '月报',
    '合同', '协议', '报告'
  ]
  return docTags.value.some(tag => templateKeywords.some(kw => tag.tagName?.includes(kw)))
})

// 提取文件后缀名（转小写）
const fileExt = computed(() => {
  if (!fileData.value?.fileName) return ''
  return fileData.value.fileName.split('.').pop()?.toLowerCase() || ''
})

// 脑图支持类型：PDF、Word、PPT、长文本等
const canShowMindmap = computed(() => {
  const validExts = ['pdf', 'doc', 'docx', 'ppt', 'pptx', 'txt', 'md']
  return validExts.includes(fileExt.value)
})

// OCR 支持类型：图片、PDF，以及已转换为 PDF 预览的 Word/PPT
const canShowOcr = computed(() => {
  const ft = fileData.value?.fileType?.toLowerCase() || ''
  const imageOrPdfExts = ['pdf', 'jpg', 'jpeg', 'png', 'bmp', 'webp']
  if (imageOrPdfExts.includes(fileExt.value)) return true
  // Word/PPT 等已转换为 PDF 预览后，同样可以识别当前 PDF 页
  if (['doc', 'docx', 'ppt', 'pptx'].includes(ft)) {
    return fileData.value?.previewStatus === 'COMPLETED' && !!fileData.value?.previewPdfPath
  }
  return false
})

// Word/PPT 转换完成前，OCR Tab 提示“转换中”而不是“不支持”
const ocrPendingOffice = computed(() => {
  const ft = fileData.value?.fileType?.toLowerCase() || ''
  if (!['doc', 'docx', 'ppt', 'pptx'].includes(ft)) return false
  if (fileData.value?.previewStatus === 'COMPLETED') return false
  const convStatus = ft.startsWith('ppt') ? convertStatus.value : wordConvertStatus.value
  return convStatus !== 'FAILED'
})

/** 标签 chip 配色（与 TagCloud 组件保持一致的视觉风格） */
const tagChipPalette = [
  { bg: '#E8F5E9', text: '#2E7D32', border: '#66BB6A' },
  { bg: '#E3F2FD', text: '#1565C0', border: '#42A5F5' },
  { bg: '#FFF3E0', text: '#E65100', border: '#FFA726' },
  { bg: '#F3E5F5', text: '#7B1FA2', border: '#AB47BC' },
  { bg: '#E0F7FA', text: '#00695C', border: '#26A69A' },
  { bg: '#FCE4EC', text: '#C62828', border: '#EF5350' },
  { bg: '#F1F8E9', text: '#558B2F', border: '#9CCC65' },
  { bg: '#EDE7F6', text: '#4527A0', border: '#7E57C2' },
]
function getTagChipStyle(index: number) {
  const p = tagChipPalette[index % tagChipPalette.length]
  return { '--chip-bg': p.bg, '--chip-text': p.text, '--chip-border': p.border }
}

/**
 * 返回按钮：始终导航到文档空间，避免因退出登录清空浏览器历史导致 router.back() 跳回首页
 */
function handleGoBack() {
  router.push({ name: 'Document' })
}

function goPreview(fileId: number) {
  if (!fileId) return
  router.push(`/preview/${fileId}`)
}

// ==================== 相关文件推荐 ====================

function getTodayEndTimestamp(): number {
  const now = new Date()
  now.setHours(23, 59, 59, 999)
  return now.getTime()
}

function isHiddenToday(): boolean {
  try {
    const hideUntil = parseInt(localStorage.getItem(RELATED_HIDE_KEY.value) || '0', 10)
    return hideUntil > Date.now()
  } catch {
    return false
  }
}

async function fetchRelatedFiles() {
  if (!fileId.value || fileId.value <= 0) return
  if (isHiddenToday()) {
    showRelatedFilesFloat.value = false
    return
  }
  relatedFilesLoading.value = true
  try {
    const res = await getRelatedFiles(fileId.value)
    const data = (res.data as any).data as FileDTO[] | undefined
    relatedFiles.value = (data || []).filter(f => f.id !== fileId.value)
    showRelatedFilesFloat.value = relatedFiles.value.length > 0
  } catch (e) {
    console.error('[相关文件推荐] 获取失败:', e)
    relatedFiles.value = []
    showRelatedFilesFloat.value = false
  } finally {
    relatedFilesLoading.value = false
  }
}

function handleCloseRelatedFiles() {
  if (doNotShowToday.value) {
    try {
      localStorage.setItem(RELATED_HIDE_KEY.value, String(getTodayEndTimestamp()))
    } catch { /* ignore */ }
    ElMessage.success('今日不再显示相关文件推荐')
  }
  showRelatedFilesFloat.value = false
}

function startDragFloat(e: MouseEvent) {
  // 只响应标题栏拖拽，不响应按钮点击
  if ((e.target as HTMLElement)?.closest('.related-files-actions')) return
  isDraggingFloat.value = true
  floatDragStart.value = {
    mouseX: e.clientX,
    mouseY: e.clientY,
    offsetX: floatDragOffset.value.x,
    offsetY: floatDragOffset.value.y,
    hasMoved: false,
  }
  window.addEventListener('mousemove', onDragFloat)
  window.addEventListener('mouseup', stopDragFloat)
}

function onDragFloat(e: MouseEvent) {
  if (!isDraggingFloat.value) return
  const dx = e.clientX - floatDragStart.value.mouseX
  const dy = e.clientY - floatDragStart.value.mouseY
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    floatDragStart.value.hasMoved = true
  }
  floatDragOffset.value = {
    x: floatDragStart.value.offsetX + dx,
    y: floatDragStart.value.offsetY + dy,
  }
}

function stopDragFloat() {
  isDraggingFloat.value = false
  window.removeEventListener('mousemove', onDragFloat)
  window.removeEventListener('mouseup', stopDragFloat)
}

function handleFloatHeaderClick() {
  // 拖拽过程中不触发展开/收起
  if (floatDragStart.value.hasMoved) return
  relatedFilesCollapsed.value = !relatedFilesCollapsed.value
}

function handleDoNotShowTodayChange(val: boolean | string | number) {
  if (val) {
    try {
      localStorage.setItem(RELATED_HIDE_KEY.value, String(getTodayEndTimestamp()))
    } catch { /* ignore */ }
  } else {
    try {
      localStorage.removeItem(RELATED_HIDE_KEY.value)
    } catch { /* ignore */ }
  }
}

function handleGenerateFromTemplate() {
  if (!fileData.value) return
  // 根据AI标签匹配最合适的模板类型
  const tagNames = docTags.value.map(t => t.tagName || '').join(',')
  let templateType = 'meeting-minutes' // 默认
  if (tagNames.includes('纪要') || tagNames.includes('会议')) templateType = 'meeting-minutes'
  else if (tagNames.includes('方案') || tagNames.includes('项目')) templateType = 'project-proposal'
  else if (tagNames.includes('技术') || tagNames.includes('架构') || tagNames.includes('API')) templateType = 'technical-doc'
  else if (tagNames.includes('汇报') || tagNames.includes('周报') || tagNames.includes('月报')) templateType = 'work-report'
  else if (tagNames.includes('合同') || tagNames.includes('协议')) templateType = 'contract'

  // 将文件内容、OCR文本作为参考内容传入
  const refContent = ocrText.value || fileData.value.extractedText || ''
  router.push({
    path: '/ai/generate',
    query: {
      templateType,
      fileName: fileData.value.fileName || '',
      hasReference: refContent ? '1' : '0',
      hasAiTags: '1'
    }
  })
  const fileId = fileData.value.id
  // 通过sessionStorage传递参考内容（避免URL过长）
  if (refContent) {
    sessionStorage.setItem(`ref_content_${fileId}`, refContent)
  }
  sessionStorage.setItem(`ref_file_id`, String(fileId))

  // 收集AI标签元数据并传递给模板生成页面
  // 优先使用待确认的aiExtractedTags（有tagKey/tagValue结构化数据）
  // 其次解析已确认的docTags（tagName格式为 "tag_key: tag_value"）
  const metaMap: Record<string, string> = {}
  if (aiExtractedTags.value.length > 0) {
    for (const tag of aiExtractedTags.value) {
      if (tag.tagKey && tag.tagValue) {
        metaMap[tag.tagKey] = tag.tagValue
      }
    }
  } else {
    for (const tag of docTags.value) {
      if (tag.tagName && tag.tagSource?.toLowerCase() === 'ai') {
        const colonIdx = tag.tagName.indexOf(':')
        if (colonIdx > 0) {
          const key = tag.tagName.substring(0, colonIdx).trim()
          const value = tag.tagName.substring(colonIdx + 1).trim()
          if (key && value) metaMap[key] = value
        }
      }
    }
  }
  if (Object.keys(metaMap).length > 0) {
    sessionStorage.setItem(`ai_meta_${fileId}`, JSON.stringify(metaMap))
  }
}

function handleVersionUploadComplete(state: ChunkUploadState) {
  ElMessage.success('新版本上传成功')
  showVersionUpload.value = false
  // 合并是同步的，上传完成时数据已写入数据库，静默刷新（不显示loading骨架屏）
  refreshAfterVersionUpdate()
}

async function refreshAfterVersionUpdate() {
  if (!fileId.value) return
  try {
    const res = await getFileDetail(fileId.value)
    fileData.value = res.data.data || res.data
    // 同步刷新 document store 缓存，确保文件列表中的版本号一致
    if (fileData.value?.directoryId !== undefined) {
      try {
        const filesRes = await getFiles(fileData.value.directoryId)
        const files = filesRes.data?.data || filesRes.data
        docStore.patchFiles(files, fileData.value.directoryId)
      } catch {
        // store 刷新失败不影响主流程
      }
    }
    // 关键修复：销毁旧 pdf.js 文档对象前，先从缓存中移除该文件条目，
    // 否则下次 restoreFileFromCache 会恢复已 destroy 的 pdfDoc，导致白屏
    const updatedFileId = fileData.value?.id
    if (updatedFileId) {
      const cached = fileCacheMap.get(updatedFileId)
      if (cached) {
        cached.pdfDoc?.destroy()
        cached.pptDoc?.destroy()
        cached.wordDoc?.destroy()
        if (cached.imageBlobUrl) URL.revokeObjectURL(cached.imageBlobUrl)
        fileCacheMap.delete(updatedFileId)
      }
    }
    // 销毁旧的 pdf.js 文档对象，强制重新加载
    pdfDoc?.destroy()
    pdfDoc = null
    pptDoc?.destroy()
    pptDoc = null
    wordDoc?.destroy()
    wordDoc = null
    // 重置转换状态，确保重新触发转换（新版本文件内容变了）
    convertStatus.value = 'NOT_STARTED'
    wordConvertStatus.value = 'NOT_STARTED'
    // 文件内容已更新，重置阅读位置到第一页
    resetReadingPosition()
    // 重置 lastLoadedFileId，确保 watch(fileId) 不会因 id 相同而跳过重新加载
    lastLoadedFileId = 0
    await loadPreviewContent()
    fetchVersions()
    fetchTags()
  } catch {
    // 静默失败
  }
}

/** 文件内容更新时，重置阅读位置到第一页（本地+云端） */
function resetReadingPosition() {
  if (!fileData.value) return
  const fid = fileData.value.id
  const ft = fileData.value.fileType?.toLowerCase()

  // 重置本地分页状态
  currentPage.value = 1
  currentPptPage.value = 1
  currentWordPage.value = 1

  // 重置媒体播放位置
  if (mediaRef.value) {
    mediaRef.value.currentTime = 0
  }

  // 同步重置云端进度
  if (ft === 'pdf' || ft === 'pptx' || ft === 'ppt' || ft === 'docx' || ft === 'doc') {
    saveProgressApi({ fileId: fid, progressType: 1, progressValue: 1 }).catch(() => {})
  } else if (isAudioVideo(ft)) {
    saveProgressApi({ fileId: fid, progressType: 2, progressValue: 0 }).catch(() => {})
  }
}

function handleVersionUploadClose() {
  showVersionUpload.value = false
  if (versionUploaderRef.value) {
    versionUploaderRef.value.reset()
  }
}

const showAiTagConfirm = ref(false)
const aiExtractedTags = ref<DocMetadata[]>([])

const pptxContainerRef = ref<HTMLDivElement>()

// ==================== pdf.js 渲染相关 ====================
// 设置 pdf.js worker
pdfjsLib.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version}/pdf.worker.min.js`

const pdfCanvasRef = ref<HTMLCanvasElement>()
const pptCanvasRef = ref<HTMLCanvasElement>()
const wordCanvasRef = ref<HTMLCanvasElement>()
const pdfTextLayerRef = ref<HTMLElement>()
const pptTextLayerRef = ref<HTMLElement>()
const wordTextLayerRef = ref<HTMLElement>()
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let lastLoadedFileId = 0
let pptDoc: pdfjsLib.PDFDocumentProxy | null = null
let wordDoc: pdfjsLib.PDFDocumentProxy | null = null
let currentRenderTask: any = null // 用于打断上一次未完成的渲染，防止快速翻页导致 Canvas 崩溃

// ==================== 文件缓存机制 ====================
const MAX_FILE_CACHE = 5

interface FileCacheEntry {
  fileData: FileDTO
  pdfDoc: pdfjsLib.PDFDocumentProxy | null
  pptDoc: pdfjsLib.PDFDocumentProxy | null
  wordDoc: pdfjsLib.PDFDocumentProxy | null
  currentPage: number
  currentPptPage: number
  currentWordPage: number
  pdfTotalPages: number
  pptTotalPages: number
  wordTotalPages: number
  convertStatus: string
  wordConvertStatus: string
  textContent: string
  isFavorited: boolean
  // Excel 预览状态（修复：之前未缓存导致切回 xlsx 文件时显示"解析失败"）
  excelSheets: ExcelSheetData[]
  excelActiveSheet: number
  imageBlobUrl: string
  lastAccess: number
}

const fileCacheMap = new Map<number, FileCacheEntry>()

/** 保存当前文件状态到缓存 */
function saveCurrentFileToCache() {
  if (!fileData.value) return
  const id = fileData.value.id
  // 如果 pdfDoc 已被 destroy，不缓存
  if (fileData.value.fileType?.toLowerCase() === 'pdf' && !pdfDoc) return

  fileCacheMap.set(id, {
    fileData: fileData.value,
    pdfDoc,
    pptDoc,
    wordDoc,
    currentPage: currentPage.value,
    currentPptPage: currentPptPage.value,
    currentWordPage: currentWordPage.value,
    pdfTotalPages: pdfTotalPages.value,
    pptTotalPages: pptTotalPages.value,
    wordTotalPages: wordTotalPages.value,
    convertStatus: convertStatus.value,
    wordConvertStatus: wordConvertStatus.value,
    textContent: textContent.value,
    isFavorited: isFavorited.value,
    // 保存 Excel 状态，修复切回 xlsx 文件时显示"解析失败"的问题
    excelSheets: excelSheets.value,
    excelActiveSheet: excelActiveSheet.value,
    imageBlobUrl: _imageBlobUrl,
    lastAccess: Date.now(),
  })

  // LRU 淘汰：超过上限时移除最久未访问的
  if (fileCacheMap.size > MAX_FILE_CACHE) {
    let oldestKey = 0
    let oldestTime = Infinity
    for (const [key, entry] of fileCacheMap) {
      if (entry.lastAccess < oldestTime) {
        oldestTime = entry.lastAccess
        oldestKey = key
      }
    }
    if (oldestKey) {
      const evicted = fileCacheMap.get(oldestKey)
      // 销毁被淘汰的 pdf.js 文档对象，释放内存
      evicted?.pdfDoc?.destroy()
      evicted?.pptDoc?.destroy()
      evicted?.wordDoc?.destroy()
      if (evicted?.imageBlobUrl) URL.revokeObjectURL(evicted.imageBlobUrl)
      fileCacheMap.delete(oldestKey)
    }
  }
}

/** 从缓存恢复文件状态，返回 true 表示命中缓存 */
function restoreFileFromCache(id: number): boolean {
  const cached = fileCacheMap.get(id)
  if (!cached) return false

  cached.lastAccess = Date.now()

  // 恢复文件数据
  fileData.value = cached.fileData
  pdfDoc = cached.pdfDoc
  pptDoc = cached.pptDoc
  wordDoc = cached.wordDoc
  currentPage.value = cached.currentPage
  currentPptPage.value = cached.currentPptPage
  currentWordPage.value = cached.currentWordPage
  pdfTotalPages.value = cached.pdfTotalPages
  pptTotalPages.value = cached.pptTotalPages
  wordTotalPages.value = cached.wordTotalPages
  convertStatus.value = cached.convertStatus as any
  wordConvertStatus.value = cached.wordConvertStatus as any
  textContent.value = cached.textContent
  isFavorited.value = cached.isFavorited
  // 恢复 Excel 状态，修复切回 xlsx 文件时显示"解析失败"的问题
  excelSheets.value = cached.excelSheets || []
  excelActiveSheet.value = cached.excelActiveSheet ?? 0

  // 恢复图片 blob URL
  revokeImageBlob()
  if (cached.imageBlobUrl) {
    _imageBlobUrl = cached.imageBlobUrl
    imageUrl.value = cached.imageBlobUrl
  } else {
    imageUrl.value = ''
  }

  return true
}

// PDF 预览状态
const pdfTotalPages = ref(0)
const currentPage = ref(1)
const jumpPageNum = ref(1)
const pdfLoading = ref(false)
const pdfScale = ref(1.5)

let isInitializing = false

const pptTotalPages = ref(0)
const currentPptPage = ref(1)
const jumpPptPageNum = ref(1)
const pptLoading = ref(false)
const convertStatus = ref<ConvertStatus['status']>('NOT_STARTED')
const convertMessage = ref('')

// Word 预览状态（异步转换 + pdf.js）
const wordTotalPages = ref(0)
const currentWordPage = ref(1)
const jumpWordPageNum = ref(1)
const wordLoading = ref(false)
const wordConvertStatus = ref<ConvertStatus['status']>('NOT_STARTED')
const wordConvertMessage = ref('')

// 转换轮询定时器
let convertPollTimer: ReturnType<typeof setInterval> | null = null
let wordConvertPollTimer: ReturnType<typeof setInterval> | null = null
// 浏览计数定时器（停留 10s 以上才记录）
let viewRecordTimer: ReturnType<typeof setTimeout> | null = null

const TEXT_FILE_TYPES = new Set(['txt', 'md', 'csv', 'log', 'json', 'xml', 'html', 'htm', 'css', 'js', 'java', 'py', 'c', 'cpp', 'h', 'go', 'rs', 'ts', 'sql', 'yaml', 'yml', 'ini', 'conf', 'cfg', 'sh', 'bat', 'properties'])

function isTextFile(ft: string) {
  return TEXT_FILE_TYPES.has(ft?.toLowerCase())
}

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
          if (i + 1 < line.length && line[i + 1] === '"') {
            cell += '"'
            i++
          } else {
            inQuotes = false
          }
        } else {
          cell += ch
        }
      } else {
        if (ch === '"') {
          inQuotes = true
        } else if (ch === ',') {
          cells.push(cell)
          cell = ''
        } else {
          cell += ch
        }
      }
    }
    cells.push(cell)
    return cells
  }
  const escapeHtml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  let html = '<div class="csv-table-wrapper"><table><thead><tr>'
  const headerCells = parseRow(displayLines[0])
  for (const h of headerCells) {
    html += `<th>${escapeHtml(h)}</th>`
  }
  html += '</tr></thead><tbody>'
  for (let r = 1; r < displayLines.length; r++) {
    html += '<tr>'
    const cells = parseRow(displayLines[r])
    for (const c of cells) {
      html += `<td>${escapeHtml(c)}</td>`
    }
    html += '</tr>'
  }
  html += '</tbody></table>'
  if (lines.length > MAX_ROWS) {
    html += `<p style="text-align:center;color:#909399;margin-top:12px;">仅显示前 ${MAX_ROWS} 行，共 ${lines.length} 行。请下载查看完整文件</p>`
  }
  html += '</div>'
  return html
}

const pdfUrl = computed(() => fileData.value ? getPreviewUrl(fileData.value!.id) : '')
// 视频/音频：使用 blob URL（通过 axios 携带 Authorization header），避免 media 标签直接请求被 403
const mediaBlobUrl = ref('')
let _mediaBlobUrl = ''
function revokeMediaBlob() {
  if (_mediaBlobUrl) {
    URL.revokeObjectURL(_mediaBlobUrl)
    _mediaBlobUrl = ''
  }
}
async function loadMediaBlob(fileId: number) {
  revokeMediaBlob()
  try {
    const res = await request.get(`/front/preview/${fileId}`, { responseType: 'blob' })
    const blob = new Blob([res.data])
    _mediaBlobUrl = URL.createObjectURL(blob)
    mediaBlobUrl.value = _mediaBlobUrl
  } catch {
    mediaBlobUrl.value = ''
  }
}
// 图片预览：使用 blob URL（通过 axios 携带 Authorization header），避免 img 标签直接请求被 403
const imageUrl = ref('')
let _imageBlobUrl = ''
function revokeImageBlob() {
  if (_imageBlobUrl) {
    URL.revokeObjectURL(_imageBlobUrl)
    _imageBlobUrl = ''
  }
}
async function loadImageBlob(fileId: number) {
  revokeImageBlob()
  try {
    const res = await request.get(`/front/preview/${fileId}`, { responseType: 'blob' })
    const blob = new Blob([res.data])
    _imageBlobUrl = URL.createObjectURL(blob)
    imageUrl.value = _imageBlobUrl
  } catch {
    imageUrl.value = ''
  }
}
const docHtmlContent = ref('')

// ==================== 阅读记忆（云端同步） ====================
const mediaRef = ref<HTMLVideoElement | HTMLAudioElement>()

// 防抖保存进度（停留1.5秒后触发，避免高频请求，符合设计文档要求）
const debouncedSaveProgress = debounce((fileId: number, type: number, val: number) => {
  saveProgressApi({ fileId, progressType: type, progressValue: val })
    .catch(e => console.error('进度同步失败', e))
}, 1500)

function saveReadingProgress() {
  if (!fileData.value) return
  const fid = fileData.value.id
  const ft = fileData.value.fileType?.toLowerCase()
  if (ft === 'pdf') {
    debouncedSaveProgress(fid, 1, currentPage.value)
  } else if (ft === 'pptx' || ft === 'ppt') {
    debouncedSaveProgress(fid, 1, currentPptPage.value)
  } else if (ft === 'docx' || ft === 'doc') {
    debouncedSaveProgress(fid, 1, currentWordPage.value)
  } else if (isAudioVideo(ft)) {
    if (mediaRef.value) {
      saveProgressApi({ fileId: fid, progressType: 2, progressValue: mediaRef.value.currentTime })
        .catch(e => console.error('进度同步失败', e))
    }
  }
}

let mediaTimeSaveTimer: ReturnType<typeof setTimeout> | null = null

function handleMediaTimeUpdate() {
  // 节流：每5秒保存一次播放进度
  if (!mediaTimeSaveTimer && fileData.value) {
    mediaTimeSaveTimer = setTimeout(() => {
      if (mediaRef.value && fileData.value) {
        saveProgressApi({ fileId: fileData.value.id, progressType: 2, progressValue: mediaRef.value.currentTime })
          .catch(e => console.error('进度同步失败', e))
      }
      mediaTimeSaveTimer = null
    }, 5000)
  }
}

function handleMediaPause() {
  // 暂停/结束时立即保存一次，避免页面卸载时丢失最后一次进度
  if (mediaTimeSaveTimer) {
    clearTimeout(mediaTimeSaveTimer)
    mediaTimeSaveTimer = null
  }
  if (mediaRef.value && fileData.value) {
    saveProgressApi({ fileId: fileData.value.id, progressType: 2, progressValue: mediaRef.value.currentTime })
      .catch(e => console.error('进度同步失败', e))
  }
}

/** 音视频播放回调处理 */
async function handleMediaLoaded() {
  if (!fileData.value) return
  const targetFileId = fileData.value.id
  try {
    const res = await getProgressApi(targetFileId, 2)

    // 核心拦截：比对路由参数，防止多级回调串屏
    if (targetFileId !== fileId.value) return

    const time = res.data?.data ?? res.data ?? 0
    if (time > 0 && mediaRef.value) {
      mediaRef.value.currentTime = Math.floor(time)
    }
  } catch (e) {
    console.error('获取播放历史失败', e)
  }
}

// ==================== pdf.js 渲染函数 ====================

/** 渲染PDF页面到canvas */
async function renderPdfPageToCanvas(
  doc: pdfjsLib.PDFDocumentProxy,
  pageNum: number,
  canvasRef: Ref<HTMLCanvasElement | undefined>,
  scale: number = 1.5,
  textLayerRef?: Ref<HTMLElement | undefined>
) {
  if (!canvasRef.value) return
  const page = await doc.getPage(pageNum)
  const viewport = page.getViewport({ scale })
  const canvas = canvasRef.value
  const context = canvas.getContext('2d')
  if (!context) return

  // 1. 设置 canvas 缓冲区尺寸（属性）
  canvas.width = viewport.width
  canvas.height = viewport.height

  // 2. 显式设置 canvas CSS 显示尺寸（像素），避免依赖 CSS 百分比
  //    百分比与 inline-block wrapper 存在循环依赖，首次渲染时解析失败
  canvas.style.width = viewport.width + 'px'
  canvas.style.height = viewport.height + 'px'

  // 3. 设置 wrapper 尺寸
  const wrapper = canvas.parentElement
  if (wrapper) {
    wrapper.style.width = `${viewport.width}px`
    wrapper.style.height = `${viewport.height}px`
  }

  // 4. 仅首次渲染时等待布局完成（canvas 刚进入 DOM 时 wrapper 无尺寸）
  //    翻页时 canvas 已在 DOM 中，跳过等待以减少延迟
  const needsLayoutWait = !canvas.style.width || canvas.style.width === '0px'
  if (needsLayoutWait) {
    await new Promise<void>(resolve => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => resolve())
      })
    })
  }

  // 取消上一次未完成的渲染，防止快速翻页导致 Canvas 崩溃
  if (currentRenderTask) {
    try { await currentRenderTask.cancel() } catch (e) {}
  }

  currentRenderTask = page.render({ canvasContext: context, viewport })

  try {
    await currentRenderTask.promise
  } catch (err: any) {
    if (err.name === 'RenderingCancelledException') return
    console.error('[PDF] 渲染异常:', err)
  }

  if (textLayerRef?.value) {
    const textContent = await page.getTextContent()
    const textLayerEl = textLayerRef.value
    textLayerEl.innerHTML = ''

    textLayerEl.style.width = viewport.width + 'px'
    textLayerEl.style.height = viewport.height + 'px'
    textLayerEl.style.setProperty('--scale-factor', String(scale))

    try {
      if (typeof (pdfjsLib as any).renderTextLayer === 'function') {
        const task = (pdfjsLib as any).renderTextLayer({
          textContentSource: textContent,
          container: textLayerEl,
          viewport: viewport,
        })
        await task.promise
      } else if (typeof (pdfjsLib as any).TextLayer !== 'undefined') {
        const textLayer = new (pdfjsLib as any).TextLayer({
          container: textLayerEl,
          textContentSource: textContent,
          viewport: viewport,
        })
        await textLayer.render()
      }
    } catch (err) {
      console.error('[PDF] 文本图层渲染失败:', err)
    }
  }
}

/** 加载PDF文件（直接流） */
async function loadPdfWithPdfJs(targetFileId: number) {
  pdfLoading.value = true
  try {
    const url = getPdfStreamUrl(targetFileId) + '?v=' + (fileData.value?.version || Date.now())
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const arrayBuffer = await response.arrayBuffer()
    const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer })
    const [doc, progressRes] = await Promise.all([loadingTask.promise, getProgressApi(targetFileId, 1).catch(() => null)])

    // 核心拦截：使用路由同步计算值 fileId.value 比对，防止僵尸回调
    if (targetFileId !== fileId.value) return

    pdfDoc = doc
    pdfTotalPages.value = pdfDoc.numPages

    const serverPage = progressRes?.data?.data ?? progressRes?.data ?? 0
    let targetPage = serverPage > 0 ? Math.floor(serverPage) : 1
    if (targetPage > pdfTotalPages.value) targetPage = pdfTotalPages.value

    isInitializing = true
    currentPage.value = targetPage

    await nextTick()
    await renderPdfPageToCanvas(pdfDoc, currentPage.value, pdfCanvasRef, 1.5, pdfTextLayerRef)
    isInitializing = false
  } catch (e) {
    console.error('PDF加载失败:', e)
    isInitializing = false
  }
  pdfLoading.value = false
}

/** 加载已转换的PDF（Word/PPT转PDF后） */
async function loadConvertedPdfWithPdfJs(targetFileId: number, target: 'ppt' | 'word') {
  const canvasRef = target === 'ppt' ? pptCanvasRef : wordCanvasRef
  const textLayerRef = target === 'ppt' ? pptTextLayerRef : wordTextLayerRef
  const loading = target === 'ppt' ? pptLoading : wordLoading
  loading.value = true

  try {
    const url = getConvertedPdfUrl(targetFileId) + '?v=' + (fileData.value?.version || Date.now())
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const arrayBuffer = await response.arrayBuffer()
    const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer })
    const [doc, progressRes] = await Promise.all([loadingTask.promise, getProgressApi(targetFileId, 1).catch(() => null)])

    // 核心拦截：使用路由同步计算值 fileId.value 比对，防止僵尸回调
    if (targetFileId !== fileId.value) return

    if (target === 'ppt') { pptDoc = doc; pptTotalPages.value = doc.numPages }
    else { wordDoc = doc; wordTotalPages.value = doc.numPages }

    const serverPage = progressRes?.data?.data ?? progressRes?.data ?? 0
    let targetPage = serverPage > 0 ? Math.floor(serverPage) : 1
    if (targetPage > doc.numPages) targetPage = doc.numPages

    if (target === 'ppt') currentPptPage.value = targetPage
    else currentWordPage.value = targetPage

    isInitializing = true

    await nextTick()
    await renderPdfPageToCanvas(doc, targetPage, canvasRef, 1.5, textLayerRef)
    isInitializing = false
  } catch (e) {
    console.error('转换PDF加载失败:', e)
    isInitializing = false
    // 健壮性修复：加载失败时（如 converted-pdf 返回 404），重置状态为 NOT_STARTED，
    // 让 checkAndStartConversion 重新触发转换，避免卡在白屏。
    // 同时同步更新 fileData 的 previewStatus，防止下次缓存恢复又拿到错误的 COMPLETED。
    if (fileData.value) {
      fileData.value = { ...fileData.value, previewStatus: 'NOT_STARTED', previewPdfPath: null }
    }
    if (target === 'ppt') {
      convertStatus.value = 'NOT_STARTED'
    } else {
      wordConvertStatus.value = 'NOT_STARTED'
    }
  }
  loading.value = false
}

/** PPT: 触发异步转换 */
async function startPptConversion() {
  if (!fileData.value) return
  convertStatus.value = 'PROCESSING'
  convertMessage.value = '文档转换已启动，请稍候...'
  try {
    await triggerConvert(fileData.value.id)
    startConvertPolling('ppt')
  } catch {
    convertStatus.value = 'FAILED'
    convertMessage.value = '触发转换失败'
  }
}

/** PPT: 强制重新转换（清除 Redis 缓存后重试） */
async function retryPptConvert() {
  if (!fileData.value) return
  convertStatus.value = 'PROCESSING'
  convertMessage.value = '正在重新转换...'
  try {
    await triggerConvert(fileData.value.id, true)
    startConvertPolling('ppt')
  } catch {
    convertStatus.value = 'FAILED'
    convertMessage.value = '重新转换失败'
  }
}

/** Word: 触发异步转换 */
async function startWordConversion() {
  if (!fileData.value) return
  wordConvertStatus.value = 'PROCESSING'
  wordConvertMessage.value = '文档转换已启动，请稍候...'
  try {
    await triggerConvert(fileData.value.id)
    startConvertPolling('word')
  } catch {
    wordConvertStatus.value = 'FAILED'
    wordConvertMessage.value = '触发转换失败'
  }
}

/** Word: 强制重新转换（清除 Redis 缓存后重试） */
async function retryWordConvert() {
  if (!fileData.value) return
  wordConvertStatus.value = 'PROCESSING'
  wordConvertMessage.value = '正在重新转换...'
  try {
    await triggerConvert(fileData.value.id, true)
    startConvertPolling('word')
  } catch {
    wordConvertStatus.value = 'FAILED'
    wordConvertMessage.value = '重新转换失败'
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

  let isRequesting = false // ✅ 核心修复 2.2：请求锁，防止接口响应慢导致上一个轮询还没结束，下一个又开始
  let autoRetried = false // 自动重试标记：FAILED 时静默重试一次，避免用户手动刷新
  const timer = setInterval(async () => {
    if (isRequesting) return
    if (!fileData.value) { stopPolling(); return }

    isRequesting = true
    try {
      const res = await getConvertStatus(fileData.value.id)
      const data = res.data.data || res.data
      statusRef.value = data.status
      messageRef.value = data.message || ''

      if (data.status === 'COMPLETED') {
        stopPolling()
        // 同步更新 fileData 的 previewStatus，使 OCR Tab 的 canShowOcr 计算属性立即生效
        if (fileData.value) {
          fileData.value = { ...fileData.value, previewStatus: 'COMPLETED', previewPdfPath: fileData.value.previewPdfPath || `__converted__${fileData.value.id}` }
        }
        await loadConvertedPdfWithPdfJs(fileData.value.id, target)
      } else if (data.status === 'FAILED') {
        stopPolling()
        // 自动重试：首次失败时（如 LibreOffice 首次调用未就绪），静默重试一次
        // 避免用户看到"转换失败"后必须手动刷新页面
        if (!autoRetried) {
          autoRetried = true
          statusRef.value = 'PROCESSING'
          messageRef.value = '正在重新转换...'
          try {
            await triggerConvert(fileData.value.id, true)
            startConvertPolling(target)
          } catch {
            statusRef.value = 'FAILED'
            messageRef.value = '重新转换失败'
          }
        }
      }
    } catch {
      stopPolling()
      statusRef.value = 'FAILED'
      messageRef.value = '查询转换状态失败'
    } finally {
      isRequesting = false
    }
  }, 3000)

  if (target === 'ppt') convertPollTimer = timer
  else wordConvertPollTimer = timer
}

/** 检查转换状态并决定是否需要触发 */
async function checkAndStartConversion(target: 'ppt' | 'word') {
  if (!fileData.value) return
  const statusRef = target === 'ppt' ? convertStatus : wordConvertStatus

  // 【安全拦截】如果当前已经是 PROCESSING（转换中）或 COMPLETED（已完成），直接拦截
  // 防止并发调用覆盖状态，解决竞态条件导致的 "正在准备文档环境..." 卡死问题
  if (statusRef.value === 'PROCESSING' || statusRef.value === 'COMPLETED') {
    return
  }

  // ✅ 核心修复 2.1：立即锁定状态为 PROCESSING，防止组件快速更新导致多次并发执行
  statusRef.value = 'PROCESSING'

  try {
    const res = await getConvertStatus(fileData.value.id)
    const data = res.data.data || res.data
    statusRef.value = data.status

    if (data.status === 'COMPLETED') {
      // 同步更新 fileData 的 previewStatus，使 OCR Tab 的 canShowOcr 计算属性立即生效
      if (fileData.value) {
        fileData.value = { ...fileData.value, previewStatus: 'COMPLETED', previewPdfPath: fileData.value.previewPdfPath || `__converted__${fileData.value.id}` }
      }
      await loadConvertedPdfWithPdfJs(fileData.value.id, target)
      // 关键修复：loadConvertedPdfWithPdfJs 失败（如 converted-pdf 404）后会把 statusRef 重置为 NOT_STARTED。
      // 此时需要主动触发转换，否则会卡在"正在准备文档环境"白屏状态。
      if (statusRef.value === 'NOT_STARTED') {
        if (target === 'ppt') {
          await startPptConversion()
        } else {
          await startWordConversion()
        }
      }
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

async function fetchFileData() {
  // 仅首次进入（无 fileData）时显示骨架屏；切换文件时保留旧内容直到新数据到达，避免白板闪烁
  const isFirstLoad = !fileData.value
  if (isFirstLoad) loading.value = true
  try {
    const res = await getFileDetail(fileId.value)
    fileData.value = res.data.data || res.data
    setWatermark(userStore.realName || userStore.username || '用户', previewContentRef.value)
    await loadPreviewContent()
    await fetchFavoriteStatus()
    loadOcrResult()
    loadExcelData()
  } catch {
    fileData.value = null
  } finally {
    if (isFirstLoad) loading.value = false
  }
}

async function loadPreviewContent() {
  if (!fileData.value) return
  const ft = fileData.value.fileType?.toLowerCase()

  if (ft === 'pdf') {
    await loadPdfWithPdfJs(fileData.value.id)
  } else if (ft === 'pptx' || ft === 'ppt') {
    await checkAndStartConversion('ppt')
  } else if (ft === 'docx' || ft === 'doc') {
    await checkAndStartConversion('word')
  } else if (isImage(ft)) {
    await loadImageBlob(fileData.value.id)
  } else if (isAudioVideo(ft)) {
    await loadMediaBlob(fileData.value.id)
  } else if (isTextFile(ft)) {
    await loadTextContent()
  }
}

/** 文本预览：使用服务端截断API */
async function loadTextContent() {
  if (!fileData.value) return
  const ft = fileData.value.fileType?.toLowerCase()
  try {
    const res = await getTextPreview(fileData.value.id)
    const data: TextPreviewData = res.data.data || res.data
    if (data.error) {
      textContent.value = '文件读取失败'
      return
    }
    textContent.value = data.content
    if (data.truncated) {
      textContent.value += '\n\n... 文件过大，仅显示前512KB内容，请下载查看完整文件'
    }
    if (ft === 'csv') {
      renderedMarkdown.value = csvToHtmlTable(textContent.value)
    } else if (ft === 'md') {
      renderedMarkdown.value = renderMarkdown(textContent.value)
    }
  } catch {
    // 降级：直接下载文件
    try {
      const res = await fetch(getPreviewUrl(fileData.value!.id), {
        headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
      })
      const fullText = await res.text()
      const MAX_PREVIEW_BYTES = 512 * 1024
      textContent.value = fullText.length > MAX_PREVIEW_BYTES
        ? fullText.substring(0, MAX_PREVIEW_BYTES) + '\n\n... 文件过大，仅显示前512KB内容，请下载查看完整文件'
        : fullText
      if (ft === 'csv') renderedMarkdown.value = csvToHtmlTable(textContent.value)
      else if (ft === 'md') renderedMarkdown.value = renderMarkdown(textContent.value)
    } catch { /* ignore */ }
  }
}

async function fetchTags() {
  try {
    const res = await getDocTags(fileId.value)
    docTags.value = res.data.data || res.data
  } catch { /* ignore */ }
}

async function fetchComments() {
  try {
    const res = await getComments(fileId.value)
    comments.value = res.data.data || res.data
  } catch { /* ignore */ }
}

async function fetchVersions() {
  try {
    const res = await getFileVersions(fileId.value)
    versions.value = res.data.data || res.data
  } catch { /* ignore */ }
}

async function handleDownload() {
  if (!fileData.value) return
  try {
    const res = await downloadFile(fileData.value.id)
    const blob = new Blob([res.data])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileData.value.fileName
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}

async function handleFavorite() {
  if (!fileData.value) return
  try {
    if (isFavorited.value) {
      await removeFavorite(fileData.value.id, 0)
      isFavorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(fileData.value.id, 0)
      isFavorited.value = true
      ElMessage.success('已收藏')
    }
  } catch {
    ElMessage.error('收藏操作失败')
  }
}

function handleShare() {
  showShareDialog.value = true
}

async function handleCreateShare() {
  if (!fileData.value) return
  try {
    const res = await createShareLink({
      fileId: fileData.value.id,
      permissionType: shareForm.value.permissionType,
      password: shareForm.value.password || null,
      maxAccess: shareForm.value.maxAccess,
      expireTime: shareForm.value.expireTime,
    })
    const data = res.data.data || res.data
    ElMessage.success(`分享链接已创建: ${window.location.origin}/s/${data.token}`)
    showShareDialog.value = false
  } catch {
    ElMessage.error('创建分享链接失败')
  }
}

const ocrText = ref('')
const ocrStatus = ref<string>('NOT_STARTED')
const ocrLoading = ref(false)
const ocrCurrentPage = ref<number | null>(null)
// 页级OCR缓存：key为 "fileId:page"，value为 { text, status }
const ocrPageCache = ref<Record<string, { text: string; status: string }>>({})
let ocrPollTimer: ReturnType<typeof setInterval> | null = null

/** 获取当前文件类型的当前页码 */
function getCurrentPageNumber(): number | null {
  if (!fileData.value) return null
  const ft = fileData.value.fileType?.toLowerCase()
  if (ft === 'pdf') return currentPage.value
  if (ft === 'pptx' || ft === 'ppt') return currentPptPage.value
  if (ft === 'docx' || ft === 'doc') return currentWordPage.value
  // 图片等无分页类型返回null
  return null
}

/** 判断文件类型是否支持分页OCR */
function isPageableFileType(): boolean {
  if (!fileData.value) return false
  const ft = fileData.value.fileType?.toLowerCase()
  return ['pdf', 'pptx', 'ppt', 'docx', 'doc'].includes(ft)
}

async function loadOcrResult() {
  if (!fileData.value) return
  ocrLoading.value = true
  try {
    // 分页文件：按当前页加载
    const page = isPageableFileType() ? getCurrentPageNumber() : undefined
    const res = await getOcrResult(fileData.value.id, page ?? undefined)
    const data = res.data.data || res.data
    ocrStatus.value = data.status || 'NOT_STARTED'
    ocrText.value = data.ocrText || ''
    ocrCurrentPage.value = page ?? null
    // 写入缓存
    if (page) {
      ocrPageCache.value[`${fileData.value.id}:${page}`] = { text: ocrText.value, status: ocrStatus.value }
    }
    if (data.status === 'PROCESSING' || data.status === 'PENDING') {
      startOcrPolling()
    }
  } catch {
    ocrStatus.value = 'NOT_STARTED'
  } finally {
    ocrLoading.value = false
  }
}

function startOcrPolling() {
  if (ocrPollTimer) clearInterval(ocrPollTimer)
  ocrPollTimer = setInterval(async () => {
    if (!fileData.value) { stopOcrPolling(); return }
    try {
      const page = isPageableFileType() ? getCurrentPageNumber() : undefined
      const res = await getOcrResult(fileData.value.id, page ?? undefined)
      const data = res.data.data || res.data
      ocrStatus.value = data.status || 'NOT_STARTED'
      ocrText.value = data.ocrText || ''
      if (page) {
        ocrPageCache.value[`${fileData.value.id}:${page}`] = { text: ocrText.value, status: ocrStatus.value }
      }
      if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'NOT_STARTED') {
        stopOcrPolling()
      }
    } catch {
      stopOcrPolling()
    }
  }, 2000)
}

function stopOcrPolling() {
  if (ocrPollTimer) {
    clearInterval(ocrPollTimer)
    ocrPollTimer = null
  }
}

async function triggerOcrExtraction() {
  if (!fileData.value) return
  try {
    const page = isPageableFileType() ? getCurrentPageNumber() : undefined
    await triggerOcr(fileData.value.id, page ?? undefined)
    ocrStatus.value = 'PROCESSING'
    ocrCurrentPage.value = page ?? null
    ElMessage.success(page ? `正在识别第 ${page} 页...` : 'OCR识别已触发，请稍后查看结果')
    startOcrPolling()
  } catch {
    ElMessage.error('OCR识别触发失败')
  }
}

/** 翻页时切换OCR缓存 */
function handleOcrPageChange() {
  if (!fileData.value || !isPageableFileType()) return
  const page = getCurrentPageNumber()
  if (!page) return
  const cacheKey = `${fileData.value.id}:${page}`
  const cached = ocrPageCache.value[cacheKey]
  if (cached) {
    // 有缓存，直接显示
    ocrText.value = cached.text
    ocrStatus.value = cached.status
    ocrCurrentPage.value = page
    stopOcrPolling()
  } else {
    // 无缓存，从服务端查询
    ocrText.value = ''
    ocrCurrentPage.value = page
    loadOcrResult()
  }
}

function handleCopyOcrText() {
  if (!ocrText.value) return
  navigator.clipboard.writeText(ocrText.value).then(() => {
    ElMessage.success('OCR文本已复制')
  })
}

async function triggerTagExtractionAction() {
  if (!fileData.value) return
  try {
    await triggerTagExtraction(fileData.value.id)
    ElMessage.success('AI标签提取已触发，正在后台处理...')
    pollForTags()
  } catch {
    ElMessage.error('标签提取失败')
  }
}

function pollForTags() {
  if (!fileId.value) return
  let attempts = 0
  const maxAttempts = 60
  const interval = 3000

  const timer = setInterval(async () => {
    if (!fileId.value) { clearInterval(timer); return }
    attempts++
    try {
      const res = await getFileTags(fileId.value)
      const tags = (res.data.data || res.data) as DocMetadata[]
      if (tags.length > 0) {
        clearInterval(timer)
        aiExtractedTags.value = tags
        showAiTagConfirm.value = true
        return
      }
    } catch { /* ignore */ }
    if (attempts >= maxAttempts) {
      clearInterval(timer)
      ElMessage.warning('标签提取超时，请稍后手动刷新')
    }
  }, interval)
}

async function handleConfirmAiTags() {
  try {
    await confirmAiTags(fileId.value)
    showAiTagConfirm.value = false
    aiExtractedTags.value = []
    ElMessage.success('AI标签已添加')
    fetchTags()
  } catch {
    ElMessage.error('确认标签失败')
  }
}

async function handleDismissAiTags() {
  try {
    await dismissAiTags(fileId.value)
    showAiTagConfirm.value = false
    aiExtractedTags.value = []
    ElMessage.info('已丢弃AI标签')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleAddComment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  commentLoading.value = true
  try {
    const content = commentText.value.trim()
    const atPattern = /@([\u4e00-\u9fa5a-zA-Z0-9_]+)/g
    let match: RegExpExecArray | null
    while ((match = atPattern.exec(content)) !== null) {
      const mentionName = match[1]
      const found = allUsers.value.find(u =>
        u.realName === mentionName || u.username === mentionName
      )
      if (found) {
        mentionedUserIds.value.add(found.id)
      }
    }

    const mentionsStr = mentionedUserIds.value.size > 0
      ? Array.from(mentionedUserIds.value).join(',')
      : null
    const payload = {
      fileId: fileId.value,
      content: content,
      mentions: mentionsStr,
      quoteText: selectedQuoteText.value || null
    }
    console.log('[评论] 发送评论:', JSON.stringify(payload))
    await addComment(payload)
    commentText.value = ''
    mentionedUserIds.value.clear()
    showMentionList.value = false
    selectedQuoteText.value = null
    showAnnotationBtn.value = false
    ElMessage.success('评论已发送')
    fetchComments()
  } catch (err) {
    console.error('[评论] 发送失败:', err)
    ElMessage.error('评论发送失败')
  } finally {
    commentLoading.value = false
  }
}

function onCommentVoiceRecognized(text: string) {
  commentText.value = text
}

function handleTextSelection(e: MouseEvent) {
  const selection = window.getSelection()
  if (!selection || selection.isCollapsed || !selection.toString().trim()) {
    showAnnotationBtn.value = false
    pendingQuoteText.value = null
    return
  }
  const text = selection.toString().trim()
  if (text.length < 2) {
    showAnnotationBtn.value = false
    pendingQuoteText.value = null
    return
  }
  pendingQuoteText.value = text

  const range = selection.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  const mainEl = (e.currentTarget as HTMLElement)
  const mainRect = mainEl.getBoundingClientRect()
  annotationBtnStyle.value = {
    top: `${rect.top - mainRect.top - 40}px`,
    left: `${rect.left - mainRect.left + rect.width / 2 - 36}px`
  }
  showAnnotationBtn.value = true
}

function handleAnnotateSelection() {
  if (!pendingQuoteText.value) {
    return
  }
  selectedQuoteText.value = pendingQuoteText.value
  pendingQuoteText.value = null
  showAnnotationBtn.value = false
  window.getSelection()?.removeAllRanges()
}

function clearQuoteSelection() {
  selectedQuoteText.value = null
}

function handleCommentInput() {
  const text = commentText.value
  const cursorPos = text.length
  const textBeforeCursor = text.substring(0, cursorPos)
  const atIndex = textBeforeCursor.lastIndexOf('@')

  console.log('[评论@调试] 输入内容:', text, '光标位置:', cursorPos, '@位置:', atIndex)
  console.log('[评论@调试] allUsers数量:', allUsers.value.length)

  if (atIndex !== -1) {
    const textAfterAt = textBeforeCursor.substring(atIndex + 1)
    const spaceIdx = textAfterAt.indexOf(' ')
    const searchWord = spaceIdx !== -1 ? textAfterAt.substring(0, spaceIdx) : textAfterAt
    console.log('[评论@调试] @后面的文本:', textAfterAt, '搜索词:', searchWord)

    if (!textAfterAt.includes('\n')) {
      mentionSearch.value = searchWord
      showMentionList.value = true
      mentionIndex.value = 0
      console.log('[评论@调试] 显示下拉列表, filteredMentionUsers数量:', filteredMentionUsers.value.length)
      return
    }
  }
  showMentionList.value = false
}

function handleCommentKeydown(e: KeyboardEvent) {
  if (showMentionList.value && filteredMentionUsers.value.length > 0) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      mentionIndex.value = (mentionIndex.value + 1) % filteredMentionUsers.value.length
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      mentionIndex.value = (mentionIndex.value - 1 + filteredMentionUsers.value.length) % filteredMentionUsers.value.length
      return
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      selectMentionUser(filteredMentionUsers.value[mentionIndex.value])
      return
    }
    if (e.key === 'Escape') {
      showMentionList.value = false
      return
    }
  }
}

function selectMentionUser(user: SimpleUser) {
  const text = commentText.value
  const cursorPos = text.length
  const textBeforeCursor = text.substring(0, cursorPos)
  const atIndex = textBeforeCursor.lastIndexOf('@')
  if (atIndex !== -1) {
    const displayName = user.realName || user.username
    const textAfterAt = textBeforeCursor.substring(atIndex + 1)
    const spaceIdx = textAfterAt.indexOf(' ')
    const replaceEnd = spaceIdx !== -1 ? atIndex + 1 + spaceIdx : cursorPos
    commentText.value = text.substring(0, atIndex) + '@' + displayName + ' ' + text.substring(replaceEnd)
    mentionedUserIds.value.add(user.id)
    console.log('[评论@调试] 选中用户:', displayName, '已添加到mentionedUserIds:', Array.from(mentionedUserIds.value))
  }
  showMentionList.value = false
  mentionSearch.value = ''
}

function renderCommentContent(content: string): string {
  if (!content) return ''
  const rendered = renderMarkdown(content)
  return rendered.replace(/@([\u4e00-\u9fa5a-zA-Z0-9_]+)/g, '<span class="mention-highlight">@$1</span>')
}

/**
 * 判断当前用户是否可以删除指定评论
 * canDelete 由后端计算，综合考虑：
 * - 评论作者本人（24小时内）
 * - 文档所有者可删除该文档下任意评论
 * - ROLE_ADMIN 可删除任意评论
 * - ROLE_DEPT_ADMIN 可删除本部门文档下的评论
 */
function canDeleteComment(comment: CommentDTO): boolean {
  return comment.canDelete === true
}

async function handleDeleteComment(comment: CommentDTO) {
  const isAuthor = comment.userId === userStore.userId
  const confirmMsg = isAuthor
    ? '确定要删除这条评论吗？发布超过24小时后将无法删除。'
    : '确定要删除这条评论吗？'
  await ElMessageBox.confirm(confirmMsg, '删除评论', { type: 'warning' })
  try {
    await deleteComment(comment.id)
    ElMessage.success('评论已删除')
    fetchComments()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '删除评论失败'
    console.error('[评论] 删除失败:', msg)
    ElMessage.error(msg)
  }
}

async function fetchAllUsers() {
  if (!fileId.value || fileId.value <= 0) return
  try {
    const res = await getMentionableUsers(fileId.value)
    allUsers.value = (res.data.data || res.data) as SimpleUser[]
  } catch (e) {
    console.error('[评论@] 获取可@用户列表失败:', e)
    allUsers.value = []
  }
}

async function loadExcelData() {
  if (!fileData.value) return
  const ft = fileData.value.fileType
  if (ft !== 'xlsx' && ft !== 'xls') return
  excelLoading.value = true
  // 自动重试：首次加载可能因文件同步延迟失败，静默重试一次
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await getExcelData(fileId.value)
      const data = res.data.data || res.data
      if (data.error) {
        if (attempt === 0) {
          // 首次失败，等待 1 秒后重试
          await new Promise(resolve => setTimeout(resolve, 1000))
          continue
        }
        excelSheets.value = []
      } else {
        excelSheets.value = data.sheets || []
        excelActiveSheet.value = 0
      }
      break
    } catch {
      if (attempt === 0) {
        await new Promise(resolve => setTimeout(resolve, 1000))
        continue
      }
      excelSheets.value = []
    }
  }
  excelLoading.value = false
}

function getColumnName(index: number): string {
  let name = ''
  let n = index
  while (n >= 0) {
    name = String.fromCharCode(65 + (n % 26)) + name
    n = Math.floor(n / 26) - 1
  }
  return name
}

async function handleAddTag() {
  const key = newTagKey.value.trim()
  const val = newTagValue.value.trim()
  if (!key) {
    ElMessage.warning('请输入标签键')
    return
  }
  if (!val) {
    ElMessage.warning('请输入标签值')
    return
  }
  const tagName = `${key}: ${val}`
  try {
    await addDocTag(fileId.value, tagName)
    newTagKey.value = ''
    newTagValue.value = ''
    showAddTag.value = false
    ElMessage.success('标签已添加')
    fetchTags()
  } catch {
    ElMessage.error('添加标签失败')
  }
}

async function handleDeleteTag(tagId: number) {
  try {
    await deleteDocTag(tagId)
    ElMessage.success('标签已删除')
    fetchTags()
  } catch {
    ElMessage.error('删除标签失败')
  }
}

/** 删除标签前确认 */
async function handleDeleteTagConfirm(tag: DocTag) {
  try {
    await ElMessageBox.confirm(
      `确定要删除标签「${tag.tagName}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await handleDeleteTag(tag.id)
  } catch { /* 用户取消 */ }
}

function handleEditTag(tag: DocTag) {
  editingTagId.value = tag.id
  // 解析 "key: value" 格式
  const idx = tag.tagName.indexOf(':')
  if (idx > -1) {
    editTagKey.value = tag.tagName.substring(0, idx).trim()
    editTagValue.value = tag.tagName.substring(idx + 1).trim()
  } else {
    editTagKey.value = tag.tagName
    editTagValue.value = ''
  }
  showEditTag.value = true
}

async function handleUpdateTag() {
  const key = editTagKey.value.trim()
  const val = editTagValue.value.trim()
  if (!key) {
    ElMessage.warning('请输入标签键')
    return
  }
  if (!val) {
    ElMessage.warning('请输入标签值')
    return
  }
  if (editingTagId.value === null) return
  const tagName = `${key}: ${val}`
  try {
    await updateDocTag(editingTagId.value, tagName)
    editTagKey.value = ''
    editTagValue.value = ''
    editingTagId.value = null
    showEditTag.value = false
    ElMessage.success('标签已更新')
    fetchTags()
  } catch {
    ElMessage.error('更新标签失败')
  }
}

/** 点击文件标签跳转到搜索页并搜索该标签 */
function handleFileTagClick(tagName: string) {
  router.push({ name: 'Search', query: { tag: tagName } })
}

async function handleRollback(targetVersion: number) {
  if (!fileData.value) return
  try {
    await rollbackVersion(fileData.value.id, targetVersion)
    ElMessage.success(`已回滚到 V${targetVersion}`)
    fetchFileData()
    fetchVersions()
    // 回滚后刷新预览
    refreshAfterVersionUpdate()
  } catch {
    ElMessage.error('回滚失败')
  }
}

async function fetchFavoriteStatus() {
  if (!fileData.value) return
  try {
    const res = await checkFavorite(fileData.value.id, 0)
    isFavorited.value = res.data.data?.favorited || false
  } catch { isFavorited.value = false }
}

function formatDate(timeStr?: string | null) {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  // 【修改说明】移除 onMounted 中的重复数据获取调用
  // 原因：onMounted 和 watch(fileId) 会同时触发，导致 fetchFileData() 并发执行两次
  // 引发竞态条件（Race Condition），使 wordConvertStatus 被覆盖为 NOT_STARTED
  // 解决方案：统一使用 watch(fileId, { immediate: true }) 处理初始化和路由切换
  window.addEventListener('click', closePageSelect)
})

// keep-alive 激活时：如果 pdfDoc 还在，重新渲染当前页（canvas 可能被清空）
// 注意：如果 watch(fileId) 已通过缓存恢复并渲染，此处跳过，避免重复渲染
let cacheRestored = false

onActivated(async () => {
  if (!fileData.value) return
  // 缓存恢复时 watch(fileId) 已处理渲染，跳过
  if (cacheRestored) {
    cacheRestored = false
    return
  }
  const ft = fileData.value.fileType?.toLowerCase()
  // 重新设置水印（DOM 重建后水印丢失）
  setWatermark(userStore.realName || userStore.username || '用户', previewContentRef.value)
  // 重新渲染当前页 canvas（keep-alive 激活时不弹进度提示，避免与打开文件时重复）
  await nextTick()
  if (ft === 'pdf' && pdfDoc) {
    await renderPdfPageToCanvas(pdfDoc, currentPage.value, pdfCanvasRef, 1.5, pdfTextLayerRef)
  } else if ((ft === 'pptx' || ft === 'ppt') && pptDoc) {
    await renderPdfPageToCanvas(pptDoc, currentPptPage.value, pptCanvasRef, 1.5, pptTextLayerRef)
  } else if ((ft === 'docx' || ft === 'doc') && wordDoc) {
    await renderPdfPageToCanvas(wordDoc, currentWordPage.value, wordCanvasRef, 1.5, wordTextLayerRef)
  }
})

// keep-alive 停用时：保存进度、缓存状态、停止轮询
onDeactivated(() => {
  saveReadingProgress()
  saveCurrentFileToCache()
  // keep-alive 缓存时清除水印，避免水印元素残留在 DOM 中
  clearWatermark()
  stopOcrPolling()
  if (convertPollTimer) { clearInterval(convertPollTimer); convertPollTimer = null }
  if (wordConvertPollTimer) { clearInterval(wordConvertPollTimer); wordConvertPollTimer = null }
  if (mediaTimeSaveTimer) { clearTimeout(mediaTimeSaveTimer); mediaTimeSaveTimer = null }
  // 核心修复：停用时重置缓存标志，防止下次激活时残留状态导致渲染跳过
  cacheRestored = false
})

onUnmounted(() => {
  clearWatermark()
  saveReadingProgress()
  revokeImageBlob()
  revokeMediaBlob()
  pdfDoc?.destroy()
  pptDoc?.destroy()
  wordDoc?.destroy()
  pdfDoc = null
  pptDoc = null
  wordDoc = null
  // 清理所有缓存的 pdf.js 文档对象
  for (const [, entry] of fileCacheMap) {
    entry.pdfDoc?.destroy()
    entry.pptDoc?.destroy()
    entry.wordDoc?.destroy()
    if (entry.imageBlobUrl) URL.revokeObjectURL(entry.imageBlobUrl)
  }
  fileCacheMap.clear()
  if (convertPollTimer) { clearInterval(convertPollTimer); convertPollTimer = null }
  if (wordConvertPollTimer) { clearInterval(wordConvertPollTimer); wordConvertPollTimer = null }
  // 清理定时器，防止内存泄露
  stopOcrPolling()
  stopMindmapPolling()
  if (mediaTimeSaveTimer) { clearTimeout(mediaTimeSaveTimer); mediaTimeSaveTimer = null }
  if (viewRecordTimer) { clearTimeout(viewRecordTimer); viewRecordTimer = null }
  debouncedSaveProgress.cancel()
  window.removeEventListener('click', closePageSelect)
})

// ==================== 任务三：长文档一键脑图生成 ====================
const mindmapLoading = ref(false)
const mindmapError = ref('')
const mindmapLoadingText = ref('正在调用 AI 分析文档结构...')
const mindmapSidebarVisible = ref(false)
const mindmapContainerRef = ref<HTMLElement | null>(null)
const mindmapContent = ref('')
// 复用 AI 助手页面的模型列表（与 stores/ai.ts 保持一致）
const mindmapModel = ref('glm-4.7-flash')
const mindmapModelOptions = [
  { label: 'MiMo-V2.5-Pro', value: 'mimo-v2.5-pro' },
  { label: 'MiMo-V2.5', value: 'mimo-v2.5' },
  { label: 'DeepSeek-V4-Flash', value: 'deepseek-v4-flash' },
  { label: 'DeepSeek-V4-Pro', value: 'deepseek-v4-pro' },
  { label: 'GLM-4.7-Flash', value: 'glm-4.7-flash' },
]
let mindmapInstance: any = null
let mindmapPollTimer: ReturnType<typeof setInterval> | null = null
// 控制脑图是否处于宽屏沉浸模式
const isMindmapExpanded = ref(false)

// 脑图多页码选择器状态
const showPageSelect = ref(false)
const pageSelectPos = ref({ top: '0px', left: '0px' })
const availablePages = ref<number[]>([])

// 点击页面其他区域关闭浮窗
const closePageSelect = () => { showPageSelect.value = false }

watch(fileId, (newId) => {
  if (!newId) return
  // keep-alive 场景：返回文件列表再进入同一文件时，fileId 先变 0 再变回原值，
  // 如果是同一个文件且数据已加载，跳过重新加载，实现零延迟恢复
  if (newId === lastLoadedFileId && fileData.value) return

  // 切换文件前，保存当前文件状态到缓存（不销毁 pdfDoc，缓存中复用）
  saveCurrentFileToCache()

  // 重置脑图状态：每个文件独立一份，切换文件时清空当前脑图显示
  mindmapContent.value = ''
  mindmapLoading.value = false
  mindmapError.value = ''
  mindmapSidebarVisible.value = false
  mindmapInstance = null
  isMindmapExpanded.value = false
  stopMindmapPolling()

  // 记录当前加载的文件ID
  lastLoadedFileId = newId

  // 停止当前轮询
  if (convertPollTimer) { clearInterval(convertPollTimer); convertPollTimer = null }
  if (wordConvertPollTimer) { clearInterval(wordConvertPollTimer); wordConvertPollTimer = null }
  stopOcrPolling()
  // 清除浏览计数定时器
  if (viewRecordTimer) { clearTimeout(viewRecordTimer); viewRecordTimer = null }

  // 尝试从缓存恢复
  if (restoreFileFromCache(newId)) {
    // 缓存命中：零延迟恢复，只需重新渲染 canvas
    cacheRestored = true
    loading.value = false
    setWatermark(userStore.realName || userStore.username || '用户', previewContentRef.value)
    // 异步渲染 canvas + 刷新辅助数据（缓存恢复不走加载函数，不再重复提示）
    nextTick().then(async () => {
      const ft = fileData.value?.fileType?.toLowerCase()
      if (ft === 'pdf' && pdfDoc) {
        await renderPdfPageToCanvas(pdfDoc, currentPage.value, pdfCanvasRef, 1.5, pdfTextLayerRef)
      } else if ((ft === 'pptx' || ft === 'ppt') && pptDoc) {
        await renderPdfPageToCanvas(pptDoc, currentPptPage.value, pptCanvasRef, 1.5, pptTextLayerRef)
      } else if ((ft === 'docx' || ft === 'doc') && wordDoc) {
        await renderPdfPageToCanvas(wordDoc, currentWordPage.value, wordCanvasRef, 1.5, wordTextLayerRef)
      }
    })
    // 后台刷新辅助数据（标签、评论等可能变化）
    fetchTags()
    fetchComments()
    fetchVersions()
    fetchAllUsers()
    return
  }

  // 缓存未命中：正常加载
  // 注意：不清空 fileData，让旧文件内容保留显示直到新文件数据到达，避免白板闪烁
  wordConvertStatus.value = 'NOT_STARTED'
  convertStatus.value = 'NOT_STARTED'
  textContent.value = ''
  excelSheets.value = []
  excelActiveSheet.value = 0

  // 清理 OCR 状态，防止切换文件时"幽灵数据"串屏
  ocrText.value = ''
  ocrStatus.value = 'NOT_STARTED'
  ocrPageCache.value = {}
  stopOcrPolling()

  // 当前文件的 pdfDoc 已保存到缓存中，这里只清空引用，不 destroy（缓存中还要复用）
  pdfDoc = null
  pptDoc = null
  wordDoc = null

  // 执行单次加载（fetchFileData 内部会判断是否首次加载以决定是否显示骨架屏）
  fetchFileData()
  fetchTags()
  fetchComments()
  fetchVersions()
  fetchAllUsers()
  fetchRelatedFiles()

  // 停留 10 秒后记录一次浏览
  if (viewRecordTimer) { clearTimeout(viewRecordTimer); viewRecordTimer = null }
  viewRecordTimer = setTimeout(() => {
    if (fileId.value && !isEmbedMode.value) {
      recordFileView(fileId.value).catch(() => { /* ignore */ })
    }
  }, 10000)
}, { immediate: true })

// PDF翻页
watch(currentPage, async (newPage) => {
  if (isInitializing) return
  if (!pdfDoc || newPage < 1 || newPage > pdfTotalPages.value) return
  await renderPdfPageToCanvas(pdfDoc, newPage, pdfCanvasRef, 1.5, pdfTextLayerRef)
  if (fileData.value?.id) debouncedSaveProgress(fileData.value.id, 1, newPage)
  handleOcrPageChange()
})

function handleJumpPage() {
  const p = jumpPageNum.value
  if (p >= 1 && p <= pdfTotalPages.value) {
    currentPage.value = p
  }
}

function handleJumpPptPage() {
  const p = jumpPptPageNum.value
  if (p >= 1 && p <= pptTotalPages.value) {
    currentPptPage.value = p
  }
}

function handleJumpWordPage() {
  const p = jumpWordPageNum.value
  if (p >= 1 && p <= wordTotalPages.value) {
    currentWordPage.value = p
  }
}

// PPT翻页
watch(currentPptPage, async (newPage) => {
  if (isInitializing) return
  if (!pptDoc || newPage < 1 || newPage > pptTotalPages.value) return
  await renderPdfPageToCanvas(pptDoc, newPage, pptCanvasRef, 1.5, pptTextLayerRef)
  if (fileData.value?.id) debouncedSaveProgress(fileData.value.id, 1, newPage)
  handleOcrPageChange()
})

// Word翻页
watch(currentWordPage, async (newPage) => {
  if (isInitializing) return
  if (!wordDoc || newPage < 1 || newPage > wordTotalPages.value) return
  await renderPdfPageToCanvas(wordDoc, newPage, wordCanvasRef, 1.5, wordTextLayerRef)
  if (fileData.value?.id) debouncedSaveProgress(fileData.value.id, 1, newPage)
  handleOcrPageChange()
})

/** 切换右侧脑图侧边栏显示/隐藏 */
async function toggleMindmapSidebar() {
  const willShow = !showRightSidebar.value || !mindmapSidebarVisible.value
  if (willShow) {
    // 展开：确保右侧边栏和脑图模块都显示
    showRightSidebar.value = true
    mindmapSidebarVisible.value = true
    if (!mindmapContent.value && !mindmapLoading.value && fileData.value?.id) {
      // 打开侧边栏时尝试加载已保存的脑图（不触发 AI 生成）
      await loadSavedMindmap()
    }
  } else {
    // 已展开，则收起脑图模块
    mindmapSidebarVisible.value = false
  }
}

/** 用户主动点击"AI 智能生成脑图"时触发（真正的扣费请求） */
async function requestGenerateMindmap() {
  await handleGenerateMindmap(false)
}

/** 重新生成脑图（强制忽略缓存） */
async function handleRegenerateMindmap() {
  await handleGenerateMindmap(true)
}

/** 加载已保存的脑图（从数据库读取，不触发 AI 生成） */
async function loadSavedMindmap() {
  if (!fileData.value?.id || mindmapContent.value || mindmapLoading.value) return
  try {
    const res = await getSavedMindmap(fileData.value.id)
    const saved = res.data?.data
    if (saved) {
      // 检测失败状态：后端将失败信息也存入了数据库
      if (saved.startsWith('# 脑图生成失败') || saved.startsWith('# AI 服务未配置')) {
        mindmapError.value = saved
        return
      }
      mindmapContent.value = saved
      await nextTick()
      await renderMindmap(saved)
    }
  } catch { /* 忽略，用户可手动生成 */ }
}

/**
 * 触发脑图生成
 * 优先提交到 RabbitMQ 异步处理，MQ 不可用时降级为同步生成
 * @param force 是否强制重新生成（忽略缓存）
 */
async function handleGenerateMindmap(force = false) {
  if (!fileData.value?.id) {
    ElMessage.warning('请先选择文件')
    return
  }
  mindmapLoading.value = true
  mindmapError.value = ''
  mindmapContent.value = ''
  mindmapLoadingText.value = '正在提交脑图生成任务...'
  stopMindmapPolling()

  // 动态文案：让用户感知到后台正在分阶段处理
  let loadingPhase = 0
  const loadingTexts = [
    '正在读取文档内容...',
    '正在调用 AI 分析文档结构...',
    'AI 正在生成脑图节点...',
    '正在优化脑图布局，即将完成...'
  ]
  const loadingTimer = setInterval(() => {
    loadingPhase = Math.min(loadingPhase + 1, loadingTexts.length - 1)
    mindmapLoadingText.value = loadingTexts[loadingPhase]
  }, 8000)

  try {
    const res = await submitMindmapTask(fileData.value.id, mindmapModel.value, force)
    const result = res.data?.data || res.data
    const mode = result?.mode

    if (!mode) {
      mindmapError.value = '脑图任务提交失败，未返回任务模式'
      return
    }

    if (mode === 'cached' || mode === 'sync') {
      // 已缓存或同步完成，直接渲染
      const content = result?.content || ''
      if (!content || content.startsWith('# 脑图生成失败') || content.startsWith('# AI 服务未配置')) {
        mindmapError.value = content || '脑图生成失败'
        return
      }
      mindmapContent.value = content
      await renderMindmapAfterNextTick(content)
    } else {
      // MQ 异步模式：启动轮询
      mindmapLoadingText.value = '脑图生成任务已提交，正在后台处理...'
      startMindmapPolling(fileData.value.id)
    }
  } catch (e: any) {
    mindmapError.value = e?.message || '脑图生成失败'
  } finally {
    clearInterval(loadingTimer)
    // MQ 模式下由轮询结束后再关闭 loading
    if (!mindmapPollTimer) {
      mindmapLoading.value = false
    }
  }
}

/** 等待容器挂载后渲染脑图 */
async function renderMindmapAfterNextTick(content: string) {
  // 等待 v-else 分支的容器 DOM 挂载完成（loading 关闭后容器才出现）
  mindmapLoading.value = false
  await nextTick()
  await nextTick()
  // 确保容器已挂载再渲染
  if (!mindmapContainerRef.value) {
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  await renderMindmap(content)
}

/** 启动脑图生成状态轮询 */
function startMindmapPolling(fileId: number) {
  stopMindmapPolling()
  let attempts = 0
  const maxAttempts = 160 // 最多轮询 160 * 3s = 8 分钟（大文档+429重试可能需要较长时间）

  mindmapPollTimer = setInterval(async () => {
    attempts++
    if (attempts > maxAttempts) {
      stopMindmapPolling()
      mindmapLoading.value = false
      mindmapError.value = '脑图生成超时，请稍后手动刷新'
      return
    }
    try {
      const res = await getMindmapTaskStatus(fileId)
      const data = res.data?.data || res.data
      const status = data?.status

      if (status === 'completed') {
        stopMindmapPolling()
        const content = data?.content || ''
        mindmapContent.value = content
        await renderMindmapAfterNextTick(content)
      } else if (status === 'failed') {
        stopMindmapPolling()
        mindmapLoading.value = false
        mindmapError.value = data?.failReason || '脑图生成失败'
      }
      // pending 状态继续轮询
    } catch (e: any) {
      // 轮询出错不立即停止，继续尝试
      console.warn('[Mindmap] 轮询状态失败:', e?.message)
    }
  }, 3000)
}

/** 停止脑图生成状态轮询 */
function stopMindmapPolling() {
  if (mindmapPollTimer) {
    clearInterval(mindmapPollTimer)
    mindmapPollTimer = null
  }
}

/**
 * 使用 Markmap 渲染脑图
 * 动态导入 markmap 库，避免打包体积过大
 * 渲染后为每个含 <!-- page: N --> 的节点绑定点击事件，实现跳转到对应页
 */
async function renderMindmap(markdown: string) {
  if (!mindmapContainerRef.value) {
    console.warn('[Mindmap] 容器未挂载，跳过渲染')
    return
  }
  // 清空容器
  mindmapContainerRef.value.innerHTML = ''
  mindmapInstance = null

  try {
    // 动态导入 markmap
    const { Transformer } = await import('markmap-lib')
    const { Markmap } = await import('markmap-view')

    const transformer = new Transformer()
    const { root } = transformer.transform(markdown)

    // 核心修改 1：画布大小 100% 自适应容器
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    svg.style.width = '100%'
    svg.style.height = '100%'
    svg.style.display = 'block'
    mindmapContainerRef.value.appendChild(svg)

    // 渲染脑图到 svg
    mindmapInstance = Markmap.create(svg, {
      maxWidth: 400,
      duration: 300,
      spacingHorizontal: 80,
      spacingVertical: 15,
      paddingX: 16,
      autoFit: false, // 核心修改 2：关闭自动乱缩放
    }, root)

    // 核心修改 3：D3 智能相机定位（0.85x 缩放 + 根节点左中央）
    await nextTick()
    setTimeout(() => {
      if (mindmapInstance && mindmapContainerRef.value) {
        const { zoom, svg: d3Svg } = mindmapInstance
        const containerWidth = mindmapContainerRef.value.clientWidth
        const containerHeight = mindmapContainerRef.value.clientHeight
        const targetScale = 0.85
        const translateX = containerWidth * 0.1
        const translateY = containerHeight / 2

        d3Svg.transition().duration(600).call(
          zoom.transform,
          d3.zoomIdentity.translate(translateX, translateY).scale(targetScale)
        )
      }
      bindMindmapClickEvents()
    }, 100)
  } catch (e: any) {
    console.error('[Mindmap] 渲染失败:', e)
    mindmapError.value = '脑图渲染失败：' + (e?.message || '未知错误')
  }
}

/**
 * 为脑图节点绑定点击事件，实现"点击节点跳转到对应页"
 * 使用 d3 命名空间事件绑定，保留 markmap 默认折叠/展开行为
 * 页码格式：[P6] / [P11-12] / [P62, P66, P68, P70] / 【P6】
 */
function bindMindmapClickEvents() {
  if (!mindmapContainerRef.value) return
  const svg = mindmapContainerRef.value.querySelector('svg')
  if (!svg) return

  // 增强版正则：允许括号内部出现多个 P/p，例如 [P89, P91] 或 [P62, P66, P68-70]
  const pageRegex = /(?:\[|【)\s*([0-9\s,\-、pP]+)(?:\]|】)/i

  const selection = d3.select(svg)
  const nodes = selection.selectAll('g.markmap-node')

  // 1. 绑定点击事件：使用 d3.select(this).text() 直接抓取 SVG 上显示的文本
  nodes.on('click.pageJump', null) // 先解绑旧事件，防止重复触发
  nodes.on('click.pageJump', function(event: MouseEvent, _d: any) {
    const textContent = d3.select(this).text() || ''
    console.log('[Mindmap] 用户点击了节点文字:', textContent)

    const match = String(textContent).match(pageRegex)
    if (match && match[1]) {
      // 匹配到了页码，拦截原本的折叠事件
      event.stopPropagation()
      event.preventDefault()

      const pageStr = match[1]
      // 解析所有页码：根据逗号或顿号拆分
      const parts = pageStr.split(/[,、]/)
      const pages: number[] = []

      parts.forEach(part => {
        // 核心修复：过滤掉所有的 P、p 字母和空格，只提取纯数字
        const p = part.replace(/[pP\s]/g, '')
        if (!p) return
        if (p.includes('-')) {
          // 如果是 "60-61" 的范围，提取起始页 60
          const start = parseInt(p.split('-')[0], 10)
          if (!isNaN(start)) pages.push(start)
        } else {
          const num = parseInt(p, 10)
          if (!isNaN(num)) pages.push(num)
        }
      })

      // 去重
      const uniquePages = [...new Set(pages)]

      if (uniquePages.length === 1) {
        // 只有一个页码，直接跳转
        jumpToMindmapPage(uniquePages[0])
        showPageSelect.value = false
      } else if (uniquePages.length > 1) {
        // 多个页码，在鼠标点击位置弹出悬浮选择器
        availablePages.value = uniquePages
        pageSelectPos.value = {
          top: (event.clientY + 15) + 'px',
          left: event.clientX + 'px'
        }
        showPageSelect.value = true
      }
    }
  })

  // 2. 为带有页码的节点添加可点击样式与提示
  nodes.each(function() {
    const textContent = d3.select(this).text() || ''
    if (pageRegex.test(textContent)) {
      const node = d3.select(this)
      node.classed('mindmap-node-clickable', true)
      node.style('cursor', 'pointer')

      // 让文字部分响应鼠标事件
      const textEl = node.select('text')
      if (!textEl.empty()) {
        textEl.style('pointer-events', 'all')
        textEl.attr('title', '包含页面链接，点击跳转')
      }
    }
  })
}

/**
 * 跳转到指定页（脑图节点点击触发）
 * 复用现有的翻页逻辑：PDF 直接设置 currentPage，Word/PPT 通过对应 ref 翻页
 * 安全兜底：AI 生成的页码可能超出实际范围，自动 clamp 到有效区间
 */
function jumpToMindmapPage(page: number) {
  const ft = fileData.value?.fileType
  if (ft === 'pdf') {
    // clamp 到有效范围，避免 AI 生成超出总页数的页码
    const target = Math.max(1, Math.min(page, pdfTotalPages.value))
    if (target !== page) {
      ElMessage.warning(`第 ${page} 页超出范围，已跳转到最接近的第 ${target} 页（共 ${pdfTotalPages.value} 页）`)
    } else {
      ElMessage.success(`已跳转到第 ${target} 页`)
    }
    currentPage.value = target
  } else if (ft === 'doc' || ft === 'docx' || ft === 'ppt' || ft === 'pptx') {
    // Word/PPT 预览基于转换后的 PDF，复用 currentWordPage
    const total = ft === 'ppt' || ft === 'pptx' ? pptTotalPages.value : wordTotalPages.value
    const target = Math.max(1, Math.min(page, total))
    if (target !== page) {
      ElMessage.warning(`第 ${page} 页超出范围，已跳转到最接近的第 ${target} 页（共 ${total} 页）`)
    } else {
      ElMessage.success(`已跳转到第 ${target} 页`)
    }
    if (ft === 'ppt' || ft === 'pptx') {
      currentPptPage.value = target
    } else {
      currentWordPage.value = target
    }
  } else {
    ElMessage.info(`该文件类型不支持页码跳转，目标页：${page}`)
  }
}

// 脑图已从侧边栏 Tab 改为全屏对话框，无需监听 Tab 切换
</script>

<style scoped>
.file-preview {
  height: calc(100vh - 96px);
  display: flex;
  flex-direction: column;
  background-color: #f7f8fa;
  padding: 16px;
  box-sizing: border-box;
  overflow: hidden;
  gap: 16px;
}

/* 嵌入模式：隐藏头部和侧栏，只显示文件内容和翻页 */
.file-preview.embed-mode {
  height: 100%;
}
.file-preview.embed-mode .preview-body {
  gap: 0;
}
.file-preview.embed-mode .preview-main {
  border-radius: 0;
  padding: 0;
  box-shadow: none;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.03);
  flex-shrink: 0;
  margin-bottom: 0;
}

.preview-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-filename {
  font-size: 18px;
  font-weight: 600;
  color: #1d1e23;
  letter-spacing: 0.3px;
}

.version-tag {
  border-radius: 4px;
  padding: 0 8px;
  font-family: 'Monaco', 'Consolas', monospace;
}

.version-badge {
  font-weight: 700;
  color: #409EFF;
}

.preview-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  position: relative;
}

.preview-main {
  flex: 1;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.03);
  overflow: hidden;
  display: flex;
  position: relative;
  transition: all 0.3s ease;
}

.preview-main-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  align-items: stretch;
  justify-content: flex-start;
  transition: opacity 0.2s ease;
  position: relative;
  overflow: hidden;
}

.preview-main-content.is-loading {
  opacity: 0;
  pointer-events: none;
}

.preview-skeleton {
  padding: 40px;
}

.sidebar-toggle-zone {
  position: absolute;
  top: 0;
  bottom: 0;
  right: 0;
  width: 24px;
  pointer-events: none;
  z-index: 100;
}

.sidebar-toggle-fab {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  width: 20px;
  height: 56px;
  border: 1px solid #e4e7ed;
  border-right: none;
  border-radius: 6px 0 0 6px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  color: #909399;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.04);
  pointer-events: auto;
  transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
  overflow: hidden;
}

.sidebar-toggle-fab:hover {
  width: 32px;
  height: 64px;
  color: #409eff;
  background: #ffffff;
  box-shadow: -6px 0 16px rgba(0, 0, 0, 0.08);
}

.sidebar-toggle-fab .toggle-arrow {
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.sidebar-toggle-fab.is-collapsed .toggle-arrow {
  transform: rotate(180deg);
}

.sidebar-toggle-fab .toggle-tip {
  display: none;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  min-height: 600px;
}

.preview-pdf, .preview-word, .preview-pptx {
  flex: 1;
  width: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.pdf-viewer-container {
  width: 100%;
  height: 100%;
  display: block;
  overflow: auto;
  background: #f2f3f5;
  position: relative;
}

.pdf-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 12px 0;
  background: rgba(247, 248, 250, 0.95);
  backdrop-filter: blur(4px);
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.pdf-canvas-container {
  display: block;
  min-width: 100%;
  width: max-content;
  text-align: center;
  padding: 20px;
  box-sizing: border-box;
}

.pdf-page-wrapper {
  display: inline-block;
  position: relative;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08), 0 0 1px rgba(0,0,0,0.2) !important;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
  text-align: left;
}

.pdf-canvas-container canvas {
  display: block;
  /* 不使用 width:100%; height:100% —— 与 inline-block wrapper 存在循环依赖，
     首次渲染时 wrapper 无显式尺寸，100% 无法解析导致 canvas 回退到默认 300×150 出现"小白板"。
     改为在 renderPdfPageToCanvas 中通过 canvas.style 显式设置像素尺寸 */
}

:deep(.textLayer) {
  position: absolute;
  text-align: initial;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  opacity: 1;
  line-height: 1;
  text-size-adjust: none;
  forced-color-adjust: none;
  transform-origin: 0 0;
  z-index: 2;
  user-select: text;
}

:deep(.textLayer :is(span, br)) {
  color: transparent !important;
  -webkit-text-fill-color: transparent !important;
  position: absolute;
  white-space: pre;
  cursor: text;
  transform-origin: 0% 0%;
}

:deep(.textLayer ::selection) {
  background: rgba(64, 158, 255, 0.35) !important;
}

.doc-html-content {
  width: 100%;
  max-height: 80vh;
  overflow-y: auto;
  padding: 0;
  background: #fff;
  :deep(table) {
    border-collapse: collapse;
    width: auto;
    th, td {
      border: 1px solid #000;
      padding: 4pt 6pt;
      vertical-align: top;
    }
  }
  :deep(img) {
    max-width: 100%;
    height: auto;
  }
  :deep(body) {
    font-family: 'SimSun', 'Microsoft YaHei', sans-serif;
    padding: 40px 60px;
    line-height: 1.5;
    color: #000;
  }
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: #909399;
  gap: 12px;
}

.docx-container {
  width: 100%;
  max-height: 80vh;
  overflow-y: auto;
}

/* 1. 图片、视频、音频、不支持文件容器：占据剩余空间，绝对居中（水平 + 垂直） */
.preview-image,
.preview-media,
.preview-unsupported {
  flex: 1;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;      /* 水平居中 */
  justify-content: center;  /* 垂直居中，让图片/视频直接在页面正中心 */
  overflow: hidden;
}

/* 2. 文本、Excel 容器：安全居中 (防止超宽时左侧被截断) */
.preview-text,
.preview-excel {
  flex: 1;
  width: 100%;
  height: 100%;
  display: block; /* 核心：弃用 Flex，改用基础的 block 布局 */
  overflow-y: auto;
  padding: 20px;
  box-sizing: border-box;
}

/* 内部元素智能居中：内容较窄时居中，过宽时靠左允许滚动，绝不截断 */
.excel-container,
.preview-text > div,
.preview-text > pre {
  margin: 0 auto;
  width: fit-content;
  max-width: 100%;
}

/* 3. 媒体元素自身的优雅缩放 */
.preview-img {
  max-width: 100%;
  max-height: 100%; /* 充分利用容器高度 */
  border-radius: 4px;
  object-fit: contain; /* 保证图片等比缩放不拉伸 */
  box-shadow: 0 4px 16px rgba(0,0,0,0.08); /* 给图片加点质感阴影 */
}

.preview-video {
  width: 80%;       /* 视频稍微留点白边更好看 */
  max-width: 900px;
  max-height: 80vh;
  border-radius: 4px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.preview-audio {
  width: 400px;
  max-width: 80%;
}

.text-content {
  white-space: pre-wrap;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
}

.markdown-body {
  padding: 20px;
  line-height: 1.8;
}

.csv-preview {
  width: 100%;
  overflow-x: auto;
}

.csv-table-wrapper {
  width: 100%;
  overflow-x: auto;
}

.csv-table-wrapper table {
  border-collapse: collapse;
  font-size: 13px;
  width: max-content;
  min-width: 100%;
}

.csv-table-wrapper th,
.csv-table-wrapper td {
  border: 1px solid #e0e0e0;
  padding: 6px 12px;
  text-align: left;
  white-space: nowrap;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.csv-table-wrapper th {
  background: #f5f7fa;
  font-weight: 600;
  position: sticky;
  top: 0;
}

.csv-table-wrapper tr:hover td {
  background: #f0f5ff;
}



.excel-loading {
  padding: 40px 20px;
}

.excel-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.excel-tabs {
  display: flex;
  justify-content: center;
}

.excel-table-wrapper {
  overflow-x: auto;
  overflow-y: auto;
  max-height: 55vh;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.excel-table {
  border-collapse: collapse;
  font-size: 13px;
  width: max-content;
  min-width: 100%;
}

.excel-table th,
.excel-table td {
  border: 1px solid #e0e0e0;
  padding: 6px 12px;
  text-align: left;
  white-space: nowrap;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.excel-table th {
  background: #f5f7fa;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.excel-table .row-num {
  background: #fafafa;
  color: #909399;
  text-align: center;
  font-size: 12px;
  min-width: 40px;
  position: sticky;
  left: 0;
  z-index: 2;
}

.excel-table thead .row-num {
  z-index: 3;
}

.excel-table tr:hover td {
  background: #f0f5ff;
}

.excel-notice {
  text-align: center;
  color: #909399;
  font-size: 12px;
  padding: 8px 0;
}

.excel-empty {
  padding: 20px 0;
}

.preview-unsupported, .preview-empty, .preview-loading {
  width: 100%;
  text-align: center;
  padding: 40px;
}

.preview-sidebar {
  width: 360px;
  background: #ffffff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.03);
  overflow-y: auto;
  flex-shrink: 0;
  transition: width 0.3s cubic-bezier(0.2, 0.8, 0.2, 1), opacity 0.3s ease;
  display: flex;
  flex-direction: column;
}

/* ==================== 组件深度美化 (按钮 & Tabs) ==================== */
.file-preview :deep(.el-button) {
  border-radius: 4px;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  font-weight: 500;
}
.file-preview :deep(.el-button:active) {
  transform: scale(0.96);
}

.preview-sidebar :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #f0f2f5;
}
.preview-sidebar :deep(.el-tabs__item) {
  font-size: 14px;
  color: #8a8f99;
  font-weight: 500;
  padding: 0 16px;
  transition: color 0.2s;
}
.preview-sidebar :deep(.el-tabs__item:hover) {
  color: #1d1e23;
}
.preview-sidebar :deep(.el-tabs__item.is-active) {
  color: #1d1e23;
  font-weight: 600;
  font-size: 15px;
}
.preview-sidebar :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px 3px 0 0;
  background-color: #1d1e23;
}


.info-section {
  margin-bottom: 20px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f2f5;
  font-size: 13.5px;
}

.info-label {
  color: #8a8f99;
  font-weight: 500;
  flex-shrink: 0;
}

.info-value {
  color: #1d1e23;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
  margin-left: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 标签云容器（复用 TagCloud 的 chip 风格） */
.tag-cloud-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}

.tag-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 40px;
  border-radius: 4px;
  border: 1.5px solid var(--chip-border);
  background: var(--chip-bg);
  color: var(--chip-text);
  font-size: 12px;
  font-weight: 500;
  padding: 6px 8px;
  word-break: break-word;
  text-align: center;
  transition: all 0.2s;
  position: relative;
}

.tag-chip:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.tag-chip-ai {
  border-style: dashed;
}

.tag-chip-text {
  flex: 1;
  cursor: pointer;
  line-height: 1.4;
  white-space: normal;
  word-break: break-word;
}

.tag-chip-edit,
.tag-chip-delete {
  cursor: pointer;
  font-size: 12px;
  opacity: 0.5;
  transition: opacity 0.2s;
  flex-shrink: 0;
}

.tag-chip-edit:hover {
  opacity: 1;
  color: var(--el-color-primary);
}

.tag-chip-delete:hover {
  opacity: 1;
  color: var(--el-color-danger);
}

.tag-empty-hint {
  font-size: 13px;
  color: #c0c4cc;
  margin-bottom: 8px;
}

/* 标签表单（两行输入） */
.tag-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.tag-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
  padding-bottom: 4px;
}

.tag-form-preview {
  font-size: 12px;
  color: #909399;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-top: 4px;
}

.tag-form-preview code {
  color: #e6a23c;
  font-size: 12px;
}

.doc-tag {
  border-radius: 6px;
}

.comment-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-input {
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.comment-input-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.comment-input-wrapper .text-right {
  text-align: right;
}

.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid #f0f2f5;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-avatar {
  background: linear-gradient(135deg, #409EFF, #1D3A8E);
  color: #fff;
  font-size: 12px;
}

.comment-user {
  font-size: 14px;
  font-weight: 600;
  color: #1d1e23;
}

.comment-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}

.comment-delete-btn {
  margin-left: 8px;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.comment-item:hover .comment-delete-btn {
  opacity: 1;
}

.comment-content {
  font-size: 13.5px;
  color: #4e5969;
  line-height: 1.6;
  background: #fafbfc;
  padding: 10px 12px;
  border-radius: 0 4px 4px 4px;
  margin-left: 36px;
  margin-top: 4px;
}

.mention-highlight {
  color: #409eff;
  font-weight: 500;
  cursor: pointer;
}

.mention-highlight:hover {
  text-decoration: underline;
}

.comment-input-wrapper {
  position: relative;
}

.mention-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  max-height: 200px;
  overflow-y: auto;
  z-index: 100;
  margin-top: 4px;
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.mention-item:hover,
.mention-item.active {
  background: #f0f5ff;
}

.mention-avatar {
  flex-shrink: 0;
}

.mention-name {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.mention-dept {
  font-size: 11px;
  color: #909399;
  margin-left: auto;
}

.empty-comments {
  padding: 20px 0;
}

.version-card {
  margin-bottom: 0;
}

.version-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.version-num {
  font-weight: 600;
  color: #409EFF;
}

.version-note {
  font-size: 13px;
  color: #606266;
  margin-top: 6px;
}

.version-meta {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.version-current {
  border-left: 3px solid #67c23a;
}

.ai-tag-confirm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 0;
}

.ai-tag-confirm-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-tag-confidence {
  font-size: 12px;
  color: #909399;
}

.ocr-section {
  min-height: 100px;
}

.ocr-loading {
  padding: 20px;
}

.ocr-empty,
.ocr-failed {
  padding: 16px 0;
}

.ocr-processing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 0;
  color: #409eff;
  font-size: 14px;
}

.ocr-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.ocr-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ocr-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ocr-page-info {
  margin-right: auto;
  font-size: 13px;
  color: #909399;
  background: #f0f2f5;
  padding: 2px 8px;
  border-radius: 4px;
}

.ocr-text-content {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.page-jump-popover {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.annotation-float-btn {
  position: absolute;
  z-index: 200;
  background: #409eff;
  color: #fff;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.4);
  transition: background 0.2s, transform 0.15s;
  white-space: nowrap;
}

.annotation-float-btn:hover {
  background: #337ecc;
  transform: scale(1.05);
}

.quote-preview-bar {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 10px;
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  margin-bottom: 8px;
}

.quote-preview-label {
  font-size: 12px;
  color: #409eff;
  font-weight: 600;
  flex-shrink: 0;
  line-height: 1.6;
}

.quote-preview-text {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  word-break: break-all;
  flex: 1;
}

.comment-quote {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  padding: 6px 8px;
  margin: 4px 0 4px 36px;
  background: #f4f4f5;
  border-left: 2px solid #c0c4cc;
  border-radius: 3px;
}

.comment-quote .quote-icon {
  color: #909399;
  font-size: 14px;
  flex-shrink: 0;
  line-height: 1;
}

.quote-text {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  word-break: break-all;
}

@media (max-width: 1180px) {
  .preview-sidebar {
    width: 320px;
  }
}

@media (max-width: 992px) {
  .preview-header {
    flex-wrap: wrap;
    gap: 10px;
    padding: 10px 14px;
  }
  .preview-header-left {
    flex: 1 1 100%;
    flex-wrap: wrap;
    min-width: 0;
  }
  .preview-filename {
    font-size: 14px;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .preview-header-right {
    flex: 1 1 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
  .preview-sidebar {
    width: 280px;
    padding: 12px;
  }
  .preview-main {
    padding: 14px;
  }
}

@media (max-width: 768px) {
  .file-preview {
    height: calc(100vh - 70px);
  }
  .preview-sidebar {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 86%;
    max-width: 340px;
    border-radius: 6px 0 0 6px;
    z-index: 50;
    box-shadow: -4px 0 20px rgba(0, 0, 0, 0.12);
  }
  .preview-main {
    padding: 12px;
  }
  .preview-body {
    position: relative;
  }
  .sidebar-toggle-fab {
    height: 40px;
    width: 14px;
  }
  .sidebar-toggle-fab:hover {
    width: 18px;
  }
}

@media (max-width: 480px) {
  .preview-header-left {
    gap: 8px;
  }
  .preview-filename {
    font-size: 13px;
  }
  .preview-header-right .el-button {
    padding: 6px 10px;
    font-size: 12px;
  }
  .preview-header-right .el-button span {
    display: none;
  }
  .preview-header-right .el-button .el-icon {
    margin: 0;
  }
}

/* ==================== 滚动条终极美化 (macOS 风格) ==================== */
.file-preview ::-webkit-scrollbar,
.file-preview :deep(::-webkit-scrollbar) {
  width: 6px;
  height: 6px;
}

.file-preview ::-webkit-scrollbar-track,
.file-preview :deep(::-webkit-scrollbar-track) {
  background: transparent;
}

.file-preview ::-webkit-scrollbar-thumb,
.file-preview :deep(::-webkit-scrollbar-thumb) {
  background: rgba(144, 147, 153, 0.15);
  border-radius: 4px;
}

.file-preview :hover::-webkit-scrollbar-thumb,
.file-preview :deep(:hover::-webkit-scrollbar-thumb) {
  background: rgba(144, 147, 153, 0.4);
}

.file-preview ::-webkit-scrollbar-thumb:hover,
.file-preview :deep(::-webkit-scrollbar-thumb:hover) {
  background: rgba(144, 147, 153, 0.6);
}

/* 右侧边栏 tabs 占据剩余空间 */
.sidebar-tabs {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* ==================== 任务三：脑图右侧侧边栏样式 ==================== */
.mindmap-section {
  display: flex;
  flex-direction: column;
  height: 420px;
  min-height: 320px;
  flex-shrink: 0;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 12px;
}

.mindmap-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 8px;
  flex-shrink: 0;
  min-width: 0;
}

.mindmap-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.mindmap-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  flex-shrink: 0;
}

.mindmap-tip {
  font-size: 12px;
  color: #909399;
}

.mindmap-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 220px;
  color: #909399;
}

.mindmap-loading p {
  margin-top: 12px;
  font-size: 13px;
}

.mindmap-loading-hint {
  margin-top: 4px !important;
  font-size: 12px !important;
  color: #C0C4CC !important;
}

.mindmap-error {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 220px;
}

.mindmap-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 220px;
  gap: 12px;
}

/* 脑图画布：侧边栏内固定高度，内部可滚动 */
.mindmap-canvas {
  flex: 1;
  width: 100%;
  min-height: 220px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  overflow: auto;
  cursor: grab;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
}

.mindmap-canvas:active {
  cursor: grabbing;
}

.mindmap-canvas svg {
  width: 100%;
  height: 100%;
  display: block;
}

/* 可点击的脑图节点样式 */
.mindmap-canvas :deep(.mindmap-node-clickable) {
  transition: opacity 0.2s;
  cursor: pointer;
}

.mindmap-canvas :deep(.mindmap-node-clickable:hover) {
  opacity: 0.7;
}

.mindmap-canvas :deep(.mindmap-node-clickable text) {
  fill: #409eff;
  font-weight: 500;
}

/* ==================== 脑图多页码选择器 (毛玻璃美化版) ==================== */
.mindmap-page-popover {
  position: fixed;
  z-index: 9999;
  /* 苹果风毛玻璃效果 */
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  /* 倒角与多层立体阴影 */
  border-radius: 6px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08), 0 0 1px rgba(0, 0, 0, 0.2);
  padding: 16px;
  width: max-content;
  max-width: 280px;
  /* 弹跳出现的丝滑微动效 */
  transform-origin: top left;
  animation: popoverBounceIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.mindmap-page-popover .popover-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 标题前面的蓝色小点缀 */
.mindmap-page-popover .popover-title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  background: #409eff;
  border-radius: 2px;
}
.mindmap-page-popover .popover-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
/* 深度定制按钮样式 */
.mindmap-page-popover :deep(.page-jump-btn) {
  margin-left: 0 !important;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  background: #fff;
  color: #606266;
  font-weight: 500;
  transition: all 0.25s ease;
  padding: 8px 14px;
}
.mindmap-page-popover :deep(.page-jump-btn:hover) {
  background: #ecf5ff;
  border-color: #a0cfff;
  color: #409eff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}
.mindmap-page-popover :deep(.page-jump-btn:active) {
  transform: translateY(0);
}
@keyframes popoverBounceIn {
  0% {
    opacity: 0;
    transform: scale(0.9) translateY(-10px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* ==================== 脑图沉浸式宽屏模式 ==================== */
.preview-sidebar.sidebar-expanded {
  width: 50vw !important;
  max-width: 800px;
  transition: width 0.3s ease;
}

.mindmap-section.mindmap-expanded {
  height: auto;
  flex: 1;
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

/* 宽屏模式下隐藏底部 Tab 栏，腾出所有空间给脑图 */
.preview-sidebar.sidebar-expanded .sidebar-tabs {
  display: none;
}

/* 宽屏模式下画布占满剩余高度 */
.mindmap-section.mindmap-expanded .mindmap-canvas {
  height: auto;
  min-height: 0;
}

/* ==================== 相关文件推荐悬浮框 ==================== */
.related-files-float {
  position: fixed;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  width: 280px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(0, 0, 0, 0.06);
  z-index: 2000;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.related-files-float.collapsed {
  width: auto;
  min-width: 160px;
}

.related-files-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-bottom: 1px solid #f0f2f5;
  cursor: grab;
  user-select: none;
}

.related-files-float.is-dragging .related-files-header {
  cursor: grabbing;
}

.related-files-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1d1e23;
}

.related-files-title .el-icon {
  color: #409eff;
}

.related-count {
  margin-left: 4px;
  font-size: 11px;
  padding: 0 6px;
  height: 18px;
  line-height: 16px;
}

.related-files-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.related-files-actions .el-button {
  padding: 4px;
  height: auto;
  color: #909399;
}

.related-files-actions .el-button:hover {
  color: #409eff;
}

.related-files-body {
  padding: 10px 12px;
  max-height: 320px;
  overflow-y: auto;
}

.related-file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
}

.related-file-item:hover {
  background: #f5f7fa;
}

.related-file-icon {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: #f5f7fa;
}

.related-file-info {
  flex: 1;
  min-width: 0;
}

.related-file-name {
  font-size: 13px;
  font-weight: 500;
  color: #1d1e23;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.related-file-meta {
  font-size: 11px;
  color: #8a8f99;
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-divider {
  color: #c0c4cc;
}

.related-files-footer {
  padding: 8px 12px 12px;
  border-top: 1px solid #f0f2f5;
}

.related-files-footer :deep(.el-checkbox__label) {
  font-size: 12px;
  color: #606266;
}
</style>
