import { computed, ref } from 'vue'
import { ApiError } from '../api/client.js'
import { fetchAuthConfig, fetchCurrentUser, loginDemo, loginWithDingTalk } from '../api/auth.js'
import { isDingTalkEnvironment, requestDingTalkAuthorizationCode } from '../auth/dingtalk.js'

export function useAuth({ location = globalThis.location } = {}) {
  const loading = ref(true)
  const error = ref('')
  const currentUser = ref(null)
  const config = ref(null)

  const sourceLabel = computed(() => {
    if (!currentUser.value) return '身份尚未建立'
    return currentUser.value.authSource === 'DINGTALK' ? '钉钉身份已验证' : '演示会话已建立'
  })

  async function authenticate() {
    loading.value = true
    error.value = ''
    try {
      config.value = await fetchAuthConfig()
      try {
        currentUser.value = await fetchCurrentUser()
        return currentUser.value
      } catch (existingError) {
        if (!(existingError instanceof ApiError) || existingError.status !== 401) throw existingError
      }

      if (config.value.mode === 'demo') {
        currentUser.value = await loginDemo()
        return currentUser.value
      }
      if (!config.value.dingtalkReady) {
        throw new Error('钉钉免登配置尚未完成，请联系管理员')
      }
      if (!isDingTalkEnvironment()) {
        throw new Error('请从钉钉工作台打开“智询”')
      }
      const url = new URL(location.href)
      const corpId = url.searchParams.get('corpid') || config.value.corpId
      const code = await requestDingTalkAuthorizationCode(corpId)
      currentUser.value = await loginWithDingTalk(code, corpId)
      return currentUser.value
    } catch (authError) {
      currentUser.value = null
      error.value = authError instanceof ApiError ? authError.message : String(authError?.message || '身份认证失败')
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    currentUser,
    config,
    sourceLabel,
    authenticate
  }
}
