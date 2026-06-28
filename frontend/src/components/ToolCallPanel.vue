<template>
  <div class="tool-panel">
    <div class="tool-header">
      <span class="tool-icon">🔧</span>
      <span class="tool-name">{{ toolName }}</span>
    </div>
    <div class="tool-section">
      <div class="section-label">Input</div>
      <pre class="section-content input-content">{{ input }}</pre>
    </div>
    <div class="tool-section" v-if="output">
      <div class="section-label">Output</div>
      <pre class="section-content output-content" :class="{ error: isError }">{{ output }}</pre>
    </div>
    <div class="tool-status" v-if="status">
      <span :class="['status-badge', status.toLowerCase()]">{{ status }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  toolName: { type: String, required: true },
  input: { type: String, default: '' },
  output: { type: String, default: '' },
  status: { type: String, default: null }
})

const isError = computed(() => props.output?.toLowerCase().includes('error') ||
                            props.output?.toLowerCase().includes('exception'))
</script>

<style scoped>
.tool-panel {
  background: var(--tool-panel-bg);
  border-radius: 12px;
  border: 1px solid var(--tool-panel-border);
  overflow: hidden;
  margin: 8px 0;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--tool-header-bg);
  border-bottom: 1px solid var(--tool-panel-border);
}

.tool-icon {
  font-size: 18px;
}

.tool-name {
  font-weight: 600;
  color: var(--accent-text);
  font-size: 14px;
}

.tool-section {
  padding: 12px 16px;
  border-bottom: 1px solid var(--tool-panel-border);
}

.tool-section:last-of-type {
  border-bottom: none;
}

.section-label {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.section-content {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  color: var(--text-primary);
  background: var(--tool-code-bg);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}

.output-content.error {
  border-left: 3px solid var(--danger);
  color: var(--canary-red);
}

.tool-status {
  padding: 8px 16px;
  background: var(--tool-header-bg);
}

.status-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.success {
  background: var(--success-bg);
  color: var(--success);
}

.status-badge.failed {
  background: var(--danger-bg);
  color: var(--danger);
}
</style>
