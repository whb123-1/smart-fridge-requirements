import test from 'node:test'
import assert from 'node:assert/strict'
import { createIdempotencyKey, createSessionIdempotencyKeyStore } from './idempotency.js'

test('idempotency keys prefer the browser UUID implementation', () => {
  const expected = '00000000-0000-4000-8000-000000000001'
  assert.equal(createIdempotencyKey({ randomUUID: () => expected }), expected)
})

test('idempotency keys fall back when randomUUID is missing or unavailable', () => {
  for (const cryptoProvider of [{}, { randomUUID: () => { throw new Error('insecure context') } }]) {
    const key = createIdempotencyKey(cryptoProvider)
    assert.match(key, /^idempotency-[a-z0-9]+-[a-z0-9]+$/)
    assert.ok(key.length <= 128)
  }
})

test('session idempotency keys survive unavailable browser storage in memory', () => {
  const storage = {
    getItem() { throw new Error('storage denied') },
    setItem() { throw new Error('storage denied') },
    removeItem() { throw new Error('storage denied') },
  }
  let sequence = 0
  const store = createSessionIdempotencyKeyStore('onboarding-key', {
    storage,
    keyFactory: () => `fallback-${++sequence}`,
  })

  assert.equal(store.get(), 'fallback-1')
  assert.equal(store.get(), 'fallback-1')
  store.clear()
  assert.equal(store.get(), 'fallback-2')
})
