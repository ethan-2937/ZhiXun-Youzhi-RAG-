import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'
import { DEMO_WORKSPACE } from './data/demoFixtures.js'

const AUTH_CONFIG = {
  mode: 'demo',
  corpId: '',
  dingtalkReady: false,
  authorizationCodeMaxLength: 512,
  csrfToken: 'fictional-csrf-token'
}
const DEMO_USER = {
  userId: 'test-user-demo-001',
  displayName: '演示用户',
  department: '产品体验组',
  authSource: 'DEMO',
  roles: ['EMPLOYEE']
}

function jsonResponse(data, ok = true, status = 200) {
  return {
    ok,
    status,
    headers: { get: () => 'application/json' },
    json: async () => data
  }
}

function authenticatedFetch(...tailResponses) {
  return vi.fn()
    .mockResolvedValueOnce(jsonResponse(AUTH_CONFIG))
    .mockResolvedValueOnce(jsonResponse({ code: 'AUTH_REQUIRED', message: '请先认证' }, false, 401))
    .mockResolvedValueOnce(jsonResponse(DEMO_USER))
    .mockResolvedValueOnce(jsonResponse(DEMO_WORKSPACE))
    .mockImplementation(() => Promise.resolve(tailResponses.shift()))
}

describe('MVP experience', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads the demo workspace and makes demo status explicit', async () => {
    const fetchMock = authenticatedFetch()
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('智询')
    expect(wrapper.text()).toContain('领导体验版 · MVP 0.1')
    expect(wrapper.text()).toContain('演示会话已建立')
    expect(wrapper.text()).toContain('差旅与报销')
    expect(fetchMock.mock.calls[2][1].headers['X-XSRF-TOKEN']).toBe('fictional-csrf-token')
  })

  it('labels the bounded Agentic RAG mode without calling it demo content', async () => {
    const agentWorkspace = {
      ...DEMO_WORKSPACE,
      releaseLabel: 'Agentic RAG · 受控试运行',
      user: { ...DEMO_WORKSPACE.user, mode: 'AGENTIC_RAG' }
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(AUTH_CONFIG))
      .mockResolvedValueOnce(jsonResponse({ code: 'AUTH_REQUIRED' }, false, 401))
      .mockResolvedValueOnce(jsonResponse(DEMO_USER))
      .mockResolvedValueOnce(jsonResponse(agentWorkspace))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('受控 Agentic RAG 已启用')
    expect(wrapper.text()).not.toContain('当前为演示环境，内容均为虚构示例')
  })

  it('submits a sample question and renders a grounded citation', async () => {
    const chatResponse = {
      status: 'answered',
      answer: '请先完成审批，再提交合规票据。',
      grounded: true,
      mode: 'DEMO_FIXTURE',
      citations: [{
        documentId: 'doc-demo-travel-v3',
        title: '差旅与费用管理办法（演示）',
        section: '第 4 章',
        excerpt: '提交合规票据。',
        updatedAt: '2026-06-18'
      }],
      suggestedQuestions: []
    }
    const fetchMock = authenticatedFetch(jsonResponse(chatResponse))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(App)
    await flushPromises()

    const starter = wrapper.findAll('[data-testid="starter-question"]')[0]
    await starter.trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(5)
    expect(wrapper.text()).toContain('请先完成审批，再提交合规票据。')
    expect(wrapper.text()).toContain('差旅与费用管理办法（演示）')
    expect(wrapper.text()).toContain('已找到依据')
  })

  it('opens and closes the mobile material drawer', async () => {
    vi.stubGlobal('fetch', authenticatedFetch())
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.mobile-menu').trigger('click')
    expect(wrapper.get('.knowledge-rail').classes()).toContain('is-open')
    await wrapper.get('.drawer-backdrop').trigger('click')
    expect(wrapper.get('.knowledge-rail').classes()).not.toContain('is-open')
  })

  it('opens an authorized source preview from the left file list', async () => {
    const realWorkspace = {
      ...DEMO_WORKSPACE,
      releaseLabel: '真实语义检索 · 试运行',
      user: { ...DEMO_WORKSPACE.user, mode: 'REAL_EMBEDDING_RETRIEVAL' },
      indexedDocuments: 2,
      availableSpaces: 1,
      spaces: [{
        id: 'space-test',
        name: '虚构测试空间',
        documentCount: 2,
        nodes: [{
          id: 'node-test-file',
          title: '虚构测试资料',
          type: 'file',
          itemCount: 2,
          fileType: 'pptx',
          updatedAt: '2026-07-01'
        }]
      }]
    }
    const preview = {
      id: 'node-test-file',
      title: '虚构测试资料',
      fileName: '虚构测试资料.pptx',
      fileType: 'pptx',
      updatedAt: '2026-07-01',
      sectionCount: 2,
      truncated: false,
      downloadAvailable: true,
      sections: [{ documentId: 'doc-test-1', section: '第 1 页', content: '完全虚构的预览正文。' }]
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(AUTH_CONFIG))
      .mockResolvedValueOnce(jsonResponse({ code: 'AUTH_REQUIRED' }, false, 401))
      .mockResolvedValueOnce(jsonResponse(DEMO_USER))
      .mockResolvedValueOnce(jsonResponse(realWorkspace))
      .mockResolvedValueOnce(jsonResponse(preview))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('.node-row.is-file').trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls[4][0]).toBe('/api/knowledge/files/node-test-file')
    expect(wrapper.text()).toContain('完全虚构的预览正文。')
    expect(wrapper.get('.preview-actions a').attributes('href'))
      .toBe('/api/knowledge/files/node-test-file/content')
  })

  it('shows a retryable authentication gate when the backend is offline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('暂时无法进入')
    expect(wrapper.text()).toContain('服务暂时无法连接')
    expect(wrapper.get('button').text()).toContain('重新尝试')
  })

  it('fails closed when DingTalk mode is opened in a normal browser', async () => {
    const config = { ...AUTH_CONFIG, mode: 'dingtalk', corpId: 'corp-test-001', dingtalkReady: true }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(config))
      .mockResolvedValueOnce(jsonResponse({ code: 'AUTH_REQUIRED' }, false, 401))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('请从钉钉工作台打开“智询”')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
