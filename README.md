# 私有云文档管理系统 — 设计架构与实现模块

> 最后更新：2026-06-20 | 版本：4.0

---

## 一、项目概述

私有云文档管理系统是一个面向企业的私有化部署文档协作平台，提供文档管理、多人实时协同编辑、AI智能辅助、全文搜索、电子签章、工作流审批、细粒度权限控制等核心能力。系统采用**前后端分离**架构，后端基于 Spring Boot 4.0.6，前端基于 Vue 3 + Vite + TypeScript，支持多AI模型集成，并提供独立的 Python AI文档标签分类微服务。

### 1.1 核心功能

| 功能域 | 能力描述 |
|--------|----------|
| 文档管理 | 文件上传（分片/断点续传）、版本管理、目录管理、回收站、批量操作、分享链接、空间类型（个人/部门） |
| 协同编辑 | 基于 Yjs CRDT 的多人实时协同编辑，支持富文本与 Markdown，协同会话管理 |
| AI智能 | AI聊天（多模型）、AI文档生成、知识问答（RAG）、OCR识别、语音转文字、AI脑图生成、AI文档标签分类（Python微服务） |
| 电子签章 | PDF电子签章、Office→PDF转换、签章审批工作流 |
| 权限控制 | RBAC 权限模型、IP准入控制（NAC）、JWT无状态认证、验证码防暴力破解、盲索引凭证去关联 |
| 全文搜索 | 基于 Elasticsearch + IK 中文分词的全文检索，支持高亮、分面、搜索热词统计 |
| 文件预览 | 支持 PDF、Office（Word/Excel/PPT）、图片、音视频在线预览，阅读进度记录 |
| 个人工作台 | 可拖拽布局工作台、文档收藏、存储配额、贡献热力图、团队动态 |

---

## 二、整体技术架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                         客户端层 (Client Layer)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ 主前端应用 │  │ 管理后台  │  │ 移动端H5 │  │ 第三方API │            │
│  │ (Vue 3)  │  │ (Vue 3)  │  │          │  │  调用方   │            │
│  └─────┬────┘  └─────┬────┘  └─────┬────┘  └─────┬────┘            │
└────────┼─────────────┼─────────────┼─────────────┼──────────────────┘
         ▼             ▼             ▼             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         接入层 (Access Layer)                         │
│  Nginx 反向代理 · IP准入控制(NAC) · 限流熔断 · HTTPS/TLS             │
└────────────────────────────────┬─────────────────────────────────────┘
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│                 应用层 — Spring Boot 4.0.6 (Application Layer)       │
│  Controller (REST API) · Security (JWT+RBAC) · WebSocket (STOMP)    │
│                                 ▼                                    │
│                    Service 业务逻辑层                                 │
│  文档空间 │ AI智能 │ 搜索服务 │ 系统管理 │ 签章 │ 工作流 │ 协同   │
│  文件预览 │ OCR │ 语音 │ 评论 │ 通知 │ 缓存 │ 存储 │ 脑图       │
│                                 ▼                                    │
│                   Repository 数据访问层                               │
│  JPA(MySQL) │ Redis │ Elasticsearch │ MinIO │ Caffeine               │
└────────────────────────────────┬─────────────────────────────────────┘
         ▼             ▼         ▼         ▼             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         数据层 (Data Layer)                           │
│  MySQL(主库) · Redis(缓存) · Elasticsearch(搜索) · MinIO(存储)       │
│  RabbitMQ(消息队列) · Caffeine(本地缓存)                              │
└──────────────────────────────────────────────────────────────────────┘
                                 ▲
┌──────────────────────────────────────────────────────────────────────┐
│                    协作层 — Node.js (Collaboration Layer)             │
│  Yjs WebSocket Server (端口1234) · 多房间管理 · 自动清理 · 健康检查   │
└──────────────────────────────────────────────────────────────────────┘
                                 ▲
┌──────────────────────────────────────────────────────────────────────┐
│                 AI微服务层 — Python (AI Tag Service)                  │
│  FastAPI · text2vec-base-chinese · 文档自动分类 · 端口8000            │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 三、核心技术栈选型及理由

### 3.1 后端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Spring Boot** | 4.0.6 | 企业级Java框架，生态成熟，自动配置简化开发 |
| **Spring Security** | — | 完善的安全框架，支持JWT、RBAC、密码加密 |
| **Spring Data JPA** | — | ORM简化数据库操作，Hibernate自动DDL |
| **Spring WebSocket** | — | STOMP协议，实时消息推送与协同 |
| **Spring AMQP** | — | RabbitMQ集成，异步任务队列 |
| **Spring Data Redis** | — | 分布式缓存与会话管理 |
| **Spring Data Elasticsearch** | — | 全文检索能力 |
| **MySQL** | — | 关系型数据库，事务支持好 |
| **Redis** | — | 高性能KV缓存，支持TTL、发布订阅 |
| **Elasticsearch** | — | 分布式搜索引擎，IK中文分词 |
| **MinIO** | — | S3兼容对象存储，私有化部署 |
| **RabbitMQ** | 4.3.2 | AMQP消息队列，支持死信、延迟重试 |
| **Caffeine** | — | 高性能本地缓存（纳秒级） |
| **JWT (jjwt)** | 0.12.6 | 无状态认证Token |
| **Apache POI** | 5.2.5 | Office文档读写 |
| **Apache PDFBox** | 3.0.3 | PDF解析与文本提取 |
| **EasyExcel** | 4.0.3 | Excel流式解析，低内存 |
| **OkHttp** | 4.12.0 | HTTP客户端，调用AI API |
| **Fastjson** | 2.0.43 | JSON序列化 |
| **Hutool** | 5.8.32 | Java工具库 |
| **Lombok** | — | 编译期代码生成 |
| **Freemarker** | — | 模板引擎 |

