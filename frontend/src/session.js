import { reactive } from 'vue'
import { api } from './services/api.js'

export const session = reactive({
  ready: false,
  authenticated: false,
  user: null,
  onboardingRequired: true,
  fridge: null,
})

let restorePromise = null

function applySession(data) {
  session.authenticated = true
  session.user = data.user
  session.onboardingRequired = Boolean(data.onboardingRequired)
}

export async function restoreSession() {
  if (session.ready) return session.authenticated
  if (!restorePromise) {
    restorePromise = api.refreshSession()
      .then(data => { applySession(data); return true })
      .catch(() => { clearSession(); return false })
      .finally(() => { session.ready = true; restorePromise = null })
  }
  return restorePromise
}

export async function login(credentials) {
  const data = await api.login(credentials)
  applySession(data)
  session.ready = true
  return data
}

export async function register(profile) {
  const data = await api.register(profile)
  applySession(data)
  session.ready = true
  return data
}

export async function logout() {
  try { await api.logout() } finally { clearSession(); session.ready = true }
}

export function completeOnboarding(fridge) {
  session.onboardingRequired = false
  session.fridge = fridge
}

export function setFridge(fridge) { session.fridge = fridge }

export function clearSession() {
  api.clearAccessToken()
  session.authenticated = false
  session.user = null
  session.onboardingRequired = true
  session.fridge = null
}
