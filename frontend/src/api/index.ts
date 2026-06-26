import http from './http'

/** 当前用户信息 */
export interface SessionUser {
  personId: string
  personName: string
  orgIndexCode: string
  orgPath: string
  roles: string[]
}

/** 知识库（Dataset）*/
export interface Dataset {
  id: string
  name: string
  description?: string
  chunkMethod: string
  embeddingModel: string
  documentCount: number
  chunkCount: number
  parserConfig?: any
}

/** 检索结果 */
export interface SearchResult {
  chunks: SearchResultChunk[]
  total: number
  responseMs: number
}

export interface SearchResultChunk {
  chunkId: string
  content: string
  similarity: number
  documentId: string
  documentName: string
  metadata?: any
  positions?: any[]
}

// ===== Session =====
export const sessionApi = {
  me: () => http.get<unknown, SessionUser>('/api/me'),
  logout: () => http.post('/sso/logout'),
}

// ===== Dataset =====
export const datasetApi = {
  list: (page = 1, pageSize = 20, keywords?: string) =>
    http.get<unknown, { datasets: Dataset[]; total: number }>('/api/datasets', { params: { page, pageSize, keywords } }),
  create: (data: any) => http.post<unknown, Dataset>('/api/datasets', data),
  update: (id: string, data: any) => http.put<unknown, Dataset>(`/api/datasets/${id}`, data),
  delete: (ids: string[]) => http.delete('/api/datasets', { params: { ids: ids.join(',') } }),
}

// ===== Document =====
export const documentApi = {
  list: (datasetId: string, page = 1, pageSize = 20, keywords?: string) =>
    http.get<unknown, any>(`/api/datasets/${datasetId}/documents`, { params: { page, pageSize, keywords } }),
  upload: (datasetId: string, file: File, parse = true) => {
    const form = new FormData()
    form.append('file', file)
    form.append('parse', String(parse))
    return http.post<unknown, any>(`/api/datasets/${datasetId}/documents`, form)
  },
  delete: (datasetId: string, docIds: string[]) =>
    http.delete(`/api/datasets/${datasetId}/documents`, { params: { ids: docIds.join(',') } }),
  reparse: (datasetId: string, docId: string) =>
    http.post(`/api/datasets/${datasetId}/documents/${docId}/reparse`),
  download: (datasetId: string, docId: string) =>
    http.get<unknown, Blob>(`/api/datasets/${datasetId}/documents/${docId}/download`, { responseType: 'blob' }),
}

// ===== Chunk =====
export const chunkApi = {
  list: (datasetId: string, docId: string, keywords?: string) =>
    http.get<unknown, any>(`/api/datasets/${datasetId}/documents/${docId}/chunks`, { params: { keywords } }),
  update: (datasetId: string, docId: string, chunkId: string, data: any) =>
    http.patch(`/api/datasets/${datasetId}/documents/${docId}/chunks/${chunkId}`, data),
  setAvailability: (datasetId: string, docId: string, chunkIds: string[], available: boolean) =>
    http.patch(`/api/datasets/${datasetId}/documents/${docId}/chunks/availability`, { chunkIds, available }),
  delete: (datasetId: string, docId: string, chunkIds: string[]) =>
    http.delete(`/api/datasets/${datasetId}/documents/${docId}/chunks`, { data: { chunkIds } }),
}

// ===== Search =====
export const searchApi = {
  search: (data: { question: string; datasetIds?: string[]; topK?: number; useKg?: boolean }) =>
    http.post<unknown, SearchResult>('/api/search', data),
  retrievalTest: (data: any) => http.post<unknown, SearchResult>('/api/retrieval-test', data),
  testHistory: (page = 1, pageSize = 20) =>
    http.get<unknown, any>('/api/retrieval-test/history', { params: { page, pageSize } }),
}

