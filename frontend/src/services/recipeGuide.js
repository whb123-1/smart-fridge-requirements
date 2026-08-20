const toolRules = [
  [/蒸|蒸锅|蒸制/, '蒸锅'], [/炒|翻炒|爆香|大火/, '炒锅'], [/煎|煎至/, '平底锅'], [/烤|烘烤/, '烤箱'],
  [/煮|焯|汤|粥|面/, '汤锅'], [/搅拌|打散|腌制|拌匀/, '料理碗'], [/打碎|搅打|浓汤/, '料理机'],
  [/沥干|捞出/, '滤网'], [/称|克|毫升/, '厨房秤/量杯'],
]

function stepTitle(text, index) {
  if (/洗|切|擦干|打散|腌/.test(text)) return '备料'
  if (/焯|煮开|预热/.test(text)) return '预处理'
  if (/炒|煎|蒸|烤|煮/.test(text)) return '烹制'
  if (/调味|装盘|出锅|淋|撒/.test(text)) return '调味装盘'
  return `制作步骤 ${index + 1}`
}

function heatLabel(text) {
  if (/大火|水开|沸/.test(text)) return '大火'
  if (/小火|慢炖|焖/.test(text)) return '小火'
  if (/中火|煎|炒/.test(text)) return '中火'
  if (/冷藏|静置|装盘|切|洗/.test(text)) return '无需加热'
  return '按状态调整'
}

function checkpoint(text) {
  if (/蛋/.test(text)) return '蛋液凝固但仍保持嫩滑'
  if (/鸡|肉|鱼|虾/.test(text)) return '中心熟透、无生色后再进入下一步'
  if (/蔬菜|青椒|西兰花|生菜/.test(text)) return '颜色明亮、刚断生即可'
  if (/汤|粥|咖喱/.test(text)) return '质地均匀，尝味后再补调料'
  return '观察香气、颜色和质地，避免只依赖计时'
}

export function buildRecipeGuide(recipe = {}) {
  const rawSteps = Array.isArray(recipe.steps) ? recipe.steps.filter(Boolean) : []
  const minutes = Math.max(1, Number(recipe.time || recipe.cookMinutes || 0))
  const perStep = Math.max(1, Math.round(minutes / Math.max(rawSteps.length, 1)))
  const steps = rawSteps.map((value, index) => {
    const instruction = typeof value === 'string' ? value : value.instruction || value.text || ''
    return {
      number: index + 1,
      title: typeof value === 'object' && value.title ? value.title : stepTitle(instruction, index),
      instruction,
      duration: typeof value === 'object' && value.duration ? value.duration : `约 ${perStep} 分钟`,
      heat: typeof value === 'object' && value.heat ? value.heat : heatLabel(instruction),
      checkpoint: typeof value === 'object' && value.checkpoint ? value.checkpoint : checkpoint(instruction),
    }
  })
  const corpus = `${recipe.name || ''} ${rawSteps.map(step => typeof step === 'string' ? step : step.instruction || '').join(' ')}`
  const utensils = ['菜刀', '砧板', ...toolRules.filter(([pattern]) => pattern.test(corpus)).map(([, tool]) => tool)]
  if (!utensils.some(tool => /锅|烤箱|料理机/.test(tool))) utensils.push('烹饪锅具')
  return { steps, utensils: [...new Set(utensils)] }
}
