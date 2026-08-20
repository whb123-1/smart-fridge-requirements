const unitAliases = Object.freeze({
  克: 'g', 千克: 'kg', 毫升: 'ml', 个: 'piece', 盒: 'box', 瓶: 'bottle', 袋: 'bag', 杯: 'cup', 份: 'serving',
})

const unitLabels = Object.freeze({
  g: '克', kg: '千克', ml: '毫升', piece: '个', box: '盒', bottle: '瓶', bag: '袋', cup: '杯', serving: '份',
})

function normalizedUnit(value) {
  return unitAliases[value] || String(value || 'serving').toLowerCase()
}

function quantityInBase(value, unit) {
  const amount = Number(value || 0)
  const normalized = normalizedUnit(unit)
  if (normalized === 'kg') return { amount: amount * 1000, unit: 'g' }
  return { amount, unit: normalized }
}

function formatNumber(value) {
  return Number(Number(value).toFixed(2)).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function displayQuantity(value, unit) {
  if (unit === 'g' && value >= 1000) return `${formatNumber(value / 1000)} 千克`
  return `${formatNumber(value)} ${unitLabels[unit] || unit}`
}

function nameKey(name) {
  return String(name || '').trim().toLowerCase()
}

function groupForFood(food) {
  if (!food) return '菜谱缺料'
  if (['蔬菜', '水果'].includes(food.category)) return '蔬果'
  return food.category || '其他'
}

export function buildRestockCandidates(foods = [], plans = []) {
  const candidates = new Map()

  foods.filter(food => food.lowStock).forEach(food => {
    const base = quantityInBase(food.amount, food.apiUnit || food.unit)
    const key = `${nameKey(food.name)}|${base.unit}`
    candidates.set(key, {
      id: `low-${food.id}`,
      name: food.name,
      group: groupForFood(food),
      current: `库存 ${displayQuantity(base.amount, base.unit)}`,
      threshold: '低库存阈值',
      amount: displayQuantity(base.unit === 'g' ? Math.max(500, base.amount) : 1, base.unit),
      quantity: base.unit === 'g' ? Math.max(500, base.amount) : 1,
      unit: base.unit,
      note: '库存低于已配置阈值',
      sourceType: 'LOW_STOCK',
    })
  })

  const requirements = new Map()
  plans.forEach(plan => {
    const recipe = plan.recipe || {}
    const baseServings = Math.max(0.1, Number(recipe.servings || 1))
    const scale = Math.max(0.1, Number(plan.servings || baseServings)) / baseServings
    ;(recipe.ingredients || []).forEach(ingredient => {
      const base = quantityInBase(Number(ingredient.quantity ?? ingredient.amount ?? 0) * scale, ingredient.apiUnit || ingredient.unit)
      if (!(base.amount > 0)) return
      const key = `${nameKey(ingredient.name)}|${base.unit}`
      const current = requirements.get(key) || { name: ingredient.name, unit: base.unit, required: 0, recipes: new Set() }
      current.required += base.amount
      current.recipes.add(recipe.name || '待制作菜谱')
      requirements.set(key, current)
    })
  })

  requirements.forEach((requirement, key) => {
    const matchingFoods = foods.filter(food => {
      const base = quantityInBase(food.amount, food.apiUnit || food.unit)
      return nameKey(food.name) === nameKey(requirement.name) && base.unit === requirement.unit
    })
    const available = matchingFoods.reduce((total, food) => total + quantityInBase(food.amount, food.apiUnit || food.unit).amount, 0)
    const shortage = Math.max(0, requirement.required - available)
    if (shortage <= 0.001) return
    const recipeNames = [...requirement.recipes]
    candidates.set(key, {
      id: `plan-${key}`,
      name: requirement.name,
      group: groupForFood(matchingFoods[0]),
      current: `库存 ${displayQuantity(available, requirement.unit)}`,
      threshold: `计划需 ${displayQuantity(requirement.required, requirement.unit)}`,
      amount: displayQuantity(shortage, requirement.unit),
      quantity: Number(shortage.toFixed(3)),
      unit: requirement.unit,
      note: `待制作：${recipeNames.join('、')}`,
      sourceType: 'RECIPE_PLAN',
    })
  })

  return [...candidates.values()]
}
