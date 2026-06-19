<template>
  <div class="stream-viewer">
    <div class="timeline">
      <div
        v-for="(event, index) in events"
        :key="index"
        class="timeline-item"
        :class="[`event-${event.type.toLowerCase()}`]"
      >
        <div class="timeline-marker" :class="getMarkerClass(event.type)">
          {{ getMarker(event.type) }}
        </div>
        <div class="timeline-content">
          <template v-if="event.type === 'TOOL_CALL'">
            <ToolCallPanel
              :toolName="event.toolName"
              :input="event.input || event.content"
              :output="getToolOutput(event.toolName)"
              :status="getToolStatus(event.toolName)"
            />
          </template>
          <template v-else-if="event.type === 'TOOL_RESULT'">
            <!-- Tool result is displayed within ToolCallPanel -->
          </template>
          <template v-else>
            <ChatMessage :type="event.type" :content="event.content" />
          </template>
        </div>
      </div>
    </div>
    <div ref="bottomRef"></div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import ChatMessage from './ChatMessage.vue'
import ToolCallPanel from './ToolCallPanel.vue'

const props = defineProps({
  events: { type: Array, default: () => [] }
})

const bottomRef = ref(null)

watch(() => props.events.length, () => {
  nextTick(() => {
    bottomRef.value?.scrollIntoView({ behavior: 'smooth' })
  })
})

function getMarker(type) {
  const markers = {
    THINKING: '🧠',
    PLANNING: '📋',
    TOOL_CALL: '🔧',
    TOOL_RESULT: '📦',
    MEMORY_READ: '📖',
    MEMORY_WRITE: '💾',
    VERIFICATION: '✅',
    FINAL: '✨',
    ERROR: '❌'
  }
  return markers[type] || '📝'
}

function getMarkerClass(type) {
  return `marker-${type.toLowerCase()}`
}

const toolOutputs = ref({})
const toolStatuses = ref({})

function getToolOutput(toolName) {
  return toolOutputs.value[toolName]
}

function getToolStatus(toolName) {
  return toolStatuses.value[toolName]
}

// Update tool outputs when TOOL_RESULT events come
watch(() => props.events, (events) => {
  for (const event of events) {
    if (event.type === 'TOOL_RESULT' && event.toolName) {
      toolOutputs.value[event.toolName] = event.content
      if (event.content?.toLowerCase().includes('error')) {
        toolStatuses.value[event.toolName] = 'FAILED'
      } else {
        toolStatuses.value[event.toolName] = 'SUCCESS'
      }
    }
  }
}, { deep: true })
</script>

<style scoped>
.stream-viewer {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.timeline-item {
  display: flex;
  gap: 12px;
  position: relative;
}

.timeline-marker {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
  z-index: 1;
}

.marker-thinking, .marker-planning {
  background: #7c3aed;
}

.marker-tool_call {
  background: #3b82f6;
}

.marker-tool_result {
  background: #22c55e;
}

.marker-memory_read, .marker-memory_write {
  background: #f59e0b;
}

.marker-verification {
  background: #22c55e;
}

.marker-final {
  background: #ec4899;
}

.marker-error {
  background: #ef4444;
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 15px;
  top: 32px;
  bottom: -4px;
  width: 2px;
  background: #2a2a4a;
}
</style>
