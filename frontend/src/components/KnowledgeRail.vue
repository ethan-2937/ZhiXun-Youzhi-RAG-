<template>
  <aside class="knowledge-rail" :class="{ 'is-open': open }" aria-label="资料导航">
    <div class="rail-header">
      <div>
        <span class="eyebrow">访问范围</span>
        <h2>资料空间</h2>
      </div>
      <button class="icon-button rail-close" type="button" aria-label="关闭资料导航" @click="$emit('close')">
        <span aria-hidden="true">×</span>
      </button>
    </div>

    <label class="rail-search">
      <span class="search-icon" aria-hidden="true"></span>
      <input v-model="filter" type="search" placeholder="搜索空间或文件" />
    </label>

    <nav class="space-list">
      <section v-for="space in filteredSpaces" :key="space.id" class="space-block">
        <button
          class="space-heading"
          :class="{ active: selectedSpaceId === space.id }"
          type="button"
          @click="$emit('select-space', space.id)"
        >
          <span class="space-mark"><i></i></span>
          <span class="space-copy">
            <strong>{{ space.name }}</strong>
            <small>{{ space.documentCount }} 份资料</small>
          </span>
          <span class="space-arrow" aria-hidden="true">›</span>
        </button>
        <div v-if="selectedSpaceId === space.id" class="node-list">
          <button
            v-for="node in space.nodes"
            :key="node.id"
            type="button"
            class="node-row"
            :class="{ active: selectedFileId === node.id, 'is-file': node.type === 'file' }"
            :aria-label="node.type === 'file' ? `查看 ${node.title}` : node.title"
            @click="openNode(node)"
          >
            <span class="node-file-mark" :data-type="node.fileType || 'folder'"></span>
            <span class="node-copy">
              <strong>{{ node.title }}</strong>
              <small>{{ nodeMeta(node) }}</small>
            </span>
            <b v-if="node.type === 'file'" aria-hidden="true">查看</b>
            <small v-else>{{ node.itemCount }}</small>
          </button>
        </div>
      </section>
    </nav>

    <div class="rail-footnote">
      <span class="lock-icon" aria-hidden="true"></span>
      <p><strong>权限范围已隔离</strong><br />仅显示当前账号有权访问的资料。</p>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  spaces: { type: Array, default: () => [] },
  selectedSpaceId: { type: String, default: '' },
  selectedFileId: { type: String, default: '' },
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['select-space', 'open-file', 'close'])

const filter = ref('')
const filteredSpaces = computed(() => {
  const keyword = filter.value.trim().toLowerCase()
  if (!keyword) return props.spaces
  return props.spaces
    .map((space) => ({
      ...space,
      nodes: space.nodes.filter((node) => node.title.toLowerCase().includes(keyword))
    }))
    .filter((space) => space.name.toLowerCase().includes(keyword) || space.nodes.length)
})

function openNode(node) {
  if (node.type === 'file') emit('open-file', node.id)
}

function nodeMeta(node) {
  if (node.type !== 'file') return `${node.itemCount} 项`
  const type = String(node.fileType || 'FILE').toUpperCase()
  const unit = node.fileType === 'pptx' ? '页' : '节'
  return `${type} · ${node.itemCount} ${unit}`
}
</script>