// ===== Audit =====
export const auditApi = {
  templates: {
    list: () => http.get<unknown, any[]>('/api/audit/templates'),
    create: (data: any) => http.post('/api/audit/templates', data),
  },
  pending: (params?: any) => http.get<unknown, any>('/api/audit/pending', { params }),
  detail: (instanceId: number) => http.get<unknown, any>(`/api/audit/${instanceId}`),
  approve: (instanceId: number, comment?: string) =>
    http.post(`/api/audit/${instanceId}/approve`, { comment }),
  reject: (instanceId: number, comment?: string) =>
    http.post(`/api/audit/${instanceId}/reject`, { comment }),
  submit: (datasetId: string, ragflowDocId: string, templateId: number) =>
    http.post('/api/audit/submit', { datasetId, ragflowDocId, templateId }),
  myPending: () => http.get<unknown, any>('/api/audit/my-pending'),
  aiIssues: (params?: any) => http.get<unknown, any>('/api/audit/ai/issues', { params }),
  aiRun: (datasetId: string, docId: string) =>
    http.post('/api/audit/ai/run', { datasetId, docId }),
}

// ===== Permission =====
export const permissionApi = {
  roles: {
    list: () => http.get<unknown, any[]>('/api/roles'),
    assign: (personId: string, roleCodes: string[]) =>
      http.post(`/api/roles/persons/${personId}/assign`, { roleCodes }),
    getByPerson: (personId: string) => http.get<unknown, string[]>(`/api/roles/persons/${personId}`),
  },
  policies: {
    list: (params?: any) => http.get<unknown, any[]>('/api/permission-policies', { params }),
    create: (data: any) => http.post('/api/permission-policies', data),
    delete: (id: number) => http.delete(`/api/permission-policies/${id}`),
  },
  operationLogs: (params?: any) => http.get<unknown, any>('/api/operation-logs', { params }),
}

// ===== Agent & Chat =====
export const agentApi = {
  list: () => http.get<unknown, any[]>('/api/agents'),
  create: (data: any) => http.post('/api/agents', data),
  update: (id: string, data: any) => http.put(`/api/agents/${id}`, data),
  delete: (id: string) => http.delete(`/api/agents/${id}`),
}

export const chatApi = {
  listAssistants: () => http.get<unknown, any[]>('/api/chats'),
  createAssistant: (data: any) => http.post('/api/chats', data),
  deleteAssistant: (chatId: string) => http.delete(`/api/chats/${chatId}`),
  listSessions: (chatId: string) => http.get<unknown, any[]>(`/api/chats/${chatId}/sessions`),
  createSession: (chatId: string, name?: string) => http.post(`/api/chats/${chatId}/sessions`, { name }),
  deleteSession: (chatId: string, sessionId: string) =>
    http.delete(`/api/chats/${chatId}/sessions/${sessionId}`),
  converse: (chatId: string, data: { question: string; sessionId: string }) =>
    http.post<unknown, { answer: string; reference?: { chunks: any[] } }>(`/api/chats/${chatId}/completions`, data),
  feedback: (chatId: string, sessionId: string, messageId: string, feedback: string) =>
    http.patch(`/api/chats/${chatId}/sessions/${sessionId}/messages/${messageId}/feedback`, { feedback }),
}

// ===== Version =====
export const versionApi = {
  list: (datasetId?: string) => http.get<unknown, any[]>('/api/versions', { params: { datasetId } }),
  switchOnline: (versionId: number) => http.post(`/api/versions/${versionId}/switch-online`),
  switchTest: (versionId: number) => http.post(`/api/versions/${versionId}/switch-test`),
  rollback: (versionId: number) => http.post(`/api/versions/${versionId}/rollback`),
}

// ===== Stats =====
export const statsApi = {
  dashboard: () => http.get<unknown, any>('/api/stats/dashboard'),
  trend: (days = 30) => http.get<unknown, any[]>('/api/stats/trend', { params: { days } }),
  hotKeywords: (days = 7, topN = 20) =>
    http.get<unknown, any[]>('/api/stats/hot-keywords', { params: { days, topN } }),
  topChunks: (datasetId?: string, topN = 10) =>
    http.get<unknown, any[]>('/api/stats/top-chunks', { params: { datasetId, topN } }),
  performance: () => http.get<unknown, any>('/api/stats/performance'),
}
