<template>
  <AuthGate
    v-if="initializing || startupError || !currentUser"
    :loading="initializing"
    :error="startupError || authError"
    :mode="authConfig?.mode"
    @retry="initialize"
  />

  <div v-else class="app-shell">
    <header class="topbar">
      <div class="brand-lockup">
        <button class="icon-button mobile-menu" type="button" aria-label="打开资料导航" @click="drawerOpen = true">
          <span></span><span></span>
        </button>
        <div class="brand-seal"><span></span></div>
        <div>
          <strong>智询</strong>
          <small>企业信息助手</small>
        </div>
      </div>

      <div class="topbar-actions">
        <div class="topbar-center">
          <span class="demo-pill">{{ workspace?.releaseLabel || '正在载入演示' }}</span>
          <span class="connection-state api"><i></i>{{ sourceLabel }}</span>
        </div>
        <div class="profile-chip">
          <span>{{ currentUser.displayName?.slice(0, 1) || '员' }}</span>
          <div>
            <strong>{{ currentUser.displayName }}</strong>
            <small>{{ currentUser.department }}</small>
          </div>
        </div>
      </div>
    </header>

    <main class="workspace">
      <KnowledgeRail
        :spaces="workspace?.spaces || []"
        :selected-space-id="selectedSpaceId"
        :selected-file-id="selectedFileId"
        :open="drawerOpen"
        @select-space="selectSpace"
        @open-file="openFile"
        @close="drawerOpen = false"
      />
      <button v-if="drawerOpen" class="drawer-backdrop" type="button" aria-label="关闭导航" @click="drawerOpen = false"></button>

      <section class="answer-workspace">
        <div class="workspace-toolbar reveal-1">
          <div class="scope-title">
            <span>当前资料范围</span>
            <i aria-hidden="true">/</i>
            <strong>{{ selectedSpace?.name || '全部可访问资料' }}</strong>
          </div>
          <div class="workspace-metrics">
            <span><i></i> 权限校验已启用</span>
            <span>{{ workspace?.indexedDocuments || '—' }} 份资料</span>
          </div>
        </div>

        <ConversationView
          :messages="messages"
          :sample-questions="workspace?.sampleQuestions || []"
          :real-retrieval="['REAL_EMBEDDING_RETRIEVAL', 'AGENTIC_RAG'].includes(workspace?.user?.mode)"
          :agentic-rag="workspace?.user?.mode === 'AGENTIC_RAG'"
          :busy="busy"
          @ask="askQuestion"
        />

        <QuestionComposer :busy="busy" :error="errorMessage" @submit="askQuestion" />
      </section>
    </main>

    <FilePreview
      :open="Boolean(selectedFileId)"
      :preview="filePreview"
      :loading="filePreviewLoading"
      :error="filePreviewError"
      :download-url="selectedFileId ? knowledgeFileDownloadUrl(selectedFileId) : ''"
      @close="closeFile"
      @retry="loadSelectedFile"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  ApiError,
  askQuestion as requestAnswer,
  fetchKnowledgeFile,
  fetchWorkspace,
  knowledgeFileDownloadUrl
} from './api/experience.js'
import AuthGate from './components/AuthGate.vue'
import ConversationView from './components/ConversationView.vue'
import FilePreview from './components/FilePreview.vue'
import KnowledgeRail from './components/KnowledgeRail.vue'
import QuestionComposer from './components/QuestionComposer.vue'
import { useAuth } from './composables/useAuth.js'

const workspace = ref(null)
const selectedSpaceId = ref('')
const messages = ref([])
const busy = ref(false)
const drawerOpen = ref(false)
const errorMessage = ref('')
const selectedFileId = ref('')
const filePreview = ref(null)
const filePreviewLoading = ref(false)
const filePreviewError = ref('')
const initializing = ref(true)
const startupError = ref('')
let messageSequence = 0

const {
  error: authError,
  currentUser,
  config: authConfig,
  sourceLabel,
  authenticate
} = useAuth()

const selectedSpace = computed(() => workspace.value?.spaces?.find((item) => item.id === selectedSpaceId.value))

onMounted(initialize)

async function initialize() {
  initializing.value = true
  startupError.value = ''
  try {
    const user = await authenticate()
    if (!user) return
    workspace.value = await fetchWorkspace()
    selectedSpaceId.value = workspace.value.spaces[0]?.id || ''
  } catch (error) {
    startupError.value = error instanceof ApiError ? error.message : '应用资料暂时无法载入'
  } finally {
    initializing.value = false
  }
}

function selectSpace(spaceId) {
  selectedSpaceId.value = spaceId
  drawerOpen.value = false
}

async function openFile(nodeId) {
  selectedFileId.value = nodeId
  filePreview.value = null
  drawerOpen.value = false
  await loadSelectedFile()
}

async function loadSelectedFile() {
  if (!selectedFileId.value || filePreviewLoading.value) return
  filePreviewLoading.value = true
  filePreviewError.value = ''
  try {
    filePreview.value = await fetchKnowledgeFile(selectedFileId.value)
  } catch (error) {
    filePreviewError.value = error instanceof ApiError ? error.message : '资料原文暂时无法载入'
  } finally {
    filePreviewLoading.value = false
  }
}

function closeFile() {
  selectedFileId.value = ''
  filePreview.value = null
  filePreviewError.value = ''
}

async function askQuestion(question) {
  const normalized = String(question || '').trim()
  if (!normalized || busy.value) return
  if (normalized.length > 1000) {
    errorMessage.value = '问题不能超过1000个字符'
    return
  }
  errorMessage.value = ''
  messages.value.push({ id: ++messageSequence, role: 'user', text: normalized })
  busy.value = true
  try {
    const response = await requestAnswer(normalized, selectedSpaceId.value)
    messages.value.push({ id: ++messageSequence, role: 'assistant', ...response })
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '暂时无法完成问答，请稍后再试'
  } finally {
    busy.value = false
  }
}
</script>
