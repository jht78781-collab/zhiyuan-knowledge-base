import { ref } from 'vue'
import { api, resetCsrf } from './api.js'

export const user = ref(null)
let ready

export function loadSession() {
  ready ??= api('/auth/me', { quiet: true }).then(result => { user.value = result }).catch(() => {})
  return ready
}

export async function login(credentials) {
  user.value = await api('/auth/login', { method: 'POST', body: credentials, quiet: true })
  resetCsrf()
}

export async function logout() {
  await api('/auth/logout', { method: 'POST' })
  clearSession()
}

export function clearSession() {
  user.value = null
  resetCsrf()
}
