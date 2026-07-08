const BASE = '/api/sentiment'

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const body = await res.json()
      if (body.details?.length) message = body.details.join('; ')
      else if (body.error) message = body.error
    } catch { /* keep default message */ }
    throw new Error(message)
  }
  if (res.status === 204 || res.headers.get('content-length') === '0') return null
  return res.json()
}

export const api = {
  analyze: (text, source) =>
    request('/analyze', { method: 'POST', body: JSON.stringify({ text, source }) }),
  batch: (texts, source) =>
    request('/batch', { method: 'POST', body: JSON.stringify({ texts, source }) }),
  history: (page = 0, size = 20) => request(`/history?page=${page}&size=${size}`),
  clearHistory: () => request('/history', { method: 'DELETE' }),
  stats: () => request('/stats'),
  modelInfo: () => request('/model-info'),
}
