<template>
  <div class="chat-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <h2>Sessions</h2>
        <button class="new-chat-btn" @click="createNewSession">+</button>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: session.id === currentSessionId }]"
          @click="selectSession(session.id)"
        >
          <span class="session-name">{{ session.name }}</span>
          <button class="delete-btn" @click.stop="deleteSession(session.id)">×</button>
        </div>
      </div>
    </aside>

    <main class="main-content">
      <header class="chat-header">
        <h1>AI Coding Agent</h1>
        <div class="header-actions">
          <button class="stop-btn" v-if="isStreaming" @click="stopGeneration">
            ■ Stop
          </button>
          <button class="clear-btn" @click="clearHistory">Clear</button>
        </div>
      </header>

      <StreamViewer :events="events" />

      <InputBox
        ref="inputBoxRef"
        :disabled="isStreaming"
        :loading="isStreaming"
        @submit="handleSubmit"
      />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import StreamViewer from '../components/StreamViewer.vue'
import InputBox from '../components/InputBox.vue'
import { createAgentStreamPost } from '../api/agent.js'

const sessions = ref([])
const currentSessionId = ref(null)
const events = ref([])
const isStreaming = ref(false)
const inputBoxRef = ref(null)
let currentStream = null

function generateSessionName(message) {
  return message.substring(0, 30) + (message.length > 30 ? '...' : '')
}

function createNewSession() {
  const id = Date.now().toString()
  sessions.value.unshift({ id, name: 'New Chat', messages: [] })
  currentSessionId.value = id
  events.value = []
}

function selectSession(id) {
  currentSessionId.value = id
  const session = sessions.value.find(s => s.id === id)
  events.value = session?.messages || []
}

function deleteSession(id) {
  sessions.value = sessions.value.filter(s => s.id !== id)
  if (currentSessionId.value === id) {
    const remaining = sessions.value[0]
    if (remaining) {
      selectSession(remaining.id)
    } else {
      createNewSession()
    }
  }
}

function clearHistory() {
  events.value = []
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session) session.messages = []
}

function handleSubmit(message) {
  console.log('handleSubmit called:', message)
  if (!message.trim()) return

  if (!currentSessionId.value) {
    createNewSession()
  }

  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session) {
    session.name = generateSessionName(message)
    session.messages.push({ type: 'USER', content: message })
  }

  events.value.push({ type: 'USER', content: message })
  isStreaming.value = true

  currentStream = createAgentStreamPost(message)
  console.log('Starting stream...')
  currentStream.start(
    (data) => {
      console.log('Received data:', data)
      handleStreamEvent(data)
    },
    (err) => {
      console.error('Stream error:', err)
      isStreaming.value = false
      events.value.push({ type: 'ERROR', content: 'Connection error: ' + err.message })
    }
  )
}

function handleStreamEvent(data) {
  console.log('handleStreamEvent:', data)
  const event = {
    type: data.type || data.eventType || 'UNKNOWN',
    content: data.content || data.message || '',
    toolName: data.toolName || null,
    input: data.input || null
  }

  if (event.type === 'TOOL_CALL') {
    events.value.push({
      type: 'TOOL_CALL',
      toolName: event.toolName || extractToolName(event.content),
      input: event.input || event.content,
      content: ''
    })
  } else if (event.type === 'TOOL_RESULT') {
    events.value.push({
      type: 'TOOL_RESULT',
      toolName: event.toolName || extractToolName(event.content),
      content: event.content
    })
  } else {
    events.value.push(event)
  }

  if (event.type === 'FINAL' || event.type === 'ERROR') {
    isStreaming.value = false
    saveSession()
  }
}

function extractToolName(content) {
  if (!content) return 'Unknown'
  const match = content.match(/(\w+Tool)/)
  return match ? match[1] : 'Unknown'
}

function saveSession() {
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session) {
    session.messages = [...events.value]
  }
}

function stopGeneration() {
  if (currentStream) {
    currentStream.abort()
    currentStream = null
  }
  isStreaming.value = false
}

onMounted(() => {
  if (sessions.value.length === 0) {
    createNewSession()
  }
  inputBoxRef.value?.focus()
})

onUnmounted(() => {
  if (currentStream) {
    currentStream.abort()
  }
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  background: #0f0f1a;
}

.sidebar {
  width: 240px;
  background: #1a1a2e;
  border-right: 1px solid #2a2a4a;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #2a2a4a;
}

.sidebar-header h2 {
  font-size: 14px;
  font-weight: 600;
  color: #888;
}

.new-chat-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: #3b82f6;
  color: white;
  cursor: pointer;
  font-size: 18px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
}

.session-item:hover {
  background: #2a2a4a;
}

.session-item.active {
  background: #2a2a4a;
  border-left: 3px solid #3b82f6;
}

.session-name {
  font-size: 13px;
  color: #ccc;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.delete-btn {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: #666;
  cursor: pointer;
  font-size: 14px;
  display: none;
}

.session-item:hover .delete-btn {
  display: block;
}

.delete-btn:hover {
  background: #ef4444;
  color: white;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #2a2a4a;
  background: #1a1a2e;
}

.chat-header h1 {
  font-size: 16px;
  font-weight: 600;
  color: #e0e0e0;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.stop-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: none;
  background: #ef4444;
  color: white;
  font-size: 12px;
  cursor: pointer;
}

.clear-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid #3a3a5a;
  background: transparent;
  color: #888;
  font-size: 12px;
  cursor: pointer;
}
</style>
