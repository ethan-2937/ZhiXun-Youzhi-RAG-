<template>
  <div ref="scrollTarget" class="conversation" aria-live="polite">
    <div class="welcome-card reveal-1">
      <div class="welcome-symbol" aria-hidden="true"><span></span><span></span></div>
      <span class="eyebrow">基于授权资料回答</span>
      <h1>今天想了解什么？</h1>
      <p>询问公司制度、办事流程或产品规范。每个结论都会附上可核验的资料来源。</p>
      <div class="demo-notice">
        <i></i>{{ realRetrieval ? '真实语义检索已启用，当前回答为授权原文摘录' : '当前为演示环境，内容均为虚构示例' }}
      </div>
    </div>

    <div v-if="messages.length === 0" class="starter-grid reveal-2">
      <button
        v-for="(item, index) in sampleQuestions"
        :key="item"
        type="button"
        class="starter-card"
        @click="$emit('ask', item)"
      >
        <small>{{ String(index + 1).padStart(2, '0') }}</small>
        <span>{{ item }}</span>
        <b aria-hidden="true">→</b>
      </button>
    </div>

    <article v-for="message in messages" :key="message.id" class="message" :class="`message-${message.role}`">
      <div class="message-label">{{ message.role === 'user' ? '你' : '智询' }}</div>
      <div v-if="message.role === 'user'" class="user-bubble">{{ message.text }}</div>
      <div v-else class="answer-card" :class="{ 'is-insufficient': message.status === 'insufficient' }">
        <div class="answer-state">
          <span>{{ message.status === 'answered' ? '已找到依据' : '资料不足' }}</span>
          <small>{{ message.status === 'answered' ? '来源可核验' : '未使用外部常识补写' }}</small>
        </div>
        <p class="answer-text">{{ message.answer }}</p>

        <div v-if="message.citations?.length" class="citations">
          <div class="citation-title">引用资料</div>
          <button v-for="citation in message.citations" :key="citation.documentId" type="button" class="citation-card">
            <span class="file-badge"><i></i></span>
            <span class="citation-copy">
              <strong>{{ citation.title }}</strong>
              <small>{{ citation.section }} · 更新于 {{ citation.updatedAt }}</small>
              <em>“{{ citation.excerpt }}”</em>
            </span>
            <b aria-hidden="true">→</b>
          </button>
        </div>

        <div v-if="message.suggestedQuestions?.length" class="followups">
          <button
            v-for="suggestion in message.suggestedQuestions.slice(0, 3)"
            :key="suggestion"
            type="button"
            @click="$emit('ask', suggestion)"
          >
            {{ suggestion }}
          </button>
        </div>
      </div>
    </article>

    <div v-if="busy" class="thinking-card">
      <span></span><span></span><span></span>
      <p>正在从授权资料中整理依据…</p>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  sampleQuestions: { type: Array, default: () => [] },
  realRetrieval: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})

defineEmits(['ask'])

const scrollTarget = ref(null)

watch(() => [props.messages.length, props.busy], async () => {
  await nextTick()
  if (scrollTarget.value) scrollTarget.value.scrollTop = scrollTarget.value.scrollHeight
})
</script>
