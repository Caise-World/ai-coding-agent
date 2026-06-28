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
          <button class="theme-btn" @click="toggleTheme" :title="isLight ? 'Switch to dark' : 'Switch to light'">
            {{ isLight ? '☀️' : '🌙' }}
          </button>
          <button class="stop-btn" v-if="isStreaming" @click="stopGeneration">
            ■ Stop
          </button>
          <button class="clear-btn" @click="clearHistory">Clear</button>
        </div>
      </header>

      <WorkspaceOpener @workspace-changed="handleWorkspaceChanged" />

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
import WorkspaceOpener from '../components/WorkspaceOpener.vue'
import { createAgentStreamPost } from '../api/agent.js'

const sessions = ref([])
const currentSessionId = ref(null)
const events = ref([])
const isStreaming = ref(false)
const isLight = ref(false)
const inputBoxRef = ref(null)
const sessionStreams = new Map()  // sessionId -> stream object
let streamingForSessionId = null

function applyTheme() {
  document.documentElement.classList.toggle('light', isLight.value)
}
function toggleTheme() {
  isLight.value = !isLight.value
  applyTheme()
  localStorage.setItem('theme', isLight.value ? 'light' : 'dark')
}

function generateSessionName(message) {
  return message.substring(0, 30) + (message.length > 30 ? '...' : '')
}

function persistCurrentSession() {
  if (currentSessionId.value) {
    const session = sessions.value.find(s => s.id === currentSessionId.value)
    if (session) {
      session.messages = [...events.value]
    }
  }
}

function createNewSession() {
  persistCurrentSession()
  const id = Date.now().toString()
  sessions.value.unshift({ id, name: 'New Chat', messages: [] })
  currentSessionId.value = id
  events.value = []
  // Update streaming state for the new session
  isStreaming.value = sessionStreams.has(id)
  streamingForSessionId = isStreaming.value ? id : null
}

function selectSession(id) {
  if (id === currentSessionId.value) return
  persistCurrentSession()
  currentSessionId.value = id
  const session = sessions.value.find(s => s.id === id)
  events.value = session?.messages || []
  // Update streaming state for the selected session
  isStreaming.value = sessionStreams.has(id)
  streamingForSessionId = isStreaming.value ? id : null
}

function stopCurrentStream() {
  persistCurrentSession()
  const stream = sessionStreams.get(currentSessionId.value)
  if (stream) {
    stream.abort()
    sessionStreams.delete(currentSessionId.value)
  }
  isStreaming.value = false
  streamingForSessionId = null
}

function deleteSession(id) {
  const stream = sessionStreams.get(id)
  if (stream) {
    stream.abort()
    sessionStreams.delete(id)
  }
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

function buildHistory() {
  // Extract USER/FINAL pairs from current events to provide conversation context
  const history = []
  for (const event of events.value) {
    if (event.type === 'USER') {
      history.push({ role: 'user', content: event.content })
    } else if (event.type === 'FINAL') {
      history.push({ role: 'assistant', content: event.content })
    }
  }
  // Keep last 10 turns (20 messages) to avoid blowing up the prompt
  return history.slice(-20)
}

function handleSubmit(message) {
  console.log('handleSubmit called:', message)
  if (!message.trim()) return
  // Guard against double-submission (Enter + click firing simultaneously)
  if (sessionStreams.has(currentSessionId.value)) return

  // Abort any running stream from the current session before starting a new one
  stopCurrentStream()

  if (!currentSessionId.value) {
    createNewSession()
  }

  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session) {
    session.name = generateSessionName(message)
    session.messages.push({ type: 'USER', content: message })
  }

  const history = buildHistory()
  events.value.push({ type: 'USER', content: message })
  isStreaming.value = true
  streamingForSessionId = currentSessionId.value

  const stream = createAgentStreamPost(message, null, history)
  const owningSessionId = currentSessionId.value
  sessionStreams.set(owningSessionId, stream)
  console.log('Starting stream for session:', owningSessionId)
  stream.start(
    (data) => {
      console.log('Received data:', data)
      handleStreamEvent(data, owningSessionId)
    },
    (err) => {
      console.error('Stream error:', err)
      if (currentSessionId.value === owningSessionId) {
        isStreaming.value = false
        streamingForSessionId = null
      }
      sessionStreams.delete(owningSessionId)
      events.value.push({ type: 'ERROR', content: 'Connection error: ' + err.message })
    }
  )
}

function handleStreamEvent(data, owningSessionId) {
  console.log('handleStreamEvent:', data, 'for session:', owningSessionId)
  const isActiveSession = owningSessionId === currentSessionId.value
  const event = {
    type: data.type || data.eventType || 'UNKNOWN',
    content: data.content || data.message || '',
    toolName: data.toolName || null,
    input: data.input || null
  }

  // For background (inactive) sessions: only save FINAL/ERROR and clean up
  if (!isActiveSession) {
    if (event.type === 'FINAL' || event.type === 'ERROR') {
      const session = sessions.value.find(s => s.id === owningSessionId)
      if (session) {
        session.messages.push(event)
      }
      sessionStreams.delete(owningSessionId)
    }
    return
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
    streamingForSessionId = null
    sessionStreams.delete(owningSessionId)
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
  stopCurrentStream()
}

function handleWorkspaceChanged(workspace) {
  console.log('Workspace changed:', workspace)
}

onMounted(() => {
  const saved = localStorage.getItem('theme')
  if (saved === 'light') {
    isLight.value = true
    applyTheme()
  }
  if (sessions.value.length === 0) {
    createNewSession()
  }
  inputBoxRef.value?.focus()
})

onUnmounted(() => {
  persistCurrentSession()
  for (const stream of sessionStreams.values()) {
    stream.abort()
  }
  sessionStreams.clear()
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-primary);
}

.sidebar {
  width: 240px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.sidebar-header h2 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
}

.new-chat-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: var(--accent);
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
  background: var(--bg-tertiary);
}

.session-item.active {
  background: var(--bg-tertiary);
  border-left: 3px solid var(--accent);
}

.session-name {
  font-size: 13px;
  color: var(--text-secondary);
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
  color: var(--text-hint);
  cursor: pointer;
  font-size: 14px;
  display: none;
}

.session-item:hover .delete-btn {
  display: block;
}

.delete-btn:hover {
  background: var(--danger);
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
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.chat-header h1 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.theme-btn {
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid var(--border-subtle);
  background: transparent;
  font-size: 16px;
  cursor: pointer;
  line-height: 1;
}

.theme-btn:hover {
  background: var(--bg-tertiary);
}

.stop-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: none;
  background: var(--danger);
  color: white;
  font-size: 12px;
  cursor: pointer;
}

.clear-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--border-subtle);
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}
</style>
