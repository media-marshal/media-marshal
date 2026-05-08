import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { mediaApi } from '@/api/media'
import type { MediaTask, MediaType, TaskStatus } from '@/types'

export const useMediaStore = defineStore('media', () => {
  const tasks = ref<MediaTask[]>([])
  const loading = ref(false)

  const queueTasks = computed(() =>
    tasks.value.filter((t) => t.status === 'AWAITING_CONFIRMATION'),
  )

  const doneTasks = computed(() => tasks.value.filter((t) => t.status === 'DONE'))

  const failedTasks = computed(() => tasks.value.filter((t) => t.status === 'FAILED'))

  async function fetchTasks(status?: TaskStatus) {
    loading.value = true
    try {
      const res = await mediaApi.listTasks(status)
      tasks.value = res.data.data
    } finally {
      loading.value = false
    }
  }

  async function fetchQueue() {
    loading.value = true
    try {
      const res = await mediaApi.getPendingQueue()
      tasks.value = res.data.data
    } finally {
      loading.value = false
    }
  }

  /** WebSocket 推送时更新本地任务状态 */
  function updateTaskStatus(taskId: number, status: TaskStatus) {
    const task = tasks.value.find((t) => t.id === taskId)
    if (task) task.status = status
  }

  function updateTask(updatedTask: MediaTask) {
    const index = tasks.value.findIndex((task) => task.id === updatedTask.id)
    if (index >= 0) {
      tasks.value[index] = updatedTask
    } else {
      tasks.value.unshift(updatedTask)
    }
  }

  async function confirmTask(id: number, tmdbId: number, mediaType: MediaType) {
    await mediaApi.confirmTask(id, tmdbId, mediaType)
    updateTaskStatus(id, 'PROCESSING')
  }

  async function skipTask(id: number) {
    await mediaApi.skipTask(id)
    updateTaskStatus(id, 'SKIPPED')
  }

  async function deleteTask(id: number) {
    await mediaApi.deleteTask(id)
    tasks.value = tasks.value.filter((task) => task.id !== id)
  }

  function clearTasks() {
    tasks.value = []
  }

  return {
    tasks,
    loading,
    queueTasks,
    doneTasks,
    failedTasks,
    fetchTasks,
    fetchQueue,
    updateTaskStatus,
    updateTask,
    confirmTask,
    skipTask,
    deleteTask,
    clearTasks,
  }
})