### 3.2 前端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Vue 3** | 3.4.21 | Composition API、性能优化 |
| **TypeScript** | 5.4.3 | 类型安全，可维护性高 |
| **Vite** | 5.2.8 | 极速HMR，原生ESM |
| **Element Plus** | 2.6.1 | 企业级UI组件库 |
| **Pinia** | 2.1.7 | 状态管理，TS友好 |
| **Vue Router** | 4.3.0 | 路由管理，守卫与懒加载 |
| **Axios** | 1.6.8 | HTTP客户端 |
| **ECharts** | 6.1.0 | 数据可视化 |
| **Quill** | 2.0.3 | 富文本编辑器 |
| **Yjs** | 13.6.30 | CRDT协同编辑 |
| **Markdown-it** | 14.1.1 | Markdown解析 |
| **PDF.js** | 3.11.174 | PDF浏览器端预览 |
| **docx-preview** | 0.3.7 | Word文档浏览器端预览 |
| **pptx2html** | 0.3.4 | PPT转换为HTML |
| **marked** | 18.0.3 | Markdown解析（备用） |
| **highlight.js** | 11.11.1 | 代码语法高亮 |
| **SparkMD5** | 3.0.2 | 文件MD5计算 |
| **JSEncrypt** | 3.5.4 | RSA前端加密 |
| **@stomp/stompjs** | 7.3.0 | STOMP协议WebSocket客户端 |
| **sockjs-client** | 1.6.1 | SockJS WebSocket回退 |
| **d3** | 7.9.0 | 数据可视化 |
| **gsap** | 3.15.0 | 动画引擎 |
| **markmap** | 0.18.x | Markdown脑图渲染 |
| **grid-layout-plus** | 1.1.1 | 可拖拽网格布局 |
| **quill-cursors** | 4.3.0 | 协同编辑光标显示 |
| **vue3-lottie** | 3.3.1 | Lottie动画 |
| **@tsparticles** | 4.2.1 | 粒子动画背景 |
| **lodash-es** | 4.18.1 | 工具函数库 |
| **pinia-plugin-persistedstate** | 3.2.3 | Pinia状态持久化 |

### 3.3 基础设施

| 组件 | 版本 | 用途 |
|------|------|------|
| **RabbitMQ** | 4.3.2 | 异步任务队列 |
| **Erlang OTP** | 26.2.5.2 | RabbitMQ运行环境 |
| **MinIO** | 2025-04-22 | 分布式对象存储 |
| **Nginx** | — | 反向代理、HTTPS |
| **Node.js** | 18.19.1 | Yjs协同服务器 |
| **nssm** | 2.24 | Windows服务管理 |

---

## 四、各层级/模块的设计理念

### 4.1 后端分层架构

```
Controller（接口层）→ Service（业务逻辑层）→ Repository（数据访问层）→ 基础设施
```

**核心原则**：单一职责、依赖倒置、面向接口编程、开闭原则。

### 4.2 前端架构

```
Views → Components → Composables → Stores(Pinia) → API Services(Axios)
```

**核心原则**：组件复用、状态集中管理、路由懒加载、TypeScript全量类型。

### 4.3 后端模块划分（按业务领域）

| 顶层包 | 领域 | 子模块 |
|--------|------|--------|
| `front.workspace` | 工作空间 | documentspace、personalworkspace |
| `front.intelligence` | 智能能力 | ai、ocr、preview、text、speech、comment、notification、search、signature |
| `front.search` | 搜索 | engine（ES）、qa（AI问答+系统提示词） |
| `front.system` | 系统管理 | 用户、角色、权限、部门、审计、敏感词 |
| `front.mq` | 消息队列 | 队列配置、发布、消费、处理器（6种任务） |
| `front.cache` | 缓存 | Caffeine配置、二级缓存服务 |
| `front.storage` | 存储 | 本地存储、MinIO存储、存储路由 |
| `front.hxconfig` | 基础配置 | 安全、JWT、Redis、WebSocket、跨域、异步、RSA |
| `front.esign` | 电子签章 | PDF签章、Office转PDF |
| `front.workflow` | 工作流 | 审批流程、签章审批 |
| `front.collaboration` | 协同会话 | 协同编辑会话管理 |
| `front.config` | 启动检查 | 服务启动连通性自检 |
| `back.admin` | 管理后台 | 仪表盘统计API |

---

## 五、主要功能模块的划分与职责

### 5.1 后端模块

#### 5.1.1 系统管理模块 (`front.system`)

**职责**：用户认证、RBAC权限、审计日志、敏感词检测

