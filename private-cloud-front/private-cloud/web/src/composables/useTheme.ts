import { ref, watchEffect } from 'vue'

const STORAGE_KEY = 'theme_mode'

function getInitialTheme(): 'light' | 'dark' {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark' || stored === 'light') return stored
  // 跟随系统偏好
  if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark'
  return 'light'
}

const theme = ref<'light' | 'dark'>(getInitialTheme())

function applyTheme(value: 'light' | 'dark') {
  document.documentElement.setAttribute('data-theme', value)
}

// 初始化 + 监听变化
applyTheme(theme.value)

watchEffect(() => {
  applyTheme(theme.value)
  localStorage.setItem(STORAGE_KEY, theme.value)
})

export function useTheme() {
  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
  }

  return {
    theme,
    toggleTheme,
    isDark: () => theme.value === 'dark',
  }
}
