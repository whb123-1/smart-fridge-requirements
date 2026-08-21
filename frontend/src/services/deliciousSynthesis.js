import { api } from './api.js'
import { buildRecipeGuide } from './recipeGuide.js'

function displayUnit(value) {
  return ({ g: '克', kg: '千克', piece: '个', box: '盒', bottle: '瓶', bag: '袋', cup: '杯', ml: '毫升', serving: '份' })[value] || value || ''
}

function finiteQuantity(value, fallback = 0) {
  if (value == null || value === '') return fallback
  const quantity = Number(value)
  return Number.isFinite(quantity) ? quantity : fallback
}

function formatQuantity(value) {
  const quantity = finiteQuantity(value, null)
  return quantity == null ? '' : String(Number(quantity.toFixed(3)))
}

export function ingredientAvailabilityMessage(item) {
  if (item?.state === 'missing') return '库存缺少'
  if (item?.state === 'unknown') return '库存未记录'
  if (item?.state === 'unit-mismatch') return '库存单位不一致，无法换算'
  if (item?.state === 'insufficient') {
    const shortage = formatQuantity(item.shortage)
    return shortage ? `还差 ${shortage}${item.unit || ''}` : '库存数量待确认'
  }
  return '库存充足'
}

function similar(left, right) {
  const a = String(left || '').trim().toLowerCase()
  const b = String(right || '').trim().toLowerCase()
  return Boolean(a && b && (a.includes(b) || b.includes(a)))
}

function mapRecipe(recipe, supplied) {
  const components = (recipe.ingredients || []).filter(item => item.role !== 'SEASONING')
  const missing = recipe.missing || []
  const mapped = {
    id: recipe.id,
    name: recipe.name,
    desc: recipe.description || '',
    time: Number(recipe.cookMinutes || 0),
    kcal: Math.round(Number(recipe.perServing?.calories ?? recipe.total?.calories ?? 0)),
    protein: Math.round(Number(recipe.perServing?.protein ?? recipe.total?.protein ?? 0)),
    level: missing.length ? `缺 ${missing.length} 样食材` : '库存齐全',
    missing,
    art: '🍲',
    ingredients: components.map(item => {
      const selected = supplied.find(candidate => similar(candidate.name, item.name))
      const isMissing = missing.some(name => similar(name, item.name))
      const comparable = selected && selected.unit === item.unit
      const shortage = comparable ? Math.max(0, finiteQuantity(item.quantity) - finiteQuantity(selected.quantity)) : null
      return {
        ...item,
        amount: finiteQuantity(item.quantity),
        unit: displayUnit(item.unit),
        state: isMissing
          ? (selected ? (comparable ? 'insufficient' : 'unit-mismatch') : 'missing')
          : 'available',
        shortage,
      }
    }),
    seasonings: (recipe.ingredients || []).filter(item => item.role === 'SEASONING'),
    steps: recipe.steps || [],
    servings: Number(recipe.servings || 1),
    favorite: Boolean(recipe.bookmarked),
    collected: Boolean(recipe.bookmarked),
    source: recipe.source,
    attribution: recipe.attribution,
    detailedSteps: recipe.detailedSteps || [],
    utensils: recipe.utensils || [],
    nutritionSource: recipe.nutritionSource || 'AI_RECIPE_SEARCH',
  }
  const guide = buildRecipeGuide(mapped)
  return { ...mapped, detailedSteps: mapped.detailedSteps.length ? mapped.detailedSteps : guide.steps, utensils: mapped.utensils.length ? mapped.utensils : guide.utensils }
}

export async function matchRecipeCombination(payload, { signal } = {}) {
  if (signal?.aborted) throw new DOMException('Synthesis request aborted', 'AbortError')
  const ingredients = (payload?.ingredients || []).map(item => ({
    batchId: item.batchId || null,
    name: item.name,
    quantity: Number(item.quantity),
    unit: item.unit,
  }))
  const result = await api.matchRecipes(ingredients)
  if (signal?.aborted) throw new DOMException('Synthesis request aborted', 'AbortError')
  const recipe = result?.recipes?.[0]
  if (recipe) return {
    requestId: result?.synthesisId || null,
    status: 'matched',
    recipe: { ...mapRecipe(recipe, ingredients), synthesisId: result?.synthesisId || null, draft: result?.source === 'AI_DRAFT' },
    source: result?.source || 'LIBRARY',
    matched: result?.matched || [],
    unmatched: result?.unmatched || [],
    suggestion: null,
  }
  const ingredientName = result?.suggestions?.[0] || null
  return {
    requestId: result?.synthesisId || null,
    status: 'unmatched',
    recipe: null,
    matched: result?.matched || [],
    unmatched: result?.unmatched || ingredients.map(item => item.name),
    suggestion: ingredientName ? {
      ingredientName,
      targetRecipeName: '数据库中的相近菜谱',
      reason: `当前数据库没有可直接制作的组合，可补充 ${ingredientName} 后重试。`,
    } : null,
  }
}
