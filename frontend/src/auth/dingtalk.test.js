import { describe, expect, it, vi } from 'vitest'
import { isDingTalkEnvironment, requestDingTalkAuthorizationCode } from './dingtalk.js'

describe('DingTalk client adapter', () => {
  it('detects DingTalk from the SDK or user agent', () => {
    expect(isDingTalkEnvironment({ env: { platform: 'android' } }, '')).toBe(true)
    expect(isDingTalkEnvironment({ env: { platform: 'notInDingTalk' } }, 'Mozilla DingTalk/7')).toBe(true)
    expect(isDingTalkEnvironment({ env: { platform: 'notInDingTalk' } }, 'Mozilla/5.0')).toBe(false)
  })

  it('requests a one-time code for the configured corporation', async () => {
    const requestAuthCode = vi.fn().mockResolvedValue({ code: 'fictional-one-time-code' })
    const sdk = { runtime: { permission: { requestAuthCode } } }

    const code = await requestDingTalkAuthorizationCode('corp-test-001', sdk)

    expect(code).toBe('fictional-one-time-code')
    expect(requestAuthCode).toHaveBeenCalledWith({ corpId: 'corp-test-001' })
  })

  it('fails clearly when the SDK does not return a code', async () => {
    const sdk = { runtime: { permission: { requestAuthCode: vi.fn().mockResolvedValue({}) } } }

    await expect(requestDingTalkAuthorizationCode('corp-test-001', sdk))
      .rejects.toThrow('钉钉未返回免登码')
  })
})
