/**
 * Yjs WebSocket 协同编辑同步服务器 - 调试版
 *
 * 启动: node server.js              (默认端口 1234)
 * 环境变量: PORT=1234
 */
const http = require('http');
const WebSocket = require('ws');
const Y = require('yjs');
const syncProtocol = require('y-protocols/dist/sync.cjs');
const awarenessProtocol = require('y-protocols/dist/awareness.cjs');
const encoding = require('lib0/dist/encoding.cjs');
const decoding = require('lib0/dist/decoding.cjs');

const PORT = process.env.PORT || 1234;

const messageSync = 0;
const messageAwareness = 1;
const messageQueryAwareness = 3;

const rooms = new Map();

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ status: 'ok', rooms: rooms.size }));
});

const wss = new WebSocket.Server({ server });

let clientIdCounter = 0;
wss.on('connection', (ws, req) => {
  const cid = ++clientIdCounter;
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const roomName = url.pathname.replace(/^\/+/, '') || 'default';
  console.log(`[1] C${cid}连接 → 房间: "${roomName}"`);

  let room = rooms.get(roomName);
  if (!room) {
    const ydoc = new Y.Doc();
    const awareness = new awarenessProtocol.Awareness(ydoc);
    room = { ydoc, awareness, clients: [] };
    rooms.set(roomName, room);
    console.log(`[2] 创建房间: "${roomName}"`);

    // 文档更新广播
    ydoc.on('update', (update, origin) => {
      console.log(`[3] ydoc.update触发, 大小=${update.length}, 是否有origin=${origin ? '有' : '无'}`);
      room.clients.forEach(c => {
        if (c.ws !== origin && c.ws.readyState === WebSocket.OPEN) {
          const enc = encoding.createEncoder();
          encoding.writeVarUint(enc, messageSync);
          syncProtocol.writeUpdate(enc, update);
          c.ws.send(encoding.toUint8Array(enc));
          console.log(`[3a] → 已转发给其他客户端`);
        } else if (c.ws === origin) {
          console.log(`[3b] → 跳过来源客户端`);
        }
      });
    });

    // awareness 广播
    awareness.on('update', ({ added, updated, removed }, conn) => {
      const changed = [...added, ...updated, ...removed];
      const msg = awarenessProtocol.encodeAwarenessUpdate(awareness, changed);
      room.clients.forEach(c => {
        if (c.ws !== conn && c.ws.readyState === WebSocket.OPEN) {
          const enc = encoding.createEncoder();
          encoding.writeVarUint(enc, messageAwareness);
          encoding.writeVarUint8Array(enc, msg);
          c.ws.send(encoding.toUint8Array(enc));
        }
      });
    });
  }

  const { ydoc, awareness } = room;
  room.clients.push({ ws });

  ws.on('message', (data) => {
    try {
      const decoder = decoding.createDecoder(new Uint8Array(data));
      const messageType = decoding.readVarUint(decoder);
      const typeName = ['messageSync', 'messageAwareness', '', 'messageQueryAwareness'][messageType] || '未知';
      console.log(`[4] 收到消息: type=${messageType}(${typeName})`);

      if (messageType === messageSync) {
        // 用全新 decoder 调用 readSyncMessage
        const msgData = new Uint8Array(data);
        const msgDecoder = decoding.createDecoder(msgData);
        decoding.readVarUint(msgDecoder); // 跳过 messageType=0

        // 读子类型做日志
        const subType = decoding.readVarUint(msgDecoder);
        const subNames = ['Step1', 'Step2', 'Update'];
        console.log(`[4a] sync子类型=${subType}(${subNames[subType] || '?'})`);

        // 日志后不能再走旧 decoder，重建一次
        const workDecoder = decoding.createDecoder(msgData);
        decoding.readVarUint(workDecoder); // 跳过 messageType
        const encoder = encoding.createEncoder();
        syncProtocol.readSyncMessage(workDecoder, encoder, ydoc, ws);

        const ytext = ydoc.getText('quill');
        console.log(`[4b] 处理完毕, 响应长度=${encoding.length(encoder)}, ytext长度=${ytext.toString().length}`);

        if (encoding.length(encoder) > 1) {
          ws.send(encoding.toUint8Array(encoder));
          console.log(`[4c] → 已发送sync响应`);
        }

      } else if (messageType === messageAwareness) {
        awarenessProtocol.applyAwarenessUpdate(awareness, decoding.readVarUint8Array(decoder), ws);
        console.log(`[5] awareness更新已应用`);

      } else if (messageType === messageQueryAwareness) {
        const enc = encoding.createEncoder();
        encoding.writeVarUint(enc, messageAwareness);
        encoding.writeVarUint8Array(enc,
          awarenessProtocol.encodeAwarenessUpdate(awareness, Array.from(awareness.getStates().keys()))
        );
        ws.send(encoding.toUint8Array(enc));
        console.log(`[6] 已回复awareness查询`);
      }
    } catch (err) {
      console.error(`[ERR] 消息处理错误 (${roomName}):`, err.message);
    }
  });

  ws.on('close', () => {
    console.log(`[7] 客户端断开: "${roomName}"`);
    room.clients = room.clients.filter(c => c.ws !== ws);
    awarenessProtocol.removeAwarenessStates(awareness, [ws], null);
    if (room.clients.length === 0) {
      console.log(`[7a] 房间空闲，5分钟后清理`);
      setTimeout(() => {
        if (room.clients.length === 0 && rooms.has(roomName)) {
          rooms.delete(roomName);
          ydoc.destroy();
        }
      }, 5 * 60 * 1000);
    }
  });

  ws.on('error', () => {});
});

server.listen(PORT, () => {
  console.log(`========================================`);
  console.log(`  Yjs 协同服务器 (端口 ${PORT})`);
  console.log(`========================================`);
});
