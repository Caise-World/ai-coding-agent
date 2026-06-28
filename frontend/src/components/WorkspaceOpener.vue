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
  background: var(--ws-bg);
  border-bottom: 1px solid var(--border-color);
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
  background: var(--ws-input-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 8px 12px;
  color: var(--text-primary);
  font-size: 13px;
  font-family: 'Fira Code', 'Consolas', monospace;
  outline: none;
  min-width: 0;
}

.path-input:focus {
  border-color: var(--accent);
}

.path-input:disabled {
  opacity: 0.6;
}

.open-btn {
  padding: 8px 16px;
  border-radius: 6px;
  border: none;
  background: var(--accent);
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  flex-shrink: 0;
  min-width: 80px;
}

.open-btn:hover:not(:disabled) {
  background: var(--accent-hover);
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
  color: var(--text-muted);
  padding: 6px 10px;
  background: var(--ws-status-bg);
  border-radius: 6px;
  border-left: 3px solid var(--success);
}

.status-icon {
  font-size: 14px;
}

.status-path {
  flex: 1;
  font-family: 'Fira Code', 'Consolas', monospace;
  color: var(--accent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-meta {
  color: var(--text-muted);
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.cache-badge {
  background: var(--warning-bg);
  color: var(--warning);
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
  color: var(--canary-red);
  padding: 6px 10px;
  background: var(--canary-red-bg);
  border-radius: 6px;
  border-left: 3px solid var(--danger);
}

.error-icon {
  font-size: 14px;
}

.error-msg {
  font-family: 'Fira Code', 'Consolas', monospace;
}
</style>
