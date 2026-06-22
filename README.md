# 私有云盘智能文档管理系统——项目说明文档

> **项目名称**：PrivateCloud（私有云盘智能文档管理系统）  
> **性质**：本科毕业设计 / 课程设计  
> **作者**：Danny  
> **日期**：2026 年 6 月

---

## 目录

- [一、项目概述](#一项目概述)
- [二、需求分析](#二需求分析)
- [三、系统设计](#三系统设计)
- [四、数据库设计](#四数据库设计)
- [五、功能模块详解](#五功能模块详解)
  - [5.1 用户认证与安全体系](#51-用户认证与安全体系)
  - [5.2 三级空间体系](#52-三级空间体系)
  - [5.3 文件管理](#53-文件管理)
  - [5.4 智能能力](#54-智能能力)
  - [5.5 全文搜索](#55-全文搜索)
  - [5.6 多人协同编辑](#56-多人协同编辑)
  - [5.7 安全分享](#57-安全分享)
  - [5.8 电子签章](#58-电子签章)
  - [5.9 通知中心](#59-通知中心)
  - [5.10 审批工作流](#510-审批工作流)
  - [5.11 个人工作台](#511-个人工作台)
  - [5.12 后台管理端](#512-后台管理端)
- [六、技术亮点](#六技术亮点)
- [七、部署说明](#七部署说明)
- [八、总结](#八总结)

---

## 一、项目概述

### 1.1 项目背景

PrivateCloud 是一款面向企业级用户的**私有云盘智能文档管理系统**，旨在为企业提供安全可控、可扩展的文档协作与知识管理平台。系统覆盖文档全生命周期管理——从创建、编辑、审批、归档到共享，并结合 AI 大模型能力实现智能标签提取、文档摘要、知识问答、模板生成等智能化功能。

### 1.2 技术栈

| 层级 | 技术选型 | 版本 |
|------|---------|------|
| **后端框架** | Spring Boot | 4.0.6 |
| **开发语言** | Java | 17 |
| **ORM** | Spring Data JPA (Hibernate) | — |
| **数据库** | MySQL | 8.0 (utf8mb4) |
| **缓存** | Redis + Caffeine（两级缓存） | — |
| **消息队列** | RabbitMQ（Spring AMQP） | — |
| **对象存储** | MinIO（可选，支持本地磁盘降级） | — |
| **搜索引擎** | Elasticsearch + IK 分词器 | 7.17 |
| **安全认证** | Spring Security + JWT + RSA + BCrypt | — |
| **前端框架** | Vue 3 + TypeScript + Vite | — |
| **协同编辑** | Yjs (CRDT) + Quill v2 + y-websocket | — |
| **AI 大模型** | DeepSeek / 智谱 GLM / 小米 MiMo | — |
| **OCR** | 百度云 OCR API | — |
| **语音识别** | 浏览器 Web Speech API（Chrome/Edge） | — |
| **文档处理** | Apache POI 5.2.5 / PDFBox 3.0.3 / EasyExcel 4.0.3 | — |
| **模板引擎** | FreeMarker | — |

### 1.3 项目结构

```
private-cloud-new/
├── private-cloud-back/          # 后端 Spring Boot 工程
│   └── private_cloud_back/
│       └── src/main/java/
│           ├── com/document/private_cloud_back/  # 应用入口
│           ├── front/                            # 前台业务模块
│           │   ├── cache/          # 两级缓存（Redis + Caffeine）
│           │   ├── collaboration/  # 多人协同编辑
│           │   ├── config/         # 启动检查
│           │   ├── esign/          # 电子签章
│           │   ├── hxconfig/       # 核心配置（JWT、安全、过滤器）
│           │   ├── intelligence/   # AI 智能模块
│           │   │   ├── ai/         # AI 标签、文档生成、对话
│           │   │   ├── comment/    # 评论
│           │   │   ├── notification/ # 通知
│           │   │   ├── ocr/       # OCR 文字识别
│           │   │   ├── preview/    # 预览转换
│           │   │   ├── search/     # 搜索热词
│           │   │   ├── signature/  # 签章记录
│           │   │   ├── speech/     # 语音识别（已废弃）
│           │   │   └── text/       # 全文提取
│           │   ├── mq/             # RabbitMQ 消息队列
│           │   ├── search/         # 搜索引擎
│           │   │   ├── engine/     # ES 全文检索
│           │   │   └── qa/         # AI 知识问答（RAG）
│           │   ├── storage/        # 存储路由（本地 + MinIO）
│           │   ├── system/         # 用户、角色、权限、审计
│           │   ├── workflow/       # 审批工作流
│           │   └── workspace/      # 文档空间
│           │       ├── documentspace/  # 文件/目录/分享/版本/回收站
│           │       └── personalworkspace/ # 工作台/收藏
│           └── back/admin/         # 后台管理端 API
├── private-cloud-front/          # 前端工程
│   └── private-cloud/
│       ├── web/                   # Web 前台（员工端）
│       ├── admin/                 # Admin 后台（管理员端）
│       └── collab-ws-server/      # Yjs WebSocket 协同服务器
└── resource/                      # 部署与配置文档
```

---

## 二、需求分析

### 2.1 功能需求总览

系统分为**前台用户系统**（面向企业员工）和**后台管理系统**（面向管理员）两大入口，按模块划分为：

| 模块分类 | 模块名称 | 说明 |
|---------|---------|------|
| 前台 | 文档管理与工作台 | 三级空间、分片上传、版本管理、回收站、收藏、工作台 |
| 前台 | 智能处理与内容生成 | AI 标签、AI 摘要、AI 文档生成、OCR、评论 |
| 前台 | 检索与知识问答 | ES 全文搜索、热词统计、RAG 知识问答 |
| 前台 | 外部协同与电子签章 | 分享链接、水印、签章、协同编辑 |
| 后台 | 数据仪表盘 | 存储趋势、文档类型分布、热门文档、搜索热词 |
| 后台 | 权限与安全合规 | RBAC 权限、在线用户管理、操作日志、访问日志 |
| 后台 | 工作流与审批管理 | 审批流设计、审批监控、管理员干预 |
| 后台 | 系统设置与拓展 | 部门/角色管理、敏感词过滤、缓存监控、存储配置 |

### 2.2 非功能需求

- **安全性**：JWT 无状态认证 + BCrypt 密码加密 + 盲索引去关联化 + IP 网络准入 + 登录防暴破
- **高可用**：RabbitMQ 死信队列 + 本地存储/MinIO 自动降级 + ES 空结果自动降级 MySQL
- **性能**：Redis + Caffeine 两级缓存，文件列表缓存 30 秒，权限缓存 10 分钟
- **可扩展**：多 AI 厂商热切换、存储策略可配置、MQ 可开关降级

---

## 三、系统设计

### 3.1 整体架构图

```
┌──────────────────────────────────────────────────────────────┐
│                        前端表现层                              │
│  ┌──────────────────┐  ┌──────────────────┐                  │
│  │  Web 前台 (Vue 3) │  │ Admin 后台 (Vue 3)│                  │
│  │  端口 5173        │  │  端口 5174        │                  │
│  └────────┬─────────┘  └────────┬─────────┘                  │
│           │    HTTP/REST        │                             │
├───────────┼────────────────────┼─────────────────────────────┤
│           ▼                     ▼                             │
│  ┌──────────────────────────────────────────────┐            │
│  │        Spring Boot 后端 (端口 8080)            │            │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐  │            │
│  │  │ JWT 认证  │ │ 权限过滤  │ │  IP 准入过滤  │  │            │
│  │  └──────────┘ └──────────┘ └──────────────┘  │            │
│  │  ┌──────────────────────────────────────┐    │            │
│  │  │        业务服务层                      │    │            │
│  │  │  文件管理 │ AI 智能 │ 搜索 │ 工作流 │ 签章 │    │            │
│  │  │  存储路由 │ 评论通知 │ 协同 │ 分享    │    │            │
│  │  └──────────────────────────────────────┘    │            │
│  └───────┬──────────┬──────────┬───────────────┘            │
│          │          │          │                              │
├──────────┼──────────┼──────────┼─────────────────────────────┤
│          ▼          ▼          ▼              数据层          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                      │
│  │ MySQL 8  │ │  Redis   │ │  MinIO   │                      │
│  │ 3306     │ │  6379    │ │  9000    │                      │
│  └──────────┘ └──────────┘ └──────────┘                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐              │
│  │  ES 7.17 │ │ RabbitMQ │ │ Yjs WS Server    │              │
│  │  9200    │ │  5672    │ │  端口 1234        │              │
│  └──────────┘ └──────────┘ └──────────────────┘              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 核心数据流

1. **文件上传流**：前端分片 → `ChunkUploadController` → `ChunkUploadService` 接收分片 → 合并文件 → `StorageRouter` 决策存储位置（本地 / MinIO）→ 写入 `doc_file` 表 → 发布 MQ 消息（OCR / 全文提取 / AI 标签）→ 异步消费处理
2. **搜索流**：用户输入关键词 → ES 全文检索（IK 分词 + BM25 评分）→ 空结果降级 MySQL LIKE → 关键词高亮 → 返回结果
3. **AI 问答流**：用户提问 → ES 检索相关文档（RAG）→ 构建 Prompt → 调用 AI 大模型 API → 返回带来源引用的回答
4. **通知推送流**：业务事件（评论 / 审批 / 分享）→ `NotificationHandler` 通过 WebSocket STOMP 推送到前端

### 3.3 安全架构

- **认证层**：`HxJwtFilter` 拦截所有 `/api/**` 请求，验证 JWT Token
- **授权层**：`PermissionFilter` 基于 RBAC 模型，检查用户角色与操作权限
- **网络层**：`IpAccessFilter` IP 白名单准入控制（校园网 / 指定网段）
- **数据层**：盲索引去关联化（`sys_user_credential.link_token`），切断用户与密码的明文 ID 关联
- **防暴破**：登录失败 5 次锁定 15 分钟
- **传输层**：RSA 加密传输敏感数据

---

## 四、数据库设计

### 4.1 核心 ER 关系

系统共设计 **20+ 张业务表**，按模块分为以下五组：

| 分组 | 表名 | 说明 |
|------|------|------|
| 用户与权限 | `sys_user`, `sys_user_credential`, `sys_role`, `sys_permission`, `sys_user_role`, `sys_role_permission`, `sys_department` | RBAC 权限模型 + 盲索引凭证分离 |
| 文档空间 | `doc_file`, `doc_directory`, `doc_file_version`, `doc_upload_task`, `recycle_bin`, `doc_share_link`, `doc_favorite` | 文件全生命周期管理 |
| 智能模块 | `doc_tag`, `doc_metadata`, `generated_doc`, `ocr_record`, `doc_comment`, `doc_progress_record` | AI 标签、OCR、评论、进度 |
| 搜索与问答 | `search_keyword`, `system_prompt`, `ai_chat_history` | 搜索热词、AI 系统提示词、对话历史 |
| 工作流与审计 | `wf_approval_request`, `signature_record`, `sys_notification`, `sys_operation_log`, `sys_file_access_log`, `sys_sensitive_word`, `collab_session` | 审批、签章、通知、日志、敏感词 |

### 4.2 核心表字段说明

#### 4.2.1 sys_user — 用户表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `username` | VARCHAR(50) | 用户名，唯一 |
| `password` | VARCHAR(255) | BCrypt 加密密码（迁移后仅作遗留字段） |
| `real_name` | VARCHAR(50) | 真实姓名 |
| `email` | VARCHAR(100) | 邮箱 |
| `phone` | VARCHAR(20) | 手机号 |
| `avatar` | VARCHAR(255) | 头像 URL |
| `department_id` | BIGINT | 所属部门 ID |
| `status` | INTEGER | 状态：1-正常，0-禁用 |
| `storage_quota` | BIGINT | 存储配额（默认 10GB = 10737418240） |
| `workspace_layout` | TEXT | 工作台布局配置（JSON） |
| `deleted` | INTEGER | 逻辑删除标记，默认 0 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

#### 4.2.2 sys_user_credential — 用户凭证表（盲索引分离）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `link_token` | VARCHAR(64) | **主键**，HMAC-SHA256(user_id, secret)，切断与 sys_user 的明文 ID 关联 |
| `password` | VARCHAR(255) | BCrypt 加密密码 |
| `last_login_time` | DATETIME | 最后登录时间 |
| `last_login_ip` | VARCHAR(64) | 最后登录 IP |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

> **安全设计**：拖库后凭证表中只有无规律的 `link_token` 和 BCrypt 密文，缺少服务器 `blind-index-secret` 无法将密码对应到具体用户。

#### 4.2.3 sys_role — 角色表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `role_name` | VARCHAR(50) | 角色名称，唯一 |
| `role_code` | VARCHAR(50) | 角色编码，唯一（如 `ROLE_ADMIN`、`ROLE_USER`） |
| `description` | VARCHAR(255) | 角色描述 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

#### 4.2.4 sys_permission — 权限表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `permission_name` | VARCHAR(100) | 权限名称 |
| `permission_code` | VARCHAR(100) | 权限编码，唯一 |
| `parent_id` | BIGINT | 父级权限 ID |
| `type` | INTEGER | 权限类型 |
| `sort_order` | INTEGER | 排序序号 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.5 sys_department — 部门表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `dept_name` | VARCHAR(100) | 部门名称 |
| `parent_id` | BIGINT | 父级部门 ID，默认 0 |
| `sort_order` | INTEGER | 排序序号 |
| `company_id` | BIGINT | 所属公司 ID（多租户隔离） |

#### 4.2.6 doc_file — 文件表（核心表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_name` | VARCHAR(255) | 文件名 |
| `file_type` | VARCHAR(50) | 文件类型（扩展名） |
| `file_size` | BIGINT | 文件大小（字节） |
| `file_path` | VARCHAR(500) | 文件存储路径（本地路径或 MinIO 对象名） |
| `storage_type` | VARCHAR(20) | 存储类型：`local` / `minio` |
| `md5` | VARCHAR(64) | 文件 MD5 哈希值（用于秒传） |
| `fulltext_content` | LONGTEXT | 全文内容（OCR 提取 / 文本解析后写入） |
| `directory_id` | BIGINT | 所属目录 ID |
| `department_id` | BIGINT | 所属部门 ID |
| `space_type` | INTEGER | 空间类型：0-个人空间，1-部门空间，2-企业空间 |
| `space_id` | BIGINT | 空间 ID（个人空间为 user_id，部门空间为 dept_id） |
| `uploader_id` | BIGINT | 上传者 ID |
| `uploader_name` | VARCHAR(100) | 上传者姓名（冗余） |
| `version` | INTEGER | 当前版本号，默认 1 |
| `view_count` | INTEGER | 浏览次数 |
| `download_count` | INTEGER | 下载次数 |
| `status` | INTEGER | 状态：1-正常，0-隐藏 |
| `preview_status` | VARCHAR(20) | 预览转换状态：`NOT_STARTED` / `PROCESSING` / `DONE` / `FAILED` |
| `preview_pdf_path` | VARCHAR(500) | 预览 PDF 路径 |
| `mindmap_content` | LONGTEXT | AI 生成的脑图内容（Markdown 格式） |
| `deleted` | INTEGER | 逻辑删除标记，默认 0 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

**索引设计**：`idx_directory_id`、`idx_department_id`、`idx_deleted_status`、`idx_directory_deleted_status`、`idx_space`

#### 4.2.7 doc_directory — 目录表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `dir_name` | VARCHAR(200) | 目录名称 |
| `parent_id` | BIGINT | 父级目录 ID |
| `department_id` | BIGINT | 所属部门 ID |
| `space_type` | INTEGER | 空间类型 |
| `space_id` | BIGINT | 空间 ID |
| `owner_id` | BIGINT | 拥有者 ID |
| `sort_order` | INTEGER | 排序序号 |
| `deleted` | INTEGER | 逻辑删除标记 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

**索引设计**：`idx_dir_space(space_type, space_id, parent_id)`

#### 4.2.8 doc_file_version — 文件版本表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_id` | BIGINT | 关联文件 ID |
| `version` | INTEGER | 版本号 |
| `file_path` | VARCHAR(500) | 该版本文件存储路径 |
| `file_size` | BIGINT | 该版本文件大小 |
| `operator_id` | BIGINT | 操作者 ID |
| `change_note` | VARCHAR(500) | 变更说明 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.9 doc_upload_task — 分片上传任务表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_id` | VARCHAR(64) | 文件唯一标识（MD5） |
| `file_name` | VARCHAR(255) | 文件名 |
| `total_chunks` | INTEGER | 分片总数 |
| `received_chunks` | INTEGER | 已接收分片数 |
| `file_size` | BIGINT | 文件总大小 |
| `status` | INTEGER | 任务状态：0-上传中，1-已完成 |
| `uploader_id` | BIGINT | 上传者 ID |
| `directory_id` | BIGINT | 目标目录 ID |
| `department_id` | BIGINT | 所属部门 ID |
| `mode` | VARCHAR(20) | 上传模式：`new` / `update` |
| `update_file_id` | BIGINT | 更新模式下的目标文件 ID |
| `space_type` | INTEGER | 空间类型 |
| `space_id` | BIGINT | 空间 ID |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

#### 4.2.10 recycle_bin — 回收站表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `item_type` | VARCHAR(10) | 项目类型：`file` / `directory` |
| `item_id` | BIGINT | 项目 ID |
| `item_name` | VARCHAR(500) | 项目名称（冗余） |
| `deleted_by` | BIGINT | 删除者 ID |
| `expire_time` | DATETIME | 过期时间（自动清理） |
| `create_time` | DATETIME | 删除时间 |

#### 4.2.11 doc_share_link — 分享链接表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_id` | BIGINT | 文件 ID |
| `creator_id` | BIGINT | 创建者 ID |
| `token` | VARCHAR(255) | 分享 Token，唯一 |
| `password` | VARCHAR(255) | 访问密码（可选） |
| `expire_time` | DATETIME | 过期时间 |
| `max_access` | INTEGER | 最大访问次数，0 表示不限 |
| `access_count` | INTEGER | 已访问次数 |
| `permission_type` | VARCHAR(20) | 权限类型：`view` / `download` |
| `status` | INTEGER | 状态：1-有效，0-已关闭 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.12 doc_favorite — 收藏表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `user_id` | BIGINT | 用户 ID |
| `target_id` | BIGINT | 收藏目标 ID |
| `target_type` | INTEGER | 目标类型：1-文件，2-文件夹 |
| `create_time` | DATETIME | 收藏时间 |

#### 4.2.13 doc_tag — 文档标签表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_id` | BIGINT | 文件 ID |
| `tag_name` | VARCHAR(100) | 标签名称 |
| `tag_source` | VARCHAR(20) | 标签来源：`ai` / `manual` |
| `confidence` | DECIMAL(5,2) | 置信度 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.14 doc_metadata — 文档元数据表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_id` | BIGINT | 文件 ID |
| `tag_key` | VARCHAR(100) | 元数据键（如"合同金额"、"甲方"） |
| `tag_value` | VARCHAR(500) | 元数据值 |
| `confidence` | DOUBLE | 置信度 |
| `source_model` | VARCHAR(50) | 来源模型名称 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.15 generated_doc — AI 生成文档表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_name` | VARCHAR(255) | 文件名 |
| `title` | VARCHAR(255) | 文档标题 |
| `template_id` | VARCHAR(100) | 模板 ID |
| `template_name` | VARCHAR(100) | 模板名称 |
| `file_path` | VARCHAR(500) | 生成文件存储路径 |
| `content` | LONGTEXT | 生成内容 |
| `model` | VARCHAR(50) | 使用的 AI 模型 |
| `department_id` | BIGINT | 所属部门 |
| `creator_id` | BIGINT | 创建者 ID |
| `creator_name` | VARCHAR(100) | 创建者姓名 |
| `status` | INTEGER | 状态：1-成功，0-失败 |
| `fail_reason` | VARCHAR(1000) | 失败原因 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.16 ocr_record — OCR 识别记录表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_id` | BIGINT | 文件 ID |
| `page_number` | INTEGER | 页码 |
| `status` | INTEGER | 状态：0-处理中，1-成功，2-失败 |
| `ocr_text` | LONGTEXT | OCR 识别文本 |
| `error_message` | VARCHAR(500) | 错误信息 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

#### 4.2.17 doc_comment — 文档评论表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `file_id` | BIGINT | 文件 ID |
| `user_id` | BIGINT | 评论者 ID |
| `content` | TEXT | 评论内容 |
| `parent_id` | BIGINT | 父评论 ID（支持嵌套回复） |
| `mentions` | VARCHAR(500) | @提及用户 ID 列表 |
| `department_id` | BIGINT | 所属部门 |
| `quote_text` | TEXT | 引用文本（批注） |
| `deleted_by` | BIGINT | 删除者 ID |
| `delete_reason` | VARCHAR(500) | 删除原因 |
| `deleted` | INTEGER | 逻辑删除标记 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

#### 4.2.18 wf_approval_request — 审批请求表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `applicant_id` | BIGINT | 申请人 ID |
| `document_id` | BIGINT | 关联文档 ID |
| `title` | VARCHAR(255) | 审批标题 |
| `type` | VARCHAR(50) | 审批类型 |
| `status` | INTEGER | 状态：0-待审批，1-审批中，2-已通过，3-已驳回 |
| `current_step` | INTEGER | 当前步骤 |
| `stamped_file_id` | BIGINT | 签章后文件 ID |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

#### 4.2.19 signature_record — 签章记录表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `document_id` | BIGINT | 文档 ID |
| `signer_id` | BIGINT | 签署人 ID |
| `signer_name` | VARCHAR(100) | 签署人姓名 |
| `sign_time` | DATETIME | 签署时间 |
| `seal_image_url` | VARCHAR(500) | 印章图片 URL |
| `signature_hash` | VARCHAR(128) | 签名哈希 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.20 sys_notification — 通知表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `receiver_id` | BIGINT | 接收者用户 ID |
| `type` | VARCHAR(30) | 通知类型（comment / approval / share / system） |
| `title` | VARCHAR(200) | 通知标题 |
| `content` | TEXT | 通知内容 |
| `from_user_id` | BIGINT | 触发者用户 ID |
| `from_username` | VARCHAR(50) | 触发者用户名 |
| `file_id` | BIGINT | 关联文件 ID |
| `file_name` | VARCHAR(255) | 关联文件名 |
| `comment_id` | BIGINT | 关联评论 ID |
| `related_id` | BIGINT | 关联业务 ID |
| `is_read` | INTEGER | 是否已读，默认 0 |
| `read_time` | DATETIME | 阅读时间 |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.21 sys_operation_log — 操作日志表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `user_id` | BIGINT | 操作用户 ID |
| `username` | VARCHAR(50) | 操作用户名（冗余） |
| `module` | VARCHAR(30) | 操作模块：AUTH / USER / FILE / ROLE / SYSTEM |
| `operation` | VARCHAR(100) | 操作类型：LOGIN / CREATE / UPDATE / DELETE / VIEW / DOWNLOAD 等 |
| `target_type` | VARCHAR(30) | 操作目标类型 |
| `target_id` | BIGINT | 操作目标 ID |
| `detail` | TEXT | 操作详情 |
| `status` | VARCHAR(10) | 操作结果：success / fail |
| `ip` | VARCHAR(50) | 请求 IP |
| `user_agent` | VARCHAR(500) | 请求 User-Agent |
| `create_time` | DATETIME | 创建时间 |

#### 4.2.22 sys_file_access_log — 文件访问日志表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `user_id` | BIGINT | 访问用户 ID |
| `file_id` | BIGINT | 文件 ID |
| `access_type` | VARCHAR(20) | 访问类型：preview / download / edit |
| `ip` | VARCHAR(50) | 访问 IP |
| `create_time` | DATETIME | 访问时间 |

#### 4.2.23 search_keyword — 搜索关键词统计表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `keyword` | VARCHAR(200) | 搜索关键词，唯一 |
| `search_count` | INTEGER | 搜索次数 |
| `user_id` | BIGINT | 最后搜索用户 ID |
| `is_hot` | INTEGER | 是否热词：0-否，1-是 |
| `deleted` | INTEGER | 逻辑删除标记 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

#### 4.2.24 collab_session — 协同编辑会话表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT | 主键，自增 |
| `document_id` | BIGINT | 关联文档 ID |
| `session_id` | VARCHAR(64) | 会话 ID，唯一 |
| `room_name` | VARCHAR(200) | 房间名称 |
| `status` | INTEGER | 状态：0-活跃，1-已关闭 |
| `permission_mode` | VARCHAR(20) | 权限模式：`editable` / `readonly` |
| `owner_id` | BIGINT | 房主 ID |
| `content` | LONGTEXT | 协同编辑内容（Quill HTML 格式） |
| `create_time` | DATETIME | 创建时间 |
| `close_time` | DATETIME | 关闭时间 |

#### 4.2.25 其他辅助表

| 表名 | 说明 |
|------|------|
| `doc_progress_record` | 用户文件阅读/播放进度（联合唯一索引 `uk_user_file_type`） |
| `ai_chat_history` | AI 对话历史记录（按 session_id 分组） |
| `system_prompt` | AI 系统提示词模板（预设 + 用户自定义） |
| `sys_sensitive_word` | 敏感词词库（支持分类、级别、启用/禁用） |

---

## 五、功能模块详解

### 5.1 用户认证与安全体系

**功能描述**：系统不对外开放注册，所有账号由管理员在后台统一创建。用户通过用户名 + 密码登录，支持滑块验证码防暴破。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `HxJwtFilter` | `front/hxconfig/filter/` | JWT 认证过滤器，拦截 `/api/**` |
| `PermissionFilter` | `front/hxconfig/filter/` | RBAC 权限过滤器 |
| `IpAccessFilter` | `front/hxconfig/filter/` | IP 网络准入过滤器 |
| `SysUserCredential` | `front/system/entity/` | 盲索引凭证实体，link_token 为主键 |
| `DataMigrationRunner` | `front/system/` | 启动时自动迁移密码到凭证表 |

**安全特性**：
- JWT 无状态认证，Token 包含用户 ID、角色、过期时间
- BCrypt 加密密码存储
- 盲索引去关联化：`sys_user_credential.link_token = HMAC-SHA256(user_id, secret)`，拖库后无法关联用户与密码
- 登录失败 5 次锁定 15 分钟
- RSA 加密传输敏感数据
- 滑块验证码防机器人

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/auth/login` | 用户登录 |
| POST | `/api/front/auth/logout` | 用户登出 |
| GET | `/api/front/auth/user-info` | 获取当前用户信息 |

---

### 5.2 三级空间体系

**功能描述**：系统将文件组织为三级空间结构——个人空间（space_type=0）、部门空间（space_type=1）、企业空间（space_type=2）。每个空间独立管理目录树和文件，通过 `space_type` + `space_id` 隔离。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `DocFile` | `front/workspace/documentspace/entity/` | 文件实体，含 `space_type`、`space_id` 字段 |
| `DocDirectory` | `front/workspace/documentspace/entity/` | 目录实体，含 `space_type`、`space_id` 字段 |
| `DocDirectoryService` | `front/workspace/documentspace/service/` | 目录树 CRUD 与拖拽排序 |

**关键 SQL**：
```sql
-- 查询某空间下的文件列表
SELECT * FROM doc_file 
WHERE space_type = ? AND space_id = ? AND directory_id = ? 
  AND deleted = 0 AND status = 1
ORDER BY create_time DESC;

-- 查询某空间下的目录树
SELECT * FROM doc_directory 
WHERE space_type = ? AND space_id = ? AND deleted = 0
ORDER BY sort_order ASC;
```

**空间隔离规则**：
- 个人空间：`space_id = user_id`，仅本人可见
- 部门空间：`space_id = department_id`，部门成员可见
- 企业空间：`space_id = 0`，全公司可见

---

### 5.3 文件管理

**功能描述**：覆盖文件全生命周期——上传（分片/断点/秒传）、预览、版本管理、回收站、收藏、批量下载。

#### 5.3.1 分片上传 / 断点续传 / 秒传

**实现原理**：
1. 前端将文件切分为 5MB 分片，计算文件 MD5
2. 上传前调用 `checkMd5` 接口，若已存在则直接秒传（跳过上传）
3. 每个分片独立上传，后端记录到 `doc_upload_task` 表
4. 断点续传：前端通过 `received_chunks` 字段获知已上传分片，跳过已上传部分
5. 所有分片完成后调用合并接口，后端合并文件并写入 `doc_file` 表

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `ChunkUploadController` | `front/workspace/documentspace/controller/` | 分片上传控制器 |
| `ChunkUploadService` | `front/workspace/documentspace/service/` | 分片上传业务逻辑 |
| `DocUploadTask` | `front/workspace/documentspace/entity/` | 上传任务实体 |
| `StorageRouter` | `front/storage/service/` | 存储路由，决定本地/MinIO |

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/upload/chunk` | 上传单个分片 |
| GET | `/api/front/upload/check-md5` | 检查 MD5 是否存在（秒传） |
| POST | `/api/front/upload/merge` | 合并分片，完成上传 |
| GET | `/api/front/upload/progress/{fileId}` | 查询上传进度 |

#### 5.3.2 版本管理

**实现原理**：每次编辑保存时，将旧版本文件复制到 `doc_file_version` 表，`doc_file.version` 自增。用户可在文件详情页查看历史版本列表并回滚。

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/front/files/{fileId}/versions` | 获取版本列表 |
| POST | `/api/front/files/{fileId}/versions/rollback/{versionId}` | 回滚到指定版本 |

#### 5.3.3 回收站

**实现原理**：删除操作执行软删除（`deleted=1`），同时写入 `recycle_bin` 表记录过期时间（默认 30 天）。定时任务定期清理过期记录。

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/front/recycle-bin/list` | 回收站列表 |
| POST | `/api/front/recycle-bin/restore/{id}` | 还原文件 |
| DELETE | `/api/front/recycle-bin/permanent/{id}` | 彻底删除 |

#### 5.3.4 收藏

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/favorites/toggle` | 切换收藏状态 |
| GET | `/api/front/favorites/list` | 我的收藏列表 |

#### 5.3.5 标签

**实现原理**：支持手动标签和 AI 自动标签两种来源。AI 标签在文件上传后由 MQ 异步消费生成。标签云展示时按标签出现次数统计，12 种随机配色胶囊展示。

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/front/ai/tags/cloud` | 获取标签云（GROUP BY 聚合统计） |
| GET | `/api/front/ai/tags/file-ids?tagName=xxx` | 按标签名查找关联文件 |

---

### 5.4 智能能力

**功能描述**：集成 DeepSeek / 智谱 GLM / 小米 MiMo 三家大模型，提供 AI 标签提取、AI 摘要、AI 文档生成、AI 对话问答（RAG）等智能功能。

#### 5.4.1 AI 标签与元数据提取

**实现原理**：文件上传后，RabbitMQ 异步消费 `QUEUE_AI_GENERATE` 消息，调用大模型 API 提取文档关键元数据（如合同金额、甲方名称等），写入 `doc_tag` 和 `doc_metadata` 表。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `AiGenerateService` | `front/intelligence/ai/service/` | AI 标签/元数据生成服务 |
| `DocTag` | `front/intelligence/ai/entity/` | 标签实体 |
| `DocMetadata` | `front/intelligence/ai/entity/` | 元数据实体 |

#### 5.4.2 AI 文档生成

**实现原理**：用户选择模板（如合同模板、对账单模板），填写表单参数，后端通过 FreeMarker 模板引擎 + AI 大模型填充内容，生成 Word 文档。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `GeneratedDoc` | `front/intelligence/ai/entity/` | 生成文档实体 |
| `AiDocGenerateService` | `front/intelligence/ai/service/` | AI 文档生成服务 |

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/ai/documents/generate` | 提交文档生成任务 |
| GET | `/api/front/ai/documents/generated/list` | 生成记录列表 |
| GET | `/api/front/ai/documents/generated/download/{id}` | 下载生成的文档 |

#### 5.4.3 AI 对话问答（RAG）

**实现原理**：用户提问 → ES 检索相关文档（IK 分词 + BM25 评分）→ 构建 Prompt（System Prompt + 检索到的文档片段）→ 调用大模型 API → 返回带来源引用的回答。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `KnowledgeQAService` | `front/search/qa/service/` | RAG 问答服务 |
| `SystemPrompt` | `front/search/qa/entity/` | 系统提示词模板 |
| `AiChatHistory` | `front/intelligence/ai/entity/` | 对话历史记录 |

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/search/qa/ask` | 提交问答请求 |
| GET | `/api/front/search/qa/history` | 获取对话历史 |
| GET | `/api/front/search/qa/prompts` | 获取可用提示词模板 |

#### 5.4.4 OCR 文字识别

**实现原理**：文件上传后，RabbitMQ 异步消费 `QUEUE_FILE_OCR` 消息，调用百度云 OCR API 识别图片/扫描件中的文字，结果写入 `ocr_record` 表和 `doc_file.fulltext_content`。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `OcrRecord` | `front/intelligence/ocr/entity/` | OCR 记录实体 |
| `BaiduOcrService` | `front/intelligence/ocr/service/` | 百度云 OCR 服务 |

#### 5.4.5 语音录入

**实现方式**：已简化实现。前端直接使用浏览器 Web Speech API（SpeechRecognition），无需后端 API 支持。适用于 Chrome/Edge 80+ 浏览器。原来的后端百度语音识别模块（`BaiduSpeechConfig`）保留在代码中但不再使用。

---

### 5.5 全文搜索

**功能描述**：基于 Elasticsearch 7.17 + IK 分词器的全文搜索，支持多条件交叉过滤、BM25 匹配度评分、关键词高亮、自动补全、热词统计。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `DocFileIndexService` | `front/search/engine/es/` | ES 索引同步服务，启动时全量重建 |
| `DocFileSearchRepository` | `front/search/engine/es/` | ES 搜索仓库 |
| `SearchKeyword` | `front/intelligence/search/entity/` | 搜索关键词统计实体 |

**搜索流程**：
1. 用户输入关键词 → ES IK 分词检索（BM25 评分，百分制映射）
2. 空结果自动降级 MySQL LIKE 搜索
3. 关键词高亮（红色 `<em>` 包裹）
4. 自动补全（双源合并：MySQL 搜索历史 + ES 文档标题前缀匹配）
5. 搜索关键词记录到 `search_keyword` 表，统计热词

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/search/advanced` | 高级搜索（多条件 + 全文） |
| GET | `/api/front/search/suggest?keyword=xxx` | 搜索自动补全 |
| GET | `/api/front/search/hot-keywords` | 热词排行 |
| POST | `/api/front/search/index/rebuild` | 手动重建 ES 索引 |

**ES 索引同步策略**：
- 启动时自动全量重建（`@PostConstruct`）
- 文档上传/更新/提取文字后自动增量同步
- 支持手动 POST `/index/rebuild`

---

### 5.6 多人协同编辑

**功能描述**：基于 Yjs (CRDT) + Quill v2 的多人实时协同编辑，支持光标同步、在线用户感知、自动保存、历史版本。

**技术架构**：
- CRDT 引擎：Yjs v13（无冲突复制数据类型），多人并发编辑无冲突合并
- 富文本编辑器：Quill v2 + y-quill 绑定
- 实时通信：独立 y-websocket 同步服务器（Node.js，端口 1234）
- 数据持久化：后端 Spring Boot REST API → MySQL（`collab_session` 表）

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `CollabSession` | `front/collaboration/entity/` | 协同会话实体 |
| `CollabSessionController` | `front/collaboration/controller/` | 协同会话 API |

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/collaboration/sessions` | 创建协同会话 |
| GET | `/api/front/collaboration/sessions` | 获取活跃会话列表 |
| GET | `/api/front/collaboration/sessions/{sessionId}` | 获取会话详情 |
| PUT | `/api/front/collaboration/sessions/{sessionId}/content` | 保存会话内容 |
| DELETE | `/api/front/collaboration/sessions/{sessionId}` | 关闭会话 |

---

### 5.7 安全分享

**功能描述**：生成带有效期、访问次数限制、动态密码的外发分享链接，支持权限分级（view/download），外部访客无需登录即可预览。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `DocShareLink` | `front/workspace/documentspace/entity/` | 分享链接实体 |
| `ShareLinkController` | `front/workspace/documentspace/controller/` | 分享链接 API |

**安全机制**：
- 分享链接 Token 随机生成
- 可选访问密码（BCrypt 存储）
- 有效期限制（过期自动失效）
- 最大访问次数限制（超出后自动关闭）
- 权限分级：`view`（仅查看）/ `download`（可下载）
- 外部预览页叠加动态水印（访客标识 + 时间戳）
- 防截图：禁止右键菜单、屏蔽 F12/Ctrl+Shift+I

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/front/share-links` | 创建分享链接 |
| GET | `/api/front/share-links/token/{token}` | 按 Token 查分享链接 |
| GET | `/api/front/share-links/file/{fileId}` | 按文件查分享链接 |
| GET | `/api/front/share-links/mine/all` | 我的分享列表 |
| DELETE | `/api/front/share-links/{linkId}` | 关闭分享链接 |
| POST | `/api/front/share-links/access/{token}` | 记录访问 |

---

### 5.8 电子签章

**功能描述**：模拟级电子签章，不对接真实 CA。审批通过后，使用 Java Graphics2D 绘制红色圆形印章，通过 Apache PDFBox 覆盖到 PDF 文件每一页右下角。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `SignatureRecord` | `front/intelligence/signature/entity/` | 签章记录实体 |
| `StampService` | `front/esign/service/` | 印章绘制与盖章服务 |

**签章流程**：
1. 用户提交签章申请（`ApprovalSubmit.vue`）
2. 审批通过后，调用 `StampService` 绘制印章
3. 印章内容：企业名称 + 五角星 + "已签署" + 签署人 + 日期
4. 通过 PDFBox 将印章覆盖到 PDF 每一页右下角
5. 记录到 `signature_record` 表

**格式转换**：安装 LibreOffice 后支持 doc/docx/xls/xlsx/ppt/pptx 等格式自动转 PDF 再盖章；未安装时通过 ZIP/XML 解析降级处理。

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/esign/stamp/{documentId}?signer=张三` | 对文档盖章 |

**实现状态**：模拟级实现，印章为演示用途，不具备法律效力。

---

### 5.9 通知中心

**功能描述**：站内通知 + 邮件通知（已简化实现），基于 WebSocket STOMP 实时推送。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `SysNotification` | `front/intelligence/notification/entity/` | 通知实体 |
| `NotificationHandler` | `front/intelligence/notification/` | 通知推送处理器 |

**通知类型**：
- `comment`：评论与 @ 提及
- `approval`：审批状态变更
- `share`：分享链接相关
- `system`：系统公告

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/front/notifications/list` | 通知列表 |
| PUT | `/api/front/notifications/{id}/read` | 标记已读 |
| GET | `/api/front/notifications/unread-count` | 未读数量 |

**邮件通知**：已简化实现，邮件发送模块保留在代码中但当前默认使用站内通知。

---

### 5.10 审批工作流

**功能描述**：自建审批工作流，不集成 Flowable。实现串行审批（申请人 → 审批人），支持管理员强制干预（终止、驳回、重新指派）。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `ApprovalRequest` | `front/workflow/entity/` | 审批请求实体 |
| `ApprovalController` | `front/workflow/controller/` | 审批 API |

**审批流程**：
1. 用户在签章页面（`ApprovalSubmit.vue`）提交审批请求
2. 审批人收到通知，进行审批操作
3. 审批通过后自动触发签章
4. 管理员可在后台监控所有审批状态，进行强制干预

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/workflow/approval/convertible-files` | 获取可转换为 PDF 的文件列表 |
| POST | `/api/workflow/approval/submit` | 提交审批请求 |
| GET | `/api/workflow/approval/my-requests` | 我的审批记录 |

**实现状态**：简化实现。自建审批表实现串行流转，审批步骤（`approval_step` 表）通过 `wf_approval_request` 的 `current_step` 字段控制。

---

### 5.11 个人工作台

**功能描述**：用户首页展示统计仪表盘，包括文档总数、存储占用、最近访问文件、待办审批、收藏列表。

**关键类**：
| 类名 | 路径 | 说明 |
|------|------|------|
| `PersonalWorkspaceService` | `front/workspace/personalworkspace/service/` | 工作台服务 |
| `DocFavorite` | `front/workspace/personalworkspace/entity/` | 收藏实体 |

**统计指标**：
- 文档总数（按空间类型分组）
- 存储空间占用（总量 + 已用 + 剩余）
- 最近访问文件列表
- 待办审批数量
- 未读通知数量

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/front/workspace/stats` | 获取工作台统计数据 |
| GET | `/api/front/workspace/recent-files` | 最近访问文件 |

---

### 5.12 后台管理端

**功能描述**：面向管理员的独立后台入口（端口 5174），提供数据仪表盘、权限管理、安全审计、工作流监控、系统设置等功能。

#### 5.12.1 数据仪表盘

- ECharts 存储容量趋势与今日文件吞吐量统计
- 各类型文档占比可视化图表
- 系统热门文档排行
- 员工搜索热词统计

**关键接口**：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/dashboard/overview` | 系统概览数据 |
| GET | `/api/admin/dashboard/storage-trend` | 存储趋势 |
| GET | `/api/admin/dashboard/hot-docs` | 热门文档 |
| GET | `/api/admin/dashboard/hot-keywords` | 搜索热词 |

#### 5.12.2 权限与安全合规

- **RBAC 权限管理**：角色、权限的增删改查，用户角色分配
- **在线用户管理**：实时监控在线用户，支持强制踢出下线
- **操作日志**：全局操作日志（上传、删除、分享、权限变更），支持按用户/时间/模块检索
- **文件访问日志**：记录用户访问文件的时间、IP、操作类型（预览/下载/编辑），支持导出
- **过期文件自动销毁**：定时任务扫描并删除/冻结过期文件

#### 5.12.3 工作流与审批管理

- 全局审批进度可视化（查看所有审批单状态、当前步骤）
- 管理员强制干预：终止、驳回、重新指派审批人
- 审批流设计（自建审批表，串行流转）

#### 5.12.4 系统设置与拓展

- **部门与公司管理**：多层级部门增删改查，多公司（多租户）隔离
- **敏感词过滤**：敏感词词库管理（`sys_sensitive_word` 表），支持分类、级别、启用/禁用
- **缓存监控**：Redis 运行状态（内存占用、键数量、命中率），支持手动清除指定缓存
- **存储配置**：本地存储 / MinIO 配置文件切换

---

## 六、技术亮点

### 6.1 存储抽象：StorageRouter

`StorageRouter` 是文件存储的核心路由组件，同时支持本地磁盘与 MinIO 对象存储，并具备以下特性：

- **自动选路**：根据 `minio.enabled` 配置和文件大小阈值 `minio.min-size` 决定新文件存储位置
- **嗅探降级**：`looksLikeLocalPath()` 方法通过检测路径中是否含反斜杠、盘符、`./`、`uploads` 关键词等特征，自动识别本地路径，修复历史数据中 `storageType` 与 `filePath` 不一致的问题
- **MinIO 故障降级**：MinIO 上传失败时抛出 `StorageFallbackException`，调用方捕获后使用本地存储重试
- **统一接口**：`download()`、`delete()`、`exists()`、`copy()` 等方法对内屏蔽存储差异，对外暴露统一 API

```java
// 关键代码：路径嗅探逻辑
private boolean looksLikeLocalPath(String filePath) {
    if (filePath.indexOf('\\') >= 0) return true;       // 含反斜杠
    if (filePath.startsWith("./") || filePath.startsWith(".\\")) return true;
    if (filePath.length() >= 2 && filePath.charAt(1) == ':') return true; // 盘符
    if (filePath.contains("uploads")) return true;       // 本地存储目录特征
    return false;
}
```

### 6.2 多级缓存：Redis + Caffeine

系统采用两级缓存策略，兼顾性能与一致性：

- **L1 缓存（Caffeine）**：本地内存缓存，极低延迟，适用于热点数据（如用户会话、权限信息）
- **L2 缓存（Redis）**：分布式缓存，支持多实例共享，适用于目录树、部门信息、热词统计
- **缓存策略**：文件列表缓存 30 秒，权限缓存 10 分钟，文档更新时主动失效相关缓存
- **管理员监控**：后台提供缓存监控界面，查看键数量、内存占用、命中率，支持手动清除

### 6.3 异步处理：RabbitMQ 解耦

RabbitMQ 作为消息中间件，将耗时任务从主流程解耦，提升用户体验：

| 队列 | 路由键 | 用途 |
|------|--------|------|
| `QUEUE_FILE_OCR` | `task.file.ocr` | 百度 OCR 文字识别 |
| `QUEUE_FILE_FULLTEXT` | `task.file.fulltext` | 全文文本提取 |
| `QUEUE_FILE_OFFICE_CONVERT` | `task.file.office.convert` | Office 转 PDF 预览 |
| `QUEUE_AI_GENERATE` | `task.ai.generate` | AI 标签 / 元数据 / 摘要生成 |
| `QUEUE_FILE_MINDMAP` | `task.file.mindmap` | AI 脑图生成 |
| `QUEUE_SYS_AUDIT_LOG` | `task.sys.audit.log` | 审计日志异步写入 |

**高可用设计**：
- 每个队列配置死信交换机（DLX），任务失败后进入 DLQ 等待人工处理
- `mq.enabled` 配置开关，支持降级为同步处理

### 6.4 多 AI 厂商适配

系统通过统一的 `@ConfigurationProperties` 配置类实现多 AI 厂商热切换：

| 配置类 | 配置前缀 | 厂商 | 模型 |
|--------|---------|------|------|
| `DeepSeekConfig` | `deepseek` | DeepSeek | `deepseek-v4-flash` / `deepseek-v4-pro` |
| `GlmConfig` | `glm` | 智谱 AI | `glm-4.7-flash` |
| `MimoConfig` | `mimo` | 小米 MiMo | `mimo-v2.5` / `mimo-v2.5-pro` |

每个配置类封装了 `apiKey`、`baseUrl`、模型名称，服务层通过 `AiModelRouter` 按配置热切换调用目标厂商。切换 AI 厂商只需修改 `application.yml` 中的 `ai.provider` 配置项，无需改动代码。

### 6.5 安全体系

- **RSA + JWT 双因子认证**：RSA 加密传输登录凭证，JWT 无状态会话管理
- **滑块验证码**：防机器人登录
- **IP 访问过滤**：`IpAccessFilter` 实现 IP 白名单准入控制（校园网/指定网段）
- **权限拦截器**：`PermissionFilter` 基于 RBAC 模型，检查用户角色与操作权限
- **盲索引去关联化**：`sys_user_credential` 表使用 HMAC-SHA256 生成的 `link_token` 作为主键，彻底切断与用户表的明文 ID 关联
- **BCrypt 密码加密**：不可逆哈希存储
- **防暴破**：登录失败 5 次锁定 15 分钟
- **动态水印**：Canvas 生成旋转水印覆盖层，防止截屏泄露

### 6.6 ES 搜索降级策略

- 主搜索路径：Elasticsearch 7.17 + IK 分词器 + BM25 评分
- 降级路径：ES 空结果自动降级 MySQL LIKE 搜索
- 索引同步：启动时全量重建 + 运行时增量同步
- 高亮支持：ES HighlightBuilder / MySQL LIKE 均支持关键词高亮

---

## 七、部署说明

### 7.1 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 后端运行环境 |
| MySQL | 8.0 | 数据库（utf8mb4） |
| Redis | 6.0+ | 缓存中间件 |
| RabbitMQ | 3.9+ | 消息队列（可选，`mq.enabled=false` 可关闭） |
| Elasticsearch | 7.17 | 搜索引擎（可选，不可用时降级 MySQL） |
| MinIO | 任意 | 对象存储（可选，`minio.enabled=false` 使用本地存储） |
| Node.js | 18+ | 前端构建 + Yjs WebSocket 服务器 |
| LibreOffice | 24+ | Office 文档预览转换（可选） |

### 7.2 后端配置

`application.yml` 关键配置项：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/private_cloud?useUnicode=true&characterEncoding=utf8mb4
    username: root
    password: your_password
  redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672

mq:
  enabled: true          # RabbitMQ 开关

minio:
  enabled: false         # MinIO 开关
  min-size: 104857600    # 大于 100MB 走 MinIO

deepseek:
  api-key: ${DEEPSEEK_API_KEY}
  base-url: https://api.deepseek.com

glm:
  api-key: ${GLM_API_KEY}
  base-url: https://open.bigmodel.cn/api/paas/v4

mimo:
  api-key: ${MIMO_API_KEY}
  base-url: https://token-plan-cn.xiaomimimo.com/v1

ai:
  provider: deepseek     # 当前使用的 AI 厂商：deepseek / glm / mimo
```

### 7.3 前端启动

```bash
# Web 前台（端口 5173）
cd private-cloud-front/private-cloud/web
npm install
npm run dev

# Admin 后台（端口 5174）
cd private-cloud-front/private-cloud/admin
npm install
npm run dev

# Yjs 协同编辑服务器（端口 1234）
cd private-cloud-front/private-cloud/collab-ws-server
npm install
node server.js
```

### 7.4 数据库初始化

1. 创建数据库 `private_cloud`，字符集 utf8mb4
2. 启动后端应用，Hibernate `ddl-auto=update` 自动建表
3. 执行初始化 SQL 脚本创建管理员角色和默认用户
4. 执行盲索引迁移脚本 `blind_index_migration.sql`（若需升级）

---

## 八、总结

### 8.1 项目成果

PrivateCloud 私有云盘智能文档管理系统完整实现了企业级文档协作与知识管理的核心功能，涵盖：

- **前台用户系统**：三级空间文件管理、AI 智能标签/摘要/文档生成、ES 全文搜索、RAG 知识问答、Yjs 多人协同编辑、安全分享、电子签章、通知中心、审批工作流、个人工作台
- **后台管理系统**：数据仪表盘、RBAC 权限管理、安全审计、工作流监控、系统设置

### 8.2 技术亮点回顾

1. **存储抽象**：StorageRouter 统一管理本地磁盘与 MinIO，嗅探路径自动降级
2. **多级缓存**：Redis + Caffeine 两级缓存，兼顾性能与一致性
3. **异步解耦**：RabbitMQ 解耦 OCR、预览转换、AI 标签等耗时任务，配置死信队列保障可靠性
4. **多 AI 厂商适配**：DeepSeek / GLM / MiMo 三厂商统一封装，配置热切换
5. **安全体系**：RSA + JWT + 滑块验证码 + IP 过滤 + 权限拦截器 + 盲索引去关联化
6. **搜索降级**：ES 主路径 + MySQL 降级路径，保障搜索可用性

### 8.3 简化说明

| 功能 | 实现方式 | 说明 |
|------|---------|------|
| 电子签章 | 模拟实现 | 不对接真实 CA，使用 Graphics2D 绘制印章 |
| 工作流引擎 | 自建审批表 | 放弃 Flowable，实现串行审批 |
| OCR 引擎 | 单一配置 | 仅使用百度云 OCR API |
| 语音识别 | 前端实现 | 浏览器 Web Speech API，后端模块保留但不再使用 |
| 云存储切换 | 配置文件 | 不支持控制台动态切换 |
| 邮件通知 | 简化实现 | 邮件模块保留，默认使用站内通知 |

### 8.4 分工说明

| 模块 | 负责人 |
|------|--------|
| 文档管理与工作台、智能处理与内容生成 | 贺翔 |
| 检索与知识问答、外部协同与电子签章、数据仪表盘 | 鲁麒志 |
| 权限与安全合规、工作流与审批管理、系统设置与拓展 | 付开寅 |

---

> **文档版本**：v1.0  
> **最后更新**：2026 年 6 月
