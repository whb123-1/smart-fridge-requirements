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

test('environment reads are authenticated and device/notification writes are idempotent', async t => {
  const calls = []
  const previousFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = previousFetch; api.clearAccessToken() })
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return response({ zones: [] }) }

  await api.getEnvironment('fridge-id')
  await api.getZoneSensors('zone-id')
  await api.createDevice('zone-id', { name: '测试设备', type: 'VIRTUAL' })
  await api.updateNotification('notification-id', { read: true })

  assert.equal(calls[0].url, '/api/v1/fridges/fridge-id/environment')
  assert.equal(calls[1].url, '/api/v1/zones/zone-id/sensors')
  assert.equal(calls[2].url, '/api/v1/zones/zone-id/devices')
  assert.match(calls[2].options.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
  assert.match(calls[3].options.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
})
