import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AuthGate from './AuthGate.vue'

describe('AuthGate', () => {
  it('communicates the loading state and DingTalk session mode', () => {
    const wrapper = mount(AuthGate, {
      props: { loading: true, mode: 'dingtalk' }
    })

    expect(wrapper.get('section').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('[role="status"]').text()).toContain('正在建立安全会话')
    expect(wrapper.text()).toContain('钉钉安全会话')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('renders a retryable error without changing the supplied message', async () => {
    const wrapper = mount(AuthGate, {
      props: { error: '服务暂时无法连接，请稍后重试' }
    })

    expect(wrapper.get('[role="alert"]').text()).toBe('服务暂时无法连接，请稍后重试')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