| 分层 | 核心文件 | 职责 |
|------|----------|------|
| Entity | `SysUser`, `SysRole`, `SysPermission`, `SysDepartment`, `SysUserRole`, `SysRolePermission`, `SysOperationLog`, `SysSensitiveWord`, `SysUserCredential` | 9个数据实体 |
| Repository | 9个JPA Repository | 数据访问 |
| Service | `SysUserService`, `CaptchaService`, `AuditLogService`, `SensitiveWordService` | 4个服务 |
| Controller | `AuthController`, `SysUserController`, `AuditLogController`, `SensitiveWordController` | 4个REST接口 |

**关键设计**：登录流程 = 验证码校验 → RSA解密 → BCrypt比对 → JWT签发 → Redis记录状态；审计日志通过MQ异步写入；`DataMigrationRunner`自动将旧版密码迁移到凭证表；`SecurityHashUtil`使用HMAC-SHA256盲索引实现凭证去关联。

#### 5.1.2 文档空间模块 (`front.workspace.documentspace`)

**职责**：文件全生命周期管理

| 分层 | 核心文件 | 职责 |
|------|----------|------|
| Entity | `DocFile`(含mindmapContent/previewStatus/spaceType/spaceId等新字段), `DocDirectory`, `DocFileVersion`, `DocShareLink`, `DocUploadTask`, `FileAccessLog`, `RecycleBin` | 7个实体 |
| DTO | `FileDTO`, `DirectoryDTO`, `ChunkUploadDTO`, `VersionDTO` | 4个传输对象 |
| Service | `DocFileService`, `ChunkUploadService`, `FileVersionService`, `FileListCacheService`, `DirectoryService`, `RecycleBinService`, `ChunkProgressCacheService`, `DirectoryTreeCacheService`, `BatchDownloadService`, `DocShareLinkService`, `FileAccessLogService` | 11个服务 |
| Controller | `DocFileController`, `DocShareLinkController`, `DirectoryController`, `RecycleBinController`, `ChunkUploadController`, `FileVersionController`, `BatchDownloadController` | 7个控制器 |

**关键设计**：`StorageRouter`根据文件大小自动选择MinIO/本地存储；分片上传通过Redis记录进度；文件变更自动同步ES索引；支持个人空间/部门空间(`spaceType`/`spaceId`)；批量下载服务；文件列表/目录树多级缓存。

#### 5.1.3 AI智能模块 (`front.intelligence.ai`)

**职责**：多AI模型接入、AI聊天、文档生成、RAG知识问答、AI脑图、AI文档标签

| 分层 | 核心文件 | 职责 |
|------|----------|------|
| Config | `DeepSeekConfig`, `GlmConfig`, `MimoConfig`, `AiServiceRunner` | 4个配置/启动 |
| Entity | `AiChatHistory`, `DocMetadata`, `DocTag`, `GeneratedDoc` | 4个实体 |
| DTO | `ChatRequestDTO`, `GenerateDocumentDTO`, `QuickActionDTO`, `SummarizeRequestDTO` | 4个传输对象 |
| Service | `DocumentGenerateService`, `AiTagService`, `RagService`, `DeepSeekService`, `MimoService`, `GlmService`, `GeneratedDocService`, `DocTagService`, `AiChatHistoryService` | 9个服务 |
| Controller | `AiChatController`, `DocumentGenerateController`, `AiTagController`, `AiChatHistoryController`, `GeneratedDocController` | 5个控制器 |

**关键设计**：RAG流程 = ES检索相关文档 → 拼接Prompt上下文 → AI模型生成回答；流式输出使用SSE；AI文档生成通过MQ异步执行；`AiServiceRunner`启动时自动检测并启动Python AI标签服务；AI脑图通过MQ队列`task.file.mindmap`异步生成；AI标签服务调用Python微服务进行文档自动分类。

#### 5.1.3.1 其他智能子模块 (`front.intelligence.*`)

| 子模块 | Entity | Controller | Service | 职责 |
|--------|--------|------------|---------|------|
| `comment` | `DocComment` | `CommentController` | `CommentService` | 文档评论（@提及/嵌套回复） |
| `notification` | `SysNotification` | `NotificationController` | `NotificationService` | 消息通知（WebSocket推送） |
| `ocr` | `OcrRecord` | `OcrController` | `OcrService` | OCR文字识别（百度OCR） |
| `preview` | `DocProgressRecord` | `PreviewController`, `DocProgressController` | `PreviewService`, `PreviewCleanupService`, `DocProgressService` | 文件预览（PDF/Office/图片/音视频）+ 阅读进度 + 缓存清理 |
| `search` | `SearchKeyword` | `SearchKeywordController` | `SearchKeywordService` | 搜索关键词热词统计 |
| `signature` | `SignatureRecord` | `SignatureRecordController` | `SignatureRecordService` | 签章记录管理 |
| `speech` | — | `SpeechController` | `BaiduSpeechService` | 语音转文字（百度语音） |
| `text` | — | `FulltextExtractController` | `FulltextExtractService` | 文档全文提取 |

#### 5.1.4 搜索引擎模块 (`front.search`)

**职责**：Elasticsearch全文检索、AI知识问答

