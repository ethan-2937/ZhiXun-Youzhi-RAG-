<template>
  <div class="tw:shrink-0 tw:px-3 tw:pb-[max(12px,env(safe-area-inset-bottom))] tw:pt-2 tw:sm:px-7 tw:sm:pb-5 tw:lg:px-12">
    <div
      v-if="error"
      id="composer-error"
      class="rag-composer-error tw:mx-auto tw:mb-2.5 tw:flex tw:w-full tw:max-w-[900px] tw:items-start tw:gap-2.5 tw:rounded-xl tw:border tw:border-[#ecd2cc] tw:bg-[#fff8f6] tw:px-3.5 tw:py-2.5 tw:text-xs tw:leading-5 tw:text-[#9b4635]"
      role="alert"
    >
      <span class="tw:mt-0.5 tw:grid tw:size-4 tw:shrink-0 tw:place-items-center tw:rounded-full tw:bg-[#b6533f] tw:text-[10px] tw:font-bold tw:text-white" aria-hidden="true">!</span>
      <span>{{ error }}</span>
    </div>

    <form
      class="rag-composer-panel tw:mx-auto tw:w-full tw:max-w-[900px] tw:rounded-2xl tw:border tw:border-[var(--line-strong)] tw:bg-white tw:px-4 tw:pb-3 tw:pt-3.5 tw:shadow-[0_16px_42px_rgba(25,34,46,0.1)] tw:sm:px-5 tw:sm:pb-3.5 tw:sm:pt-4"
      :data-busy="busy"
      @submit.prevent="submit"
    >
      <label class="tw:sr-only" for="question-input">输入问题</label>
      <textarea
        id="question-input"
        ref="textarea"
        v-model="draft"
        class="tw:block tw:min-h-10 tw:max-h-32 tw:w-full tw:resize-none tw:overflow-y-hidden tw:border-0 tw:bg-transparent tw:p-0 tw:text-[15px] tw:leading-7 tw:text-[var(--ink)] tw:outline-none tw:placeholder:text-[var(--muted-light)]"
        rows="1"
        maxlength="1000"
        placeholder="输入制度、流程或产品问题…"
        aria-label="输入问题"
        :aria-describedby="error ? 'composer-hint composer-error' : 'composer-hint'"
        :aria-invalid="Boolean(error)"
        @input="resizeTextarea"
        @keydown.enter.exact.prevent="submit"
      ></textarea>

      <div class="tw:mt-2.5 tw:flex tw:items-end tw:justify-between tw:gap-3 tw:border-t tw:border-[var(--line)] tw:pt-2.5">
        <p id="composer-hint" class="tw:m-0 tw:hidden tw:items-center tw:gap-2 tw:text-[11px] tw:leading-5 tw:text-[var(--muted-light)] tw:sm:flex">
          <span><kbd>Enter</kbd> 发送</span>
          <i class="tw:size-0.5 tw:rounded-full tw:bg-[var(--line-strong)]" aria-hidden="true"></i>
          <span><kbd>Shift + Enter</kbd> 换行</span>
        </p>

        <div class="tw:ml-auto tw:flex tw:items-center tw:gap-2.5">
          <span
            class="rag-character-count tw:min-w-[54px] tw:text-right tw:text-[11px] tw:font-medium tw:tabular-nums tw:text-[var(--muted-light)]"
            :data-state="draft.length >= 950 ? 'critical' : draft.length >= 800 ? 'warning' : 'default'"
          >
            {{ draft.length }} / 1000
          </span>
          <button
            type="submit"
            class="rag-send-button tw:flex tw:size-11 tw:items-center tw:justify-center tw:gap-2 tw:rounded-xl tw:border-0 tw:px-0 tw:text-xs tw:font-semibold tw:text-white tw:sm:w-auto tw:sm:min-w-[92px] tw:sm:px-4"
            :disabled="busy || !draft.trim()"
            :aria-label="busy ? '正在整理回答，请稍候' : '发送问题'"
          >
            <span class="tw:hidden tw:sm:inline">{{ busy ? '整理中' : '发送' }}</span>
            <b aria-hidden="true">{{ busy ? '…' : '↑' }}</b>
          </button>
        </div>
      </div>
    </form>

    <p class="tw:mx-auto tw:mb-0 tw:mt-2 tw:w-full tw:max-w-[900px] tw:text-center tw:text-[10px] tw:leading-4 tw:text-[var(--muted-light)]">
      智询可能产生错误，请以引用的原始资料为准
    </p>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  busy: { type: Boolean, default: false },
  externalQuestion: { type: String, default: '' },
  error: { type: String, default: '' }
})
const emit = defineEmits(['submit'])
const draft = ref('')
const textarea = ref(null)

watch(() => props.externalQuestion, async (value) => {
  if (!value) return
  draft.value = value.slice(0, 1000)
  await nextTick()
  resizeTextarea()
})

function resizeTextarea() {
  const element = textarea.value
  if (!element) return
  element.style.height = 'auto'
  const height = Math.min(element.scrollHeight, 128)
  element.style.height = `${height}px`
  element.style.overflowY = element.scrollHeight > 128 ? 'auto' : 'hidden'
}

async function submit() {
  const value = draft.value.trim()
  if (!value || props.busy) return
  emit('submit', value)
  draft.value = ''
  await nextTick()
  resizeTextarea()
}
</script>
