<template>
  <div
    ref="scrollTarget"
    class="rag-conversation tw:h-full tw:overflow-y-auto tw:px-4 tw:py-5 tw:sm:px-7 tw:sm:py-7 tw:lg:px-12 tw:lg:py-8"
    aria-live="polite"
    :aria-busy="busy"
  >
    <div v-if="messages.length === 0" class="tw:flex tw:min-h-full tw:items-center">
      <section class="rag-launchpad tw:mx-auto tw:grid tw:w-full tw:max-w-[960px] tw:gap-9 tw:py-3 tw:lg:grid-cols-[0.88fr_1.12fr] tw:lg:items-center tw:lg:gap-14">
        <header class="tw:min-w-0">
          <div class="rag-welcome-symbol" aria-hidden="true">
            <span></span><span></span>
          </div>
          <span class="tw:mt-6 tw:block tw:text-xs tw:font-semibold tw:tracking-[0.16em] tw:text-[var(--blue)]">在授权范围内提问</span>
          <h1 class="tw:m-0 tw:mt-4 tw:max-w-[520px] tw:text-[32px] tw:font-semibold tw:leading-[1.16] tw:tracking-[-0.045em] tw:text-[var(--ink)] tw:sm:text-[38px] tw:lg:text-[42px]">
            今天想了解什么？
          </h1>
          <p class="tw:m-0 tw:mt-4 tw:max-w-[500px] tw:text-sm tw:leading-7 tw:text-[var(--muted)]">
            描述制度、流程或产品问题。智询会先检索你的授权资料，再给出可核验的引用。
          </p>
          <div
            class="rag-mode-status tw:mt-6 tw:inline-flex tw:max-w-full tw:items-start tw:gap-2.5 tw:rounded-xl tw:border tw:px-3.5 tw:py-2.5 tw:text-xs tw:leading-5"
            :data-mode="agenticRag ? 'agentic' : realRetrieval ? 'retrieval' : 'demo'"
          >
            <i class="tw:mt-1.5 tw:size-1.5 tw:shrink-0 tw:rounded-full" aria-hidden="true"></i>
            <span>{{ agenticRag
              ? '受控 Agentic RAG 已启用，回答经过授权检索和引用复核'
              : realRetrieval
                ? '真实语义检索已启用，当前回答为授权原文摘录'
                : '当前为演示环境，内容均为虚构示例' }}</span>
          </div>
        </header>

        <section class="tw:min-w-0" aria-labelledby="starter-title">
          <div class="tw:flex tw:items-end tw:justify-between tw:gap-4 tw:border-b tw:border-[var(--line)] tw:pb-4">
            <div>
              <span class="tw:block tw:text-[11px] tw:font-semibold tw:tracking-[0.15em] tw:text-[var(--muted-light)]">QUICK START</span>
              <h2 id="starter-title" class="tw:m-0 tw:mt-1.5 tw:text-lg tw:font-semibold tw:text-[var(--ink)]">从常用问题开始</h2>
            </div>
            <span class="tw:hidden tw:text-xs tw:text-[var(--muted)] tw:sm:block">也可以在下方自由输入</span>
          </div>

          <div class="tw:divide-y tw:divide-[var(--line)]">
            <button
              v-for="(item, index) in sampleQuestions"
              :key="item"
              type="button"
              class="rag-starter-button tw:grid tw:min-h-[78px] tw:w-full tw:grid-cols-[42px_minmax(0,1fr)_34px] tw:items-center tw:gap-3 tw:border-0 tw:bg-transparent tw:px-1 tw:py-3 tw:text-left"
              data-testid="starter-question"
              @click="$emit('ask', item)"
            >
              <small class="tw:text-[11px] tw:font-semibold tw:tracking-[0.12em] tw:text-[var(--muted-light)]">{{ String(index + 1).padStart(2, '0') }}</small>
              <span class="tw:min-w-0 tw:text-sm tw:font-medium tw:leading-6 tw:text-[var(--ink-soft)]">{{ item }}</span>
              <b class="tw:grid tw:size-8 tw:place-items-center tw:rounded-full tw:bg-[var(--blue-soft)] tw:text-sm tw:font-medium tw:text-[var(--blue)]" aria-hidden="true">→</b>
            </button>
          </div>
        </section>
      </section>
    </div>

    <div v-else class="tw:mx-auto tw:w-full tw:max-w-[900px] tw:pb-6">
      <article
        v-for="message in messages"
        :key="message.id"
        class="rag-message-enter tw:mt-6"
        :data-role="message.role"
      >
        <div
          class="tw:mb-2 tw:text-[11px] tw:font-semibold tw:text-[var(--muted)]"
          :class="message.role === 'user' ? 'tw:text-right' : 'tw:text-left'"
        >
          {{ message.role === 'user' ? '你' : '智询' }}
        </div>

        <div
          v-if="message.role === 'user'"
          class="tw:ml-auto tw:w-fit tw:max-w-[82%] tw:break-words tw:rounded-[16px_5px_16px_16px] tw:bg-[var(--blue-deep)] tw:px-4 tw:py-3 tw:text-sm tw:leading-7 tw:text-white tw:shadow-[0_8px_22px_rgba(49,87,232,0.16)] tw:sm:max-w-[72%]"
        >
          {{ message.text }}
        </div>

        <section
          v-else
          class="rag-answer-card tw:rounded-[6px_18px_18px_18px] tw:border tw:border-[var(--line)] tw:bg-white/95 tw:p-5 tw:shadow-[0_12px_34px_rgba(25,34,46,0.055)] tw:sm:p-6"
          :data-status="message.status"
        >
          <header class="tw:flex tw:flex-wrap tw:items-center tw:justify-between tw:gap-3">
            <span class="rag-answer-state tw:inline-flex tw:items-center tw:text-xs tw:font-semibold">
              {{ message.status === 'answered' ? '已找到依据' : '资料不足' }}
            </span>
            <small class="tw:text-[11px] tw:text-[var(--muted-light)]">
              {{ message.status === 'answered' ? '来源可核验' : '未使用外部常识补写' }}
            </small>
          </header>

          <p class="tw:m-0 tw:mt-5 tw:whitespace-pre-wrap tw:break-words tw:text-[15px] tw:leading-8 tw:text-[var(--ink-soft)]">
            {{ message.answer }}
          </p>

          <section v-if="message.citations?.length" class="tw:mt-6 tw:border-t tw:border-[var(--line)] tw:pt-5" aria-label="引用资料">
            <div class="tw:mb-3 tw:flex tw:items-center tw:justify-between tw:gap-4">
              <h3 class="tw:m-0 tw:text-xs tw:font-semibold tw:text-[var(--ink-soft)]">引用资料</h3>
              <span class="tw:text-[11px] tw:text-[var(--muted-light)]">{{ message.citations.length }} 项授权来源</span>
            </div>

            <div class="tw:grid tw:gap-2.5">
              <article
                v-for="(citation, index) in message.citations"
                :key="citation.documentId"
                class="tw:grid tw:min-w-0 tw:grid-cols-[38px_minmax(0,1fr)] tw:gap-3 tw:rounded-xl tw:border tw:border-[var(--line)] tw:bg-[var(--surface-soft)] tw:p-3.5"
              >
                <span class="rag-file-badge" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="tw:min-w-0">
                  <strong class="tw:block tw:break-words tw:text-[13px] tw:font-semibold tw:leading-5 tw:text-[var(--ink)]">{{ citation.title }}</strong>
                  <small class="tw:mt-1 tw:block tw:text-[11px] tw:text-[var(--muted)]">{{ citation.section }} · 更新于 {{ citation.updatedAt }}</small>
                  <em class="tw:mt-2 tw:block tw:break-words tw:text-xs tw:not-italic tw:leading-6 tw:text-[#5f6874]">“{{ citation.excerpt }}”</em>
                </span>
              </article>
            </div>
          </section>

          <div v-if="message.suggestedQuestions?.length" class="tw:mt-5 tw:flex tw:flex-wrap tw:gap-2" aria-label="后续问题">
            <button
              v-for="suggestion in message.suggestedQuestions.slice(0, 3)"
              :key="suggestion"
              type="button"
              class="rag-followup-button tw:min-h-9 tw:rounded-lg tw:border tw:border-[var(--line)] tw:bg-white tw:px-3 tw:py-1.5 tw:text-left"
              @click="$emit('ask', suggestion)"
            >
              <span class="tw:text-xs tw:leading-5 tw:text-[var(--ink-soft)]">{{ suggestion }}</span>
            </button>
          </div>
        </section>
      </article>

      <div v-if="busy" class="tw:mt-6 tw:flex tw:items-center tw:gap-1.5 tw:text-[var(--muted)]" role="status">
        <span class="rag-thinking-dot"></span>
        <span class="rag-thinking-dot"></span>
        <span class="rag-thinking-dot"></span>
        <p class="tw:m-0 tw:ml-2 tw:text-xs">正在从授权资料中整理依据…</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  sampleQuestions: { type: Array, default: () => [] },
  realRetrieval: { type: Boolean, default: false },
  agenticRag: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})

defineEmits(['ask'])

const scrollTarget = ref(null)

watch(() => [props.messages.length, props.busy], async () => {
  await nextTick()
  if (scrollTarget.value) scrollTarget.value.scrollTop = scrollTarget.value.scrollHeight
})
</script>
