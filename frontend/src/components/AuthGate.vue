<template>
  <main class="auth-gate">
    <section class="auth-card">
      <div class="auth-brand"><div class="auth-seal"><span></span></div><strong>智询</strong></div>
      <div class="auth-status-icon" :class="{ error: !loading }"><span></span></div>
      <h1>{{ loading ? '正在确认身份' : '暂时无法进入' }}</h1>
      <p v-if="loading">正在建立安全会话并确认你的资料访问范围。</p>
      <p v-else role="alert">{{ error }}</p>
      <div v-if="loading" class="auth-progress"><span></span></div>
      <button v-else type="button" @click="$emit('retry')">重新尝试 <b>→</b></button>
      <small>{{ mode === 'dingtalk' ? '钉钉安全会话' : 'MVP 演示会话' }}</small>
    </section>
  </main>
</template>

<script setup>
defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  mode: { type: String, default: 'demo' }
})

defineEmits(['retry'])
</script>