| 核心文件 | 职责 |
|----------|------|
| `SearchServiceImpl` | 全文检索 + 高亮 + 分面 + 权限过滤 |
| `DocFileIndexService` | MySQL→ES索引同步 |
| `DocContentController` | 文档内容检索接口 |
| `AiQaServiceImpl` | RAG问答编排 |
| `SystemPromptService` / `SystemPromptController` | 系统提示词模板管理（自定义AI问答角色） |
| `AiProviderConfig` | AI提供商配置 |

#### 5.1.5 消息队列模块 (`front.mq`)

**职责**：6种异步任务 + 死信重试 + MQ降级

| 业务队列 | 路由键 | 用途 |
|----------|--------|------|
| `task.ai.generate` | `ai.generate` | AI文档生成 |
| `task.file.ocr` | `file.ocr` | OCR文字识别 |
| `task.file.fulltext` | `file.fulltext` | 全文提取 |
| `task.file.office.convert` | `file.office.convert` | Office转PDF |
| `task.file.mindmap` | `file.mindmap` | AI脑图生成 |
| `task.sys.audit.log` | `sys.audit.log` | 审计日志写入 |

每个队列对应一个`.dlq`死信队列，重试3次失败后进入。

#### 5.1.6 缓存模块 (`front.cache`)

**二级缓存**：L1 Caffeine(纳秒级) → L2 Redis(毫秒级) → DB

| 缓存区 | L1 TTL | L2 TTL | 用途 |
|--------|--------|--------|------|
| `userPermissions` | 10分钟 | 30分钟 | 用户权限 |
| `fileList` | 30秒 | 5分钟 | 文件列表 |
| `sysConfig` | 30分钟 | 2小时 | 系统配置 |

#### 5.1.7 文件存储模块 (`front.storage`)

**双存储策略**：`StorageService`接口 → `LocalStorageService` + `MinioStorageService`实现 → `StorageRouter`自动路由。MinIO故障时透明降级到本地存储。

#### 5.1.8 基础设施配置 (`front.hxconfig`)

核心配置：`SecurityConfig`（安全）、`HxJwtFilter`+`JwtUtil`（认证）、`IpAccessFilter`（IP准入）、`PermissionFilter`（权限过滤）、`WebSocketConfig`+`NotificationHandler`（实时通知）、`RedisConfig`（缓存）、`AsyncConfig`（异步线程池）、`RsaKeyManager`（RSA加解密）、`CorsConfig`（跨域）、`DataMigrationRunner`（密码迁移）、`SecurityHashUtil`（盲索引哈希）。

#### 5.1.9 电子签章模块 (`front.esign`)

**职责**：PDF电子签章、Office文档转PDF

| 分层 | 核心文件 | 职责 |
|------|----------|------|
| Service | `StampService` | PDF签章逻辑 |
| Service | `OfficeConverter` | Office(Word/Excel/PPT)→PDF转换（基于Apache POI + xdocreport） |
| Controller | `StampController` | 签章REST接口 |

**关键设计**：Office文档通过`OfficeConverter`转换为PDF后，使用`StampService`在指定位置加盖电子印章；签章记录存储在`signature_record`表中。

#### 5.1.10 工作流审批模块 (`front.workflow`)

**职责**：文档签章审批流程

| 分层 | 核心文件 | 职责 |
|------|----------|------|
| Entity | `ApprovalRequest` | 审批请求实体（申请人/文档/类型/状态/盖章文件） |
| Repository | `ApprovalRequestRepository` | 数据访问 |
| Controller | `ApprovalController` | 审批REST接口（提交/审批/撤回/列表） |

**关键设计**：审批流程支持多步骤审批（`wf_approval_step`表），与电子签章模块集成，审批通过后方可执行签章操作。

#### 5.1.11 协同会话模块 (`front.collaboration`)

**职责**：协同编辑会话状态管理

| 分层 | 核心文件 | 职责 |
|------|----------|------|
| Entity | `CollabSession` | 协同会话实体（文档ID/会话ID/房间名/权限模式/内容） |
| Service | `CollabSessionService` | 会话管理逻辑 |
| Controller | `CollabSessionController` | 会话REST接口 |

#### 5.1.12 AI文档标签微服务 (`ai-tag-service`)

**职责**：基于深度学习的文档自动分类

| 项目 | 详情 |
|------|------|
| **框架** | FastAPI + Uvicorn |
| **模型** | `shibing624/text2vec-base-chinese`（中文语义向量） |
| **设备** | 自动检测 CUDA/CPU |
| **接口** | `POST /api/predict_tag` → `{filename, content}` → `{tag, score}` |
| **标签集** | 合同协议、财务报表、技术文档、会议纪要、通知公告、项目报告、产品手册、培训资料、法律法规、人事行政（10个） |
| **置信度阈值** | 0.55，低于则返回`tag: null` |
| **依赖** | PyTorch 2.2.1, Transformers 4.38.2, Pydantic 2.6.4 |

**关键设计**：后端`AiTagService`通过HTTP调用Python微服务接口，上传文件时自动触发文档分类；`AiServiceRunner`在Spring Boot启动时自动检测虚拟环境并启动Python服务。

### 5.2 前端模块

#### 5.2.1 页面路由

