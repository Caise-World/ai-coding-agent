<template>
  <div class="rag-panel">
    <div class="rag-header">
      <span class="rag-icon">🔍</span>
      <span class="rag-title">RAG Context</span>
      <span class="rag-count">{{ chunkCount }} chunks</span>
      <button v-if="collapsed" class="expand-btn" @click="collapsed = false">Show</button>
      <button v-else class="expand-btn" @click="collapsed = true">Hide</button>
    </div>
    <div v-if="!collapsed" class="rag-body">
      <div v-if="chunks.length === 0" class="rag-empty">No chunks retrieved</div>
      <div v-for="chunk in chunks" :key="chunk.idx" class="rag-chunk">
        <div class="chunk-loc">
          <span class="loc-idx">#{{ chunk.idx }}</span>
          <span class="loc-path" :title="chunk.path">{{ shortPath(chunk.path) }}</span>
          <span class="loc-lines">L{{ chunk.startLine }}-{{ chunk.endLine }}</span>
          <span class="loc-score">score {{ chunk.score.toFixed(3) }}</span>
        </div>
        <pre class="chunk-content">{{ chunk.content }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  content: { type: String, default: '' }
})

const collapsed = ref(false)

const chunks = computed(() => parseChunks(props.content))
const chunkCount = computed(() => chunks.value.length)

function parseChunks(content) {
  if (!content) return []
  const blocks = content.split(/\n\n+/).map(b => b.trim()).filter(Boolean)
  return blocks.map(parseChunk).filter(Boolean)
}

function parseChunk(block) {
  // Format: [N] path:startLine-endLine (score=0.xxx)\ncontent
  const headerMatch = block.match(/^\[(\d+)\]\s+(.+?):(\d+)-(\d+)\s+\(score=([0-9.]+)\)\s*\n([\s\S]*)$/)
  if (!headerMatch) return null
  return {
    idx: parseInt(headerMatch[1]),
    path: headerMatch[2],
    startLine: parseInt(headerMatch[3]),
    endLine: parseInt(headerMatch[4]),
    score: parseFloat(headerMatch[5]),
    content: headerMatch[6]
  }
}

function shortPath(path) {
  if (!path) return ''
  if (path.length <= 60) return path
  return '...' + path.slice(-57)
}
</script>

<style scoped>
.rag-panel {
  background: #1a1a3a;
  border-radius: 12px;
  border: 1px solid #2a3a5a;
  overflow: hidden;
  margin: 8px 0;
}

.rag-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #0f1f3f;
  border-bottom: 1px solid #2a3a5a;
}

.rag-icon {
  font-size: 16px;
}

.rag-title {
  font-weight: 600;
  color: #60a5fa;
  font-size: 13px;
  flex: 1;
}

.rag-count {
  font-size: 11px;
  color: #888;
  background: #2a2a4a;
  padding: 2px 8px;
  border-radius: 10px;
}

.expand-btn {
  background: transparent;
  border: 1px solid #3a3a5a;
  color: #888;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 4px;
  cursor: pointer;
}

.expand-btn:hover {
  border-color: #3b82f6;
  color: #60a5fa;
}

.rag-body {
  max-height: 400px;
  overflow-y: auto;
}

.rag-empty {
  padding: 16px;
  text-align: center;
  color: #666;
  font-size: 12px;
  font-style: italic;
}

.rag-chunk {
  border-bottom: 1px solid #2a2a4a;
}

.rag-chunk:last-child {
  border-bottom: none;
}

.chunk-loc {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #15152a;
  font-size: 11px;
  font-family: 'Fira Code', 'Consolas', monospace;
}

.loc-idx {
  color: #ec4899;
  font-weight: 600;
  background: #2a1a3a;
  padding: 1px 6px;
  border-radius: 3px;
}

.loc-path {
  color: #60a5fa;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loc-lines {
  color: #888;
  background: #2a2a4a;
  padding: 1px 6px;
  border-radius: 3px;
}

.loc-score {
  color: #22c55e;
  font-weight: 500;
}

.chunk-content {
  padding: 10px 16px;
  margin: 0;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  color: #d0d0e0;
  background: #0d0d1a;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.5;
}
</style>
