import { expect, test } from '@playwright/test'

const fridgeId = '00000000-0000-7000-8000-000000000201'
const zoneId = '00000000-0000-7000-8000-000000000202'
const itemId = '00000000-0000-7000-8000-000000000203'
const batchId = '00000000-0000-7000-8000-000000000204'
const recipeId = '00000000-0000-7000-8000-000000000205'
const now = '2026-08-21T06:00:00Z'

function envelope(data) {
  return { code: 'OK', message: 'ok', data, traceId: 'business-layout-e2e' }
}

const session = {
  accessToken: 'business-layout-token', expiresInSeconds: 900, onboardingRequired: false,
  user: {
    id: '00000000-0000-7000-8000-000000000200', username: 'layout_user', displayName: '布局用户',
    email: 'layout@example.com', role: 'USER', passwordChangeRequired: false,
    temperatureUnit: 'C', timezone: 'Asia/Shanghai',
  },
}

const fridge = {
  id: fridgeId, name: '布局测试冰箱',
  zones: [{
    id: zoneId, name: '冷藏室', kind: 'REFRIGERATED', enabled: true,
    targetTemperatureC: 4, targetHumidityPct: 55, temperatureSensorCount: 0, humiditySensorCount: 0,
  }],
}

const recipe = {
  id: recipeId, name: '番茄炒蛋', description: '使用现有库存快速完成的家常菜。', cuisine: '家常菜', taste: '咸鲜', goal: '均衡',
  cookMinutes: 18, servings: 2,
  total: { calories: 640, protein: 32, fat: 36, carbs: 24 },
  perServing: { calories: 320, protein: 16, fat: 18, carbs: 12 },
  ingredients: [
    { id: itemId, name: '番茄', role: 'PRIMARY', quantity: 300, unit: 'g', scalingRule: 'LINEAR' },
    { id: '00000000-0000-7000-8000-000000000206', name: '鸡蛋', role: 'PRIMARY', quantity: 2, unit: 'piece', scalingRule: 'LINEAR' },
    { id: '00000000-0000-7000-8000-000000000207', name: '盐', role: 'SEASONING', quantity: 3, unit: 'g', scalingRule: 'FIXED' },
  ],
  steps: ['番茄切块，鸡蛋打散。', '先炒鸡蛋，再加入番茄翻炒。'],
  detailedSteps: [
    { number: 1, title: '处理食材', instruction: '番茄切块，鸡蛋充分打散。', heat: '无需加热', duration: '3 分钟', checkpoint: '蛋液均匀且番茄大小一致' },
    { number: 2, title: '完成翻炒', instruction: '先炒鸡蛋，再加入番茄快速翻炒。', heat: '中火', duration: '5 分钟', checkpoint: '番茄出汁且鸡蛋熟透' },
  ],
  utensils: ['炒锅', '锅铲'], nutritionSource: 'LOCAL_RECIPE', bookmarked: false,
  source: 'LOCAL', attribution: '测试菜谱', imageUrl: null, imageSourceUrl: null, imageAttribution: null,
  availability: 'DIRECT', missing: [], validationWarnings: [], webSources: [],
}

async function installApi(page) {
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const json = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(data)) })

    if (path === '/api/v1/auth/refresh') return json(session)
    if (path === '/api/v1/fridges') return json([fridge])
    if (path === '/api/v1/inventory/items') return json([{
      id: itemId, fridgeId, catalogId: itemId, name: '番茄', canonicalName: '番茄', originalNames: ['西红柿'],
      category: 'VEGETABLE', defaultUnit: 'g', lowStock: false,
      batches: [{ id: batchId, inputName: '番茄', zoneId, storedAt: now, remainingQuantity: 600, unit: 'g', status: 'ACTIVE', assessment: null }],
    }])
    if (path === '/api/v1/expiry' || path === '/api/v1/inventory/transactions') return json([])
    if (path === '/api/v1/recipes') return json([recipe])
    if (path === `/api/v1/fridges/${fridgeId}/recipe-plans`) return json([])
    if (path === '/api/v1/shopping-lists') return json([{
      id: '00000000-0000-7000-8000-000000000208', fridgeId, name: '默认采购清单',
      items: [{
        id: '00000000-0000-7000-8000-000000000209', name: '牛奶', category: 'OTHER', quantity: 1,
        unit: 'box', note: '早餐库存', status: 'PENDING', sourceType: 'MANUAL',
      }],
    }])
    if (path === '/api/v1/meals') return json([{
      id: '00000000-0000-7000-8000-000000000210', mealAt: now, mealType: '早餐', name: '番茄炒蛋', servings: 1,
      nutrition: { calories: 320, protein: 16, fat: 18, carbs: 12 },
    }])
    if (path.endsWith('/devices') || path.endsWith('/sensors')) return json([])
    if (path.endsWith('/environment')) return json({ fridgeId, zones: [] })
    if (path === '/api/v1/notifications') return json([])
    if (path === '/api/v1/me/preferences') return json({
      tastes: ['清淡'], cuisines: ['家常菜'], allergies: [], dislikes: [], dietaryGoal: '均衡饮食',
      calorieTarget: 1650, temperatureUnit: 'C',
    })
    if (path === '/api/v1/analytics/diet') return json({ calories: 320, protein: 16, fat: 18, carbs: 12, mealCount: 1 })
    if (path === '/api/v1/analytics/consumption') return json({ topIngredients: [] })
    if (path === '/api/v1/assistant/briefing') return json({ insights: [], pendingActionCount: 0, unreadNotificationCount: 0, fallback: false })
    if (path === '/api/v1/assistant/capabilities') return json({
      localRecipes: true, webRecipeSearch: false, aiGeneration: true, recipeWriteRequiresConfirmation: true,
      foodNormalization: true, sourceModes: ['LOCAL'], preferenceModes: ['PREFERENCE_FIRST'], webSearchStatus: '联网搜索未启用',
    })
    if (path === '/api/v1/recipe-synthesis/match' && request.method() === 'POST') return json({
      synthesisId: '00000000-0000-7000-8000-000000000211', source: 'LIBRARY', recipes: [recipe],
      matched: ['番茄'], unmatched: [], suggestions: [],
    })
    return json([])
  })
}

