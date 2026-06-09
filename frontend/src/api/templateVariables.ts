import http from './http'
import type { ApiResponse, TemplatePreviewRequest, TemplatePreviewResponse, TemplateVariableGroup } from '@/types'

export const templateVariablesApi = {
  listVariables() {
    return http.get<ApiResponse<TemplateVariableGroup[]>>('/api/template-variables')
  },

  previewTemplate(data: TemplatePreviewRequest) {
    return http.post<ApiResponse<TemplatePreviewResponse>>('/api/template-variables/preview', data)
  },
}
