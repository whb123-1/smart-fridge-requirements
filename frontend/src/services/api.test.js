import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from './api.js'

function response(data, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify({ code: status >= 200 && status < 300 ? 'OK' : 'ERROR', data }),
  }
}

test('inventory writes carry auth and a fresh idempotency key', async t => {
  const calls = []
  const previousFetch = globalThis.fetch
  t.after(() => {
    globalThis.fetch = previousFetch
    api.clearAccessToken()
  })
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith('/api/v1/auth/login')) return response({ accessToken: 'test-token' })
    return response({ id: 'inventory-id', batches: [] })
  }

  await api.login({ identifier: 'test-user', password: 'test-password' })
  await api.createInventoryItem({ fridgeId: 'fridge-id', name: '鸡蛋', defaultUnit: 'piece', batches: [] })
  await api.updateInventoryBatch('batch-id', { zoneId: 'zone-id' })

  const writes = calls.slice(1)
  assert.equal(writes.length, 2)
  assert.equal(writes[0].options.headers.Authorization, 'Bearer test-token')
  assert.match(writes[0].options.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
  assert.match(writes[1].options.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
  assert.notEqual(writes[0].options.headers['Idempotency-Key'], writes[1].options.headers['Idempotency-Key'])
})

test('shopping store uses the atomic store endpoint and idempotency header', async t => {
  const calls = []
  const previousFetch = globalThis.fetch
  t.after(() => {
    globalThis.fetch = previousFetch
    api.clearAccessToken()
  })
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return response({ id: 'shopping-item-id', status: 'STORED' })
  }

  await api.storeShoppingItem('shopping-item-id', { quantity: 2, unit: 'box' })
  assert.equal(calls[0].url, '/api/v1/shopping-items/shopping-item-id/store')
  assert.equal(calls[0].options.method, 'POST')
  assert.match(calls[0].options.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
})

test('environment reads are authenticated and notification writes are idempotent', async t => {
  const calls = []
  const previousFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = previousFetch; api.clearAccessToken() })
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return response({ zones: [] }) }

  await api.getEnvironment('fridge-id')
  await api.getZoneSensors('zone-id')
  await api.updateNotification('notification-id', { read: true })

  assert.equal(calls[0].url, '/api/v1/fridges/fridge-id/environment')
  assert.equal(calls[1].url, '/api/v1/zones/zone-id/sensors')
  assert.equal(calls[2].url, '/api/v1/notifications/notification-id')
  assert.match(calls[2].options.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
})

test('the browser does not expose a manual sensor provisioning request', () => {
  assert.equal(typeof api.initializeSensor, 'undefined')
  assert.equal(typeof api.unbindDeviceSensor, 'undefined')
})

test('recipe, meal, preference and assistant flows use v1 APIs with idempotent writes', async t => {
  const calls = []
  const previousFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = previousFetch; api.clearAccessToken() })
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith('/api/v1/auth/login')) return response({ accessToken: 'phase-four-token' })
    return response({ id: 'result-id', recipes: [], message: { id: 'message-id', content: 'ok' } })
  }

  await api.login({ identifier: 'demo', password: 'password' })
  await api.generateRecipeBatch({ count: 3, inventory: [] })
  await api.setRecipeBookmark('recipe-id', true)
  await api.cookRecipe('recipe-id', { servings: 1, consumptions: [], recordMeal: true })
  await api.createMeal({ name: '番茄炒蛋', mealAt: new Date().toISOString(), servings: 1 })
  await api.updatePreferences({ tastes: [], cuisines: [], allergies: [], dislikes: [] })
  await api.createAssistantConversation('冰箱助手')
  await api.sendAssistantMessage('conversation-id', { content: '今天吃什么', page: 'home' })

  const writes = calls.slice(1)
  assert.equal(writes[0].url, '/api/v1/recipes/generate')
  assert.equal(writes[0].options.headers.Authorization, 'Bearer phase-four-token')
  for (const call of writes.slice(1)) {
    assert.match(call.options.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
    assert.equal(call.options.headers.Authorization, 'Bearer phase-four-token')
  }
})
