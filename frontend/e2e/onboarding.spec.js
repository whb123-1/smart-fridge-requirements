import { expect, test } from '@playwright/test'

const account = {
  id: '00000000-0000-7000-8000-000000000010',
  username: 'mobile_user',
  displayName: '手机用户',
  email: 'mobile@example.com',
  role: 'USER',
  passwordChangeRequired: false,
}

const fridge = {
  id: '00000000-0000-7000-8000-000000000020',
  name: '我的冰箱',
  zones: [],
}

function envelope(data, code = 'OK', message = 'ok') {
  return { code, message, data, traceId: 'onboarding-e2e-trace' }
}

async function installApi(page, { initializeFailures = 0 } = {}) {
  let authenticated = false
  let initialized = false
  let remainingFailures = initializeFailures
  const requests = []

  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    requests.push({ path, method: request.method(), headers: request.headers() })
    const json = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(data)) })

    if (path === '/api/v1/auth/refresh') {
      if (!authenticated) {
        return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify(envelope(null, 'UNAUTHENTICATED', '会话不存在')) })
      }
      return json(session())
    }
    if (path === '/api/v1/auth/register' || path === '/api/v1/auth/login') {
      authenticated = true
      return json(session())
    }
    if (path === '/api/v1/auth/logout') {
      authenticated = false
      return json(null)
    }
    if (path === '/api/v1/onboarding' && request.method() === 'GET') {
      return json({ completed: initialized, zoneDefaults: [], fridge: initialized ? fridge : null })
    }
    if (path === '/api/v1/onboarding/initialize') {
      if (remainingFailures > 0) {
        remainingFailures -= 1
        return route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify(envelope(null, 'TEMPORARY_ERROR', '初始化暂时失败')),
        })
      }
      initialized = true
      return json(fridge)
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(envelope(null, 'NOT_FOUND', 'not found')) })
  })

  return { requests }
}

function session() {
  return { accessToken: 'mobile-token', accessTokenExpiresAt: '2026-08-20T12:00:00Z', user: account, onboardingRequired: true }
}

async function register(page) {
  await page.goto('/login')
  await page.getByRole('tab', { name: '注册' }).click()
  await page.getByLabel('称呼').fill(account.displayName)
  await page.getByLabel('用户名').fill(account.username)
  await page.getByLabel('邮箱').fill(account.email)
  await page.getByLabel('密码').fill('correct-horse-battery-staple')
  await page.getByRole('button', { name: '创建账户并配置冰箱' }).click()
  await expect(page).toHaveURL(/\/onboarding$/)
  await expect(page.getByRole('heading', { name: '配置分区' })).toBeVisible()
}

async function reachConfirmation(page) {
  await page.getByRole('button', { name: '继续' }).click()
  await page.getByRole('button', { name: '继续' }).click()
  await expect(page.getByRole('heading', { name: '确认冰箱' })).toBeVisible()
}

test.beforeEach(async ({ page }, testInfo) => {
  test.skip(!testInfo.project.name.includes('mobile'), 'mobile onboarding regression coverage')
  await page.addInitScript(() => {
    if (globalThis.Crypto?.prototype) {
      Object.defineProperty(globalThis.Crypto.prototype, 'randomUUID', { configurable: true, value: undefined })
    }
  })
})

test('mobile onboarding retries without randomUUID or session storage', async ({ page }) => {
  await page.addInitScript(() => {
    const getItem = Storage.prototype.getItem
    const setItem = Storage.prototype.setItem
    const removeItem = Storage.prototype.removeItem
    Storage.prototype.getItem = function (key) {
      if (key === 'xianzhi.onboarding.idempotency-key') throw new DOMException('storage denied', 'SecurityError')
      return getItem.call(this, key)
    }
    Storage.prototype.setItem = function (key, value) {
      if (key === 'xianzhi.onboarding.idempotency-key') throw new DOMException('storage denied', 'SecurityError')
      return setItem.call(this, key, value)
    }
    Storage.prototype.removeItem = function (key) {
      if (key === 'xianzhi.onboarding.idempotency-key') throw new DOMException('storage denied', 'SecurityError')
      return removeItem.call(this, key)
    }
  })
  const api = await installApi(page, { initializeFailures: 1 })
  await register(page)
  await reachConfirmation(page)

  const submit = page.getByRole('button', { name: '确认并进入首页' })
  await submit.click()
  await expect(page.getByRole('alert')).toContainText('初始化暂时失败')
  await expect(submit).toBeEnabled()
  await submit.click()
  await expect(page).toHaveURL(/\/app\/home$/)

  const attempts = api.requests.filter(value => value.path === '/api/v1/onboarding/initialize')
  expect(attempts).toHaveLength(2)
  expect(attempts[0].headers['idempotency-key']).toMatch(/^idempotency-/)
  expect(attempts[1].headers['idempotency-key']).toBe(attempts[0].headers['idempotency-key'])
})

test('mobile users can return to login explicitly and with browser back', async ({ page }) => {
  const api = await installApi(page)
  await register(page)

  await page.getByRole('button', { name: '返回登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('tab', { name: '登录' })).toHaveAttribute('aria-selected', 'true')

  await page.getByLabel('邮箱或用户名').fill(account.username)
  await page.getByLabel('密码').fill('correct-horse-battery-staple')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/onboarding$/)

  await page.goBack()
  await expect(page).toHaveURL(/\/login$/)
  expect(api.requests.filter(value => value.path === '/api/v1/auth/logout')).toHaveLength(2)
  expect(api.requests.some(value => value.method === 'DELETE')).toBeFalsy()
})