| 路径 | 组件 | 功能 |
|------|------|------|
| `/login` | `Login.vue` | 登录（验证码+RSA加密） |
| `/s/:token` | `SharePreview.vue` | 外部共享文档预览 |
| `/dashboard` | `Workbench.vue` | 个人工作台（可拖拽布局、小组件） |
| `/document` | `DocumentSpace.vue` | 文档空间 |
| `/search` | `AdvancedSearchEngine.vue` | 高级搜索 |
| `/qa` | `KnowledgeQA.vue` | AI知识问答 |
| `/collab` | `Collaboration.vue` | 外部协作列表 |
| `/collab/editor/:id` | `CollabEditor.vue` | 协同编辑器 |
| `/share/manage` | `ShareManage.vue` | 分享管理 |
| `/workflow/approval` | `ApprovalSubmit.vue` | 申请签章 |
| `/ai/generate` | `DocumentGenerate.vue` | 智能文档生成 |
| `/ai/generated-docs` | `GeneratedDocs.vue` | 生成文档管理 |
| `/preview/:id` | `FilePreview.vue` | 文件预览（PDF/Office/图片/音视频/脑图） |
| `/admin/sensitive-words` | `SensitiveWords.vue` | 敏感词管理 |

**工作台小组件**：`WidgetRecentFiles`(最近文件)、`WidgetMyFavorites`(我的收藏)、`WidgetStorageQuota`(存储配额)、`WidgetPendingApprovals`(待审批)、`WidgetTeamUpdates`(团队动态)、`WidgetContributionHeatmap`(贡献热力图)。

#### 5.2.2 状态管理 (Pinia)

| Store | 核心状态 | 职责 |
|-------|----------|------|
| `useUserStore` | token, userInfo, permissions | 认证状态，持久化到localStorage |
| `useDocumentStore` | files, directories, currentDir | 文档空间状态 |
| `useAiStore` | chatHistory, currentModel | AI聊天状态 |
| `useNotificationStore` | notifications, unreadCount | 通知状态 |

**API服务层**：`ai.ts`, `document.ts`, `share-preview.ts`, `workspace.ts`, `preview.ts`, `auth.ts`, `adv-search.ts`, `search.ts`, `approval.ts`, `collab.ts`, `share.ts`, `notification.ts`, `comment.ts`, `upload.ts`, `signature.ts`（16个API服务文件）。

#### 5.2.3 协同编辑子项目 (`collab-ws-server`)

独立Node.js WebSocket服务，基于Yjs。支持多房间管理、Awareness广播、自动清理空闲房间（5分钟）、健康检查端点。连接方式：`ws://host:1234/doc-{id}`。

#### 5.2.4 管理后台子项目 (`admin`)

基于Vue 3 + Element Plus的独立管理后台，提供数据仪表盘（`DashboardContent.vue`）和管理功能入口。

| 路由 | 功能 | 状态 |
|------|------|------|
| `/login` | 管理员登录 | ✅ 已实现 |
| `/dashboard` | 数据仪表盘（概览统计/文档分布/存储趋势/热门文档/搜索热词/待审批） | ✅ 已实现 |
| `/system/user` | 用户管理 | ⚠️ 占位 |
| `/system/dept` | 部门管理 | ⚠️ 占位 |
| `/system/role` | 角色管理 | ⚠️ 占位 |
| `/workflow/approval` | 审批管理 | ⚠️ 占位 |
| `/security/operation-log` | 操作日志 | ⚠️ 占位 |

后端对应`AdminDashboardController`提供仪表盘API：概览统计、文档类型分布、存储趋势、热门文档、搜索热词、待审批列表。

---

## 六、模块间的交互关系

### 6.1 前后端交互模式

```
┌──────────┐  REST API (JSON)   ┌──────────────┐
│ Vue 3 前端│ ◄────────────────► │ Spring Boot  │
│          │  WebSocket (STOMP) │    后端       │
│          │ ◄════════════════► │              │
└────┬─────┘                   └──────┬───────┘
     │ ws://1234/doc-{id}             │ AMQP        │ HTTP
     ▼                                ▼              ▼
┌──────────┐                   ┌──────────┐   ┌──────────┐
│ Yjs WS  │                   │ RabbitMQ │   │ AI Tag   │
│ Server   │                   │ 队列     │   │ Service  │
└──────────┘                   └────┬─────┘   │ (Python) │
                                    │ 消费    └──────────┘
                                    ▼
                             ┌──────────┐
                             │ Handlers │
                             └──────────┘
```

### 6.2 文档上传流程

```
前端选择文件 → POST /upload → StorageRouter判断 → 存储文件(MinIO/本地)
             → 保存DB元数据 → 同步ES索引 → 发布OCR/全文提取/脑图MQ任务
             → 调用AI标签服务分类 → 返回成功
```

### 6.3 AI知识问答流程

```
用户提问 → POST /qa/ask → ES检索相关文档片段(RAG)
         → 构建Prompt(上下文+问题) → 调用AI模型
         → SSE流式返回回答 → 前端逐字渲染
```

### 6.4 实时协同编辑流程

```
用户A编辑 → Yjs Update → WS Server广播 → 用户B应用Update → 渲染变更
用户B编辑 → Yjs Update → WS Server广播 → 用户A应用Update → 渲染变更
```

---

## 七、关键技术实现细节

### 7.1 双存储策略 (StorageRouter)

