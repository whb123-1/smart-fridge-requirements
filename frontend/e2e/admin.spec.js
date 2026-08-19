import { expect, test } from '@playwright/test'

const now = '2026-08-18T12:00:00Z'
const admin = user('00000000-0000-7000-8000-000000000001', 'admin', '系统管理员', 'ADMIN')
const member = user('00000000-0000-7000-8000-000000000002', 'lin_zhixia', '林知夏', 'USER')

function user(id, username, displayName, role, overrides = {}) {
  return {
    id, username, displayName, role, email: `${username}@example.com`, status: 'ACTIVE',
    onboardingCompleted: role === 'USER', passwordChangeRequired: false, activeSessionCount: 1,
    lastLoginAt: now, createdAt: now, updatedAt: now, deletedAt: null,
    deletionRequestedAt: null, anonymizedAt: null, ...overrides,
  }
}

function envelope(data, code = 'OK') {
  return { code, message: 'ok', data, traceId: 'e2e-trace' }
}

async function installApi(page, { initialRole = null, loginRole = 'ADMIN', manyUsers = false } = {}) {
  let authenticatedRole = initialRole
  let users = manyUsers
    ? [admin, member, ...Array.from({ length: 20 }, (_, index) => user(
      `00000000-0000-7000-8000-${String(index + 3).padStart(12, '0')}`,
      `member_${String(index + 3).padStart(2, '0')}`,
      `成员 ${index + 3}`,
      'USER',
    ))]
    : [admin, member]
  const requests = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    requests.push({ path, method: request.method(), headers: request.headers() })
    const json = payload => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(payload)) })

    if (path === '/api/v1/auth/refresh') {
      if (!authenticatedRole) return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify(envelope(null, 'UNAUTHENTICATED')) })
      return json(session(authenticatedRole))
    }
    if (path === '/api/v1/auth/login') {
      authenticatedRole = loginRole
      return json(session(loginRole))
    }
    if (path === '/api/v1/auth/logout') return json(null)
    if (path === '/api/v1/admin/users' && request.method() === 'GET') {
      const query = (url.searchParams.get('query') || '').toLowerCase()
      const role = url.searchParams.get('role') || ''
      const status = url.searchParams.get('status') || ''
      const page = Number(url.searchParams.get('page') || 0)
      const size = Number(url.searchParams.get('size') || 20)
      const filtered = users.filter(value => (!query || [value.username, value.displayName, value.email]
        .some(field => field.toLowerCase().includes(query)))
        && (!role || value.role === role)
        && (!status || (status === 'DELETED' ? Boolean(value.deletedAt) : value.status === status)))
      return json({
        items: filtered.slice(page * size, (page + 1) * size),
        total: filtered.length,
        page,
        size,
        totalPages: Math.ceil(filtered.length / size),
      })
    }
    if (path.endsWith('/audit-logs')) return json({ items: [], total: 0, page: 0, size: 20, totalPages: 0 })
    if (/\/api\/v1\/admin\/users\/[^/]+\/status$/.test(path)) {
      const id = path.split('/')[5]
      const status = JSON.parse(request.postData() || '{}').status
      users = users.map(value => value.id === id ? { ...value, status } : value)
      return json({ userId: id, action: 'STATUS_CHANGED', role: 'USER', status, occurredAt: now })
    }
    if (/\/api\/v1\/admin\/users\/[^/]+\/password-reset$/.test(path)) {
      return json({ userId: member.id, temporaryPassword: 'Xz!23456789abcdefghijklm', expiresAt: '2026-08-19T12:00:00Z' })
    }
    if (/\/api\/v1\/admin\/users\/[^/]+\/restore$/.test(path) && request.method() === 'POST') {
      const id = path.split('/')[5]
      users = users.map(value => value.id === id ? {
        ...value, status: 'ACTIVE', deletedAt: null, deletionRequestedAt: null,
      } : value)
      return json({ userId: id, action: 'RESTORED', role: 'USER', status: 'ACTIVE', occurredAt: now })
    }
    if (/\/api\/v1\/admin\/users\/[^/]+$/.test(path) && request.method() === 'DELETE') {
      const id = path.split('/')[5]
      users = users.map(value => value.id === id ? {
        ...value, status: 'DISABLED', deletedAt: now, deletionRequestedAt: now,
      } : value)
      return json({ userId: id, action: 'SOFT_DELETED', role: 'USER', status: 'DISABLED', occurredAt: now })
    }
    if (path === '/api/v1/admin/recipe-import-jobs') return json({ items: [], total: 0, page: 0, size: 20, totalPages: 0 })
    if (path === '/api/v1/admin/search-index') return json({ activeCollection: 'recipes_v1', recipeCount: 3, indexedCount: 3, failedCount: 0, latestRebuild: null })
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(envelope(null, 'NOT_FOUND')) })
  })
  return { requests, users: () => users }
}

