<template>
  <main class="auth-canvas tw:relative tw:grid tw:min-h-screen tw:place-items-center tw:overflow-hidden tw:px-4 tw:py-5 tw:md:px-8 tw:md:py-8">
    <section
      class="auth-frame tw:relative tw:grid tw:w-full tw:max-w-[1080px] tw:overflow-hidden tw:rounded-[28px] tw:border tw:border-white/75 tw:bg-white tw:shadow-[0_32px_90px_rgba(31,42,58,0.14)] tw:md:min-h-[620px] tw:md:grid-cols-[1.06fr_.94fr]"
      :aria-busy="loading"
      aria-labelledby="auth-title"
    >
      <div class="tw:relative tw:flex tw:min-h-0 tw:flex-col tw:overflow-hidden tw:bg-[#17233d] tw:px-6 tw:py-6 tw:text-white tw:sm:min-h-[320px] tw:sm:px-10 tw:sm:py-10 tw:md:min-h-full tw:md:px-9 tw:md:py-12 tw:lg:px-12">
        <div class="tw:absolute tw:-right-20 tw:-top-20 tw:h-64 tw:w-64 tw:rounded-full tw:border tw:border-white/10" aria-hidden="true"></div>
        <div class="tw:absolute tw:-right-5 tw:top-6 tw:h-40 tw:w-40 tw:rounded-full tw:border tw:border-white/10" aria-hidden="true"></div>

        <div class="tw:relative tw:flex tw:items-center tw:justify-between tw:gap-4">
          <div class="tw:flex tw:items-center tw:gap-3">
            <div class="tw:grid tw:size-10 tw:place-items-center tw:rounded-xl tw:bg-[#3157e8] tw:shadow-[0_10px_28px_rgba(49,87,232,0.35)]" aria-hidden="true">
              <span class="tw:relative tw:block tw:h-4 tw:w-4 tw:before:absolute tw:before:left-0 tw:before:top-0 tw:before:size-1 tw:before:rounded-full tw:before:bg-white tw:after:absolute tw:after:bottom-0 tw:after:right-0 tw:after:size-1 tw:after:rounded-full tw:after:bg-white">
                <i class="tw:absolute tw:left-1/2 tw:top-1/2 tw:h-px tw:w-3 tw:-translate-x-1/2 tw:-translate-y-1/2 tw:-rotate-45 tw:bg-white/90"></i>
              </span>
            </div>
            <div>
              <strong class="tw:block tw:text-lg tw:font-semibold tw:tracking-[0.12em]">智询</strong>
              <span class="tw:mt-0.5 tw:block tw:text-xs tw:text-white/55">企业信息助手</span>
            </div>
          </div>
          <span class="tw:hidden tw:rounded-full tw:border tw:border-white/15 tw:bg-white/5 tw:px-3 tw:py-1.5 tw:text-[11px] tw:font-medium tw:tracking-[0.14em] tw:text-white/65 tw:sm:inline-flex">SECURE ACCESS</span>
        </div>

        <div class="tw:relative tw:my-auto tw:py-7 tw:sm:py-10 tw:md:py-14">
          <span class="tw:text-xs tw:font-semibold tw:tracking-[0.18em] tw:text-[#8fa7ff]">受控知识问答入口</span>
          <h2 class="tw:mb-0 tw:mt-4 tw:max-w-[430px] tw:text-[28px] tw:font-semibold tw:leading-[1.24] tw:tracking-[-0.035em] tw:text-white tw:sm:mt-5 tw:sm:text-[36px] tw:lg:text-[40px]">
            先确认身份，<br />再进入授权知识。
          </h2>
          <p class="tw:mb-0 tw:mt-5 tw:hidden tw:max-w-[430px] tw:text-sm tw:leading-7 tw:text-white/62 tw:sm:block">
            智询只基于你有权访问的公司资料回答，并为每个结论保留可核验的来源。
          </p>
        </div>

        <ol class="auth-path tw:relative tw:m-0 tw:hidden tw:list-none tw:grid-cols-3 tw:gap-3 tw:p-0 tw:pb-1 tw:md:grid" aria-label="安全访问流程">
          <li class="tw:relative tw:z-10">
            <span class="auth-path-node">01</span>
            <strong class="tw:mt-3 tw:block tw:text-xs tw:font-medium tw:text-white/85">确认身份</strong>
          </li>
          <li class="tw:relative tw:z-10">
            <span class="auth-path-node">02</span>
            <strong class="tw:mt-3 tw:block tw:text-xs tw:font-medium tw:text-white/85">校验权限</strong>
          </li>
          <li class="tw:relative tw:z-10">
            <span class="auth-path-node">03</span>
            <strong class="tw:mt-3 tw:block tw:text-xs tw:font-medium tw:text-white/85">进入资料</strong>
          </li>
        </ol>
      </div>

      <div class="tw:flex tw:min-h-[450px] tw:flex-col tw:justify-center tw:px-7 tw:py-8 tw:sm:px-12 tw:sm:py-12 tw:md:min-h-full tw:md:px-9 tw:lg:px-14">
        <div class="tw:flex tw:items-center tw:justify-between tw:gap-4">
          <span class="tw:text-[11px] tw:font-semibold tw:tracking-[0.16em] tw:text-[var(--muted)]">ACCESS CHECK</span>
          <span class="tw:inline-flex tw:items-center tw:gap-2 tw:whitespace-nowrap tw:rounded-full tw:bg-[var(--surface-soft)] tw:px-3 tw:py-1.5 tw:text-xs tw:text-[var(--muted)]">
            <i class="tw:size-1.5 tw:rounded-full tw:bg-[var(--blue)]" aria-hidden="true"></i>
            {{ mode === 'dingtalk' ? '钉钉安全会话' : 'MVP 演示会话' }}
          </span>
        </div>

        <div class="tw:mt-8 tw:sm:mt-12 tw:md:mt-16">
          <div class="auth-status-mark" :class="loading ? 'is-loading' : 'is-error'" aria-hidden="true">
            <span v-if="loading" class="auth-status-spinner"></span>
            <svg v-else viewBox="0 0 24 24" class="tw:size-6" fill="none">
              <path d="M7 7l10 10M17 7L7 17" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </div>

          <h1 id="auth-title" class="tw:mb-0 tw:mt-7 tw:text-[28px] tw:font-semibold tw:leading-tight tw:tracking-[-0.035em] tw:text-[var(--ink)] tw:sm:text-[32px]">
            {{ loading ? '正在确认身份' : '暂时无法进入' }}
          </h1>
          <p v-if="loading" class="tw:mb-0 tw:mt-4 tw:max-w-md tw:text-sm tw:leading-7 tw:text-[var(--muted)]" role="status">
            正在建立安全会话，并确认你的资料访问范围。
          </p>
          <p v-else class="tw:mb-0 tw:mt-4 tw:max-w-md tw:break-words tw:text-sm tw:leading-7 tw:text-[var(--muted)]" role="alert">
            {{ error }}
          </p>

          <div v-if="loading" class="auth-progress-track tw:mt-9" aria-label="身份确认处理中">
            <span></span>
          </div>
          <button v-else type="button" class="auth-retry-button tw:mt-9 tw:flex tw:min-h-12 tw:w-full tw:items-center tw:justify-between tw:rounded-xl tw:border-0 tw:px-5 tw:text-sm tw:font-semibold" @click="$emit('retry')">
            <span>重新尝试</span>
            <span class="tw:grid tw:size-7 tw:place-items-center tw:rounded-full tw:bg-white/12" aria-hidden="true">→</span>
          </button>
        </div>

        <div class="tw:mt-auto tw:flex tw:items-start tw:gap-3 tw:border-t tw:border-[var(--line)] tw:pt-6">
          <span class="tw:mt-0.5 tw:grid tw:size-6 tw:shrink-0 tw:place-items-center tw:rounded-full tw:bg-[var(--green-soft)] tw:text-xs tw:font-bold tw:text-[var(--green)]" aria-hidden="true">✓</span>
          <p class="tw:m-0 tw:text-xs tw:leading-5 tw:text-[var(--muted)]">
            身份和资料权限均由服务端校验，浏览器不会自行扩大访问范围。
          </p>
        </div>
      </div>
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
