import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'
import i18n from '@/i18n'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

// 统一错误处理
http.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse<unknown>
    if (!data.success) {
      ElMessage.error(data.message || i18n.global.t('common.requestFailed'))
      return Promise.reject(new Error(data.message))
    }
    return response
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || i18n.global.t('common.networkError')
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default http
