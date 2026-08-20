import test from 'node:test'
import assert from 'node:assert/strict'
import { buildRestockCandidates } from './recipePlanning.js'

test('planned recipes aggregate requirements and subtract compatible inventory', () => {
  const foods = [{ id: 'food-1', name: '鸡胸肉', amount: 100, apiUnit: 'g', unit: '克', category: '肉蛋', lowStock: false }]
  const plans = [
    { servings: 2, recipe: { name: '香煎鸡胸', servings: 2, ingredients: [{ name: '鸡胸肉', quantity: 300, apiUnit: 'g' }] } },
    { servings: 1, recipe: { name: '鸡肉沙拉', servings: 1, ingredients: [{ name: '鸡胸肉', quantity: 150, apiUnit: 'g' }] } },
  ]

  const result = buildRestockCandidates(foods, plans)

  assert.equal(result.length, 1)
  assert.equal(result[0].name, '鸡胸肉')
  assert.equal(result[0].quantity, 350)
  assert.equal(result[0].unit, 'g')
  assert.match(result[0].note, /香煎鸡胸、鸡肉沙拉/)
})

test('only planned recipes contribute recipe shortages', () => {
  const unplannedRecipe = { servings: 1, recipe: { name: '未计划的汤', ingredients: [{ name: '番茄', quantity: 2, apiUnit: 'piece' }] } }

  assert.deepEqual(buildRestockCandidates([], []), [])
  assert.equal(buildRestockCandidates([], [unplannedRecipe])[0].name, '番茄')
})

test('sufficient inventory suppresses a recipe shortage even when units use kilograms', () => {
  const foods = [{ id: 'food-1', name: '土豆', amount: 1, apiUnit: 'kg', category: '蔬菜', lowStock: false }]
  const plans = [{ servings: 2, recipe: { name: '土豆泥', servings: 2, ingredients: [{ name: '土豆', quantity: 800, apiUnit: 'g' }] } }]

  assert.deepEqual(buildRestockCandidates(foods, plans), [])
})

test('low-stock advice remains when a planned recipe is already covered', () => {
  const foods = [{ id: 'food-1', name: '土豆', amount: 1, apiUnit: 'kg', category: '蔬菜', lowStock: true }]
  const plans = [{ servings: 2, recipe: { name: '土豆泥', servings: 2, ingredients: [{ name: '土豆', quantity: 800, apiUnit: 'g' }] } }]

  const result = buildRestockCandidates(foods, plans)
  assert.equal(result.length, 1)
  assert.equal(result[0].sourceType, 'LOW_STOCK')
})
