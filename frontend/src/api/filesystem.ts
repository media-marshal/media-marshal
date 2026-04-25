import http from './http'
import type { ApiResponse } from '@/types'

export interface DirEntry {
  name: string
  path: string
}

export interface BrowseResult {
  currentPath: string
  parentPath: string | null
  dirs: DirEntry[]
}

export const filesystemApi = {
  browse(path = '/') {
    return http.get<ApiResponse<BrowseResult>>('/api/filesystem/browse', {
      params: { path },
    })
  },
}