根据文件大小和MinIO可用性自动选择后端，故障时透明降级：
- 大文件(>=minio.min-size) → MinIO对象存储
- 小文件 → 本地磁盘
- MinIO故障 → 自动降级到本地

### 7.2 二级缓存 (Caffeine + Redis)

```
请求 → L1 Caffeine(纳秒级,本地JVM) → miss → L2 Redis(毫秒级) → miss → DB
```
数据变更时同时清除L1和L2，下次请求从DB重新加载。

### 7.3 MQ降级机制

RabbitMQ不可用时自动降级为线程池同步执行，保证服务可用性。

### 7.4 JWT + IP双重认证

```
请求 → IpAccessFilter(IP白名单) → HxJwtFilter(JWT解析) → SecurityConfig(权限匹配) → Controller
```

**登录认证流程**：

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  前端    │     │ Auth     │     │ SysUser  │     │ Credential│     │ JWT      │
│  Login   │     │ Controller│    │ Service  │     │ Service   │     │ Util     │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │                │
     │ 1.输入用户名密码 │                │                │                │
     │───────────────►│                │                │                │
     │                │                │                │                │
     │                │ 2.RSA解密密码   │                │                │
     │                │────────────────┼────────────────┼────────────────│
     │                │                │                │                │
     │                │ 3.查询sys_user │                │                │
     │                │───────────────►│                │                │
     │                │                │                │                │
     │                │ 4.返回用户信息  │                │                │
     │                │◄───────────────│                │                │
     │                │                │                │                │
     │                │ 5.查询sys_user_credential      │                │
     │                │────────────────┼───────────────►│                │
     │                │                │                │                │
     │                │ 6.返回密码哈希  │                │                │
     │                │◄───────────────┼────────────────│                │
     │                │                │                │                │
     │                │ 7.BCrypt验证    │                │                │
     │                │────────────────┼────────────────┼────────────────│
     │                │                │                │                │
     │                │ 8.签发JWT Token │                │                │
     │                │────────────────┼────────────────┼───────────────►│
     │                │                │                │                │
     │                │ 9.返回Token     │                │                │
     │◄───────────────┼────────────────┼────────────────┼────────────────│
     │                │                │                │                │
     │ 10.存储Token    │                │                │                │
     │───────────────►│                │                │                │
```

**安全设计要点**：
- **用户名密码分离存储**：`sys_user`存储身份信息，`sys_user_credential`存储认证凭证，1:1关联
- **RSA前端加密**：登录密码使用RSA公钥加密传输，后端私钥解密，防止中间人攻击
- **BCrypt哈希**：密码使用BCrypt算法加盐哈希存储，不可逆
- **JWT防复用**：Token中嵌入`serverInstanceId`，服务重启后旧Token自动失效
- **IP准入控制**：`IpAccessFilter`限制仅允许指定网段访问

### 7.5 协同编辑 (Yjs CRDT)

基于CRDT算法，客户端独立编辑、自动合并冲突。组件栈：Quill → y-quill → y-websocket → WS Server。支持协同光标显示（quill-cursors）。

### 7.6 分片上传

大文件切片 → 计算MD5 → 逐片上传 → Redis记录进度 → 全部完成后合并 → 触发后续任务(OCR/全文提取/脑图生成)。

### 7.7 RAG检索增强生成

用户提问 → ES检索相关文档片段 → 拼接为Prompt上下文 → 发送给AI模型 → 返回增强回答。支持自定义系统提示词模板（`system_prompt`表）。

### 7.8 AI脑图生成

文件上传 → MQ发布`task.file.mindmap`任务 → `FileMindmapHandler`消费 → 调用AI模型生成Markdown脑图 → 存储到`doc_file.mindmap_content` → 前端使用markmap渲染。

### 7.9 电子签章与审批流程

```
用户提交签章申请 → ApprovalController创建审批 → 审批人审批通过
              → StampController执行签章 → OfficeConverter转PDF(如需)
              → StampService加盖印章 → 记录到signature_record
