import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import QuestionComposer from './QuestionComposer.vue'

describe('QuestionComposer', () => {
  it('submits a trimmed question and resets the draft', async () => {
    const wrapper = mount(QuestionComposer)
    const input = wrapper.get('textarea')

    await input.setValue('  差旅审批如何发起？  ')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toEqual([['差旅审批如何发起？']])
    expect(input.element.value).toBe('')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('uses Enter to submit while Shift+Enter remains available for a newline', async () => {
    const wrapper = mount(QuestionComposer)
    const input = wrapper.get('textarea')

    await input.setValue('第一行')
    await input.trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(wrapper.emitted('submit')).toBeUndefined()

    await input.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('submit')).toEqual([['第一行']])
  })

  it('keeps the draft and exposes an explicit busy state while submission is blocked', async () => {
    const wrapper = mount(QuestionComposer, { props: { busy: true } })
    const input = wrapper.get('textarea')
    const button = wrapper.get('button[type="submit"]')

    await input.setValue('下一条问题')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(input.element.value).toBe('下一条问题')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-label')).toBe('正在整理回答，请稍候')
    expect(button.text()).toContain('整理中')
  })

  it('limits external text, grows within its height budget, and associates errors', async () => {
    const wrapper = mount(QuestionComposer, { props: { error: '暂时无法完成问答' } })
    const input = wrapper.get('textarea')
    Object.defineProperty(input.element, 'scrollHeight', { configurable: true, value: 180 })

    await wrapper.setProps({ externalQuestion: '问'.repeat(1200) })
    await flushPromises()

    expect(input.element.value).toHaveLength(1000)
    expect(input.element.style.height).toBe('128px')
    expect(input.element.style.overflowY).toBe('auto')
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toContain('composer-error')
    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法完成问答')
    expect(wrapper.get('.rag-character-count').attributes('data-state')).toBe('critical')
  })
})
