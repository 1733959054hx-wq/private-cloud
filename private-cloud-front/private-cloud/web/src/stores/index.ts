import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const isCollapsed = ref(false)
  return { isCollapsed }
})
