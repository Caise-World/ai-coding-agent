<template>
  <div class="workspace-opener">
    <div class="opener-row">
      <input
        v-model="pathInput"
        type="text"
        class="path-input"
        placeholder="/path/to/project"
        :disabled="loading"
        @keydown.enter="handleOpen"
      />
      <button
        class="open-btn"
        :disabled="loading || !pathInput.trim()"
        @click="handleOpen"
      >
        <span v-if="loading" class="spinner">◌</span>
        <span v-else>{{ currentWorkspace ? 'Reindex' : 'Open' }}</span>
      </button>
    </div>
    <div v-if="currentWorkspace" class="status-row">
      <span class="status-icon">📁</span>
      <span class="status-path" :title="currentWorkspace.path">{{ currentWorkspace.path }}</span>
      <span class="status-meta">
        {{ currentWorkspace.chunksCreated }} chunks
        <span v-if="currentWorkspace.cacheHit" class="cache-badge">cached</span>
      </span>
    </div>
    <div v-if="error" class="error-row">
      <span class="error-icon">⚠</span>
      <span class="error-msg">{{ error }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { openWorkspace, getCurrentWorkspace } from '../api/workspace.js'

const emit = defineEmits(['workspace-changed'])

const pathInput = ref('')
const loading = ref(false)
const error = ref(null)
const currentWorkspace = ref(null)

async function refreshCurrent() {
  try {
    currentWorkspace.value = await getCurrentWorkspace()
    if (currentWorkspace.value) {
      pathInput.value = currentWorkspace.value.path
    }
  } catch (e) {
    currentWorkspace.value = null
  }
}

async function handleOpen() {
  const path = pathInput.value.trim()
  if (!path) return
  loading.value = true
  error.value = null
  try {
    const result = await openWorkspace(path)
    currentWorkspace.value = result
    emit('workspace-changed', result)
  } catch (e) {
    error.value = e.message || String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCurrent()
})
</script>

<style scoped>
.workspace-opener {
  padding: 12px 16px;
  background: #15152a;
  border-bottom: 1px solid #2a2a4a;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.opener-row {
  display: flex;
  gap: 8px;
}

.path-input {
  flex: 1;
  background: #0f0f1a;
  border: 1px solid #2a2a4a;
  border-radius: 6px;
  padding: 8px 12px;
  color: #e0e0e0;
  font-size: 13px;
  font-family: 'Fira Code', 'Consolas', monospace;
  outline: none;
  min-width: 0;
}

.path-input:focus {
  border-color: #3b82f6;
}

.path-input:disabled {
  opacity: 0.6;
}

.open-btn {
  padding: 8px 16px;
  border-radius: 6px;
  border: none;
  background: #3b82f6;
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  flex-shrink: 0;
  min-width: 80px;
}

.open-btn:hover:not(:disabled) {
  background: #2563eb;
}

.open-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.spinner {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #888;
  padding: 6px 10px;
  background: #0f1f2f;
  border-radius: 6px;
  border-left: 3px solid #22c55e;
}

.status-icon {
  font-size: 14px;
}

.status-path {
  flex: 1;
  font-family: 'Fira Code', 'Consolas', monospace;
  color: #60a5fa;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-meta {
  color: #888;
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.cache-badge {
  background: #f59e0b20;
  color: #f59e0b;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 500;
}

.error-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #fca5a5;
  padding: 6px 10px;
  background: #2a1a1a;
  border-radius: 6px;
  border-left: 3px solid #ef4444;
}

.error-icon {
  font-size: 14px;
}

.error-msg {
  font-family: 'Fira Code', 'Consolas', monospace;
}
</style>
