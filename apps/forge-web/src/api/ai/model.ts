import request from '@/utils/request'

/** 文本生成类型标识（与后端 AiModelTypes.TEXT、字典 ai_model_type 的 value 一致） */
export const AI_MODEL_TYPE_TEXT = 'text'

// 模型配置响应
export interface ModelConfigResponse {
  id: number
  modelName: string
  modelCode: string
  provider: string
  modelType: string
  apiEndpoint: string | null
  apiKey: string | null
  maxTokens: number
  temperature: number
  thinkingDisabled: number | null // 0: 默认 1: 关闭思考模式（思考类模型防预算耗尽）
  contextWindow: number | null
  inputPrice: number | null
  outputPrice: number | null
  isDefault: number // 0: 否, 1: 是
  status: number // 0: 禁用, 1: 启用, 2: 连接异常
  remark: string | null
  createTime: string
  updateTime: string
}

// 模型配置请求
export interface ModelConfigRequest {
  modelName?: string
  modelCode?: string
  provider?: string
  modelType?: string
  apiKey?: string
  apiEndpoint?: string
  maxTokens?: number
  temperature?: number
  thinkingDisabled?: number
}

// 新增模型请求
export interface AddModelRequest {
  modelName: string
  modelCode: string
  provider: string
  modelType: string
  apiEndpoint?: string
  apiKey?: string
  maxTokens?: number
  temperature?: number
  thinkingDisabled?: number
  remark?: string
}

// 模型切换请求
export interface ModelSwitchRequest {
  modelId: number
}

export const modelApi = {
  // 获取模型列表（modelType 可选过滤，如 'text' 仅文本生成模型）
  list: (modelType?: string) =>
    request.get<ModelConfigResponse[]>('/ai/model/list', { params: modelType ? { modelType } : {} }),

  // 获取模型详情
  get: (id: number) =>
    request.get<ModelConfigResponse>(`/ai/model/${id}`),

  // 新增模型配置
  add: (data: AddModelRequest) =>
    request.post('/ai/model', data),

  // 配置模型
  config: (id: number, data: ModelConfigRequest) =>
    request.put<ModelConfigResponse>(`/ai/model/${id}/config`, data),

  // 设为默认模型
  switch: (id: number) =>
    request.put<ModelConfigResponse>(`/ai/model/${id}/default`),

  // 删除模型配置
  delete: (id: number) =>
    request.delete(`/ai/model/${id}`),

  // 刷新模型状态
  refreshStatus: (id: number) =>
    request.post<ModelConfigResponse>(`/ai/model/${id}/refresh`),

  // 批量刷新状态
  refreshAll: () =>
    request.post<ModelConfigResponse[]>('/ai/model/refresh-all')
}

// 导出独立函数
export const getModelList = (modelType?: string) => modelApi.list(modelType).then(res => res.data)
export const getModel = (id: number) => modelApi.get(id).then(res => res.data)
export const addModel = (data: AddModelRequest) => modelApi.add(data)
export const configModel = (id: number, data: ModelConfigRequest) =>
  modelApi.config(id, data).then(res => res.data)
export const switchModel = (id: number) => modelApi.switch(id).then(res => res.data)
export const deleteModel = (id: number) => modelApi.delete(id)
export const refreshModelStatus = (id: number) =>
  modelApi.refreshStatus(id).then(res => res.data)
export const refreshAllModels = () => modelApi.refreshAll().then(res => res.data)