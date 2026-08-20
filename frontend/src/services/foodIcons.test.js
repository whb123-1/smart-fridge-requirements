import test from 'node:test'
import assert from 'node:assert/strict'
import { foodIcon, hasDedicatedFoodIcon } from './foodIcons.js'

test('典型食材使用专属图标', () => {
  assert.equal(foodIcon('番茄', '蔬菜'), '🍅')
  assert.equal(foodIcon('土豆', '蔬菜'), '🥔')
  assert.equal(foodIcon('鸡胸肉', '肉蛋'), '🍗')
  assert.equal(foodIcon('三文鱼', '水产'), '🍣')
  assert.equal(hasDedicatedFoodIcon('西兰花'), true)
})

test('未知食材回退到分类图标', () => {
  assert.equal(foodIcon('一种新食材', '水果'), '🍎')
})