```

### 7.10 工作台可拖拽布局

基于`grid-layout-plus`实现可拖拽网格布局，布局配置持久化到`sys_user.workspace_layout`字段。支持6种小组件：最近文件、我的收藏、存储配额、待审批、团队动态、贡献热力图。

---

## 八、数据库设计概要

### 8.1 核心数据表

| 表名 | 模块 | 说明 |
|------|------|------|
| `sys_user` | 系统管理 | 用户身份信息（用户名/姓名/邮箱/手机/头像/状态/部门/存储配额/工作台布局） |
| `sys_user_credential` | 系统管理 | 用户认证凭证（密码哈希/盐值/算法/过期时间），与用户表1:1解耦 |
| `sys_role` | 系统管理 | 角色定义（超级管理员/普通用户） |
| `sys_permission` | 系统管理 | 权限定义 |
| `sys_user_role` | 系统管理 | 用户-角色关联 |
| `sys_role_permission` | 系统管理 | 角色-权限关联 |
| `sys_department` | 系统管理 | 部门组织 |
| `sys_company` | 系统管理 | 公司表（多租户预留） |
| `sys_operation_log` | 系统管理 | 操作审计日志 |
| `sys_sensitive_word` | 系统管理 | 敏感词库（词/严重性/分类/等级/启用） |
| `sys_dict_type` | 系统管理 | 字典类型 |
| `sys_dict_data` | 系统管理 | 字典数据 |
| `sys_cache_stats` | 系统管理 | 缓存统计（名称/键数/内存/命中率） |
| `sys_notification` | 通知 | 消息通知（接收者/标题/内容/类型/发送者/文件） |
| `doc_file` | 文档空间 | 文件元数据（含storage_type/storage_path双存储、mindmap_content脑图、preview_status预览状态、space_type/space_id空间类型） |
| `doc_directory` | 文档空间 | 目录树（支持space_id） |
| `doc_file_version` | 文档空间 | 文件版本历史 |
| `doc_share_link` | 文档空间 | 分享链接（token/密码/过期时间/最大访问次数/权限类型） |
| `doc_upload_task` | 文档空间 | 分片上传任务 |
| `file_access_log` | 文档空间 | 文件访问日志 |
| `recycle_bin` | 文档空间 | 回收站（item_type/item_id/过期时间） |
| `doc_favorite` | 个人工作台 | 文档收藏（user_id/target_id/target_type） |
| `doc_comment` | 评论 | 文档评论（file_id/user_id/content/mentions/parent_id） |
| `doc_progress_record` | 文件预览 | 阅读进度记录（user_id/file_id/progress_type/progress_value） |
| `ai_chat_history` | AI智能 | 聊天历史（user_id/session_id/role/content/model） |
| `doc_metadata` | AI智能 | 文档元数据标签（file_id/tag_key/tag_value/confidence/source_model） |
| `doc_tag` | AI智能 | 文档标签（file_id/tag_name/tag_source[AI/manual]/confidence） |
| `generated_doc` | AI智能 | AI生成文档（file_name/content/model/creator_id/department_id） |
| `system_prompt` | AI智能 | 系统提示词模板（name/label/prompt_content/is_preset/user_id） |
| `ocr_record` | OCR | OCR识别记录（file_id/ocr_text/page_number） |
| `search_keyword` | 搜索 | 搜索关键词统计（keyword/search_count/is_hot） |
| `signature_record` | 签章 | 电子签章记录（document_id/signer_id/seal_image_url/signature_hash） |
| `collab_session` | 协同 | 协同会话（document_id/session_id/room_name/permission_mode/content） |
| `wf_approval_request` | 工作流 | 审批请求（applicant_id/document_id/type/status/stamped_file_id） |
| `wf_approval_step` | 工作流 | 审批步骤（request_id/step_order/approver_id/status/comment） |

### 8.2 用户名与密码分离存储设计

本系统采用**用户名与密码不同表解耦**的安全设计模式，将用户身份信息与认证凭证分离存储：

```
┌─────────────────────────────────────────────────────────────────┐
│                    用户认证数据模型                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐         ┌─────────────────────┐       │
│  │    sys_user          │         │  sys_user_credential │       │
│  │  (用户身份信息表)     │ 1     1 │  (用户认证凭证表)     │       │
│  ├─────────────────────┤ ──────► ├─────────────────────┤       │
│  │ id (PK)             │         │ id (PK)             │       │
│  │ username            │         │ user_id (FK)        │       │
│  │ real_name           │         │ credential_type     │       │
│  │ email               │         │ credential_value    │       │
│  │ phone               │         │ salt                │       │
│  │ avatar              │         │ algorithm           │       │
│  │ status              │         │ expire_at           │       │
│  │ dept_id (FK)        │         │ created_at          │       │
│  │ storage_quota       │         │ updated_at          │       │
│  │ workspace_layout    │         └─────────────────────┘       │
│  │ created_at          │                                       │
│  │ updated_at          │                                       │
│  └─────────────────────┘                                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**设计优势**：

| 优势 | 说明 |
|------|------|
| **安全性隔离** | 即使用户信息表被泄露，攻击者也无法直接获取密码哈希；两张表可设置不同的访问权限 |
| **多凭证支持** | `credential_type`字段支持多种认证方式（密码、API密钥、OAuth Token等），扩展性强 |
| **独立安全策略** | 密码表可独立实施更强的安全策略（如独立加密存储、独立备份策略、独立审计日志） |
| **合规性** | 满足等保、GDPR等安全合规要求，密码与个人信息分离存储 |
| **历史密码管理** | 可在同一张凭证表中存储历史密码哈希，防止用户重复使用旧密码 |

**认证流程**：

```
用户登录请求 → 查询 sys_user 获取用户基本信息
             → 查询 sys_user_credential 获取密码哈希
             → BCrypt 验证明文密码与哈希
             → 验证通过 → 签发 JWT Token
             → 验证失败 → 记录失败次数 + 返回错误
```

### 8.3 数据库选型理由

- **MySQL**：成熟的关系型数据库，事务ACID支持，适合结构化数据存储
- **密码凭证分离**：`sys_user_credential`独立存储密码哈希，降低数据泄露风险
- **审计字段**：关键表包含`created_at`、`updated_at`、`created_by`、`updated_by`审计字段

---

## 九、架构设计的优势与潜在挑战

### 9.1 架构优势

