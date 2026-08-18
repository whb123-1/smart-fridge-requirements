const env = import.meta.env || {}
const API_BASE_URL = String(env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const LEGACY_REMOTE_ENABLED = env.VITE_LEGACY_API_ENABLED === 'true'
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

const wait = (data, delay = 280) => new Promise(resolve => setTimeout(() => resolve(data), delay))

const recipeCatalog = [
  {
    name: '鸡胸肉豆腐煲',
    desc: '优先使用鸡胸肉、豆腐和上海青，口味清淡且蛋白质充足。',
    time: 25,
    kcal: 386,
    protein: 42,
    color: '#dcefe5',
    art: '🍲',
    tags: ['高蛋白', '低油'],
    base: 300,
    ingredients: [{ name: '鸡胸肉', amount: 300, unit: '克' }, { name: '北豆腐', amount: 1, unit: '盒' }, { name: '上海青', amount: 200, unit: '克' }],
    seasonings: [{ name: '低钠生抽', amount: 10, unit: '毫升' }, { name: '姜片', amount: 3, unit: '片' }],
    steps: ['鸡胸肉切块，用少量生抽和姜片腌制 5 分钟。', '豆腐切块，与鸡胸肉一起小火煎至表面微黄。', '加入清水炖煮 12 分钟，放入上海青煮熟即可。'],
  },
  {
    name: '蒜蓉上海青',
    desc: '8 分钟完成，适合优先处理新鲜度较低的蔬菜。',
    time: 8,
    kcal: 96,
    protein: 4,
    color: '#e8efd1',
    art: '🥬',
    tags: ['低热量', '快手菜'],
    base: 300,
    ingredients: [{ name: '上海青', amount: 300, unit: '克' }],
    seasonings: [{ name: '蒜', amount: 8, unit: '克' }, { name: '盐', amount: 2, unit: '克' }],
    steps: ['上海青洗净，蒜切末。', '热锅少油，下蒜末炒香。', '放入上海青大火快炒，加盐后出锅。'],
  },
  {
    name: '香煎三文鱼温沙拉',
    desc: '三文鱼搭配蔬菜，适合作为一顿轻盈又有饱腹感的正餐。',
    time: 22,
    kcal: 438,
    protein: 32,
    color: '#f4e2d8',
    art: '🥗',
    tags: ['优质脂肪', '均衡一餐'],
    base: 250,
    ingredients: [{ name: '三文鱼', amount: 250, unit: '克' }, { name: '小番茄', amount: 120, unit: '克' }],
    seasonings: [{ name: '黑胡椒', amount: 2, unit: '克' }, { name: '柠檬汁', amount: 10, unit: '毫升' }],
    steps: ['三文鱼擦干水分，撒黑胡椒。', '平底锅小火煎至两面熟透。', '配上蔬菜和柠檬汁拌匀。'],
  },
  {
    name: '酸奶燕麦杯',
    desc: '适合作为早餐或下午加餐，准备时间很短。',
    time: 5,
    kcal: 268,
    protein: 13,
    color: '#e2edf5',
    art: '🥣',
    tags: ['适合早餐', '免烹饪'],
    base: 1,
    ingredients: [{ name: '无糖酸奶', amount: 1, unit: '杯' }, { name: '燕麦片', amount: 40, unit: '克' }],
    seasonings: [],
    steps: ['将酸奶倒入杯中。', '加入燕麦片，静置或冷藏后食用。'],
  },
  {
    name: '番茄虾仁意面',
    desc: '酸甜番茄配虾仁，做法清爽，适合快速晚餐。',
    time: 20,
    kcal: 468,
    protein: 29,
    color: '#f3ded9',
    art: '🍝',
    tags: ['20 分钟', '海鲜'],
    base: 280,
    ingredients: [{ name: '虾仁', amount: 160, unit: '克' }, { name: '番茄', amount: 180, unit: '克' }, { name: '意面', amount: 120, unit: '克' }],
    seasonings: [{ name: '橄榄油', amount: 8, unit: '毫升' }],
    steps: ['意面煮至八成熟。', '虾仁煎熟后加入番茄炒出汁。', '放入意面翻拌均匀即可。'],
  },
]

const ingredientCatalog = ['上海青', '鲜牛奶', '鸡胸肉', '北豆腐', '鸡蛋', '三文鱼', '无糖酸奶', '低钠生抽', '燕麦片', '黑胡椒', '小番茄', '柠檬', '虾仁', '番茄', '意面']

function normalizeRecipe(template, inventory, prompt, index) {
  const available = new Set(inventory.filter(item => Number(item.amount) > 0).map(item => item.name))
  const missing = template.ingredients.filter(item => !available.has(item.name)).map(item => item.name)
  const match = Math.max(0, Math.round((template.ingredients.length - missing.length) / template.ingredients.length * 100))
  const cleanPrompt = String(prompt || '').trim()
  const directMatch = cleanPrompt && (cleanPrompt.includes(template.name) || template.ingredients.some(item => cleanPrompt.includes(item.name)))
  const name = directMatch ? template.name : template.name

  return {
    ...template,
    id: `recipe-${Date.now()}-${index}`,
    name,
    desc: cleanPrompt && directMatch ? `${template.desc} AI 已结合“${cleanPrompt}”和当前库存调整建议。` : template.desc,
    match,
    level: missing.length === 0 ? '可直接制作' : missing.length === 1 ? '缺 1 样食材' : `缺 ${missing.length} 样食材`,
    favorite: false,
    collected: false,
    missing,
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
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json' },
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

function mockBatch({ inventory = [], prompt = '', count = 3 }) {
  const normalizedPrompt = String(prompt || '').toLowerCase()
  const preferred = recipeCatalog.filter(recipe => normalizedPrompt && (recipe.name.toLowerCase().includes(normalizedPrompt) || recipe.ingredients.some(item => item.name.toLowerCase().includes(normalizedPrompt))))
  const candidates = [...preferred, ...recipeCatalog.filter(recipe => !preferred.includes(recipe))]
  return candidates.slice(0, Math.max(1, count)).map((recipe, index) => normalizeRecipe(recipe, inventory, prompt, index))
}

function mockSuggestions({ query = '', context = 'ingredient', limit = 6 }) {
  const normalized = String(query).trim().toLowerCase()
  if (!normalized) return []
  const source = context === 'dish' ? recipeCatalog.map(recipe => recipe.name) : ingredientCatalog
  const startsWith = source.filter(name => name.toLowerCase().startsWith(normalized))
  const includes = source.filter(name => !startsWith.includes(name) && name.toLowerCase().includes(normalized))
  return [...startsWith, ...includes].slice(0, limit).map((name, index) => ({ id: `${context}-${index}-${name}`, name, context }))
}

function mockNutrition({ dishName = '', amount, unit = '份' }) {
  const matched = recipeCatalog.find(recipe => recipe.name.includes(dishName) || dishName.includes(recipe.name))
  const baseCalories = matched?.kcal || Math.max(80, Math.min(760, Math.round(String(dishName).length * 58 + 110)))
  const numericAmount = Number(amount)
  const scale = Number.isFinite(numericAmount) && numericAmount > 0
    ? ['克', '毫升'].includes(unit) ? numericAmount / 250 : numericAmount
    : 1
  const calories = Math.max(30, Math.round(baseCalories * scale))
  return { calories, protein: Math.round(calories * 0.075), estimated: true, source: matched ? '菜谱营养模型' : 'AI 食材营养模型' }
}

export const api = {
  login: credentials => request('/api/v1/auth/login', { method: 'POST', auth: false, body: JSON.stringify(credentials) }).then(rememberSession),
  register: profile => request('/api/v1/auth/register', { method: 'POST', auth: false, body: JSON.stringify(profile) }).then(rememberSession),
  refreshSession: refreshAccessToken,
  logout: async () => { try { return await request('/api/v1/auth/logout', { method: 'POST', auth: false }) } finally { accessToken = '' } },
  clearAccessToken: () => { accessToken = '' },
  getMe: () => request('/api/v1/me'),
  updateMe: profile => request('/api/v1/me', { method: 'PATCH', body: JSON.stringify(profile) }),
  changePassword: password => request('/api/v1/me/password', { method: 'PATCH', body: JSON.stringify(password) }),
  getOnboarding: () => request('/api/v1/onboarding'),
  initializeOnboarding: (payload, idempotencyKey) => request('/api/v1/onboarding/initialize', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload),
  }),
  getFridges: () => request('/api/v1/fridges'),
  getDashboard: () => wait({ updatedAt: new Date().toISOString() }),
  listInventoryItems: filters => request(`/api/v1/inventory/items${queryString(filters || {})}`),
  createInventoryItem: payload => request('/api/v1/inventory/items', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  updateInventoryItem: (id, payload) => request(`/api/v1/inventory/items/${id}`, {
    method: 'PATCH', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  deleteInventoryItem: id => request(`/api/v1/inventory/items/${id}`, {
    method: 'DELETE', headers: { 'Idempotency-Key': idempotencyKey() },
  }),
  updateInventoryBatch: (id, payload) => request(`/api/v1/inventory/batches/${id}`, {
    method: 'PATCH', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  transactInventoryBatch: (id, payload) => request(`/api/v1/inventory/batches/${id}/transactions`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  getExpiry: filters => request(`/api/v1/expiry${queryString(filters || {})}`),
  getShoppingLists: () => request('/api/v1/shopping-lists'),
  createShoppingList: payload => request('/api/v1/shopping-lists', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  createShoppingItem: (listId, payload) => request(`/api/v1/shopping-lists/${listId}/items`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  updateShoppingItem: (id, payload) => request(`/api/v1/shopping-items/${id}`, {
    method: 'PATCH', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  deleteShoppingItem: id => request(`/api/v1/shopping-items/${id}`, {
    method: 'DELETE', headers: { 'Idempotency-Key': idempotencyKey() },
  }),
  storeShoppingItem: (id, payload) => request(`/api/v1/shopping-items/${id}/store`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey() }, body: JSON.stringify(payload),
  }),
  updateZone: zone => wait(zone),
  updatePreferences: preferences => wait(preferences),
  toggleFavorite: recipeId => wait({ recipeId }),

  generateRecipeBatch: async requestBody => {
    if (API_BASE_URL && LEGACY_REMOTE_ENABLED) return request('/api/recipes/generate', { method: 'POST', body: JSON.stringify(requestBody) })
    return wait({ recipes: mockBatch(requestBody) }, 520)
  },

  getNameSuggestions: async requestBody => {
    if (requestBody.context === 'ingredient' && accessToken) {
      const suggestions = await request(`/api/v1/catalog/suggestions${queryString({ query: requestBody.query || '', limit: requestBody.limit || 6 })}`)
      return { suggestions: suggestions.map(item => ({ ...item, context: 'ingredient' })) }
    }
    if (API_BASE_URL && LEGACY_REMOTE_ENABLED) {
      const params = new URLSearchParams({ query: requestBody.query || '', context: requestBody.context || 'ingredient', limit: String(requestBody.limit || 6) })
      return request(`/api/name-suggestions?${params}`)
    }
    return wait({ suggestions: mockSuggestions(requestBody) }, 180)
  },

  estimateMealNutrition: async requestBody => {
    if (API_BASE_URL && LEGACY_REMOTE_ENABLED) return request('/api/meals/estimate-nutrition', { method: 'POST', body: JSON.stringify(requestBody) })
    return wait(mockNutrition(requestBody), 360)
  },
}
