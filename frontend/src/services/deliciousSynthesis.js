const API_BASE_URL = String(import.meta.env?.VITE_API_BASE_URL || '').replace(/\/$/, '')

const ingredientAliases = new Map([
  ['鲜牛奶', '牛奶'],
  ['纯牛奶', '牛奶'],
  ['低脂牛奶', '牛奶'],
  ['北豆腐', '豆腐'],
  ['嫩豆腐', '豆腐'],
  ['老豆腐', '豆腐'],
  ['低钠生抽', '生抽'],
  ['酱油', '生抽'],
  ['西红柿', '番茄'],
  ['小番茄', '番茄'],
  ['鸡胸', '鸡胸肉'],
  ['青菜', '上海青'],
])

const mockRecipeCatalog = [
  {
    id: 'synthesis-tomato-egg',
    name: '番茄炒蛋',
    desc: '番茄酸甜、鸡蛋软嫩，是一锅就能完成的家常菜。',
    time: 12,
    kcal: 286,
    protein: 18,
    color: '#f8dfd8',
    art: '🍅',
    tags: ['家常菜', '快手菜'],
    base: 250,
    ingredients: [{ name: '鸡蛋', amount: 2, unit: '个' }, { name: '番茄', amount: 250, unit: '克' }],
    seasonings: [{ name: '盐', amount: 2, unit: '克' }, { name: '食用油', amount: 8, unit: '毫升' }],
    steps: ['鸡蛋打散，番茄切块。', '先将鸡蛋炒至凝固后盛出。', '番茄炒出汁，再倒回鸡蛋翻匀调味。'],
  },
  {
    id: 'synthesis-chicken-tofu',
    name: '鸡胸肉豆腐煲',
    desc: '鸡胸肉和豆腐吸满清鲜汤汁，搭配上海青更均衡。',
    time: 25,
    kcal: 386,
    protein: 42,
    color: '#dfe9df',
    art: '🍲',
    tags: ['高蛋白', '少油'],
    base: 300,
    ingredients: [{ name: '鸡胸肉', amount: 300, unit: '克' }, { name: '北豆腐', amount: 1, unit: '盒' }, { name: '上海青', amount: 200, unit: '克' }],
    seasonings: [{ name: '低钠生抽', amount: 10, unit: '毫升' }],
    steps: ['鸡胸肉切块，用少量生抽腌制 5 分钟。', '豆腐切块，与鸡胸肉一起煎至表面微黄。', '加水炖煮后放入上海青，煮熟即可。'],
  },
  {
    id: 'synthesis-garlic-greens',
    name: '蒜蓉上海青',
    desc: '大火快炒保留清脆口感，适合优先处理临期蔬菜。',
    time: 8,
    kcal: 96,
    protein: 4,
    color: '#e8edd6',
    art: '🥬',
    tags: ['低热量', '快手菜'],
    base: 300,
    ingredients: [{ name: '上海青', amount: 300, unit: '克' }],
    seasonings: [{ name: '蒜', amount: 8, unit: '克' }, { name: '盐', amount: 2, unit: '克' }],
    steps: ['上海青洗净沥干，蒜切末。', '热锅少油，将蒜末炒香。', '放入上海青大火快炒，调味后出锅。'],
  },
  {
    id: 'synthesis-salmon-salad',
    name: '香煎三文鱼温沙拉',
    desc: '香煎三文鱼搭配清爽蔬菜，适合作为轻盈正餐。',
    time: 22,
    kcal: 438,
    protein: 32,
    color: '#f1e1d6',
    art: '🥗',
    tags: ['优质脂肪', '均衡一餐'],
    base: 250,
    ingredients: [{ name: '三文鱼', amount: 250, unit: '克' }, { name: '小番茄', amount: 120, unit: '克' }],
    seasonings: [{ name: '黑胡椒', amount: 2, unit: '克' }, { name: '柠檬汁', amount: 10, unit: '毫升' }],
    steps: ['三文鱼擦干水分并简单调味。', '平底锅小火煎至两面熟透。', '与蔬菜拌匀，淋上柠檬汁。'],
  },
  {
    id: 'synthesis-yogurt-oats',
    name: '酸奶燕麦杯',
    desc: '酸奶搭配燕麦，冷藏后口感更绵密。',
    time: 5,
    kcal: 268,
    protein: 13,
    color: '#e2edf5',
    art: '🥣',
    tags: ['免烹饪', '适合早餐'],
    base: 250,
    ingredients: [{ name: '无糖酸奶', amount: 1, unit: '杯' }, { name: '燕麦片', amount: 40, unit: '克' }],
    seasonings: [],
    steps: ['将无糖酸奶倒入杯中。', '加入燕麦片并搅拌均匀。', '冷藏静置后即可食用。'],
  },
]

function normalizedName(value) {
  const compact = String(value || '').trim().toLowerCase().replace(/[\s·_-]+/g, '')
  return ingredientAliases.get(compact) || compact
}

function namesMatch(left, right) {
  const a = normalizedName(left)
  const b = normalizedName(right)
  return Boolean(a && b && (a === b || a.includes(b) || b.includes(a)))
}

function inventoryEntryFor(name, inventory) {
  return inventory.find(item => namesMatch(item.name, name))
}