| 优势 | 说明 |
|------|------|
| **高可用** | 双存储策略（MinIO故障降级本地）、MQ降级（不可用降级线程池）、二级缓存（任一层异常不影响业务） |
| **高性能** | 二级缓存（Caffeine纳秒级 + Redis毫秒级）、ES全文检索、异步任务队列削峰 |
| **可扩展** | 模块化设计（新增AI模型只需新增Config+Service）、存储抽象接口（易于扩展新存储后端）、Python微服务独立部署 |
| **安全性** | 用户名密码分离存储（安全隔离）、JWT+IP双重认证、RBAC权限、RSA加密传输、验证码防暴力破解、敏感词检测、HMAC-SHA256盲索引凭证去关联 |
| **实时性** | WebSocket/STOMP通知推送、Yjs CRDT协同编辑、SSE流式输出 |
| **可维护** | 分层清晰、TypeScript全量类型、统一响应格式、完善审计日志 |
| **启动自检** | 启动时自动验证Redis/MinIO/RabbitMQ/ES连通性，快速发现环境问题 |

### 9.2 潜在挑战与应对

| 挑战 | 风险描述 | 应对策略 |
|------|----------|----------|
| **单体架构瓶颈** | 随业务增长单体应用可能成为性能与维护瓶颈 | 模块化设计为未来微服务拆分预留基础；关键模块（AI/搜索/存储）已通过接口解耦 |
| **文件存储一致性** | 双存储策略可能导致文件元数据与实际存储位置不一致 | `StorageRouter`统一管理存储路径，`doc_file.storage_type`记录实际存储位置 |
| **ES索引同步延迟** | MySQL数据变更后ES索引可能未及时更新 | 文件变更时主动触发索引同步；提供手动重建索引能力 |
| **协同编辑冲突** | 高并发编辑可能产生冲突 | Yjs CRDT算法天然支持无冲突合并；WebSocket连接管理包含心跳与重连机制 |
| **AI服务依赖** | 外部AI服务不可用时影响智能功能 | MQ异步执行+重试机制；AI服务独立配置，可按需启用/禁用 |
| **缓存一致性** | 多级缓存可能导致数据不一致 | 采用Cache-Aside模式，数据变更时主动失效缓存；L1 TTL短于L2 |
| **安全攻击面** | 文件上传可能携带恶意内容 | 验证码防暴力破解、敏感词检测、文件类型校验、大小限制 |

### 9.3 技术债务与优化方向

1. **前端管理后台**：admin子项目仅仪表盘和登录页有实际内容，其余模块为占位文件，需逐步完善
2. **单元测试覆盖**：当前测试依赖较少，需增加Service层单元测试
3. **API文档**：建议集成Swagger/SpringDoc生成API文档
4. **监控告警**：建议接入Prometheus + Grafana监控系统指标
5. **CI/CD流水线**：建议建立自动化构建、测试、部署流程
6. **容器化部署**：建议使用Docker + Docker Compose统一环境管理

---

## 附录

### A. 项目文件统计

| 维度 | 数量 |
|------|------|
| 后端Java文件 | 206个 |
| 前端TypeScript文件 | 6614个（含编译产物） |
| 后端Entity类 | 29个 |
| 后端Service类 | ~35个 |
| 后端Controller类 | ~25个 |
| 前端页面组件 | ~32个 |
| 前端API服务文件 | 16个 |
| Pinia Store | 4个 |
| 数据库表 | 30张 |
| RabbitMQ队列 | 12个（6业务+6死信） |
| Python微服务 | 1个（AI文档标签分类） |

### B. 关键文件路径

| 文件 | 路径 | 说明 |
|------|------|------|
| 后端主启动类 | `private-cloud-back/private_cloud_back/src/.../PrivateCloudBackApplication.java` | Spring Boot入口 |
| 后端配置文件 | `private-cloud-back/private_cloud_back/src/main/resources/application.properties` | 数据库/Redis/MQ等配置 |
| 前端入口 | `private-cloud-front/private-cloud/web/src/main.ts` | Vue应用入口 |
| 前端路由 | `private-cloud-front/private-cloud/web/src/router/index.ts` | 路由定义 |
| 协同服务器 | `private-cloud-front/private-cloud/collab-ws-server/server.js` | Yjs WS服务 |
| AI标签服务 | `ai-tag-service/server.py` | Python FastAPI文档分类微服务 |
| AI标签模型 | `ai-tag-service/doc_class_model/` | text2vec-base-chinese模型文件 |
| 管理后台 | `private-cloud-front/private-cloud/admin/` | Vue 3管理后台子项目 |
| 服务管理指南 | `服务管理指南.md` | 中间件启动/管理文档 |
| 数据库脚本 | `private-cloud-3.0.sql` | 数据库DDL+初始数据（30张表） |

### C. 参考文档

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Vue 3 官方文档](https://vuejs.org/)
- [Yjs 协同编辑文档](https://docs.yjs.dev/)
- [Elasticsearch 官方文档](https://www.elastic.co/guide/)
- [MinIO 官方文档](https://min.io/docs/)
- [RabbitMQ 官方文档](https://www.rabbitmq.com/documentation.html)
- [FastAPI 官方文档](https://fastapi.tiangolo.com/)
- [Hugging Face Transformers](https://huggingface.co/docs/transformers)
