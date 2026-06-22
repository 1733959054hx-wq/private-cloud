# Yjs 协同编辑同步服务器

基于 `y-websocket` 协议的多房间文档实时同步服务器。

## 启动

```bash
cd collab-ws-server
pnpm start
```

默认端口 `1234`，可通过环境变量 `PORT=5678` 修改。

## 架构

```
客户端 A ──┐                    ┌── 客户端 B
           │                    │
  Quill + y-quill              Quill + y-quill
           │                    │
  y-websocket (WS 客户端)       y-websocket (WS 客户端)
           │                    │
           └───── ws://localhost:1234/doc-{id} ────┘
                          │
                  Yjs WebSocket Server
                  (collab-ws-server/server.js)
                          │
                   内存 Y.Doc 文档
```

## 接入流程

1. 启动本服务器：`pnpm start`
2. 前端访问 `/collab` 创建/选择协作会话
3. 点击"进入协作"跳转到 `/collab/editor/:docId`
4. 页面自动连接 Yjs WebSocket 服务器，进行实时同步

## API

| 端点 | 说明 |
|------|------|
| `ws://host:port/doc-{id}` | Yjs 协同 WebSocket 连接 |
| `http://host:port/health` | 健康检查（返回房间数、客户端数） |
