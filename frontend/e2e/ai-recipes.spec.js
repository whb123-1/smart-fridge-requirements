import { expect, test } from '@playwright/test'

const fridgeId = '00000000-0000-7000-8000-000000000101'
const zoneId = '00000000-0000-7000-8000-000000000102'
const recipeId = '00000000-0000-7000-8000-000000000103'
const now = '2026-08-21T06:00:00Z'

function envelope(data) {
  return { code: 'OK', message: 'ok', data, traceId: 'ai-recipes-e2e' }
}

function session() {
  return {
    accessToken: 'ai-recipes-token', expiresInSeconds: 900, onboardingRequired: false,
    user: {
      id: '00000000-0000-7000-8000-000000000100', username: 'recipe_user', displayName: '菜谱用户',
      email: 'recipe@example.com', role: 'USER', passwordChangeRequired: false, temperatureUnit: 'C',
    },
  }
}

function fridge() {
  return {
    id: fridgeId, name: '测试冰箱',
    zones: [{
      id: zoneId, name: '冷藏室', kind: 'REFRIGERATED', enabled: true,
      targetTemperatureC: 4, targetHumidityPct: 55, temperatureSensorCount: 0, humiditySensorCount: 0,
    }],
  }
}

function inventory() {
  return [{
    id: '00000000-0000-7000-8000-000000000110', fridgeId,
    catalogId: '00000000-0000-7000-8000-000000000111', name: '番茄', canonicalName: '番茄',
    originalNames: ['西红柿', '番茄'], category: 'VEGETABLE', defaultUnit: 'g', lowStock: false,
    batches: [
      { id: '00000000-0000-7000-8000-000000000112', inputName: '西红柿', zoneId, storedAt: now, remainingQuantity: 300, unit: 'g', status: 'ACTIVE', assessment: null },
      { id: '00000000-0000-7000-8000-000000000113', inputName: '番茄', zoneId, storedAt: now, remainingQuantity: 500, unit: 'g', status: 'ACTIVE', assessment: null },
    ],
  }]
}

function webRecipe() {
  return {
    id: recipeId, name: '红烧鹅', description: '以鹅肉为主料的红烧菜谱。', cuisine: '家常菜', taste: '咸鲜', goal: '均衡',
    cookMinutes: 80, servings: 2,
    total: { calories: 960, protein: 92, fat: 58, carbs: 18 },
    perServing: { calories: 480, protein: 46, fat: 29, carbs: 9 },
    ingredients: [
      { id: '00000000-0000-7000-8000-000000000114', name: '鹅肉', role: 'PRIMARY', quantity: 800, unit: 'g', scalingRule: 'LINEAR' },
      { id: '00000000-0000-7000-8000-000000000115', name: '姜', role: 'SEASONING', quantity: 20, unit: 'g', scalingRule: 'FIXED' },
    ],
    steps: ['鹅肉焯水后沥干。', '小火红烧至鹅肉熟透。'], detailedSteps: [], utensils: ['炖锅'],
    nutritionSource: 'AI_RECIPE_SEARCH', bookmarked: false, source: 'AI_GENERATED', sourceVersion: 'test-model',
    attribution: 'AI 根据公开网页整理', imageUrl: null, imageSourceUrl: null, imageAttribution: null,
    availability: 'MISSING_FEW', missing: ['鹅肉'], validationWarnings: ['营养数据为 AI 估算，仅供参考'],
    webSources: [{
      title: '家常红烧鹅做法', summary: '介绍鹅肉焯水、炒糖色和小火焖烧步骤。',
      url: 'https://recipes.example.com/braised-goose', site: 'recipes.example.com', retrievedAt: now, sourceVersion: 'tavily-v1',
    }],
  }
}

async function installApi(page, { webEnabled = false, returnsPublishedAiRecipe = false } = {}) {
  let publishedRecipes = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const json = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(data)) })

    if (path === '/api/v1/auth/refresh') return json(session())
    if (path === '/api/v1/fridges') return json([fridge()])
    if (path === '/api/v1/inventory/items') return json(inventory())
    if (path === '/api/v1/expiry' || path === '/api/v1/inventory/transactions') return json([])
    if (path === '/api/v1/recipes') return json(publishedRecipes)
    if (path === '/api/v1/shopping-lists' || path === '/api/v1/meals') return json([])
    if (path.endsWith('/recipe-plans') || path === '/api/v1/notifications') return json([])
    if (path.endsWith('/devices') || path.endsWith('/sensors')) return json([])
    if (path.endsWith('/environment')) return json({ fridgeId, zones: [] })
    if (path === '/api/v1/me/preferences') return json({ tastes: [], cuisines: [], allergies: [], dislikes: [], dietaryGoal: null, calorieTarget: null, temperatureUnit: 'C' })
    if (path === '/api/v1/analytics/diet' || path === '/api/v1/analytics/consumption') return json({})
    if (path === '/api/v1/assistant/briefing') return json({ insights: [], pendingActionCount: 0, unreadNotificationCount: 0, fallback: false })
    if (path === '/api/v1/assistant/capabilities') return json({
      localRecipes: true, webRecipeSearch: webEnabled, aiGeneration: true,
      recipeWriteRequiresConfirmation: true, foodNormalization: true,
      sourceModes: ['LOCAL', 'WEB', 'HYBRID'], preferenceModes: ['PREFERENCE_FIRST', 'PROMPT_FIRST'],
      webSearchStatus: webEnabled ? '联网搜索可用' : '联网搜索未启用',
    })
    if (path === '/api/v1/recipes/search-web' && request.method() === 'POST') return json({
      recipes: [webRecipe()], sources: webRecipe().webSources, warnings: ['营养数据为 AI 估算，仅供参考'],
      model: 'test-model', fallback: false, rationale: '已按菜名、主料和红烧做法整理外部草稿',
      sourceMode: 'WEB', preferenceMode: 'PROMPT_FIRST',
      draftRecipeIds: returnsPublishedAiRecipe ? [] : [recipeId],
    })
    if (path === '/api/v1/recipes/generated/publish' && request.method() === 'POST') {
      publishedRecipes = [webRecipe()]
      return json(publishedRecipes)
    }
    if (path === '/api/v1/recipes/generated/discard' && request.method() === 'POST') return json({ discarded: 0 })
    return json([])
  })
}

