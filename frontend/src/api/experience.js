import { ApiError, request } from './client.js'

export { ApiError }

export function fetchWorkspace() {
  return request('/api/workspace')
}

export function fetchKnowledgeFile(nodeId) {
  const normalized = String(nodeId || '').trim()
  if (!normalized || normalized.length > 128) throw new ApiError('KNOWLEDGE_FILE_INVALID', '资料标识不合法')
  return request(`/api/knowledge/files/${encodeURIComponent(normalized)}`)
}

export function knowledgeFileDownloadUrl(nodeId) {
  return `/api/knowledge/files/${encodeURIComponent(String(nodeId || '').trim())}/content`
}

export function askQuestion(question, spaceId) {
  const normalized = String(question || '').trim()
  if (!normalized) throw new ApiError('QUESTION_EMPTY', '请输入想了解的问题')
  if (normalized.length > 1000) throw new ApiError('QUESTION_TOO_LONG', '问题不能超过1000个字符')
  return request('/api/chat', {
    method: 'POST',
    body: JSON.stringify({ question: normalized, spaceId: spaceId || null })
  })
}
