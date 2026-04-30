import http from './http'
import type { ApiResponse, TemplateVariableGroup } from '@/types'

export const templateVariablesApi = {
  listVariables() {
    return http.get<ApiResponse<TemplateVariableGroup[]>>('/api/template-variables')
  },
}
