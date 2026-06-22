<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="admin-sidebar">
      <div class="sidebar-logo">
        <el-icon :size="20"><Monitor /></el-icon>
        <span>私有云后台</span>
      </div>
      <el-menu
        :default-active="route.path"
        background-color="transparent"
        text-color="#bfcbd9"
        active-text-color="#fff"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon><span>数据仪表盘</span>
        </el-menu-item>
        <el-sub-menu index="/system">
          <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
          <el-menu-item index="/system/user">用户管理</el-menu-item>
          <el-menu-item index="/system/dept">部门管理</el-menu-item>
          <el-menu-item index="/system/role">角色管理</el-menu-item>
          <el-menu-item index="/system/dict">字典管理</el-menu-item>
          <el-menu-item index="/system/sensitive">敏感词管理</el-menu-item>
          <el-menu-item index="/system/storage">存储配置</el-menu-item>
          <el-menu-item index="/system/cache">缓存监控</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/workflow">
          <template #title><el-icon><List /></el-icon><span>工作流管理</span></template>
          <el-menu-item index="/workflow/approval">审批管理</el-menu-item>
          <el-menu-item index="/workflow/monitor">审批监控</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/security">
          <template #title><el-icon><Lock /></el-icon><span>安全审计</span></template>
          <el-menu-item index="/security/online">在线用户</el-menu-item>
          <el-menu-item index="/security/operation-log">操作日志</el-menu-item>
          <el-menu-item index="/security/access-log">文件访问日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 主区域 -->
    <el-container class="admin-main">
      <el-header class="admin-header">
        <span class="header-title">📊 数据仪表盘</span>
        <div class="header-right">
          <el-tag size="small" type="success" effect="dark" round>系统运行中</el-tag>
          <span class="header-time">{{ nowStr }}</span>
        </div>
      </el-header>
      <el-main class="admin-content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Monitor, DataAnalysis, Setting, List, Lock } from '@element-plus/icons-vue'

const route = useRoute()
const nowStr = ref('')
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  const update = () => { const d = new Date(); nowStr.value = d.toLocaleString('zh-CN') }
  update(); timer = setInterval(update, 1000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.admin-layout { display: flex; height: 100vh; overflow: hidden }

.admin-sidebar {
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  overflow-y: auto; flex-shrink: 0;
}
.sidebar-logo {
  height: 60px; display: flex; align-items: center; justify-content: center; gap: 8px;
  color: #fff; font-size: 16px; font-weight: 700; letter-spacing: 1px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.admin-sidebar :deep(.el-menu) { border-right: none }
.admin-sidebar :deep(.el-menu-item), .admin-sidebar :deep(.el-sub-menu__title) { font-size: 14px }
.admin-sidebar :deep(.el-menu-item.is-active) {
  background: rgba(74,144,217,0.2) !important; border-radius: 8px; margin: 2px 8px; width: auto;
}

.admin-main { flex: 1; display: flex; flex-direction: column; min-width: 0 }

.admin-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #f0f0f0;
  padding: 0 24px; height: 56px; flex-shrink: 0;
}
.header-title { font-size: 16px; font-weight: 600; color: #303133 }
.header-right { display: flex; align-items: center; gap: 12px }
.header-time { font-size: 13px; color: #909399 }

.admin-content {
  background: linear-gradient(180deg, #f8f9fa 0%, #f0f2f5 100%);
  padding: 20px 24px; overflow-y: auto; flex: 1;
}
</style>
