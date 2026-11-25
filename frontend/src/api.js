let csrf

export function resetCsrf() { csrf = undefined }

async function token() {
  csrf ??= fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' }).then(async response => {
    if (!response.ok) throw new Error('无法验证会话，请确认服务正常运行。')
    return response.json()
  }).catch(error => {
    csrf = undefined
    throw error
  })
  return csrf
}

export async function api(path, { method = 'GET', body, quiet = false } = {}, retry = true) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') {
    const { headerName, token: value } = await token()
    headers[headerName] = value
  }
  if (body !== undefined && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(body)
  }
  const response = await fetch('/api' + path, {
    method, headers, body, credentials: 'same-origin', cache: 'no-store'
  }).catch(() => { throw new Error('无法连接系统，请检查网络和服务状态。') })
  const data = response.headers.get('content-type')?.includes('application/json') ? await response.json() : null
  if (response.ok) return data
  if (response.status === 403 && data?.code === 'INVALID_CSRF' && retry) {
    resetCsrf()
    const original = headers['Content-Type'] ? JSON.parse(body) : body
    return api(path, { method, body: original, quiet }, false)
  }
  const error = new Error(data?.message || '请求失败，请重试。')
  error.status = response.status
  if (response.status === 401 && !quiet) window.dispatchEvent(new Event('session-expired'))
  throw error
}

export function documentUrl(kbId, docId) {
  return '/api/knowledge-bases/' + kbId + '/documents/' + docId + '/download'
}

export const dateTime = value => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'short'
}).format(new Date(value)) : '—'

export const docStatus = {
  UPLOADING: '保存中', QUEUED: '待索引', INDEXING: '索引中', AVAILABLE: '可用',
  FAILED: '处理失败', DELETING: '清理中', DELETE_FAILED: '清理失败'
}
