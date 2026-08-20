import test from 'node:test'
import assert from 'node:assert/strict'
import { buildRecipeGuide } from './recipeGuide.js'

test('菜谱步骤补齐火候、时间、检查点与厨具', () => {
  const guide = buildRecipeGuide({ name: '番茄炒蛋', time: 15, steps: ['番茄切块，鸡蛋打散。', '中火炒鸡蛋后加入番茄翻炒。'] })
  assert.deepEqual(guide.utensils, ['菜刀', '砧板', '炒锅', '料理碗'])
  assert.equal(guide.steps[0].title, '备料')
  assert.equal(guide.steps[1].heat, '中火')
  assert.match(guide.steps[1].checkpoint, /蛋液凝固/)
})
