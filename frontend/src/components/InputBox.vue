<template>
  <div class="input-box">
    <div class="input-container">
      <textarea
        ref="inputRef"
        v-model="message"
        @keydown.enter.exact.prevent="handleSubmit"
        @keydown.shift.enter="handleNewLine"
        placeholder="Ask me anything about your codebase..."
        rows="1"
        :disabled="disabled"
      ></textarea>
      <button
        class="send-btn"
        @click="handleSubmit"
        :disabled="disabled || !message.trim()"
      >
        <span v-if="!loading">➤</span>
        <span v-else class="loading-spinner">◌</span>
      </button>
    </div>
    <div class="input-hint">
      Press Enter to send, Shift+Enter for new line
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['submit'])

const message = ref('')
const inputRef = ref(null)

function handleSubmit() {
  if (message.value.trim() && !props.disabled && !props.loading) {
    emit('submit', message.value)
    message.value = ''
  }
}

function handleNewLine() {
  const textarea = inputRef.value
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  message.value = message.value.substring(0, start) + '\n' + message.value.substring(end)
  nextTick(() => {
    textarea.selectionStart = textarea.selectionEnd = start + 1
  })
}

watch(message, () => {
  nextTick(() => {
    const textarea = inputRef.value
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
  })
})

function focus() {
  inputRef.value?.focus()
}

defineExpose({ focus })
</script>

<style scoped>
.input-box {
  padding: 16px 20px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
}

.input-container {
  display: flex;
  gap: 12px;
  background: var(--input-container-bg);
  border-radius: 12px;
  padding: 8px;
  align-items: flex-end;
}

.input-container textarea {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  padding: 8px 12px;
  max-height: 150px;
  line-height: 1.5;
}

.input-container textarea::placeholder {
  color: var(--text-hint);
}

.input-container textarea:disabled {
  opacity: 0.6;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  border: none;
  background: var(--accent);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: var(--accent-hover);
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.input-hint {
  font-size: 11px;
  color: var(--text-hint);
  text-align: center;
  margin-top: 8px;
}
</style>