async function expectViewportFit(page) {
  await expect.poll(() => page.evaluate(() => ({
    documentFits: document.documentElement.scrollWidth <= window.innerWidth,
    bodyFits: document.body.scrollWidth <= window.innerWidth,
  }))).toEqual({ documentFits: true, bodyFits: true })
}

async function expectButtonsInsideViewport(page, locator) {
  const buttons = locator.locator('button:visible')
  const count = await buttons.count()
  expect(count).toBeGreaterThan(0)
  for (let index = 0; index < count; index += 1) {
    const box = await buttons.nth(index).boundingBox()
    expect(box, `button ${index} should have a layout box`).not.toBeNull()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual((await page.viewportSize()).width + 1)
    expect(box.height).toBeGreaterThanOrEqual(36)
  }
}

async function verifyBusinessPages(page, width) {
  await page.goto('/app/inventory')
  await expect(page.getByRole('heading', { name: '冰箱库存' })).toBeVisible()
  await expectButtonsInsideViewport(page, page.locator('.page-intro'))
  await expect(page.getByTitle('编辑食材')).toBeVisible()
  await page.getByRole('button', { name: '添加食材' }).click()
  await expect(page.locator('.modal-actions')).toBeVisible()
  await expectButtonsInsideViewport(page, page.locator('.modal-actions'))
  await page.getByRole('button', { name: '取消' }).click()
  await expectViewportFit(page)

  await page.goto('/app/recipes')
  const recipeActions = page.locator('.intro-actions')
  await expect(page.getByRole('button', { name: 'AI 生成新菜谱' })).toBeVisible()
  await expectButtonsInsideViewport(page, recipeActions)
  expect(await recipeActions.locator('button').count()).toBe(3)
  await expectButtonsInsideViewport(page, page.locator('.recipe-card-actions').first())
  if (width === 1440 || width === 390) {
    await page.screenshot({ path: `test-results/business-layout-${width}.png`, fullPage: true })
  }
  await page.getByRole('button', { name: '查看做法' }).first().click()
  await expect(page.getByRole('heading', { name: '番茄炒蛋' })).toBeVisible()
  await expectButtonsInsideViewport(page, page.locator('.cooking-step-actions'))
  await expectViewportFit(page)

  await page.goto('/app/shopping')
  await expectButtonsInsideViewport(page, page.locator('.page-intro'))
  await expect(page.getByTitle('编辑项目')).toBeVisible()
  await expect(page.getByTitle('删除项目')).toBeVisible()
  await expectViewportFit(page)

  await page.goto('/app/settings')
  await expect(page.getByRole('button', { name: '保存更改' })).toBeVisible()
  await expectButtonsInsideViewport(page, page.locator('.page-intro'))
  await expectViewportFit(page)

  await page.goto('/app/synthesis')
  await expect(page.getByRole('heading', { name: '美味合成' })).toBeVisible()
  await page.getByRole('button', { name: /加入番茄/ }).click()
  await expect(page.getByRole('button', { name: '清空合成区' })).toBeEnabled()
  await expect(page.getByRole('button', { name: '移除番茄' })).toBeVisible()
  await expect(page.getByRole('button', { name: '开始做菜' })).toBeVisible()
  await expectViewportFit(page)
}

test('core business action layouts fit target desktop and mobile viewports', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'explicit viewport matrix runs once in Chromium')
  await installApi(page)

  for (const viewport of [
    { width: 1440, height: 1000 },
    { width: 1024, height: 768 },
    { width: 390, height: 844 },
    { width: 320, height: 720 },
  ]) {
    await page.setViewportSize(viewport)
    await verifyBusinessPages(page, viewport.width)
  }
})