function availabilityFor(ingredient, inventory) {
  const stock = inventoryEntryFor(ingredient.name, inventory)
  if (!stock) return { state: 'missing', availableAmount: 0, availableUnit: ingredient.unit, shortage: ingredient.amount }
  if (stock.amount === '' || stock.amount === null || stock.amount === undefined || !Number.isFinite(Number(stock.amount))) {
    return { state: 'unknown', availableAmount: null, availableUnit: stock.unit || ingredient.unit, shortage: null }
  }

  const availableAmount = Number(stock.amount)
  const comparableUnits = !stock.unit || !ingredient.unit || stock.unit === ingredient.unit
  const shortage = comparableUnits ? Math.max(0, Number(ingredient.amount || 0) - availableAmount) : 0
  return {
    state: comparableUnits && shortage > 0 ? 'insufficient' : 'available',
    availableAmount,
    availableUnit: stock.unit || ingredient.unit,
    shortage,
  }
}

function recipeAnalysis(recipe, selectedIngredients, inventory) {
  const recipeItems = [...recipe.ingredients, ...recipe.seasonings]
  const coveredSelections = selectedIngredients.filter(selected => recipeItems.some(item => namesMatch(item.name, selected.name)))
  const selectedNames = new Set(selectedIngredients.map(item => normalizedName(item.name)))
  const ingredientAvailability = recipe.ingredients.map(ingredient => ({
    ...ingredient,
    ...availabilityFor(ingredient, inventory),
    selected: [...selectedNames].some(name => namesMatch(name, ingredient.name)),
  }))
  const missing = ingredientAvailability.filter(item => item.state === 'missing' || item.state === 'insufficient').map(item => item.name)
  const additionalIngredients = recipe.ingredients.filter(item => !selectedIngredients.some(selected => namesMatch(selected.name, item.name)))
  const availableCount = ingredientAvailability.filter(item => item.state === 'available').length
  const inventoryCoverage = recipe.ingredients.length ? availableCount / recipe.ingredients.length : 0
  const selectedCoverage = selectedIngredients.length ? coveredSelections.length / selectedIngredients.length : 0
  const recipeCoverage = recipe.ingredients.length ? coveredSelections.length / recipe.ingredients.length : 0
  const score = Math.round(selectedCoverage * 55 + recipeCoverage * 25 + inventoryCoverage * 20)

  return {
    recipe,
    coversAll: coveredSelections.length === selectedIngredients.length,
    coveredCount: coveredSelections.length,
    additionalIngredients,
    ingredientAvailability,
    inventoryCoverage,
    missing,
    score,
  }
}

function resultRecipe(analysis) {
  const { recipe, ingredientAvailability, missing, score } = analysis
  return {
    ...recipe,
    id: `${recipe.id}-${Date.now()}`,
    match: score,
    level: missing.length === 0 ? '库存齐全' : missing.length === 1 ? '缺 1 样食材' : `缺 ${missing.length} 样食材`,
    missing,
    favorite: false,
    collected: false,
    ingredients: ingredientAvailability,
  }
}

export function createMockSynthesisResult({ ingredients = [], inventory = [] } = {}) {
  const selectedIngredients = ingredients.filter(item => item?.name)
  const requestId = `synthesis-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  if (!selectedIngredients.length) return { requestId, status: 'unmatched', recipe: null, suggestion: null }

  const analyses = mockRecipeCatalog.map(recipe => recipeAnalysis(recipe, selectedIngredients, inventory))
  const matches = analyses.filter(item => item.coversAll).sort((left, right) => (
    left.additionalIngredients.length - right.additionalIngredients.length
    || right.inventoryCoverage - left.inventoryCoverage
    || right.score - left.score
  ))

  if (matches.length) {
    return { requestId, status: 'matched', recipe: resultRecipe(matches[0]), suggestion: null }
  }

  const nearest = analyses.sort((left, right) => (
    right.coveredCount - left.coveredCount
    || left.additionalIngredients.length - right.additionalIngredients.length
    || right.inventoryCoverage - left.inventoryCoverage
  ))[0]
  const suggestedIngredient = nearest.additionalIngredients.find(item => !selectedIngredients.some(selected => namesMatch(selected.name, item.name)))
    || nearest.recipe.ingredients[0]

  return {
    requestId,
    status: 'unmatched',
    recipe: null,
    suggestion: {
      ingredientName: suggestedIngredient.name,
      targetRecipeName: nearest.recipe.name,
      reason: `当前组合与“${nearest.recipe.name}”最接近，再加 ${suggestedIngredient.name} 可补齐关键风味。`,
    },
  }
}

function abortError() {
  const error = new Error('Synthesis request aborted')
  error.name = 'AbortError'
  return error
}

function waitForResult(result, signal, delay = 520) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(abortError())
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve(result)
    }, delay)
    function onAbort() {
      clearTimeout(timer)
      reject(abortError())
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

async function requestMatch(payload, signal) {
  const response = await fetch(`${API_BASE_URL}/api/recipe-synthesis/match`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  })
  if (!response.ok) throw new Error(`Recipe synthesis request failed: ${response.status}`)
  return response.json()
}

export async function matchRecipeCombination(payload, { signal } = {}) {
  if (API_BASE_URL) return requestMatch(payload, signal)
  return waitForResult(createMockSynthesisResult(payload), signal)
}
