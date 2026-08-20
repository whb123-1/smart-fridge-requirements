import test from 'node:test'
import assert from 'node:assert/strict'
import { ingredientAvailabilityMessage, matchRecipeCombination } from './deliciousSynthesis.js'

function response(data) {
  return { ok: true, status: 200, text: async () => JSON.stringify({ code: 'OK', data }) }
}

test('美味合成使用后端菜谱数据库结果', async t => {
  const previousFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = previousFetch })
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/recipe-synthesis/match')
    assert.equal(options.method, 'POST')
    const request = JSON.parse(options.body)
    assert.deepEqual(request.ingredients, [{ batchId: 'batch-1', name: '鸡蛋', quantity: 2, unit: 'piece' }])
    return response({
      matched: ['鸡蛋'], unmatched: [], suggestions: [],
      recipes: [{
        id: 'recipe-1', name: '番茄炒蛋', description: '数据库菜谱', cookMinutes: 15, servings: 2,
        total: { calories: 420 }, perServing: { calories: 210, protein: 12 }, missing: ['番茄'],
        ingredients: [
          { id: 'c1', name: '鸡蛋', role: 'PRIMARY', quantity: 2, unit: 'piece' },
          { id: 'c2', name: '番茄', role: 'PRIMARY', quantity: 250, unit: 'g' },
        ], steps: ['炒熟'], bookmarked: false,
      }],
    })
  }
  const result = await matchRecipeCombination({ ingredients: [{ batchId: 'batch-1', name: '鸡蛋', quantity: 2, unit: 'piece' }] })
  assert.equal(result.status, 'matched')
  assert.equal(result.recipe.name, '番茄炒蛋')
  assert.deepEqual(result.recipe.missing, ['番茄'])
})

test('数据库无匹配时返回后端建议', async t => {
  const previousFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = previousFetch })
  globalThis.fetch = async () => response({ recipes: [], matched: [], unmatched: ['牛奶'], suggestions: ['燕麦片'] })
  const result = await matchRecipeCombination({ ingredients: [{ name: '牛奶', quantity: 1, unit: 'cup' }] })
  assert.equal(result.status, 'unmatched')
  assert.equal(result.suggestion.ingredientName, '燕麦片')
})

test('库存单位不一致时明确提示，绝不渲染 null 缺口', async t => {
  const previousFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = previousFetch })
  globalThis.fetch = async () => response({
    matched: [], unmatched: ['鸡蛋'], suggestions: [],
    recipes: [{
      id: 'recipe-egg', name: '番茄炒蛋', cookMinutes: 15, servings: 2,
      ingredients: [{ id: 'egg', name: '鸡蛋', role: 'PRIMARY', quantity: 5, unit: 'piece' }],
      missing: ['鸡蛋'], steps: [], bookmarked: false,
    }],
  })

  const result = await matchRecipeCombination({ ingredients: [{ name: '鸡蛋', quantity: 100, unit: 'g' }] })
  const egg = result.recipe.ingredients[0]
  assert.equal(egg.state, 'unit-mismatch')
  assert.equal(egg.shortage, null)
  assert.equal(ingredientAvailabilityMessage(egg), '库存单位不一致，无法换算')
  assert.equal(ingredientAvailabilityMessage({ state: 'insufficient', shortage: null, unit: '个' }), '库存数量待确认')
})

test('已取消的请求不会调用后端', async () => {
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(
    matchRecipeCombination({ ingredients: [{ name: '鸡蛋', quantity: 1, unit: 'piece' }] }, { signal: controller.signal }),
    error => error.name === 'AbortError',
  )
})
