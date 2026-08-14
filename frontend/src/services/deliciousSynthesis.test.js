import test from 'node:test'
import assert from 'node:assert/strict'
import { createMockSynthesisResult, matchRecipeCombination } from './deliciousSynthesis.js'

const inventory = [
  { id: 1, name: '鸡蛋', amount: 8, unit: '个', category: '肉蛋' },
  { id: 2, name: '鸡胸肉', amount: 520, unit: '克', category: '肉蛋' },
  { id: 3, name: '嫩豆腐', amount: 2, unit: '盒', category: '豆制品' },
  { id: 4, name: '鲜牛奶', amount: 680, unit: '毫升', category: '饮料' },
  { id: 5, name: '低钠生抽', amount: 320, unit: '毫升', category: '调味品' },
]

test('鸡蛋可以模糊命中番茄炒蛋', () => {
  const result = createMockSynthesisResult({ ingredients: [{ id: 1, name: '鸡蛋', quantity: 1, unit: '个' }], inventory })
  assert.equal(result.status, 'matched')
  assert.equal(result.recipe.name, '番茄炒蛋')
  assert.deepEqual(result.recipe.missing, ['番茄'])
})

test('多食材组合命中同一道菜', () => {
  const result = createMockSynthesisResult({
    ingredients: [{ id: 2, name: '鸡胸肉', quantity: 1, unit: '克' }, { id: 3, name: '嫩豆腐', quantity: 1, unit: '盒' }],
    inventory,
  })
  assert.equal(result.status, 'matched')
  assert.equal(result.recipe.name, '鸡胸肉豆腐煲')
})

test('重复数量不改变食材种类匹配', () => {
  const result = createMockSynthesisResult({ ingredients: [{ id: 1, name: '鸡蛋', quantity: 3, unit: '个' }], inventory })
  assert.equal(result.status, 'matched')
  assert.equal(result.recipe.name, '番茄炒蛋')
})

test('不兼容组合返回补料建议', () => {
  const result = createMockSynthesisResult({
    ingredients: [{ id: 4, name: '鲜牛奶', quantity: 1, unit: '毫升' }, { id: 5, name: '低钠生抽', quantity: 1, unit: '毫升' }],
    inventory,
  })
  assert.equal(result.status, 'unmatched')
  assert.ok(result.suggestion.ingredientName)
  assert.ok(result.suggestion.targetRecipeName)
})

test('请求可以通过 AbortSignal 取消', async () => {
  const controller = new AbortController()
  const request = matchRecipeCombination({ ingredients: [{ id: 1, name: '鸡蛋', quantity: 1, unit: '个' }], inventory }, { signal: controller.signal })
  controller.abort()
  await assert.rejects(request, error => error.name === 'AbortError')
})
