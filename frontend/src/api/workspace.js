const API_BASE = '/api/workspace'

export async function openWorkspace(path) {
  const res = await fetch(`${API_BASE}/open`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path })
  })
  const data = await res.json()
  if (data.error) {
    throw new Error(data.error)
  }
  return {
    path: data.path,
    chunksCreated: data.chunksCreated,
    indexTimeMs: data.indexTimeMs,
    cacheHit: data.cacheHit
  }
}

export async function getCurrentWorkspace() {
  const res = await fetch(`${API_BASE}/current`)
  const data = await res.json()
  if (data.error) {
    return null
  }
  return {
    path: data.path,
    chunksCreated: data.chunksCreated,
    cacheHit: data.cacheHit
  }
}
