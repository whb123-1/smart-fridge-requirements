const env = import.meta.env || {}
const API_BASE_URL = String(env.VITE_API_BASE_URL || '').replace(/\/$/, '')
let accessToken = ''
let refreshPromise = null

export class ApiError extends Error {
  constructor(message, code = 'API_ERROR', status = 0, fields = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fields = fields
  }
}

function rememberSession(data) {
  accessToken = data?.accessToken || ''
  return data
}

async function readResponse(response) {
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST', credentials: 'include', headers: { Accept: 'application/json' },
    }).then(async response => {
      const payload = await readResponse(response)
      if (!response.ok) {
        accessToken = ''
        throw new ApiError(payload?.message || '会话已失效', payload?.code || 'UNAUTHENTICATED', response.status, payload?.data?.fields)
      }
      return rememberSession(payload?.data)
    }).finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

async function request(path, options = {}) {
  const { auth = true, retry = true, headers = {}, ...fetchOptions } = options
  const requestHeaders = { Accept: 'application/json', ...headers }
  if (fetchOptions.body && !(fetchOptions.body instanceof FormData) && !requestHeaders['Content-Type']) requestHeaders['Content-Type'] = 'application/json'
  if (auth && accessToken) requestHeaders.Authorization = `Bearer ${accessToken}`
  const response = await fetch(`${API_BASE_URL}${path}`, { credentials: 'include', headers: requestHeaders, ...fetchOptions })
  if (response.status === 401 && auth && retry) {
    await refreshAccessToken()
    return request(path, { ...options, retry: false })
  }
  const payload = await readResponse(response)
  if (!response.ok) throw new ApiError(payload?.message || `请求失败 (${response.status})`, payload?.code || 'API_ERROR', response.status, payload?.data?.fields || {})
  return payload?.data
}

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `idempotency-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function queryString(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

const write = (path, method, body, idempotent = false) => request(path, {
  method,
  headers: idempotent ? { 'Idempotency-Key': idempotencyKey() } : {},
  ...(body === undefined ? {} : { body: JSON.stringify(body) }),
})

export const api = {
  login: credentials => request('/api/v1/auth/login', { method: 'POST', auth: false, body: JSON.stringify(credentials) }).then(rememberSession),
  register: profile => request('/api/v1/auth/register', { method: 'POST', auth: false, body: JSON.stringify(profile) }).then(rememberSession),
  refreshSession: refreshAccessToken,
  logout: async () => { try { return await request('/api/v1/auth/logout', { method: 'POST', auth: false }) } finally { accessToken = '' } },
  clearAccessToken: () => { accessToken = '' },

  getMe: () => request('/api/v1/me'),
  updateMe: profile => write('/api/v1/me', 'PATCH', profile),
  changePassword: password => write('/api/v1/me/password', 'PATCH', password),
  getOnboarding: () => request('/api/v1/onboarding'),
  initializeOnboarding: (payload, key) => request('/api/v1/onboarding/initialize', {
    method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(payload),
  }),
  getFridges: () => request('/api/v1/fridges'),

  updateZone: (id, payload) => write(`/api/v1/zones/${id}`, 'PATCH', payload, true),
  getEnvironment: fridgeId => request(`/api/v1/fridges/${fridgeId}/environment`),
  getZoneReadings: (zoneId, filters = {}) => request(`/api/v1/zones/${zoneId}/readings${queryString(filters)}`),
  getZoneDevices: zoneId => request(`/api/v1/zones/${zoneId}/devices`),
  getZoneSensors: zoneId => request(`/api/v1/zones/${zoneId}/sensors`),
  getNotifications: filters => request(`/api/v1/notifications${queryString(filters || {})}`),
  updateNotification: (id, payload) => write(`/api/v1/notifications/${id}`, 'PATCH', payload, true),

  listInventoryItems: filters => request(`/api/v1/inventory/items${queryString(filters || {})}`),
  listInventoryTransactions: filters => request(`/api/v1/inventory/transactions${queryString(filters || {})}`),
  deleteInventoryTransaction: id => write(`/api/v1/inventory/transactions/${id}`, 'DELETE', undefined, true),
  createInventoryItem: payload => write('/api/v1/inventory/items', 'POST', payload, true),
  updateInventoryItem: (id, payload) => write(`/api/v1/inventory/items/${id}`, 'PATCH', payload, true),
  deleteInventoryItem: id => write(`/api/v1/inventory/items/${id}`, 'DELETE', undefined, true),
  updateInventoryBatch: (id, payload) => write(`/api/v1/inventory/batches/${id}`, 'PATCH', payload, true),
  transactInventoryBatch: (id, payload) => write(`/api/v1/inventory/batches/${id}/transactions`, 'POST', payload, true),
  getExpiry: filters => request(`/api/v1/expiry${queryString(filters || {})}`),

  getShoppingLists: () => request('/api/v1/shopping-lists'),
  createShoppingList: payload => write('/api/v1/shopping-lists', 'POST', payload, true),
  createShoppingItem: (listId, payload) => write(`/api/v1/shopping-lists/${listId}/items`, 'POST', payload, true),
  updateShoppingItem: (id, payload) => write(`/api/v1/shopping-items/${id}`, 'PATCH', payload, true),
  deleteShoppingItem: id => write(`/api/v1/shopping-items/${id}`, 'DELETE', undefined, true),
  storeShoppingItem: (id, payload) => write(`/api/v1/shopping-items/${id}/store`, 'POST', payload, true),

  getPreferences: () => request('/api/v1/me/preferences'),
  updatePreferences: payload => write('/api/v1/me/preferences', 'PUT', payload, true),
  getNotificationPreferences: () => request('/api/v1/me/notification-preferences'),
  updateNotificationPreferences: preferences => write('/api/v1/me/notification-preferences', 'PUT', { preferences }, true),
  uploadVoiceDraft: (fridgeId, audio) => {
    const body = new FormData()
    body.append('audio', audio)
    return request(`/api/v1/inventory/voice-drafts?fridgeId=${encodeURIComponent(fridgeId)}`, { method: 'POST', body })
  },
  getVoiceDraft: id => request(`/api/v1/inventory/voice-drafts/${id}`),
  confirmVoiceDraft: (id, inventory) => write(`/api/v1/inventory/voice-drafts/${id}/confirm`, 'POST', { inventory }, true),

  listRecipes: filters => request(`/api/v1/recipes${queryString(filters || {})}`),
  getRecipe: id => request(`/api/v1/recipes/${id}`),
  generateRecipeBatch: payload => write('/api/v1/recipes/generate', 'POST', payload),
  matchRecipes: ingredients => write('/api/v1/recipe-synthesis/match', 'POST', { ingredients }),
  scaleRecipe: (id, payload) => write(`/api/v1/recipes/${id}/scale`, 'POST', payload),
  setRecipeBookmark: (id, bookmarked) => write(`/api/v1/recipes/${id}/bookmark`, bookmarked ? 'PUT' : 'DELETE', undefined, true),
  listRecipePlans: fridgeId => request(`/api/v1/fridges/${fridgeId}/recipe-plans`),
  createRecipePlan: (fridgeId, payload) => write(`/api/v1/fridges/${fridgeId}/recipe-plans`, 'POST', payload, true),
  updateRecipePlan: (id, payload) => write(`/api/v1/recipe-plans/${id}`, 'PATCH', payload, true),
  deleteRecipePlan: id => write(`/api/v1/recipe-plans/${id}`, 'DELETE', undefined, true),
  cookRecipe: (id, payload) => write(`/api/v1/recipes/${id}/cook`, 'POST', payload, true),

  getNameSuggestions: async ({ query = '', context = 'ingredient', limit = 6 }) => {
    if (context === 'ingredient') {
      const suggestions = await request(`/api/v1/catalog/suggestions${queryString({ query, limit })}`)
      return { suggestions: suggestions.map(item => ({ ...item, context })) }
    }
    const recipes = await request(`/api/v1/recipes${queryString({ query })}`)
    return { suggestions: recipes.slice(0, limit).map(item => ({ id: item.id, name: item.name, context: 'dish' })) }
  },

  estimateMealNutrition: payload => write('/api/v1/meals/estimate-nutrition', 'POST', payload),
  listMeals: () => request('/api/v1/meals'),
  createMeal: payload => write('/api/v1/meals', 'POST', payload, true),
  deleteMeal: id => write(`/api/v1/meals/${id}`, 'DELETE', undefined, true),
  getConsumptionAnalytics: period => request(`/api/v1/analytics/consumption${queryString({ period })}`),
  getDietAnalytics: date => request(`/api/v1/analytics/diet${queryString({ date })}`),

  getAssistantBriefing: () => request('/api/v1/assistant/briefing'),
  createAssistantConversation: title => write('/api/v1/assistant/conversations', 'POST', { title }, true),
  sendAssistantMessage: (conversationId, payload) => write(`/api/v1/assistant/conversations/${conversationId}/messages`, 'POST', payload, true),
  confirmAssistantProposal: id => write(`/api/v1/assistant/action-proposals/${id}/confirm`, 'POST', undefined, true),
  dismissAssistantProposal: id => write(`/api/v1/assistant/action-proposals/${id}/dismiss`, 'POST', undefined, true),

  listAdminUsers: filters => request(`/api/v1/admin/users${queryString(filters || {})}`),
  getAdminUser: id => request(`/api/v1/admin/users/${id}`),
  getAdminUserAudit: (id, filters = {}) => request(`/api/v1/admin/users/${id}/audit-logs${queryString(filters)}`),
  setAdminUserStatus: (id, status) => write(`/api/v1/admin/users/${id}/status`, 'PATCH', { status }, true),
  setAdminUserRole: (id, role) => write(`/api/v1/admin/users/${id}/role`, 'PATCH', { role }, true),
  revokeAdminUserSessions: id => write(`/api/v1/admin/users/${id}/sessions/revoke`, 'POST', undefined, true),
  resetAdminUserPassword: id => write(`/api/v1/admin/users/${id}/password-reset`, 'POST', undefined, true),
  deleteAdminUser: id => write(`/api/v1/admin/users/${id}`, 'DELETE', undefined, true),
  restoreAdminUser: id => write(`/api/v1/admin/users/${id}/restore`, 'POST', undefined, true),
}
