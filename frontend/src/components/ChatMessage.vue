<template>
  <div :class="['message', `message-${type.toLowerCase()}`]">
    <div class="message-icon">{{ icon }}</div>
    <div class="message-content">
      <div class="message-label">{{ label }}</div>
      <div class="message-text" :class="{ typing: isTyping }">
        <template v-if="isCode">
          <pre><code>{{ content }}</code></pre>
        </template>
        <template v-else>
          {{ content }}
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  type: { type: String, required: true },
  content: { type: String, default: '' }
})

const displayedContent = ref('')
const isTyping = ref(false)

const typeConfig = {
  USER: { icon: '👤', label: 'User' },
  THINKING: { icon: '🧠', label: 'Thinking' },
  PLANNING: { icon: '📋', label: 'Planning' },
  MEMORY_READ: { icon: '📖', label: 'Memory' },
  MEMORY_WRITE: { icon: '💾', label: 'Memory' },
  TOOL_CALL: { icon: '🔧', label: 'Tool Call' },
  TOOL_RESULT: { icon: '📦', label: 'Tool Result' },
  VERIFICATION: { icon: '✅', label: 'Verification' },
  REFLECTION: { icon: '🔍', label: 'Reflection' },
  REPAIR: { icon: '🔧', label: 'Repair' },
  RETRY: { icon: '🔄', label: 'Retry' },
  MAX_RETRIES_EXCEEDED: { icon: '⛔', label: 'Max Retries' },
  FINAL: { icon: '✨', label: 'Final Answer' },
  ERROR: { icon: '❌', label: 'Error' }
}

const config = computed(() => typeConfig[props.type] || { icon: '📝', label: props.type })
const icon = computed(() => config.value.icon)
const label = computed(() => config.value.label)

const isCode = computed(() => {
  return props.content.includes('```') || props.content.includes('class ') ||
         props.content.includes('function ') || props.content.includes('import ')
})

watch(() => props.content, (newVal) => {
  if (props.type === 'THINKING' || props.type === 'PLANNING') {
    isTyping.value = true
    displayedContent.value = ''
    let i = 0
    const interval = setInterval(() => {
      if (i < newVal.length) {
        displayedContent.value += newVal[i]
        i++
      } else {
        clearInterval(interval)
        isTyping.value = false
      }
    }, 20)
  } else {
    displayedContent.value = newVal
  }
}, { immediate: true })
</script>

<style scoped>
.message {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-user {
  background: var(--msg-user-bg);
}

.message-thinking, .message-planning {
  background: var(--msg-thinking-bg);
  border-left: 3px solid var(--purple);
}

.message-tool-call {
  background: var(--msg-tool-call-bg);
  border-left: 3px solid var(--accent);
}

.message-tool-result {
  background: var(--msg-tool-result-bg);
  border-left: 3px solid var(--success);
}

.message-verification {
  background: var(--msg-verification-bg);
  border-left: 3px solid #eab308;
}

.message-final {
  background: var(--msg-final-bg);
  border-left: 3px solid var(--pink);
}

.message-error {
  background: var(--msg-error-bg);
  border-left: 3px solid var(--danger);
}

.message-reflection {
  background: var(--msg-reflection-bg);
  border-left: 3px solid #8b5cf6;
}

.message-repair {
  background: var(--msg-repair-bg);
  border-left: 3px solid var(--cyan);
}

.message-retry {
  background: var(--msg-retry-bg);
  border-left: 3px solid var(--warning);
}

.message-max-retries-exceeded {
  background: var(--msg-max-retries-bg);
  border-left: 3px solid var(--dark-red);
}

.message-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.message-text {
  color: var(--text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-text.typing::after {
  content: '▋';
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.message-text pre {
  background: var(--bg-code);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
}

.message-text code {
  font-family: 'Fira Code', monospace;
  font-size: 13px;
}
</style>
