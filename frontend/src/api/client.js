export class ApiError extends Error {
  constructor(code, message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

let csrfToken = ''

export function setCsrfToken(value) {
  csrfToken = String(value || '')
}

export async function request(path, options = {}) {
  const method = String(options.method || 'GET').toUpperCase()
  const headers = { ...options.headers }
  if (options.body != null) headers['Content-Type'] = 'application/json'
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken
  }

  let response
  try {
    response = await fetch(path, {
      credentials: 'same-origin',
      ...options,
      method,
      headers
    })
  } catch {
    throw new ApiError('NETWORK_ERROR', '服务暂时无法连接')
  }

  const contentType = response.headers?.get?.('content-type') || ''
  const data = contentType.includes('application/json') ? await response.json() : null
  if (!response.ok) {
    throw new ApiError(data?.code || 'REQUEST_FAILED', data?.message || '请求失败，请稍后再试', response.status)
  }
  return data
}
