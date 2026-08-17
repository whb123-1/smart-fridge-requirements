import test from 'node:test'
import assert from 'node:assert/strict'
import { authenticatedLanding, resolveRouteAccess } from './navigation.js'

test('login and registration land in onboarding until initialization is complete', () => {
  assert.equal(authenticatedLanding(true), '/onboarding')
  assert.equal(authenticatedLanding(false), '/app/home')
})

test('route guard sends guests to login and confines new users to onboarding', () => {
  assert.deepEqual(resolveRouteAccess({ name: 'app', meta: { requiresAuth: true } }, { authenticated: false }), { name: 'login' })
  assert.equal(resolveRouteAccess({ name: 'login', meta: { public: true } }, { authenticated: false }), true)
  assert.deepEqual(
    resolveRouteAccess({ name: 'app', meta: { requiresAuth: true } }, { authenticated: true, onboardingRequired: true }),
    { name: 'onboarding' },
  )
  assert.deepEqual(
    resolveRouteAccess({ name: 'onboarding', meta: { requiresAuth: true } }, { authenticated: true, onboardingRequired: false }),
    { name: 'app', params: { page: 'home' } },
  )
})
