import test from 'node:test'
import assert from 'node:assert/strict'
import { clearSession, login, register, restoreSession, session } from './session.js'

const originalFetch = globalThis.fetch

function resetSession() {
  clearSession()
  session.ready = false
}

test.afterEach(() => {
  globalThis.fetch = originalFetch
  resetSession()
})

test('startup restores an authenticated session from the refresh cookie', async () => {
  resetSession()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/auth/refresh')
    assert.equal(options.credentials, 'include')
    return new Response(JSON.stringify({
      code: 'OK',
      message: '',
      data: {
        accessToken: 'access-token',
        accessTokenExpiresAt: '2026-08-17T08:00:00Z',
        user: { id: 'user-id', username: 'user_name', email: 'user@example.com', displayName: 'User' },
        onboardingRequired: false,
      },
      traceId: 'trace-id',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }

  assert.equal(await restoreSession(), true)
  assert.equal(session.ready, true)
  assert.equal(session.authenticated, true)
  assert.equal(session.onboardingRequired, false)
  assert.equal(session.user.email, 'user@example.com')
  assert.equal(session.user.username, 'user_name')
})

test('login sends an email-or-username identifier', async () => {
  resetSession()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/auth/login')
    assert.deepEqual(JSON.parse(options.body), { identifier: 'user_name', password: 'test-password' })
    return sessionResponse('user_name')
  }

  await login({ identifier: 'user_name', password: 'test-password' })
  assert.equal(session.user.username, 'user_name')
})

test('registration sends username independently from display name', async () => {
  resetSession()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/auth/register')
    assert.deepEqual(JSON.parse(options.body), {
      username: 'lin_zhixia',
      email: 'lin@example.com',
      password: 'test-password',
      displayName: '林知夏',
    })
    return sessionResponse('lin_zhixia')
  }

  await register({ username: 'lin_zhixia', email: 'lin@example.com', password: 'test-password', displayName: '林知夏' })
  assert.equal(session.user.username, 'lin_zhixia')
})

function sessionResponse(username) {
  return new Response(JSON.stringify({
    code: 'OK',
    message: '',
    data: {
      accessToken: 'access-token',
      accessTokenExpiresAt: '2026-08-17T08:00:00Z',
      user: { id: 'user-id', username, email: 'user@example.com', displayName: 'User' },
      onboardingRequired: false,
    },
    traceId: 'trace-id',
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

test('failed startup recovery leaves the application in the guest state', async () => {
  resetSession()
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: 'UNAUTHENTICATED', message: 'Refresh session is missing', data: {}, traceId: 'trace-id',
  }), { status: 401, headers: { 'Content-Type': 'application/json' } })

  assert.equal(await restoreSession(), false)
  assert.equal(session.ready, true)
  assert.equal(session.authenticated, false)
  assert.equal(session.user, null)
})
