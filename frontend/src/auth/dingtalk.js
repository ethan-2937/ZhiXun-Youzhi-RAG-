export function isDingTalkEnvironment(sdk = globalThis.dd, userAgent = globalThis.navigator?.userAgent || '') {
  const platform = sdk?.env?.platform
  if (platform && platform !== 'notInDingTalk') return true
  return /DingTalk/i.test(userAgent)
}

export async function requestDingTalkAuthorizationCode(corpId, sdk = null) {
  if (!corpId) throw new Error('企业标识缺失，请从钉钉工作台重新进入应用')
  const activeSdk = sdk || await loadDingTalkSdk()
  const requestAuthCode = activeSdk?.runtime?.permission?.requestAuthCode
  if (typeof requestAuthCode !== 'function') {
    throw new Error('当前钉钉客户端不支持免登，请升级后重试')
  }
  try {
    const result = await requestAuthCode({ corpId })
    const code = result?.code || result?.authCode
    if (!code) throw new Error('钉钉未返回免登码')
    return code
  } catch (error) {
    throw new Error(error?.message || '无法获取钉钉免登码，请重新进入应用')
  }
}

async function loadDingTalkSdk() {
  const module = await import('dingtalk-jsapi')
  return module.default || module
}
