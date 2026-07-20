<template>
  <div class="composer-shell">
    <div v-if="error" class="composer-error" role="alert">{{ error }}</div>
    <form class="composer" @submit.prevent="submit">
      <textarea
        v-model="draft"
        rows="1"
        maxlength="1000"
        placeholder="输入你的问题…"
        aria-label="输入问题"
        @keydown.enter.exact.prevent="submit"
      ></textarea>
      <div class="composer-actions">
        <span>{{ draft.length }}/1000</span>
        <button type="submit" :disabled="busy || !draft.trim()" aria-label="发送问题">
          <span>发送</span><b aria-hidden="true">↑</b>
        </button>
      </div>
    </form>
    <p><span>智询可能产生错误，请以引用的原始资料为准</span><b>Enter 发送</b></p>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  busy: { type: Boolean, default: false },
  externalQuestion: { type: String, default: '' },
  error: { type: String, default: '' }
})
const emit = defineEmits(['submit'])
const draft = ref('')

watch(() => props.externalQuestion, (value) => {
  if (value) draft.value = value
})

function submit() {
  const value = draft.value.trim()
  if (!value || props.busy) return
  emit('submit', value)
  draft.value = ''
}
</script>
