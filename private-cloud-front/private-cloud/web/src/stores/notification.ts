import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getNotifications,
  getUnreadCount,
  markAsRead as markAsReadApi,
  markAllAsRead as markAllAsReadApi,
  deleteNotification as deleteNotificationApi,
  clearReadNotifications as clearReadNotificationsApi,
  type NotificationDTO
} from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationDTO[]>([])
  const unreadCount = ref(0)
  const loading = ref(false)

  const sortedNotifications = computed(() => {
    return [...notifications.value].sort((a, b) => {
      return new Date(b.createTime).getTime() - new Date(a.createTime).getTime()
    })
  })

  async function fetchNotifications() {
    loading.value = true
    try {
      const res = await getNotifications()
      notifications.value = res.data.data || []
      updateUnreadCount()
    } catch {
      notifications.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount()
      unreadCount.value = res.data.data?.count || 0
    } catch {
      unreadCount.value = 0
    }
  }

  function updateUnreadCount() {
    unreadCount.value = notifications.value.filter(n => n.isRead === 0).length
  }

  async function markAsRead(id: number) {
    try {
      await markAsReadApi(id)
      const notification = notifications.value.find(n => n.id === id)
      if (notification) {
        notification.isRead = 1
        updateUnreadCount()
      }
    } catch { /* ignore */ }
  }

  async function markAllAsRead() {
    try {
      await markAllAsReadApi()
      notifications.value.forEach(n => { n.isRead = 1 })
      updateUnreadCount()
    } catch { /* ignore */ }
  }

  async function deleteNotification(id: number) {
    try {
      await deleteNotificationApi(id)
      notifications.value = notifications.value.filter(n => n.id !== id)
      updateUnreadCount()
    } catch { /* ignore */ }
  }

  async function clearReadNotifications() {
    try {
      await clearReadNotificationsApi()
      notifications.value = notifications.value.filter(n => n.isRead === 0)
      updateUnreadCount()
    } catch { /* ignore */ }
  }

  function addRealtimeNotification(notification: NotificationDTO) {
    const exists = notifications.value.find(n => n.id === notification.id)
    if (!exists) {
      notifications.value.unshift(notification)
      updateUnreadCount()
    }
  }

  return {
    notifications,
    unreadCount,
    loading,
    sortedNotifications,
    fetchNotifications,
    fetchUnreadCount,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    clearReadNotifications,
    addRealtimeNotification
  }
})
