import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConversationView from './ConversationView.vue'

const CITATION = {
  documentId: 'doc-fictional-policy-v1',
  title: '虚构差旅制度',
  section: '第 2 章',
  updatedAt: '2026-07-01',
  excerpt: '提交审批后方可预订。'
}

describe('ConversationView', () => {
  it('renders an Agentic launchpad and emits the selected starter question', async () => {
    const questions = ['差旅审批应该如何发起？', '年假如何申请？']
    const wrapper = mount(ConversationView, {
      props: { sampleQuestions: questions, agenticRag: true }
    })

    expect(wrapper.text()).toContain('受控 Agentic RAG 已启用')
    const starters = wrapper.findAll('[data-testid="starter-question"]')
    expect(starters).toHaveLength(2)
    await starters[0].trigger('click')
    expect(wrapper.emitted('ask')).toEqual([[questions[0]]])
  })

  it('replaces the launchpad with a grounded message and authorized citation', async () => {
    const wrapper = mount(ConversationView, {
      props: {
        messages: [
          { id: 1, role: 'user', text: '差旅如何审批？' },
          {
            id: 2,
            role: 'assistant',
            status: 'answered',
            answer: '请先提交审批。',
            citations: [CITATION],
            suggestedQuestions: ['需要哪些票据？']
          }
        ]
      }
    })

    expect(wrapper.text()).not.toContain('今天想了解什么？')
    expect(wrapper.text()).toContain('已找到依据')
    expect(wrapper.text()).toContain('1 项授权来源')
    expect(wrapper.text()).toContain('虚构差旅制度')
    await wrapper.get('[aria-label="后续问题"] button').trigger('click')
    expect(wrapper.emitted('ask')).toEqual([['需要哪些票据？']])
  })

  it('makes insufficient evidence and the busy state explicit', () => {
    const wrapper = mount(ConversationView, {
      props: {
        busy: true,
        messages: [{
          id: 1,
          role: 'assistant',
          status: 'insufficient',
          answer: '当前授权资料不足，无法给出可靠回答。',
          citations: []
        }]
      }
    })

    expect(wrapper.text()).toContain('资料不足')
    expect(wrapper.text()).toContain('未使用外部常识补写')
    expect(wrapper.get('[role="status"]').text()).toContain('正在从授权资料中整理依据')
    expect(wrapper.get('.rag-conversation').attributes('aria-busy')).toBe('true')
  })
})
