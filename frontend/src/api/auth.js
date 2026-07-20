import { request, setCsrfToken } from './client.js'

export async function fetchAuthConfig() {
  const config = await request('/api/auth/config')
  setCsrfToken(config.csrfToken)
  return config
}

export function fetchCurrentUser() {
  return request('/api/auth/me')
}

export function loginDemo() {
  return request('/api/auth/demo', { method: 'POST' })
}

export function loginWithDingTalk(code, corpId) {
  return request('/api/auth/dingtalk/inside', {
    method: 'POST',
    body: JSON.stringify({ code, corpId })
  })
}

export function logout() {
  return request('/api/auth/logout', { method: 'POST' })
}
