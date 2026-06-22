import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { resolve } from 'node:path'
import type { Plugin } from 'vite'

// IP 网络准入中间件（与后端 IpAccessFilter 保持一致）
function ipAccessMiddleware(env: Record<string, string>): Plugin {
  return {
    name: 'ip-access-filter',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const enabled = env.VITE_IP_FILTER_ENABLED === 'true'
        if (!enabled) {
          next()
          return
        }

        const allowedSubnets = (env.VITE_IP_FILTER_SUBNETS || '192.168.10.,10.50.167.').split(',')

        // 获取客户端真实IP
        let clientIp = req.socket.remoteAddress || '127.0.0.1'
        clientIp = clientIp.replace('::ffff:', '')
        if (clientIp === '::1' || clientIp === '0:0:0:0:0:0:0:1') {
          clientIp = '127.0.0.1'
        }

        // 检查是否在准入网段
        const isAllowed = allowedSubnets.some(subnet => {
          const trimmed = subnet.trim()
          return trimmed && clientIp.startsWith(trimmed)
        })

        if (!isAllowed) {
          res.statusCode = 404
          res.setHeader('Content-Type', 'text/html; charset=UTF-8')
          res.end('<!DOCTYPE html><html><head><title>404</title></head><body><h1>404 Not Found</h1></body></html>')
          return
        }

        next()
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  // 用 loadEnv 正确读取 .env 文件（process.env 读不到 .env）
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue(),
      // AutoImport 仅用于 Vue API 自动导入（ref/computed 等）
      // element-plus 已在 main.ts 中全量引入，不需要 ElementPlusResolver 按需导入
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
      }),
      ipAccessMiddleware(env),
    ],
    resolve: {
      alias: {
        '@': resolve(import.meta.dirname, 'src'),
      },
    },
    // 预打包所有第三方依赖，启动时一次性完成，运行时不再发现新依赖
    optimizeDeps: {
      include: [
        'element-plus',
        '@element-plus/icons-vue',
        'jsencrypt',
        'vue3-lottie',
        'grid-layout-plus',
        '@stomp/stompjs',
        'sockjs-client/dist/sockjs',
        'markdown-it',
        'highlight.js',
        'spark-md5',
        'pdfjs-dist',
        'lodash-es',
      ],
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
              // 传递客户端真实IP给后端（本地开发时Vite代理会丢失真实IP）
              const clientIp = req.socket.remoteAddress || '127.0.0.1'
              proxyReq.setHeader('X-Forwarded-For', clientIp.replace('::ffff:', ''))
              proxyReq.setHeader('X-Real-IP', clientIp.replace('::ffff:', ''))
            })
          },
        },
        '/ws': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
          ws: true,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
              const clientIp = req.socket.remoteAddress || '127.0.0.1'
              proxyReq.setHeader('X-Forwarded-For', clientIp.replace('::ffff:', ''))
              proxyReq.setHeader('X-Real-IP', clientIp.replace('::ffff:', ''))
            })
            proxy.on('error', (err) => {
              console.error('[WS Proxy Error]', err.message)
            })
          },
        },
      },
    },
  }
})
