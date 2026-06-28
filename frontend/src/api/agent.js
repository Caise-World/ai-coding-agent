const API_BASE = '/api/agent'

export function createAgentStreamPost(message, path = null, history = []) {
  let aborted = false
  const controller = new AbortController()

  async function start(onMessage, onError) {
    try {
      const body = { message, path }
      if (history.length > 0) {
        body.history = history
      }

      const res = await fetch(`${API_BASE}/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(body),
        signal: controller.signal
      })

      if (!res.ok) throw new Error(`HTTP ${res.status}`)

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (!aborted) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const json = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
              const data = JSON.parse(json)
              onMessage(data)
            } catch (e) {
              // skip malformed JSON
            }
          }
        }
      }

      // Process remaining buffer after stream ends
      if (buffer.startsWith('data:')) {
        try {
          const json = buffer.startsWith('data: ') ? buffer.slice(6) : buffer.slice(5)
          const data = JSON.parse(json)
          onMessage(data)
        } catch (e) {
          // skip
        }
      }
    } catch (err) {
      if (!aborted) {
        onError(err)
      }
    }
  }

  return {
    start,
    abort() {
      aborted = true
      controller.abort()
    }
  }
}
