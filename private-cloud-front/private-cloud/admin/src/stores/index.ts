import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAdminStore = defineStore('admin', () => {
  const isCollapsed = ref(false)
  return { isCollapsed }
})