async function expectNoHorizontalOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
}

test('AI manual and normalized inventory fit desktop and mobile viewports', async ({ page }, testInfo) => {
  await installApi(page)
  await page.goto('/app/inventory')
  await expect(page.getByRole('heading', { name: '冰箱库存' })).toBeVisible()
  await expect(page.getByText('录入名：西红柿、番茄')).toBeVisible()
  await expect(page.getByText('保留 2 个独立批次')).toBeVisible()
  await expectNoHorizontalOverflow(page)

  await page.getByLabel('打开鲜知 AI 助手').click()
  await page.getByRole('button', { name: /AI 使用说明/ }).click()
  await expect(page.getByRole('heading', { name: 'AI 使用说明书' })).toBeVisible()
  await expect(page.getByText('联网搜索未启用', { exact: true })).toBeVisible()
  await expect(page.getByText('本地与联网找菜谱')).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await page.screenshot({ path: `test-results/ai-manual-${testInfo.project.name}.png`, fullPage: true })
})

test('external recipe sources remain visible before confirmation', async ({ page }, testInfo) => {
  await installApi(page, { webEnabled: true })
  await page.goto('/app/recipes')
  await page.getByRole('button', { name: 'AI 生成新菜谱' }).click()
  await page.getByLabel('菜谱描述').fill('红烧鹅，主料必须是鹅肉，使用红烧做法')
  const searchRequest = page.waitForRequest(request => request.url().endsWith('/api/v1/recipes/search-web'))
  await page.getByRole('button', { name: '按描述生成新菜谱' }).click()
  const request = await searchRequest

  expect(request.postDataJSON()).toMatchObject({
    prompt: '红烧鹅，主料必须是鹅肉，使用红烧做法', inventory: [], sourceMode: 'WEB', preferenceMode: 'PROMPT_FIRST',
  })
  await expect(page.getByRole('heading', { name: '选择要加入菜谱库的新菜谱' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '红烧鹅', exact: true })).toBeVisible()
  await expect(page.getByText('待确认入库', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'WEB · PROMPT_FIRST' })).toBeVisible()
  await expect(page.getByRole('link', { name: /家常红烧鹅做法/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /加入菜谱库/ })).toBeDisabled()
  await expectNoHorizontalOverflow(page)
  await page.screenshot({ path: `test-results/web-recipe-${testInfo.project.name}.png`, fullPage: true })
})

test('AI recipe card can be selected and published into the recipe library', async ({ page }) => {
  await installApi(page, { webEnabled: true })
  await page.goto('/app/recipes')
  await page.getByRole('button', { name: 'AI 生成新菜谱' }).click()
  await page.getByLabel('菜谱描述').fill('红烧鹅，主料必须是鹅肉，使用红烧做法')
  await page.getByRole('button', { name: '按描述生成新菜谱' }).click()

  await page.getByRole('heading', { name: '红烧鹅' }).click()
  const publish = page.getByRole('button', { name: /加入菜谱库/ })
  await expect(publish).toBeEnabled()

  const publishRequest = page.waitForRequest(request => request.url().endsWith('/api/v1/recipes/generated/publish'))
  await publish.click()
  const request = await publishRequest
  expect(request.postDataJSON()).toEqual({ recipeIds: [recipeId] })
  await expect(page.getByText('已将 1 道新菜谱加入现有菜谱库')).toBeVisible()
  await expect(page.getByRole('heading', { name: '红烧鹅' })).toBeVisible()
})

test('published AI recipe search results are not offered as drafts again', async ({ page }) => {
  await installApi(page, { webEnabled: true, returnsPublishedAiRecipe: true })
  await page.goto('/app/recipes')
  await page.getByRole('button', { name: 'AI 生成新菜谱' }).click()
  await page.getByLabel('菜谱描述').fill('红烧鹅，主料必须是鹅肉，使用红烧做法')
  await page.getByRole('button', { name: '按描述生成新菜谱' }).click()

  await expect(page.getByRole('heading', { name: '关键词结果 · 红烧鹅，主料必须是鹅肉，使用红烧做法' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '红烧鹅', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /加入菜谱库/ })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '加入待制作' })).toBeVisible()
})
