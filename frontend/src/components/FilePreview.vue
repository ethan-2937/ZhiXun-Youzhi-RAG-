<template>
  <div v-if="open" class="file-preview-layer">
    <button class="file-preview-backdrop" type="button" aria-label="关闭原文预览" @click="$emit('close')"></button>
    <aside class="file-preview" role="dialog" aria-modal="true" aria-label="授权原文预览">
      <header class="file-preview-header">
        <div>
          <span class="eyebrow">授权原文</span>
          <h2>{{ preview?.title || '正在打开资料' }}</h2>
          <p v-if="preview">
            <span>{{ preview.fileType?.toUpperCase() }}</span>
            <span>{{ preview.sectionCount }} {{ preview.fileType === 'pptx' ? '页' : '节' }}</span>
            <span>更新于 {{ preview.updatedAt }}</span>
          </p>
        </div>
        <button class="preview-close" type="button" aria-label="关闭原文预览" @click="$emit('close')">×</button>
      </header>

      <div v-if="loading" class="preview-state">
        <span class="preview-loader"></span>
        <strong>正在校验权限并读取原文</strong>
        <p>只会返回当前账号有权访问的内容。</p>
      </div>

      <div v-else-if="error" class="preview-state is-error">
        <strong>暂时无法打开资料</strong>
        <p>{{ error }}</p>
        <button type="button" @click="$emit('retry')">重新载入</button>
      </div>

      <template v-else-if="preview">
        <div class="preview-actions">
          <div>
            <strong>{{ preview.fileName }}</strong>
            <small>以下为系统从原文件提取的只读正文</small>
          </div>
          <a v-if="preview.downloadAvailable" :href="downloadUrl">下载原文件</a>
          <span v-else>原包包含其他权限内容，禁止整包下载</span>
        </div>

        <div class="preview-sections">
          <article v-for="(section, index) in preview.sections" :key="section.documentId" class="preview-section">
            <div class="preview-section-index">{{ String(index + 1).padStart(2, '0') }}</div>
            <div>
              <h3>{{ section.section }}</h3>
              <p>{{ section.content }}</p>
            </div>
          </article>
          <div v-if="preview.truncated" class="preview-truncated">预览已达到字符上限，请下载原文件查看剩余内容。</div>
        </div>
      </template>
    </aside>
  </div>
</template>

<script setup>
defineProps({
  open: { type: Boolean, default: false },
  preview: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  downloadUrl: { type: String, default: '' }
})

defineEmits(['close', 'retry'])
</script>
