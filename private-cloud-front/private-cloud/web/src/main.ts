import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'highlight.js/styles/github.css'
import type { Plugin } from 'vue'
import Particles from '@tsparticles/vue3'
import { loadSlim } from '@tsparticles/slim'
import './styles/design-tokens.css'
import App from './App.vue'
import router from './router'

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

const app = createApp(App)

// 全局错误捕获，防止组件异常导致白屏
app.config.errorHandler = (err, _instance, info) => {
  console.error('[全局错误捕获]', err, info)
}

// 捕获未处理的 Promise 拒绝
window.addEventListener('unhandledrejection', (event) => {
  console.error('[未捕获的Promise错误]', event.reason)
  event.preventDefault()
})

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.use(Particles as unknown as Plugin, { init: loadSlim })

// 等待路由就绪后再挂载，避免首次进入页面白屏需要刷新
router.isReady().then(() => {
  app.mount('#app')
})