function session(role) {
  const value = role === 'ADMIN' ? admin : member
  return { accessToken: `token-${role.toLowerCase()}`, expiresInSeconds: 900, user: value, onboardingRequired: false }
}

test('normal users cannot open the administrator route', async ({ page }) => {
  await installApi(page, { loginRole: 'USER' })
  await page.goto('/login')
  await page.getByLabel('邮箱或用户名').fill('lin_zhixia')
  await page.getByLabel('密码').fill('correct-horse-battery-staple')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/app\/home$/)
  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/app\/home$/)
})

test('administrator can search, disable and reset a user', async ({ page }, testInfo) => {
  test.setTimeout(45_000)
  test.skip(testInfo.project.name.includes('mobile'), 'desktop interaction is covered separately from the mobile layout')
  const api = await installApi(page)
  await page.goto('/login')
  await page.getByLabel('邮箱或用户名').fill('admin')
  await page.getByLabel('密码').fill('correct-horse-battery-staple')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/users$/)
  await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()

  await page.getByLabel('搜索账号').fill('lin')
  await page.getByRole('button', { name: '筛选' }).click()
  await expect(page.locator('.user-table .user-row')).toHaveCount(1)

  let row = page.locator('.user-table .user-row').filter({ hasText: '@lin_zhixia' })
  await row.getByRole('button', { name: '停用', exact: true }).click()
  await page.getByRole('button', { name: '确认停用' }).click()
  await expect(page.getByText('账号已停用')).toBeVisible()
  expect(api.requests.some(value => value.path.endsWith(`/users/${member.id}/status`)
    && value.headers['idempotency-key'])).toBeTruthy()

  row = page.locator('.user-table .user-row').filter({ hasText: '@lin_zhixia' })
  await row.getByRole('button', { name: '启用', exact: true }).click()
  await page.getByRole('button', { name: '确认启用' }).click()
  await expect(page.getByText('账号已启用')).toBeVisible()

  row = page.locator('.user-table .user-row').filter({ hasText: '@lin_zhixia' })
  await row.getByRole('button', { name: '重置密码' }).click()
  await page.getByRole('button', { name: '生成密码' }).click()
  await expect(page.getByRole('dialog', { name: '临时密码' })).toContainText('Xz!23456789abcdefghijklm')
  await page.getByRole('button', { name: '我已安全保存' }).click()
  await expect(page.getByText('Xz!23456789abcdefghijklm')).toHaveCount(0)

  row = page.locator('.user-table .user-row').filter({ hasText: '@lin_zhixia' })
  await row.getByLabel('更多操作').click()
  await row.getByRole('button', { name: '删除账号' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText('账号已进入删除恢复期')).toBeVisible()
  row = page.locator('.user-table .user-row').filter({ hasText: '@lin_zhixia' })
  await expect(row.getByLabel('账号状态：待匿名化')).toBeVisible()
  const restoreButton = row.getByRole('button', { name: '恢复账号' })
  if (!await restoreButton.isVisible()) await row.getByLabel('更多操作').click()
  await restoreButton.click()
  await page.getByRole('button', { name: '确认恢复' }).click()
  await expect(page.getByText('账号已恢复')).toBeVisible()
})

test('administrator can page through user search results', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name.includes('mobile'), 'desktop pagination is covered separately from mobile layout')
  await installApi(page, { initialRole: 'ADMIN', manyUsers: true })
  await page.goto('/admin/users')
  await expect(page.locator('.pagination')).toContainText('1–20 / 22')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.locator('.pagination')).toContainText('21–22 / 22')
  await expect(page.locator('.user-table .user-row')).toHaveCount(2)
})

test('mobile administrator view uses cards without horizontal overflow', async ({ page }, testInfo) => {
  test.skip(!testInfo.project.name.includes('mobile'), 'mobile-only layout assertion')
  await installApi(page, { initialRole: 'ADMIN' })
  await page.goto('/admin/users')
  await expect(page.locator('.mobile-users')).toBeVisible()
  await expect(page.locator('.mobile-users article')).toHaveCount(2)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})
