<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './services/api'
import { logout, session, setFridge } from './session'
import pixelPet from './assets/xianling-pixel-pet-transparent.png'
import AssistantPet from './components/AssistantPet.vue'
import NameSuggestionInput from './components/NameSuggestionInput.vue'
import DeliciousSynthesis from './components/DeliciousSynthesis.vue'
import { clampZoneCount, MAX_ZONES, MIN_ZONES, ZONE_KINDS } from './components/fridgeLayouts'

const FridgeModel = defineAsyncComponent(() => import('./components/FridgeModel.vue'))

const route = useRoute()
const router = useRouter()
const isAppRoute = computed(() => route.name === 'app')
const page = ref(String(route.params.page || 'home'))
const unit = ref('C')
const showAdd = ref(false)
const showFoodEditor = ref(false)
const showMealEditor = ref(false)
const showPurchase = ref(false)
const showShoppingEditor = ref(false)
const showRecipeNameGenerator = ref(false)
const showInventoryRecipeSelector = ref(false)
const assistantOpen = ref(false)
const isListening = ref(false)
const toast = ref('')
const search = ref('')
const inventoryFilter = ref('全部')
const inventoryType = ref('全部')
const expiryFilter = ref('全部')
const recipeFilter = ref('全部推荐')
const shoppingGroup = ref('全部')
const assistantInput = ref('')
const assistantConversationId = ref(null)
const assistantProposals = reactive([])
const voiceFileInput = ref(null)
const voiceDraft = ref(null)
const voiceUploadInProgress = ref(false)
const selectedFood = ref(null)
const selectedShopItem = ref(null)
const cookingRecipe = ref(null)
const cookingWeight = ref(300)
const activeCookingStep = ref(0)
const recipeNameDraft = ref('')
const isGeneratingRecipe = ref(false)
const generatedRecipes = ref([])
const selectedGeneratedIds = ref([])
const selectedInventoryIngredientIds = ref([])
const generatedRecipeSelectionCount = ref(0)
const isEstimatingMeal = ref(false)
const mealEstimate = ref(null)
const mealEstimateError = ref('')
const selectedRestockIds = ref([])
const foodUpdateVersions = new Map()
const shoppingListId = ref(null)
const expiryRecords = reactive([])
const environmentState = ref(null)
const environmentNotifications = reactive([])
let environmentPollTimer = null
let environmentRefreshInFlight = false
const USERNAME_PATTERN = /^[a-z0-9_]{3,32}$/

const foodDraft = reactive({})
const shopDraft = reactive({ id: null, name: '', group: '其他', amount: '1 份', note: '', status: 'pending' })
const localDate = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}
const newFood = reactive({ name: '', category: '蔬菜', amount: '', unit: '克', zone: '冷藏区', zoneId: null, date: localDate(), shelf: '7', reminder: '' })
const mealDraft = reactive({ name: '', meal: '晚餐', amount: '', unit: '克' })
const purchaseDraft = reactive({ amount: '', unit: '克', zone: '冷藏区', zoneId: null, shelf: '7' })

const zoneCount = ref(4)
const zoneRegistry = reactive([
  { id: 1, kind: 'chill', enabled: true, name: '冷藏区', targetTemperature: 4, targetHumidity: 70, color: '#5f94b5' },
  { id: 2, kind: 'fresh', enabled: true, name: '保鲜抽屉', targetTemperature: 2, targetHumidity: 85, color: '#6f9fc2' },
  { id: 3, kind: 'variable', enabled: true, name: '变温区', targetTemperature: 4, targetHumidity: 65, color: '#779fc9' },
  { id: 4, kind: 'freeze', enabled: true, name: '冷冻区', targetTemperature: -20, targetHumidity: 45, color: '#536d9a' },
  { id: 5, kind: 'chill', enabled: false, name: '扩展冷藏区', targetTemperature: 4, targetHumidity: 65, color: '#79a9c4' },
  { id: 6, kind: 'fresh', enabled: false, name: '扩展保鲜区', targetTemperature: 2, targetHumidity: 80, color: '#78b998' },
])
zoneRegistry.forEach(zone => Object.assign(zone, { temp: null, humidity: null, state: 'no_sensor', update: '正在同步', items: 0, sensors: [], sensorSlots: [], incidents: [], affectedBatchIds: [] }))

const zones = computed(() => zoneRegistry.filter(zone => zone.enabled))

const foods = reactive([])
const inventoryTransactions = reactive([])

function zoneById(id) { return zoneRegistry.find(zone => String(zone.id) === String(id)) }
function zoneByName(name) { return zoneRegistry.find(zone => zone.name === name) }
function zoneNameForId(id, fallback = '常温储物区') { return zoneById(id)?.name || fallback }
function zoneNameForFood(food) { return zoneNameForId(food.zoneId, food.zone) }
function normalizeZoneId(value, fallback = null) {
  if (value === null || value === undefined || value === '') return null
  const id = String(value)
  return zoneById(id) ? id : fallback
}

const recipes = reactive([])

const shopping = reactive([])

const restockCandidates = computed(() => {
  const candidates = new Map()
  foods.filter(food => food.lowStock).forEach(food => candidates.set(food.name, {
    id: `low-${food.id}`, name: food.name, group: ['蔬菜', '水果'].includes(food.category) ? '蔬果' : food.category,
    current: `${food.amount} ${food.unit}`, threshold: '后端低库存阈值', amount: `1 ${food.unit}`, note: '库存低于已配置阈值',
  }))
  recipes.flatMap(recipe => recipe.missing || []).forEach(name => {
    if (!candidates.has(name)) candidates.set(name, { id: `recipe-${name}`, name, group: '菜谱缺料', current: '库存缺少', threshold: '菜谱需要', amount: '1 份', note: '已加载菜谱缺少食材' })
  })
  return [...candidates.values()]
})

const preferences = reactive({ tastes: [], cuisine: [], allergies: [], dislikes: [], goal: '', target: 1650 })
const profile = reactive({ name: '', email: '', currentPassword: '', password: '', confirmPassword: '' })
profile.username = ''
profile.timezone = 'Asia/Shanghai'
const mealRecords = reactive([])
const dietAnalytics = ref(null)
const consumptionAnalytics = ref(null)
const histories = reactive({
  '饮食记录': [],
  '做菜记录': [],
  '采购记录': [],
})
const assistantMessages = reactive([])

const alerts = computed(() => foods.filter(food => food.days <= 3))
const warningZones = computed(() => zones.value.filter(zone => ['warning', 'stale'].includes(zone.state)))
const onlineSensorCount = computed(() => Number(environmentState.value?.onlineSensorCount || 0))
const lastEnvironmentSync = computed(() => relativeTime(environmentState.value?.lastSyncedAt))
const todaysMeals = computed(() => mealRecords.filter(meal => {
  if (!meal.mealAt) return false
  const at = new Date(meal.mealAt)
  const mealLocalDate = new Date(at.getTime() - at.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
  return mealLocalDate === localDate()
}))
const totalCalories = computed(() => Math.round(Number(dietAnalytics.value?.calories || 0)))
const caloriePercent = computed(() => Math.min(100, Math.round(totalCalories.value / preferences.target * 100)))
const pendingShoppingCount = computed(() => shopping.filter(item => item.status === 'pending').length)
const inventoryHistory = computed(() => inventoryTransactions.map(item => ({
  id: item.id,
  title: item.itemName,
  type: ['IN'].includes(item.type) ? '入库' : '使用',
  historyKey: item.type,
  meta: `${item.type} · ${relativeTime(item.createdAt)}`,
  note: `${Number(item.beforeQuantity)} → ${Number(item.afterQuantity)} ${displayUnit(item.unit)}`,
})))
const filteredFoods = computed(() => foods.filter(food => {
  const typeOkay = inventoryType.value === '全部' || (inventoryType.value === '食材' ? !['零食', '饮料'].includes(food.category) : ['零食', '饮料'].includes(food.category))
  const zoneOkay = inventoryFilter.value === '全部' || zoneNameForFood(food) === inventoryFilter.value
  return typeOkay && zoneOkay && (!search.value || food.name.includes(search.value))
}))
const allZoneNames = computed(() => zoneRegistry.map(zone => zone.name))
const selectableInventoryFoods = computed(() => foods.filter(food => Number(food.amount) > 0 && !['零食', '饮料'].includes(food.category)))
const selectedInventoryFoods = computed(() => selectableInventoryFoods.value.filter(food => selectedInventoryIngredientIds.value.includes(food.id)))
const expiryFoods = computed(() => foods.filter(food => expiryFilter.value === '全部' || food.status === expiryFilter.value).sort((a, b) => a.days - b.days))
const visibleRecipes = computed(() => recipes.filter(recipe => {
  if (recipeFilter.value === '可直接制作') return recipe.match === 100
  if (recipeFilter.value === '30 分钟内') return recipe.time <= 30
  if (recipeFilter.value === '高蛋白') return recipe.protein >= 25
  if (recipeFilter.value === '低于 400 千卡') return recipe.kcal < 400
  if (recipeFilter.value === '收藏') return recipe.collected
  return true
}))
const visibleShopping = computed(() => shopping.filter(item => shoppingGroup.value === '全部' || item.group === shoppingGroup.value))
const scaledIngredients = computed(() => {
  if (!cookingRecipe.value) return []
  const scale = Number(cookingWeight.value || 0) / cookingRecipe.value.base
  return cookingRecipe.value.ingredients.map(item => ({ ...item, display: ['盒', '杯', '个'].includes(item.unit) ? Math.max(1, Math.round(item.amount * scale)) : Math.round(item.amount * scale) }))
})
const scaledKcal = computed(() => cookingRecipe.value ? Math.round(cookingRecipe.value.kcal * Number(cookingWeight.value || 0) / cookingRecipe.value.base) : 0)
const assistantPageName = computed(() => ({ home: '首页', inventory: '库存', expiry: '保质期', recipes: '菜谱生成', synthesis: '美味合成', cooking: '做菜', diet: '饮食健康', shopping: '采购', settings: '设置', environment: '环境提醒' })[page.value])

const categoryIcons = Object.freeze({ 蔬菜: '🥬', 水果: '🍎', 水产: '🐟', 豆制品: '◻️', 零食: '🍪', 饮料: '🥛', 调味品: '🍶' })

const categoryCode = Object.freeze({ 蔬菜: 'VEGETABLE', 水果: 'FRUIT', 肉蛋: 'MEAT_EGG', 水产: 'SEAFOOD', 豆制品: 'BEAN', 零食: 'SNACK', 饮料: 'BEVERAGE', 调味品: 'CONDIMENT', 主食: 'OTHER', 蔬果: 'VEGETABLE', 菜谱缺料: 'OTHER', 其他: 'OTHER' })
const categoryLabel = Object.freeze({ VEGETABLE: '蔬菜', FRUIT: '水果', MEAT_EGG: '肉蛋', SEAFOOD: '水产', DAIRY: '饮料', BEAN: '豆制品', SNACK: '零食', BEVERAGE: '饮料', CONDIMENT: '调味品', OTHER: '其他' })
const unitCode = Object.freeze({ 克: 'g', 千克: 'kg', 个: 'piece', 盒: 'box', 瓶: 'bottle', 袋: 'bag', 杯: 'cup', 毫升: 'ml', 份: 'serving' })
const unitLabel = Object.freeze({ g: '克', kg: '千克', piece: '个', box: '盒', bottle: '瓶', bag: '袋', cup: '杯', ml: '毫升', serving: '份' })
function apiCategory(value) { return categoryCode[value] || value || 'OTHER' }
function displayCategory(value) { return categoryLabel[value] || value || '其他' }
function apiUnit(value) { return unitCode[value] || value || 'piece' }
function displayUnit(value) { return unitLabel[value] || value || '个' }
function inventoryStatus(assessment, remaining) {
  if (!Number(remaining)) return 'fresh'
  if (assessment?.safetyStatus === 'EXPIRED' || (assessment?.estimatedExpiryAt && new Date(assessment.estimatedExpiryAt) < new Date())) return 'urgent'
  if (assessment?.safetyStatus === 'EXPIRING_SOON') return 'soon'
  return 'fresh'
}
function inventoryDays(assessment) {
  if (!assessment?.estimatedExpiryAt) return null
  return Math.ceil((new Date(assessment.estimatedExpiryAt).getTime() - Date.now()) / 86400000)
}
function mapInventoryItem(item) {
  const batch = item.batches?.[0] || {}
  const assessment = batch.assessment || null
  const days = inventoryDays(assessment)
  return { id: item.id, batchId: batch.id, name: item.name, icon: foodIcon(displayCategory(item.category), item.name), category: displayCategory(item.category), zoneId: batch.zoneId || null, zone: zoneNameForId(batch.zoneId), amount: Number(batch.remainingQuantity || 0), unit: displayUnit(batch.unit || item.defaultUnit), apiUnit: batch.unit || item.defaultUnit, calories: null, days: days ?? 999, status: inventoryStatus(assessment, batch.remainingQuantity), percent: days == null ? 0 : Math.max(0, Math.min(100, Math.round(days / 30 * 100))), received: batch.storedAt ? String(batch.storedAt).slice(0, 10) : '', reminder: batch.remindAt ? String(batch.remindAt).slice(0, 10) : '', source: assessment?.explanation || '暂无保质期依据', lowStock: Boolean(item.lowStock), rawCategory: item.category }
}
function mapShoppingItem(item) {
  const amount = item.quantity == null ? '' : `${item.quantity} ${displayUnit(item.unit)}`
  const group = item.sourceType === 'RECIPE_MISSING' ? '菜谱缺料' : ({ VEGETABLE: '蔬果', FRUIT: '蔬果', CONDIMENT: '调味品' })[item.category] || '其他'
  return { id: item.id, name: item.name, note: item.note || '', amount, quantity: item.quantity, unit: item.unit, status: String(item.status || 'PENDING').toLowerCase(), group, sourceType: item.sourceType }
}

const recipeColors = ['#dfe9df', '#e8edd6', '#f1e1d6', '#dce7ef', '#eee3d5']
function mapRecipe(item, index = 0) {
  const components = item.ingredients || []
  const ingredients = components.filter(component => component.role !== 'SEASONING').map(component => ({
    id: component.id, name: component.name, amount: Number(component.quantity), quantity: Number(component.quantity),
    unit: displayUnit(component.unit), apiUnit: component.unit, role: component.role,
  }))
  const seasonings = components.filter(component => component.role === 'SEASONING').map(component => ({
    id: component.id, name: component.name, amount: Number(component.quantity), quantity: Number(component.quantity),
    unit: displayUnit(component.unit), apiUnit: component.unit, role: component.role,
  }))
  const available = new Set(foods.filter(food => Number(food.amount) > 0).map(food => food.name))
  const missing = item.missing?.length ? item.missing : ingredients.filter(component => !available.has(component.name)).map(component => component.name)
  const availability = item.availability === 'UNKNOWN' ? (missing.length ? 'MISSING_FEW' : 'DIRECT') : item.availability
  const match = ingredients.length ? Math.max(0, Math.round((ingredients.length - missing.length) / ingredients.length * 100)) : 100
  const primary = ingredients.find(component => component.apiUnit === 'g') || ingredients[0]
  return {
    id: item.id, name: item.name, desc: item.description || '', time: Number(item.cookMinutes || 0),
    kcal: Math.round(Number(item.perServing?.calories ?? item.total?.calories ?? 0)),
    protein: Math.round(Number(item.perServing?.protein ?? item.total?.protein ?? 0)),
    match, level: availability === 'DIRECT' ? '可直接制作' : missing.length ? `缺 ${missing.length} 项食材` : '库存待确认',
    color: recipeColors[index % recipeColors.length], art: '🍲', tags: [item.cuisine, item.taste, item.goal].filter(Boolean),
    favorite: Boolean(item.bookmarked), collected: Boolean(item.bookmarked), missing, base: Number(primary?.quantity || item.servings || 1),
    servings: Number(item.servings || 1), ingredients, seasonings, steps: item.steps || [], warnings: item.validationWarnings || [],
    source: item.source, sourceVersion: item.sourceVersion, attribution: item.attribution,
  }
}

function mapMeal(item) {
  const at = new Date(item.mealAt)
  return {
    id: item.id, time: at.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    name: item.mealType || '用餐', food: `${item.name} × ${item.servings} 份`,
    kcal: Math.round(Number(item.nutrition?.calories || 0)), icon: '🍽️', mealAt: item.mealAt,
  }
}

function icon(name, size = 20) {
  const paths = {
    box: '<path d="M4 7.5 12 3l8 4.5v9L12 21l-8-4.5z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9"/>', book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V4H6.5A2.5 2.5 0 0 0 4 6.5z"/><path d="M4 6.5v13M8 8h8"/>', spark: '<path d="m12 3 1.7 4.3L18 9l-4.3 1.7L12 15l-1.7-4.3L6 9l4.3-1.7z"/><path d="m18.5 15 .8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z"/>', bag: '<path d="M5 8h14l-1 13H6z"/><path d="M9 10V6a3 3 0 0 1 6 0v4"/>', plus: '<path d="M12 5v14M5 12h14"/>', minus: '<path d="M5 12h14"/>', search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>', bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>', check: '<path d="m5 12 4 4L19 6"/>', heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.7-7.5a5.5 5.5 0 0 0 1.1-8.9z"/>', close: '<path d="M6 6l12 12M18 6 6 18"/>', settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6v.2h.2v4H21a1.7 1.7 0 0 0-1.6 1z"/>', clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>', pan: '<path d="M3 13h13a5 5 0 0 1-10 0H3z"/><path d="M16 13h5M8 7c0-2 2-2 2-4M13 7c0-2 2-2 2-4"/>', trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3"/>', edit: '<path d="m4 20 4.3-1 10-10a2.1 2.1 0 0 0-3-3l-10 10z"/>', download: '<path d="M12 3v12M7 10l5 5 5-5M5 21h14"/>', thermometer: '<path d="M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0Z"/><path d="M12 9v7"/>', drop: '<path d="M12 3s5 5.3 5 10a5 5 0 0 1-10 0c0-4.7 5-10 5-10Z"/>', alert: '<path d="M12 3 2.8 20h18.4L12 3Z"/><path d="M12 9v4M12 17h.01"/>', arrow: '<path d="m14.5 5-7 7 7 7"/><path d="M8 12h12"/>', list: '<path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01"/>', logout: '<path d="M10 5H5v14h5M14 8l4 4-4 4M8 12h10"/>',
  }
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.spark}</svg>`
}

function notify(message) { toast.value = message; setTimeout(() => { if (toast.value === message) toast.value = '' }, 2600) }
function go(target) { page.value = target; search.value = ''; router.push({ name: 'app', params: { page: target } }) }
function temp(value) { return unit.value === 'C' ? Number(value).toFixed(1) : (Number(value) * 9 / 5 + 32).toFixed(1) }
function tempUnit() { return unit.value === 'C' ? '°C' : '°F' }
function dateAfter(days) { const date = new Date(); date.setDate(date.getDate() + Number(days)); return date.toISOString().slice(0, 10) }
function statusFor(days) { return Number(days) <= 1 ? 'urgent' : Number(days) <= 3 ? 'soon' : 'fresh' }
function foodIcon(category, name = '') { return category === '肉蛋' ? (name.includes('蛋') ? '🥚' : '🥩') : categoryIcons[category] || '🥕' }
function normalizeFoodAmount(value) { if (value === '' || value === null || value === undefined) return ''; const amount = Number(value); return Number.isFinite(amount) ? Math.max(0, amount) : '' }
function statusLabel(status) { return ({ pending: '待购买', purchased: '已购买', stored: '已入库' })[status] || '待购买' }
function zoneDeviation(zone) { return zone.temp - zone.targetTemperature }

async function refreshInventory() {
  const fridgeId = session.fridge?.id
  if (!fridgeId) return
  const [remote, expiry, transactions] = await Promise.all([
    api.listInventoryItems({ fridgeId }), api.getExpiry({ fridgeId }),
    api.listInventoryTransactions({ fridgeId, limit: 20 }),
  ])
  foods.splice(0, foods.length, ...remote.map(mapInventoryItem))
  expiryRecords.splice(0, expiryRecords.length, ...(expiry || []))
  inventoryTransactions.splice(0, inventoryTransactions.length, ...(transactions || []))
  zones.value.forEach(zone => { zone.items = foods.filter(food => String(food.zoneId) === String(zone.id)).length })
}

async function refreshShopping() {
  const lists = await api.getShoppingLists()
  const selected = lists.find(item => item.fridgeId === session.fridge?.id) || lists[0] || null
  shoppingListId.value = selected?.id || null
  shopping.splice(0, shopping.length, ...(selected?.items || []).map(mapShoppingItem))
  histories['采购记录'].splice(0, histories['采购记录'].length, ...shopping
    .filter(item => item.status === 'stored')
    .map(item => ({ id: item.id, title: item.name, meta: '采购入库 · 已完成', note: item.amount || '数量未记录' })))
}

async function refreshPreferences() {
  const remote = await api.getPreferences()
  preferences.tastes.splice(0, preferences.tastes.length, ...(remote.tastes || []))
  preferences.cuisine.splice(0, preferences.cuisine.length, ...(remote.cuisines || []))
  preferences.allergies.splice(0, preferences.allergies.length, ...(remote.allergies || []))
  preferences.dislikes.splice(0, preferences.dislikes.length, ...(remote.dislikes || []))
  preferences.goal = remote.dietaryGoal || ''
  preferences.target = Number(remote.calorieTarget || 1650)
  if (remote.temperatureUnit) unit.value = remote.temperatureUnit
}

async function refreshRecipes() {
  const remote = await api.listRecipes()
  recipes.splice(0, recipes.length, ...(remote || []).map(mapRecipe))
}

async function refreshMeals() {
  const remote = await api.listMeals()
  const mapped = (remote || []).map(mapMeal)
  mealRecords.splice(0, mealRecords.length, ...mapped)
  histories['饮食记录'].splice(0, histories['饮食记录'].length, ...mapped.map(item => ({
    id: item.id, title: item.food, meta: `${item.time} · ${item.name}`, note: `${item.kcal} 千卡`,
  })))
}

async function refreshDietAnalytics() {
  const [diet, consumption] = await Promise.all([
    api.getDietAnalytics(localDate()),
    api.getConsumptionAnalytics('week'),
  ])
  dietAnalytics.value = diet
  consumptionAnalytics.value = consumption
}

async function refreshAssistantBriefing() {
  const briefing = await api.getAssistantBriefing()
  if (assistantMessages.length) return
  const insight = briefing.insights?.[0]
  assistantMessages.push({
    id: `briefing-${Date.now()}`, role: 'assistant',
    text: insight ? `${insight.title}：${insight.body}` : `已同步当前库存与提醒。你有 ${briefing.unreadNotificationCount || 0} 条未读提醒，可以问我菜谱、保质期或采购建议。`,
  })
}

function relativeTime(value) {
  if (!value) return '尚无有效读数'
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000))
  return minutes < 1 ? '刚刚同步' : minutes < 60 ? `${minutes} 分钟前同步` : `${Math.floor(minutes / 60)} 小时前同步`
}
function zoneStateLabel(state) { return ({ normal: '正常', warning: '异常', stale: '数据陈旧', no_sensor: '无传感器', waiting_data: '等待首条数据' })[state] || '未知' }
function zoneStateReason(zone) {
  if (zone.state === 'stale') return '已超过 15 分钟没有有效读数，请检查设备连接。'
  if (zone.state === 'no_sensor') return '该分区只有待绑定槽位，系统不会产生陈旧告警。'
  if (zone.state === 'waiting_data') return '模拟探头已绑定，正在等待第一条模拟读数。'
  if (zone.state === 'warning') return zone.incidents?.[0]?.reason === 'OUT_OF_RANGE' ? '读数已连续偏离安全范围 15 分钟。' : '读数正在偏离安全范围。'
  return '当前温湿度在安全范围内。'
}
function applyEnvironment(remote, deviceGroups = []) {
  environmentState.value = remote
  const devicesByZone = new Map(deviceGroups.map(group => [String(group.zoneId), group]))
  ;(remote?.zones || []).forEach(source => {
    const target = zoneById(source.id)
    if (!target) return
    const temperature = source.metrics?.find(metric => metric.metric === 'TEMPERATURE')
    const humidity = source.metrics?.find(metric => metric.metric === 'HUMIDITY')
    const group = devicesByZone.get(String(source.id)) || { devices: [], slots: [] }
    const devices = group.devices || []
    const boundSensors = devices.flatMap(device => (device.sensors || []).map(sensor => ({
      id: sensor.id, deviceId: device.id, name: sensor.name || `${sensor.metric === 'TEMPERATURE' ? '温度' : '湿度'}传感器`,
      type: sensor.metric === 'TEMPERATURE' ? 'temperature' : 'humidity', value: sensor.lastValue == null ? '—' : Number(sensor.lastValue),
      unit: sensor.metric === 'TEMPERATURE' ? '°C' : '%', update: relativeTime(sensor.lastReceivedAt), quality: sensor.lastQuality || 'NO_DATA',
      lastReceivedAt: sensor.lastReceivedAt, deviceLastSeenAt: device.lastSeenAt, simulated: device.type === 'VIRTUAL',
    })))
    const slots = (group.slots || []).map(slot => {
      const device = devices.find(item => String(item.id) === String(slot.deviceId))
      return {
        ...slot, type: slot.metric === 'TEMPERATURE' ? 'temperature' : 'humidity',
        name: slot.name || `${slot.metric === 'TEMPERATURE' ? '温度' : '湿度'}槽位 ${slot.slotIndex}`,
        value: slot.lastValue == null ? '—' : Number(slot.lastValue), unit: slot.metric === 'TEMPERATURE' ? '°C' : '%',
        update: relativeTime(slot.lastReceivedAt), quality: slot.lastQuality || 'NO_DATA',
        deviceLastSeenAt: device?.lastSeenAt || null, simulated: device?.type === 'VIRTUAL',
      }
    })
    const waitingForFirstData = slots.some(slot => slot.bindingStatus === 'BOUND' && !slot.lastReceivedAt)
    Object.assign(target, {
      temp: temperature?.valueCelsius == null ? null : Number(temperature.valueCelsius),
      humidity: humidity?.displayValue == null ? null : Number(humidity.displayValue),
      state: waitingForFirstData && String(source.status || 'NO_SENSOR') === 'NO_SENSOR' ? 'waiting_data' : String(source.status || 'NO_SENSOR').toLowerCase(),
      update: relativeTime([temperature?.lastReceivedAt, humidity?.lastReceivedAt].filter(Boolean).sort().at(-1)),
      sensors: boundSensors,
      sensorSlots: slots,
      incidents: source.activeIncidents || [], affectedBatchIds: source.affectedBatchIds || [],
      onlineSensorCount: source.onlineSensorCount || 0, staleSensorCount: source.staleSensorCount || 0,
    })
  })
}
async function refreshEnvironment() {
  const fridgeId = session.fridge?.id
  if (!fridgeId || environmentRefreshInFlight) return
  environmentRefreshInFlight = true
  try {
    const remote = await api.getEnvironment(fridgeId)
    const deviceGroups = await Promise.all((remote.zones || []).map(async zone => ({
      zoneId: zone.id,
      devices: await api.getZoneDevices(zone.id),
      slots: await api.getZoneSensors(zone.id),
    })))
    const notifications = await api.getNotifications({ unreadOnly: false })
    applyEnvironment(remote, deviceGroups)
    environmentNotifications.splice(0, environmentNotifications.length, ...(notifications || []).filter(item => item.type === 'ENVIRONMENT_ALERT' && !item.dismissedAt))
  } catch (exception) {
    environmentState.value = null
    zones.value.forEach(zone => Object.assign(zone, { temp: null, humidity: null, state: 'no_sensor', update: '环境 API 暂不可用', sensors: [], sensorSlots: [], incidents: [] }))
  } finally { environmentRefreshInFlight = false }
}
async function markNotificationRead(item) {
  try { await api.updateNotification(item.id, { read: true }); await refreshEnvironment() } catch (exception) { notify(exception.message || '提醒状态更新失败') }
}

async function ensureShoppingList() {
  if (shoppingListId.value) return shoppingListId.value
  const created = await api.createShoppingList({ fridgeId: session.fridge?.id, name: '默认采购清单' })
  shoppingListId.value = created.id
  return created.id
}

function openVoiceDraftUpload() { voiceFileInput.value?.click() }
async function uploadVoiceDraft(event) {
  const audio = event.target.files?.[0]
  event.target.value = ''
  if (!audio || !session.fridge?.id) return
  voiceUploadInProgress.value = true
  isListening.value = true
  try {
    voiceDraft.value = await api.uploadVoiceDraft(session.fridge.id, audio)
    for (let attempt = 0; attempt < 30 && ['UPLOADED', 'TRANSCRIBING'].includes(voiceDraft.value.status); attempt += 1) {
      await new Promise(resolve => setTimeout(resolve, 1000))
      voiceDraft.value = await api.getVoiceDraft(voiceDraft.value.id)
    }
    if (voiceDraft.value.status !== 'READY') throw new Error(voiceDraft.value.failureReason || '语音草稿处理失败')
    const draft = voiceDraft.value.draft || {}
    Object.assign(newFood, {
      name: draft.name || '', category: displayCategory(draft.category), amount: draft.quantity || '',
      unit: displayUnit(draft.unit), zoneId: zones.value[0]?.id || null,
    })
    showAdd.value = true
    notify(`已识别：${voiceDraft.value.transcript || newFood.name}，请确认后入库`)
  } catch (exception) {
    voiceDraft.value = null
    notify(exception.message || '语音草稿创建失败')
  } finally { isListening.value = false; voiceUploadInProgress.value = false }
}

function inventoryCreatePayload() {
  const zoneId = normalizeZoneId(newFood.zoneId, zones.value[0]?.id || null)
  return {
    fridgeId: session.fridge?.id,
    name: newFood.name.trim(),
    category: apiCategory(newFood.category),
    defaultUnit: apiUnit(newFood.unit),
    batches: [{
      zoneId, storedAt: newFood.date ? new Date(`${newFood.date}T00:00:00`).toISOString() : null,
      shelfLifeDays: newFood.shelf === '' ? null : Number(newFood.shelf), quantity: Number(newFood.amount || 0),
      unit: apiUnit(newFood.unit), remindAt: newFood.reminder ? new Date(`${newFood.reminder}T00:00:00`).toISOString() : null,
    }],
  }
}

async function addFood() {
  if (!newFood.name) return notify('请填写食材名称')
  try {
    const payload = inventoryCreatePayload()
    if (voiceDraft.value?.status === 'READY') await api.confirmVoiceDraft(voiceDraft.value.id, payload)
    else await api.createInventoryItem(payload)
    await refreshInventory()
    Object.assign(newFood, { name: '', amount: '' })
    voiceDraft.value = null
    showAdd.value = false
    notify('食材已放入冰箱')
    return
  } catch (exception) {
    notify(exception.message || '食材入库失败，请稍后重试')
    return
  }
}

function editFood(food) { selectedFood.value = food; Object.assign(foodDraft, { ...food, zoneId: food.zoneId ?? zoneByName(food.zone)?.id ?? null }); showFoodEditor.value = true }
async function saveFood() {
  const food = selectedFood.value
  if (!food) return
  try {
    const zoneId = normalizeZoneId(foodDraft.zoneId, food.zoneId ?? null)
    await api.updateInventoryItem(food.id, { name: foodDraft.name.trim(), category: apiCategory(foodDraft.category), defaultUnit: apiUnit(foodDraft.unit || food.unit) })
    if (food.batchId) {
      await api.updateInventoryBatch(food.batchId, { zoneId })
      if (normalizeFoodAmount(foodDraft.amount) !== Number(food.amount)) await api.transactInventoryBatch(food.batchId, { type: 'ADJUST', quantity: normalizeFoodAmount(foodDraft.amount), unit: apiUnit(foodDraft.unit || food.unit) })
    }
    await refreshInventory()
    showFoodEditor.value = false
    notify(`${foodDraft.name} 已更新`)
    return
  } catch (exception) {
    notify(exception.message || '保存失败，请稍后重试')
    return
  }
}
async function deleteFood(food) {
  try { await api.deleteInventoryItem(food.id); await refreshInventory(); showFoodEditor.value = false; notify(`${food.name} 已移出库存`) }
  catch (exception) { notify(exception.message || '删除失败，请先处理活动批次') }
}
async function updateFoodReminder(food) {
  if (!food.batchId) return
  try {
    await api.updateInventoryBatch(food.batchId, {
      remindAt: food.reminder ? new Date(`${food.reminder}T00:00:00`).toISOString() : null,
    })
    await refreshInventory()
    notify('提醒日期已保存')
  } catch (exception) { notify(exception.message || '提醒日期保存失败') }
}
async function updateFoodAmount(food, value) {
  const previousAmount = food.amount
  const amount = normalizeFoodAmount(value)
  if (!food.batchId) return
  try {
    const updated = await api.transactInventoryBatch(food.batchId, { type: 'ADJUST', quantity: amount, unit: food.apiUnit || apiUnit(food.unit) })
    food.amount = Number(updated.remainingQuantity ?? amount)
    await refreshInventory()
    return
  } catch (exception) {
    food.amount = previousAmount
    notify(exception.message || '数量保存失败，请稍后重试')
    return
  }
}
function adjustFoodAmount(food, change) { return updateFoodAmount(food, Math.max(0, Number(food.amount || 0) + change)) }

async function generateRecipes(prompt = '', inventory = foods, selectedCount = 0) {
  if (isGeneratingRecipe.value) return false
  isGeneratingRecipe.value = true
  try {
    const result = await api.generateRecipeBatch({
      fridgeId: session.fridge?.id,
      inventory: inventory.filter(food => food.batchId).map(food => ({
        batchId: food.batchId, name: food.name, quantity: food.amount, unit: food.apiUnit || apiUnit(food.unit),
      })),
      prompt,
      count: 3,
    })
    generatedRecipes.value = (result.recipes || []).map(mapRecipe)
    selectedGeneratedIds.value = []
    generatedRecipeSelectionCount.value = selectedCount
    recipeFilter.value = '全部推荐'
    notify(`已从菜谱数据库筛选 ${generatedRecipes.value.length} 张候选菜谱`)
    return true
  } catch {
    notify('菜谱筛选失败，请检查服务后重试')
    return false
  } finally { isGeneratingRecipe.value = false }
}
function generateInventoryRecipe() { return generateRecipes() }
async function generateNamedRecipe() { if (!recipeNameDraft.value.trim()) return notify('请填写菜名'); await generateRecipes(recipeNameDraft.value.trim()); showRecipeNameGenerator.value = false; recipeNameDraft.value = '' }
function openInventoryRecipeSelector() { selectedInventoryIngredientIds.value = []; showInventoryRecipeSelector.value = true }
function closeInventoryRecipeSelector() { if (isGeneratingRecipe.value) return; selectedInventoryIngredientIds.value = []; showInventoryRecipeSelector.value = false }
function toggleInventoryRecipeIngredient(food) { const index = selectedInventoryIngredientIds.value.indexOf(food.id); index >= 0 ? selectedInventoryIngredientIds.value.splice(index, 1) : selectedInventoryIngredientIds.value.push(food.id) }
function selectAllInventoryRecipeIngredients() { selectedInventoryIngredientIds.value = selectableInventoryFoods.value.map(food => food.id) }
function clearInventoryRecipeIngredients() { selectedInventoryIngredientIds.value = [] }
async function generateSelectedInventoryRecipes() {
  const selected = selectedInventoryFoods.value
  if (!selected.length) return notify('请至少选择一项库存食材')
  const succeeded = await generateRecipes('', selected, selected.length)
  if (succeeded) closeInventoryRecipeSelector()
}
function toggleGeneratedRecipe(recipe) { const index = selectedGeneratedIds.value.indexOf(recipe.id); index >= 0 ? selectedGeneratedIds.value.splice(index, 1) : selectedGeneratedIds.value.push(recipe.id) }
async function saveGeneratedRecipes() {
  const selected = generatedRecipes.value.filter(recipe => selectedGeneratedIds.value.includes(recipe.id))
  if (!selected.length) return notify('请至少选择一张菜谱')
  try {
    await Promise.all(selected.map(recipe => api.setRecipeBookmark(recipe.id, true)))
    await refreshRecipes()
    generatedRecipes.value = []
    selectedGeneratedIds.value = []
    generatedRecipeSelectionCount.value = 0
    notify(`已收藏 ${selected.length} 张菜谱`)
  } catch (exception) { notify(exception.message || '菜谱收藏失败') }
}

function discardGeneratedRecipes() {
  generatedRecipes.value = []
  selectedGeneratedIds.value = []
  generatedRecipeSelectionCount.value = 0
  notify('已放弃本次候选菜谱')
}
function openCooking(recipe) { cookingRecipe.value = recipe; cookingWeight.value = recipe.base; activeCookingStep.value = 0; go('cooking') }
async function completeCooking() {
  if (!cookingRecipe.value) return
  if (cookingRecipe.value.missing.length) return notify(`仍缺少：${cookingRecipe.value.missing.join('、')}`)
  const scale = Number(cookingWeight.value || 0) / Number(cookingRecipe.value.base || 1)
  const consumptions = cookingRecipe.value.ingredients.map(ingredient => {
    const food = foods.find(item => item.name === ingredient.name && item.batchId)
    if (!food) return null
    return { batchId: food.batchId, quantity: Number((ingredient.quantity * scale).toFixed(3)), unit: ingredient.apiUnit }
  }).filter(Boolean)
  if (!consumptions.length) return notify('没有可扣减的库存批次')
  try {
    await api.cookRecipe(cookingRecipe.value.id, {
      servings: Number((cookingRecipe.value.servings * scale).toFixed(2)), consumptions,
      recordMeal: true, mealAt: new Date().toISOString(),
    })
    await Promise.all([refreshInventory(), refreshMeals(), refreshDietAnalytics(), refreshRecipes()])
    notify('烹饪完成，库存流水和饮食记录已同步')
  } catch (exception) { notify(exception.message || '烹饪确认失败，请检查库存数量') }
}

async function toggleRecipeBookmark(recipe) {
  const next = !recipe.collected
  try {
    await api.setRecipeBookmark(recipe.id, next)
    recipe.collected = next
    recipe.favorite = next
    notify(next ? `已收藏「${recipe.name}」` : `已取消收藏「${recipe.name}」`)
  } catch (exception) { notify(exception.message || '收藏状态更新失败') }
}

async function removeRecipeBookmark(recipe) {
  if (!recipe.collected) return notify('公共菜谱不能删除，可通过收藏筛选管理个人菜谱')
  await toggleRecipeBookmark(recipe)
  if (cookingRecipe.value?.id === recipe.id) cookingRecipe.value = null
}

async function addMissing(recipe) {
  try {
    const listId = await ensureShoppingList()
    for (const name of recipe.missing) {
      if (!shopping.some(item => item.name === name)) await api.createShoppingItem(listId, { name, category: 'OTHER', quantity: 1, unit: 'serving', note: `${recipe.name} 缺少`, sourceType: 'RECIPE_MISSING' })
    }
    await refreshShopping()
    notify('缺少食材已加入购物清单')
  } catch (exception) { notify(exception.message || '缺料保存失败') }
}

let mealEstimateTimer
let mealEstimateVersion = 0
function clearMealEstimate() { clearTimeout(mealEstimateTimer); mealEstimate.value = null; mealEstimateError.value = ''; isEstimatingMeal.value = false }
async function estimateMealNutrition() {
  const name = mealDraft.name.trim()
  if (!name) return clearMealEstimate()
  const version = ++mealEstimateVersion
  isEstimatingMeal.value = true
  mealEstimateError.value = ''
  try { const result = await api.estimateMealNutrition({ dishName: name, amount: mealDraft.amount || null, unit: apiUnit(mealDraft.unit) }); if (version === mealEstimateVersion) mealEstimate.value = result } catch { if (version === mealEstimateVersion) mealEstimateError.value = '热量估算失败，请重试' } finally { if (version === mealEstimateVersion) isEstimatingMeal.value = false }
}
watch(() => [mealDraft.name, mealDraft.amount, mealDraft.unit], () => {
  clearTimeout(mealEstimateTimer)
  mealEstimate.value = null
  mealEstimateError.value = ''
  if (!mealDraft.name.trim()) return
  mealEstimateTimer = setTimeout(estimateMealNutrition, 420)
})
async function recordMeal() {
  if (!mealDraft.name || !mealEstimate.value || isEstimatingMeal.value) return notify('请等待营养估算完成')
  try {
    await api.createMeal({
      mealAt: new Date().toISOString(), mealType: mealDraft.meal, name: mealDraft.name.trim(), servings: 1,
      calories: mealEstimate.value.calories, protein: mealEstimate.value.protein,
      fat: mealEstimate.value.fat, carbs: mealEstimate.value.carbs,
      estimated: mealEstimate.value.estimated, nutritionSource: mealEstimate.value.source,
    })
    await Promise.all([refreshMeals(), refreshDietAnalytics()])
    Object.assign(mealDraft, { name: '', amount: '', unit: '份' })
    clearMealEstimate()
    showMealEditor.value = false
    notify('饮食记录已同步')
  } catch (exception) { notify(exception.message || '饮食记录保存失败') }
}

async function deleteMealRecord(meal) {
  try {
    await api.deleteMeal(meal.id)
    await Promise.all([refreshMeals(), refreshDietAnalytics()])
    notify('饮食记录已删除')
  } catch (exception) { notify(exception.message || '饮食记录删除失败') }
}

function openShoppingEditor(item = null) {
  Object.assign(shopDraft, item ? { ...item } : { id: null, name: '', group: '其他', amount: '1 份', note: '手动添加', status: 'pending' })
  showShoppingEditor.value = true
}
function parseShoppingAmount(value) {
  const text = String(value || '')
  const match = text.match(/[\d.]+/)
  const unitText = text.replace(/[\d.\s]/g, '')
  return { quantity: match ? Number(match[0]) : null, unit: apiUnit(unitText || '个') }
}
async function saveShoppingItem() {
  if (!shopDraft.name.trim()) return notify('请填写采购项目名称')
  try {
    const parsed = parseShoppingAmount(shopDraft.amount)
    const payload = { name: shopDraft.name.trim(), category: apiCategory(shopDraft.group), quantity: parsed.quantity, unit: parsed.unit, note: shopDraft.note }
    const listId = await ensureShoppingList()
    if (shopDraft.id) {
      const updatePayload = shopDraft.status === 'stored' ? payload : { ...payload, status: String(shopDraft.status || 'pending').toUpperCase() }
      await api.updateShoppingItem(shopDraft.id, updatePayload)
    } else {
      const created = await api.createShoppingItem(listId, { ...payload, sourceType: 'MANUAL' })
      if (shopDraft.status === 'purchased') await api.updateShoppingItem(created.id, { status: 'PURCHASED' })
    }
    await refreshShopping()
    showShoppingEditor.value = false
    notify(shopDraft.id ? '采购项目已更新' : '采购项目已添加')
    return
  } catch (exception) {
    notify(exception.message || '采购项目保存失败，请稍后重试')
    return
  }
}
async function updateShoppingStatus(item, status) {
  if (status === 'stored' && item.status !== 'stored') return startPurchase(item)
  try { await api.updateShoppingItem(item.id, { status: String(status).toUpperCase() }); await refreshShopping(); notify(`${item.name} 已标记为${statusLabel(status)}`) }
  catch (exception) { notify(exception.message || '采购状态保存失败') }
}
function startPurchase(item) { selectedShopItem.value = item; const parsed = parseShoppingAmount(item.amount); Object.assign(purchaseDraft, { amount: parsed.quantity || '', unit: displayUnit(parsed.unit), zoneId: zones.value[0]?.id || null, zone: zoneNameForId(zones.value[0]?.id), shelf: '7' }); showPurchase.value = true }
async function confirmPurchase() {
  const item = selectedShopItem.value
  if (!item) return
  try {
    await api.storeShoppingItem(item.id, { fridgeId: session.fridge?.id, zoneId: normalizeZoneId(purchaseDraft.zoneId, null), quantity: Number(purchaseDraft.amount), unit: apiUnit(purchaseDraft.unit), shelfLifeDays: Number(purchaseDraft.shelf || 0) || null })
    await Promise.all([refreshInventory(), refreshShopping()])
    showPurchase.value = false
    notify(`${item.name} 已入库`)
  } catch (exception) { notify(exception.message || '采购入库失败，请重试') }
}
function toggleRestock(id) { const index = selectedRestockIds.value.indexOf(id); index >= 0 ? selectedRestockIds.value.splice(index, 1) : selectedRestockIds.value.push(id) }
async function addSelectedRestock() {
  const selected = restockCandidates.value.filter(item => selectedRestockIds.value.includes(item.id))
  if (!selected.length) return notify('请勾选要加入的补货项')
  try {
    const listId = await ensureShoppingList()
    for (const item of selected) {
      if (!shopping.some(shopItem => shopItem.name === item.name)) {
        const parsed = parseShoppingAmount(item.amount)
        await api.createShoppingItem(listId, { name: item.name, category: apiCategory(item.group), quantity: parsed.quantity, unit: parsed.unit, note: item.note, sourceType: 'LOW_STOCK' })
      }
    }
    await refreshShopping()
    selectedRestockIds.value = []
    notify(`已加入 ${selected.length} 项补货建议`)
  } catch (exception) { notify(exception.message || '补货项保存失败') }
}
async function removeShoppingItem(item) { try { await api.deleteShoppingItem(item.id); await refreshShopping(); notify('采购项目已删除') } catch (exception) { notify(exception.message || '采购项目删除失败') } }
function exportShopping() { const text = `鲜知购物清单\n${shopping.filter(item => item.status !== 'stored').map(item => `- ${item.group}｜${item.name} ${item.amount}（${statusLabel(item.status)}）`).join('\n')}`; const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' })); const link = document.createElement('a'); link.href = url; link.download = '鲜知购物清单.txt'; link.click(); URL.revokeObjectURL(url); notify('购物清单已导出') }

function updateZoneName(zone, value) {
  const next = String(value || '').trim()
  if (!next) return notify('分区名称不能为空')
  if (next.length > 12) return notify('分区名称最多 12 个字符')
  if (zoneRegistry.some(item => item.id !== zone.id && item.name === next)) return notify('分区名称不能重复')
  zone.name = next
  foods.forEach(food => { if (food.zoneId === zone.id) food.zone = next })
}
function setZoneCount(value, silent = false) {
  const next = clampZoneCount(value)
  zoneCount.value = next
  zoneRegistry.forEach((zone, index) => { zone.enabled = index < next })
  if (inventoryFilter.value !== '全部' && !zoneRegistry.some(zone => zone.name === inventoryFilter.value)) inventoryFilter.value = '全部'
  if (!silent) notify(`冰箱已切换为 ${next} 个分区`)
}
function toggleTag(collection, value) { const index = collection.indexOf(value); index >= 0 ? collection.splice(index, 1) : collection.push(value) }
async function saveProfile() {
  const username = profile.username.trim().toLowerCase()
  if (!USERNAME_PATTERN.test(username)) return notify('用户名只能包含 3-32 位小写字母、数字和下划线')
  if (profile.password && (profile.password.length < 8 || profile.password !== profile.confirmPassword)) return notify('新密码至少 8 位，且两次输入一致')
  if (profile.password && !profile.currentPassword) return notify('请输入原密码')
  try {
    const updatedUser = await api.updateMe({
      username,
      displayName: profile.name.trim(),
      timezone: profile.timezone,
      temperatureUnit: unit.value,
    })
    session.user = updatedUser
    Object.assign(profile, { username: updatedUser.username, name: updatedUser.displayName, email: updatedUser.email, timezone: updatedUser.timezone })
    if (profile.password) await api.changePassword({ currentPassword: profile.currentPassword, newPassword: profile.password })
    await api.updatePreferences({
      tastes: [...preferences.tastes], cuisines: [...preferences.cuisine], allergies: [...preferences.allergies],
      dislikes: [...preferences.dislikes], dietaryGoal: preferences.goal || null,
      calorieTarget: Number(preferences.target), temperatureUnit: unit.value,
    })
    const updatedZones = await Promise.all(zones.value.map(zone => api.updateZone(zone.id, {
      name: zone.name,
      targetTemperatureC: Number(zone.targetTemperature),
      targetHumidityPct: Number(zone.targetHumidity),
    })))
    updatedZones.forEach(updated => {
      const zone = zoneById(updated.id)
      if (zone) Object.assign(zone, {
        name: updated.name,
        targetTemperature: Number(updated.targetTemperatureC),
        targetHumidity: Number(updated.targetHumidityPct),
      })
    })
    const fridges = await api.getFridges()
    const refreshedFridge = fridges.find(fridge => String(fridge.id) === String(session.fridge?.id)) || fridges[0]
    if (refreshedFridge) setFridge(refreshedFridge)
    profile.currentPassword = ''
    profile.password = ''
    profile.confirmPassword = ''
    notify('账户、偏好与分区目标已写入数据库')
  } catch (exception) {
    if (exception.code === 'USERNAME_ALREADY_REGISTERED') notify('该用户名已被占用，请更换后重试')
    else if (exception.code === 'INVALID_CURRENT_PASSWORD') notify('原密码不正确')
    else notify(exception.message || '设置保存失败，请稍后重试')
  }
}
function navigateToZone(zone) { inventoryFilter.value = zone.name; inventoryType.value = '全部'; go('inventory') }

function applyFridgeSummary(fridge) {
  if (!fridge?.zones?.length) return
  fridge.zones.forEach((source, index) => {
    const target = zoneRegistry[index]
    if (!target) return
    const temperature = Number(source.targetTemperatureC)
    const humidity = Number(source.targetHumidityPct)
    Object.assign(target, {
      id: source.id,
      apiId: source.id,
      kind: String(source.kind).toLowerCase(),
      enabled: true,
      name: source.name,
      temp: null,
      humidity: null,
      targetTemperature: temperature,
      targetHumidity: humidity,
      state: 'no_sensor',
      update: (source.temperatureSensorCount || source.humiditySensorCount) ? '传感器待绑定' : '未接入传感器',
      items: 0,
      sensors: [],
    })
  })
  if (!zoneById(newFood.zoneId)) newFood.zoneId = fridge.zones[0]?.id || null
  if (!zoneById(purchaseDraft.zoneId)) purchaseDraft.zoneId = fridge.zones[0]?.id || null
  zoneCount.value = fridge.zones.length
  setZoneCount(fridge.zones.length, true)
}

async function loadFridgeSummary() {
  if (!isAppRoute.value) return
  recipes.splice(0, recipes.length)
  mealRecords.splice(0, mealRecords.length)
  histories['饮食记录'].splice(0, histories['饮食记录'].length)
  assistantMessages.splice(0, assistantMessages.length)
  try {
    let fridge = session.fridge
    if (!fridge) {
      const fridges = await api.getFridges()
      fridge = fridges?.[0] || null
      if (fridge) setFridge(fridge)
    }
    if (fridge) applyFridgeSummary(fridge)
    if (session.user) {
      Object.assign(profile, {
        name: session.user.displayName,
        username: session.user.username,
        email: session.user.email,
        timezone: session.user.timezone,
      })
      unit.value = session.user.temperatureUnit || 'C'
    }
    await Promise.all([refreshInventory(), refreshShopping(), refreshEnvironment(), refreshPreferences()])
    await Promise.all([refreshRecipes(), refreshMeals(), refreshDietAnalytics(), refreshAssistantBriefing()])
  } catch { notify('冰箱配置同步失败，请刷新重试') }
}

async function performLogout() {
  await logout()
  await router.replace('/login')
}

watch(zoneCount, value => setZoneCount(value, true), { immediate: true })
watch(isListening, active => {
  if (active && !voiceUploadInProgress.value) {
    isListening.value = false
    openVoiceDraftUpload()
  }
})
watch(() => route.params.page, value => { if (route.name === 'app') page.value = String(value || 'home') })
watch(isAppRoute, active => { if (active) loadFridgeSummary() }, { immediate: true })

onMounted(() => {
  environmentPollTimer = window.setInterval(() => {
    if (isAppRoute.value && !document.hidden) refreshEnvironment()
  }, 5000)
})
onBeforeUnmount(() => {
  if (environmentPollTimer) window.clearInterval(environmentPollTimer)
  clearTimeout(mealEstimateTimer)
})

async function sendAssistantMessage(preset = '') {
  const text = (preset || assistantInput.value).trim()
  if (!text) return
  assistantMessages.push({ id: `user-${Date.now()}`, role: 'user', text })
  assistantInput.value = ''
  try {
    if (!assistantConversationId.value) {
      const conversation = await api.createAssistantConversation('冰箱助手')
      assistantConversationId.value = conversation.id
    }
    const response = await api.sendAssistantMessage(assistantConversationId.value, {
      content: text, page: page.value, selection: {},
    })
    assistantMessages.push({ id: response.message.id, role: 'assistant', text: response.message.content })
    assistantProposals.splice(0, assistantProposals.length, ...(response.actionProposals || []))
  } catch (exception) {
    assistantMessages.push({ id: `error-${Date.now()}`, role: 'assistant', text: exception.message || '助手暂时不可用，请稍后重试。' })
  }
}
</script>

<template>
  <RouterView v-if="!isAppRoute" />
  <div v-else class="app-shell" :class="{ 'home-shell': page === 'home' }">
    <input ref="voiceFileInput" type="file" accept="audio/*" hidden @change="uploadVoiceDraft" />
    <div class="phase-status" aria-label="数据状态">
      <span><i></i>冰箱配置已同步</span>
      <em>实时 API 数据</em>
      <strong>{{ session.user?.displayName }}</strong>
      <button title="退出登录" aria-label="退出登录" @click="performLogout"><span v-html="icon('logout', 17)"></span></button>
    </div>
    <main :class="{ 'home-main': page === 'home' }">
      <div class="content" :class="{ 'home-content': page === 'home' }">
        <button v-if="page !== 'home'" class="page-home-link" title="返回冰箱首页" aria-label="返回冰箱首页" @click="go('home')"><span v-html="icon('arrow', 18)"></span><span>返回首页</span></button>

        <template v-if="page === 'home'">
          <section class="home-console" aria-label="冰箱总览">
            <div class="orbit-status orbit-panel orbit-status-end"><div><span><i class="online-dot"></i>{{ onlineSensorCount }} 个传感器在线</span><span>{{ lastEnvironmentSync }}</span><span class="unit-switch home-unit"><button :class="{ on: unit === 'C' }" @click="unit = 'C'">℃</button><button :class="{ on: unit === 'F' }" @click="unit = 'F'">℉</button></span></div></div>
            <div class="orbit-left orbit-panel" :class="{ 'has-warning': warningZones.length }"><div class="home-task-grid"><button class="home-task fridge-control tone-chill" @click="go('inventory')"><span v-html="icon('box', 21)"></span><b>库存</b><small>{{ foods.length }} 项在库</small></button><button class="home-task fridge-control tone-freeze" @click="go('expiry')"><span v-html="icon('clock', 21)"></span><b>保质期</b><small>{{ alerts.length }} 项待处理</small></button><button class="home-task fridge-control tone-fresh" @click="go('recipes')"><span v-html="icon('book', 21)"></span><b>菜谱</b><small>{{ recipes.length }} 道已保存</small></button><button v-if="!warningZones.length" class="home-task fridge-control tone-variable" @click="go('environment')"><span v-html="icon('thermometer', 21)"></span><b>环境状态</b><small>温度正常</small></button></div><button v-if="warningZones.length" class="home-warning" @click="go('environment')"><span v-html="icon('alert', 18)"></span><span><small>分区温度异常</small><b>{{ warningZones.length }} 个分区需要检查</b><em>查看全部环境提醒</em></span></button></div>
            <article class="center-fridge" :class="`fridge-count-${zones.length}`" aria-label="冰箱分区状态"><FridgeModel :zones="zones" :foods="foods" @zone-navigate="navigateToZone" /></article>
            <div class="orbit-right-rail orbit-panel"><div class="home-task-grid"><button class="home-task fridge-control tone-variable" @click="go('diet')"><span v-html="icon('spark', 21)"></span><b>饮食健康</b><small>{{ totalCalories }} / {{ preferences.target }} 千卡</small></button><button class="home-task fridge-control tone-fresh" @click="go('shopping')"><span v-html="icon('bag', 21)"></span><b>采购</b><small>{{ pendingShoppingCount }} 项待购买</small></button><button class="home-task fridge-control tone-smart" @click="go('settings')"><span v-html="icon('settings', 21)"></span><b>设置</b><small>{{ zones.length }} 个冰箱分区</small></button><button class="home-task fridge-control tone-synthesis" @click="go('synthesis')"><span v-html="icon('pan', 21)"></span><b>美味合成</b><small>拖食材配菜谱</small></button></div></div>
          </section>
        </template>

        <template v-else-if="page === 'environment'">
          <section class="page-intro"><div><p class="eyebrow">实时监测与异常聚合</p><h1>环境提醒</h1><p>温度异常的分区会统一显示，先检查门封、制冷和传感器位置。</p></div><button class="secondary-btn" @click="go('settings')"><span v-html="icon('settings', 18)"></span>管理分区目标</button></section>
          <section class="environment-summary"><article><span v-html="icon('alert', 23)"></span><div><strong>{{ warningZones.length }}</strong><small>个分区需要关注</small></div></article><p>状态区分正常、异常、数据陈旧和无传感器。环境恢复后，已累计的保质期风险不会减少。</p></section>
          <section class="environment-list"><article v-for="zone in zones" :key="zone.id" :class="`environment-${zone.state}`"><div class="environment-zone-icon" :style="{ background: zone.color }"><span v-html="icon(zone.state === 'normal' ? 'check' : 'thermometer', 21)"></span></div><div><p class="eyebrow">{{ zoneStateLabel(zone.state) }} · {{ zone.update }}</p><h2>{{ zone.name }}</h2><p v-if="zone.temp != null || zone.humidity != null">温度 {{ zone.temp == null ? '—' : `${temp(zone.temp)} ${tempUnit()}` }}，湿度 {{ zone.humidity == null ? '—' : `${zone.humidity.toFixed(1)} %` }}。</p><p>{{ zoneStateReason(zone) }}</p><small>在线 {{ zone.onlineSensorCount || 0 }} / 已绑定 {{ zone.sensors.length }} · 影响 {{ zone.affectedBatchIds?.length || 0 }} 个库存批次</small></div><button class="secondary-btn" @click="navigateToZone(zone)">查看库存</button></article></section>
          <section v-if="environmentNotifications.length" class="module-history compact-history"><div class="section-head"><div><p class="eyebrow">站内提醒</p><h2>环境与传感器事件</h2></div></div><article v-for="item in environmentNotifications" :key="item.id"><span v-html="icon('bell', 18)"></span><div><b>{{ item.title }}</b><small>{{ item.resolvedAt ? '已解决' : '处理中' }} · {{ relativeTime(item.createdAt) }}</small></div><em>{{ item.body }}</em><button v-if="!item.readAt" title="标记已读" @click="markNotificationRead(item)"><span v-html="icon('check', 15)"></span></button></article></section>
        </template>

        <template v-else-if="page === 'inventory'">
          <section class="page-intro"><div><p class="eyebrow">{{ foods.length }} 项库存</p><h1>冰箱库存</h1><p>按分区、类别和新鲜程度整理每一份食材。</p></div><div class="intro-actions"><button class="secondary-btn" :class="{ listening: isListening }" @click="isListening = !isListening">语音添加</button><button class="primary-btn" @click="showAdd = true"><span v-html="icon('plus', 18)"></span>添加食材</button></div></section>
          <div class="tabs"><button v-for="type in ['全部', '食材', '零食饮料']" :key="type" :class="{ active: inventoryType === type }" @click="inventoryType = type">{{ type }}</button></div><section class="inventory-toolbar"><div class="filter-pills"><button v-for="zone in ['全部', ...allZoneNames, '常温储物区']" :key="zone" :class="{ active: inventoryFilter === zone }" @click="inventoryFilter = zone">{{ zone }}</button></div><label class="mini-search"><span v-html="icon('search', 16)"></span><input v-model="search" placeholder="搜索库存" /></label></section>
          <section class="inventory-table"><div class="table-head"><span>食材</span><span>存放位置</span><span>剩余数量</span><span>新鲜度 / 建议期限</span><span>热量</span><span></span></div><div v-for="food in filteredFoods" :key="food.id" class="food-row"><span class="food-name"><i>{{ food.icon }}</i><span><b>{{ food.name }}</b><small>{{ food.category }}</small></span></span><span><b>{{ zoneNameForFood(food) }}</b><small>{{ food.zoneId && !zoneById(food.zoneId)?.enabled ? '已隐藏 · ' : '' }}{{ food.source }}</small></span><span class="quantity-cell"><span class="quantity-stepper"><button :disabled="Number(food.amount || 0) <= 0" @click="adjustFoodAmount(food, -1)"><span v-html="icon('minus', 14)"></span></button><input :value="food.amount" type="number" min="0" @change="updateFoodAmount(food, $event.target.value)" /><button @click="adjustFoodAmount(food, 1)"><span v-html="icon('plus', 14)"></span></button></span><small>{{ food.unit }}</small></span><span class="fresh-cell"><i><em :class="food.status" :style="{ width: food.percent + '%' }"></em></i><small :class="food.status">{{ food.days }} 天后建议食用完</small></span><span><b>—</b><small>暂无营养数据库</small></span><span class="row-actions"><button title="编辑食材" @click="editFood(food)"><span v-html="icon('edit', 16)"></span></button></span></div></section>
          <section class="module-history compact-history inventory-history"><div class="section-head"><div><p class="eyebrow">库存动态</p><h2>最近入库与使用</h2></div></div><article v-for="item in inventoryHistory.slice(0, 5)" :key="`${item.historyKey}-${item.id}`"><span v-html="icon(item.type === '入库' ? 'box' : 'pan', 18)"></span><div><b>{{ item.title }}</b><small>{{ item.meta }}</small></div><em>{{ item.note }}</em></article></section>
        </template>

        <template v-else-if="page === 'expiry'">
          <section class="page-intro"><div><p class="eyebrow">AI 保鲜管理</p><h1>保质期提醒</h1><p>按紧急程度排序；环境异常时会缩短建议食用期限。</p></div><button class="primary-btn" @click="go('inventory')">查看全部库存</button></section><section class="expiry-summary"><article><span class="status-dot urgent"></span><strong>{{ foods.filter(food => food.status === 'urgent').length }}</strong><small>今天优先处理</small></article><article><span class="status-dot soon"></span><strong>{{ foods.filter(food => food.status === 'soon').length }}</strong><small>3 天内到期</small></article><article><span class="status-dot fresh"></span><strong>{{ foods.filter(food => food.status === 'fresh').length }}</strong><small>保存良好</small></article></section><div class="filter-pills expiry-filters"><button v-for="filter in [['全部', '全部'], ['紧急', 'urgent'], ['即将到期', 'soon'], ['良好', 'fresh']]" :key="filter[1]" :class="{ active: expiryFilter === filter[1] }" @click="expiryFilter = filter[1]">{{ filter[0] }}</button></div><section class="expiry-list"><article v-for="food in expiryFoods" :key="food.id" :class="food.status"><i>{{ food.icon }}</i><div><b>{{ food.name }}</b><small>入库 {{ food.received }} · 预计到期 {{ dateAfter(food.days) }} · {{ zoneNameForFood(food) }}</small><em>{{ food.source }}</em></div><strong>{{ food.days === 0 ? '今天处理' : `${food.days} 天内` }}</strong><label>提醒日期<input v-model="food.reminder" type="date" @change="updateFoodReminder(food)" /></label><button title="删除食材" @click="deleteFood(food)"><span v-html="icon('trash', 17)"></span></button></article></section>
        </template>

        <template v-else-if="page === 'synthesis'">
          <DeliciousSynthesis :foods="foods" :preferences="preferences" @start-cooking="openCooking" />
        </template>

        <template v-else-if="page === 'recipes'">
          <section class="page-intro"><div><p class="eyebrow">菜谱数据库 · 库存与偏好匹配</p><h1>菜谱筛选</h1><p>已避开 {{ preferences.allergies.concat(preferences.dislikes).join('、') }}，并优先匹配{{ preferences.goal }}目标。</p></div><div class="intro-actions"><button class="secondary-btn" @click="openInventoryRecipeSelector"><span v-html="icon('list', 18)"></span>选择库存食材</button><button class="secondary-btn" @click="showRecipeNameGenerator = true"><span v-html="icon('edit', 18)"></span>按菜名筛选</button><button class="primary-btn" :disabled="isGeneratingRecipe" @click="generateInventoryRecipe"><span v-html="icon('spark', 18)"></span>{{ isGeneratingRecipe ? '筛选中' : '筛选 3 张菜谱' }}</button></div></section>
          <section v-if="generatedRecipes.length" class="generated-recipes"><div class="generated-recipes-head"><div><p class="eyebrow">数据库候选结果</p><h2>{{ generatedRecipeSelectionCount ? `按已选 ${generatedRecipeSelectionCount} 项库存食材筛选` : '选择想收藏的菜谱' }}</h2><p>可多选；保存后会写入个人收藏。</p></div><div class="generated-recipe-actions"><button class="secondary-btn" @click="discardGeneratedRecipes">取消本次筛选</button><button class="primary-btn" :disabled="!selectedGeneratedIds.length" @click="saveGeneratedRecipes">收藏已选 {{ selectedGeneratedIds.length ? `(${selectedGeneratedIds.length})` : '' }}</button></div></div><div class="recipe-gallery candidate-gallery"><article v-for="recipe in generatedRecipes" :key="recipe.id" class="recipe-card candidate-card" :class="{ selected: selectedGeneratedIds.includes(recipe.id) }"><button class="candidate-check" :aria-pressed="selectedGeneratedIds.includes(recipe.id)" :title="selectedGeneratedIds.includes(recipe.id) ? '取消选择' : '选择菜谱'" @click="toggleGeneratedRecipe(recipe)"><span v-html="icon('check', 16)"></span></button><div class="recipe-art" :style="{ background: recipe.color }"><span>{{ recipe.art }}</span><b>{{ recipe.match }}% 匹配</b></div><div class="recipe-info"><small>{{ recipe.level }}</small><h3>{{ recipe.name }}</h3><p>{{ recipe.desc }}</p><div class="recipe-metrics"><span>⏱ {{ recipe.time }} 分钟</span><span>≈ {{ recipe.kcal }} 千卡</span></div><div class="recipe-ingredients"><small>所需食材</small><span v-for="ingredient in recipe.ingredients" :key="ingredient.name" :class="{ missing: recipe.missing.includes(ingredient.name) }">{{ ingredient.name }} {{ ingredient.amount }}{{ ingredient.unit }}</span></div><div class="recipe-card-actions"><button @click="openCooking(recipe)">查看做法</button><button v-if="recipe.missing.length" @click="addMissing(recipe)">加入缺料</button></div></div></article></div></section>
          <div class="filter-pills recipe-filters"><button v-for="filter in ['全部推荐', '可直接制作', '30 分钟内', '高蛋白', '低于 400 千卡', '收藏']" :key="filter" :class="{ active: recipeFilter === filter }" @click="recipeFilter = filter">{{ filter }}</button></div><section v-if="visibleRecipes.length" class="recipe-gallery"><article v-for="recipe in visibleRecipes" :key="recipe.id" class="recipe-card"><div class="recipe-art" :style="{ background: recipe.color }"><span>{{ recipe.art }}</span><b>{{ recipe.match }}% 匹配</b><div class="recipe-art-actions"><button title="收藏菜谱" :class="{ saved: recipe.collected }" @click="toggleRecipeBookmark(recipe)"><span v-html="icon('heart', 18)"></span></button><button v-if="recipe.collected" title="取消收藏" @click="removeRecipeBookmark(recipe)"><span v-html="icon('trash', 17)"></span></button></div></div><div class="recipe-info"><small>{{ recipe.level }}</small><h3>{{ recipe.name }}</h3><p>{{ recipe.desc }}</p><div class="recipe-metrics"><span>⏱ {{ recipe.time }} 分钟</span><span>≈ {{ recipe.kcal }} 千卡</span></div><div class="recipe-card-actions"><button @click="openCooking(recipe)">查看做法</button><button v-if="recipe.missing.length" @click="addMissing(recipe)">加入缺料</button></div></div></article></section><section v-else class="recipe-empty"><span v-html="icon('book', 30)"></span><h2>暂无可用菜谱</h2><p>请确认后端已导入并发布菜谱数据。</p></section>
        </template>

        <template v-else-if="page === 'cooking'">
          <section v-if="!cookingRecipe" class="empty-state"><span v-html="icon('pan', 32)"></span><h1>选择一道菜开始制作</h1><button class="primary-btn" @click="go('recipes')">浏览菜谱</button></section><template v-else><section class="page-intro cooking-intro"><div><p class="eyebrow">做菜模式 · AI 实时换算</p><h1>{{ cookingRecipe.name }}</h1><p>{{ cookingRecipe.time }} 分钟 · 食材{{ cookingRecipe.missing.length ? `缺少 ${cookingRecipe.missing.join('、')}` : '齐全' }}</p></div><button class="secondary-btn" @click="go('recipes')">切换菜品</button></section><section class="cooking-layout"><aside class="cooking-control"><label>主料重量<input v-model.number="cookingWeight" type="number" min="1" /><span>克</span></label><div class="cooking-kcal"><small>本次总热量</small><strong>{{ scaledKcal }}</strong><span>千卡</span></div><h3>所需食材</h3><div v-for="item in scaledIngredients" :key="item.name" class="ingredient-line"><span>{{ item.name }}</span><b>{{ item.display }} {{ item.unit }}</b></div><h3>调味品</h3><div v-for="item in cookingRecipe.seasonings" :key="item.name" class="ingredient-line"><span>{{ item.name }}</span><b>{{ item.amount }} {{ item.unit }}</b></div></aside><section class="cooking-steps"><div class="cooking-progress"><button v-for="(step, index) in cookingRecipe.steps" :key="index" :class="{ active: activeCookingStep === index, done: index < activeCookingStep }" @click="activeCookingStep = index"><span>{{ index + 1 }}</span></button></div><article><p>步骤 {{ activeCookingStep + 1 }} / {{ cookingRecipe.steps.length }}</p><h2>{{ cookingRecipe.steps[activeCookingStep] }}</h2><div><button class="secondary-btn" :disabled="activeCookingStep === 0" @click="activeCookingStep--">上一步</button><button v-if="activeCookingStep < cookingRecipe.steps.length - 1" class="primary-btn" @click="activeCookingStep++">下一步</button><button v-else class="primary-btn" @click="completeCooking">完成制作</button></div></article></section></section></template>
        </template>

        <template v-else-if="page === 'diet'">
          <section class="page-intro"><div><p class="eyebrow">{{ localDate() }} · 今日</p><h1>饮食健康</h1><p>以下内容全部来自饮食记录与库存流水数据库。</p></div><button class="primary-btn" @click="showMealEditor = true"><span v-html="icon('plus', 18)"></span>记录一餐</button></section>
          <section class="nutrition-grid"><article class="calorie-card"><div class="ring large" :style="{ '--p': caloriePercent }"><div><strong>{{ totalCalories.toLocaleString() }}</strong><span>目标 {{ preferences.target.toLocaleString() }} 千卡</span></div></div><div><p class="eyebrow">今日摄入 · {{ caloriePercent }}%</p><h2>{{ totalCalories > preferences.target ? `已超出 ${totalCalories - preferences.target} 千卡` : `还可摄入 ${preferences.target - totalCalories} 千卡` }}</h2><p>共 {{ dietAnalytics?.mealCount || 0 }} 条数据库记录。</p></div></article><article class="ai-advice"><span v-html="icon('list', 24)"></span><div><p class="eyebrow">今日营养汇总</p><h2>蛋白质 {{ Number(dietAnalytics?.protein || 0).toFixed(1) }} 克</h2><p>脂肪 {{ Number(dietAnalytics?.fat || 0).toFixed(1) }} 克 · 碳水 {{ Number(dietAnalytics?.carbs || 0).toFixed(1) }} 克</p></div></article></section>
          <section class="meal-log"><div class="section-head"><div><p class="eyebrow">时间线</p><h2>今天吃过这些</h2></div><button class="text-btn" @click="showMealEditor = true">添加记录</button></div><div v-for="meal in todaysMeals" :key="meal.id" class="meal-row"><time>{{ meal.time }}</time><i>{{ meal.icon }}</i><span><b>{{ meal.name }}</b><small>{{ meal.food }}</small></span><strong>{{ meal.kcal }} <small>千卡</small></strong><button title="删除记录" @click="deleteMealRecord(meal)"><span v-html="icon('trash', 16)"></span></button></div><p v-if="!todaysMeals.length" class="sensor-form-note">今天还没有饮食记录。</p></section>
          <section class="diet-insights"><div class="section-head"><div><p class="eyebrow">最近 7 天</p><h2>库存消耗流水</h2></div><span>{{ consumptionAnalytics?.unit || 'MIXED' }}</span></div><div class="diet-insight-grid"><article class="ranking-chart"><p class="eyebrow">真实库存事务汇总</p><h2>已消耗 {{ Number(consumptionAnalytics?.consumed || 0).toFixed(2) }}</h2><ol><li><b>1</b><span>正常食用</span><em>{{ Number(consumptionAnalytics?.consumed || 0).toFixed(2) }}</em></li><li><b>2</b><span>主动丢弃</span><em>{{ Number(consumptionAnalytics?.discarded || 0).toFixed(2) }}</em></li><li><b>3</b><span>过期处理</span><em>{{ Number(consumptionAnalytics?.expired || 0).toFixed(2) }}</em></li></ol></article><article class="ranking-chart"><p class="eyebrow">数据说明</p><h2>不生成虚假评分</h2><p>{{ dietAnalytics?.disclaimer || '暂无分析数据' }}</p></article></div></section>
        </template>

        <template v-else-if="page === 'shopping'">
          <section class="page-intro"><div><p class="eyebrow">自动补货与菜谱缺料</p><h1>采购清单</h1><p>采购状态由你决定；入库时再确认实际数量和存放位置。</p></div><div class="intro-actions"><button class="secondary-btn" @click="exportShopping"><span v-html="icon('download', 18)"></span>导出 txt</button><button class="primary-btn" @click="openShoppingEditor()"><span v-html="icon('plus', 18)"></span>添加项目</button></div></section><div class="filter-pills shopping-filters"><button v-for="group in ['全部', '蔬果', '主食', '调味品', '菜谱缺料', '其他']" :key="group" :class="{ active: shoppingGroup === group }" @click="shoppingGroup = group">{{ group }}</button></div><section class="shopping-layout"><div class="shopping-list"><div class="shopping-head"><h2>{{ pendingShoppingCount }} 项待购买</h2><span>{{ visibleShopping.length }} 项清单数据</span></div><article v-for="item in visibleShopping" :key="item.id" class="shop-item" :class="`status-${item.status}`"><span class="shop-group">{{ item.group }}</span><span><b>{{ item.name }}</b><small>{{ item.note }}</small></span><em>{{ item.amount }}</em><select :value="item.status" :aria-label="`${item.name} 的采购状态`" @change="updateShoppingStatus(item, $event.target.value)"><option value="pending">待购买</option><option value="purchased">已购买</option><option value="stored">已入库</option></select><button title="编辑项目" @click="openShoppingEditor(item)"><span v-html="icon('edit', 16)"></span></button><button title="删除项目" @click="removeShoppingItem(item)"><span v-html="icon('trash', 16)"></span></button></article></div><aside class="smart-list restock-list"><span v-html="icon('spark', 24)"></span><h2>补货依据</h2><p>根据后端低库存状态和已加载菜谱缺料实时生成，勾选后才会加入清单。</p><p v-if="!restockCandidates.length">当前没有补货候选。</p><label v-for="item in restockCandidates" :key="item.id" class="restock-candidate"><input type="checkbox" :checked="selectedRestockIds.includes(item.id)" @change="toggleRestock(item.id)" /><span><b>{{ item.name }}</b><small>{{ item.current }} · 阈值 {{ item.threshold }}</small><em>{{ item.note }}，建议买 {{ item.amount }}</em></span></label><button class="secondary-btn" :disabled="!selectedRestockIds.length" @click="addSelectedRestock">加入已选补货项</button></aside></section><section class="module-history compact-history"><div class="section-head"><div><p class="eyebrow">采购历史</p><h2>最近入库</h2></div></div><article v-for="item in histories['采购记录'].slice(0, 4)" :key="item.id"><span v-html="icon('bag', 18)"></span><div><b>{{ item.title }}</b><small>{{ item.meta }}</small></div><em>{{ item.note }}</em></article></section>
        </template>

        <template v-else-if="page === 'settings'">
          <section class="page-intro"><div><p class="eyebrow">账户、偏好与冰箱分区</p><h1>设置</h1><p>过敏与忌口会作为菜谱推荐的硬性排除条件。</p></div><button class="primary-btn" @click="saveProfile">保存更改</button></section><section class="settings-layout"><div class="settings-main"><article class="setting-card"><div class="setting-title"><span v-html="icon('spark', 22)"></span><div><h2>饮食偏好</h2><p>推荐会自动遵循这些选择</p></div></div><label>口味偏好</label><div class="choice-row"><button v-for="taste in ['清淡', '少油', '低盐', '微辣', '中辣', '少糖']" :key="taste" :class="{ selected: preferences.tastes.includes(taste) }" @click="toggleTag(preferences.tastes, taste)"><span v-html="icon('check', 14)"></span>{{ taste }}</button></div><label>菜系偏好</label><div class="choice-row"><button v-for="cuisine in ['家常菜', '粤菜', '川菜', '日料', '轻食']" :key="cuisine" :class="{ selected: preferences.cuisine.includes(cuisine) }" @click="toggleTag(preferences.cuisine, cuisine)"><span v-html="icon('check', 14)"></span>{{ cuisine }}</button></div><div class="field-pair"><label>饮食目标<select v-model="preferences.goal"><option>减脂</option><option>增肌</option><option>均衡饮食</option><option>控制热量</option></select></label><label>每日热量目标<div class="input-suffix"><input v-model.number="preferences.target" type="number" /><span>千卡</span></div></label></div></article>
            <article class="setting-card">
              <div class="setting-title"><span v-html="icon('box', 22)"></span><div><h2>冰箱分区与探头</h2><p>模拟探头会随冰箱初始化自动启动。</p></div><div class="zone-count-control" aria-label="分区数量"><span>已初始化分区</span><strong>{{ zones.length }}</strong></div></div>
        <p class="zone-count-note">冰箱初始化完成后，系统会自动创建并绑定模拟探头，读数每 5 秒更新。</p>
              <div v-for="zone in zones" :key="zone.id" class="zone-editor">
                <div class="zone-editor-top zone-target-editor"><span class="zone-icon" :style="{ background: zone.color }"></span><input :value="zone.name" aria-label="分区名称" maxlength="12" @change="updateZoneName(zone, $event.target.value)" /><label>理想温度<input v-model.number="zone.targetTemperature" type="number" step="0.1" /><small>°C</small></label><label>理想湿度<input v-model.number="zone.targetHumidity" type="number" /><small>%</small></label></div>
                <div class="current-readings"><span><i v-html="icon('thermometer', 14)"></i>当前温度 <b>{{ zone.temp == null ? '—' : `${zone.temp.toFixed(1)} °C` }}</b></span><span><i v-html="icon('drop', 14)"></i>当前湿度 <b>{{ zone.humidity == null ? '—' : `${zone.humidity.toFixed(1)} %` }}</b></span><small>{{ zone.update }} · 模拟读数</small></div>
                <div class="sensor-list">
                  <div v-for="sensor in zone.sensorSlots" :key="sensor.id" class="sensor-chip" :class="{ pending: sensor.bindingStatus !== 'BOUND' || !sensor.lastReceivedAt }"><span v-html="icon(sensor.type === 'temperature' ? 'thermometer' : 'drop', 15)"></span><div><b>{{ sensor.name }}</b><small>{{ sensor.type === 'temperature' ? '温度探头' : '湿度探头' }} · {{ sensor.bindingStatus !== 'BOUND' ? '系统自动绑定中' : sensor.lastReceivedAt ? `${sensor.update} · 模拟读数` : '模拟探头已绑定，等待首条读数' }}</small></div><strong>{{ sensor.bindingStatus === 'BOUND' && sensor.lastReceivedAt ? `${sensor.value} ${sensor.unit}` : sensor.bindingStatus === 'BOUND' ? '等待数据' : '自动绑定中' }}</strong></div>
                  <p v-if="!zone.sensorSlots.length" class="sensor-form-note">该分区未配置探头槽位。</p>
                </div>
              </div>
            </article>
          </div><aside class="account-card"><span class="avatar large-avatar">{{ profile.name.slice(0, 1) }}</span><h2>账户信息</h2><label>姓名<input v-model="profile.name" /></label><label>用户名<input v-model.trim="profile.username" autocomplete="username" minlength="3" maxlength="32" pattern="[a-z0-9_]{3,32}" /></label><label>邮箱<input v-model="profile.email" type="email" readonly /></label><hr /><h3>修改密码</h3><label>原密码<input v-model="profile.currentPassword" type="password" /></label><label>新密码<input v-model="profile.password" type="password" placeholder="至少 8 位" /></label><label>确认新密码<input v-model="profile.confirmPassword" type="password" /></label><div class="account-unit"><span>温度单位</span><span class="unit-switch"><button :class="{ on: unit === 'C' }" @click="unit = 'C'">℃</button><button :class="{ on: unit === 'F' }" @click="unit = 'F'">℉</button></span></div></aside></section>
        </template>
      </div>
    </main>

    <div v-if="showAdd" class="modal-backdrop" @click.self="showAdd = false"><form class="modal" @submit.prevent="addFood"><div class="modal-head"><div><p class="eyebrow">库存录入</p><h2>添加食材</h2></div><button type="button" @click="showAdd = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">名称<NameSuggestionInput v-model="newFood.name" context="ingredient" placeholder="例如：鸡胸肉" aria-label="食材名称" /></label><label>分类<select v-model="newFood.category"><option>蔬菜</option><option>水果</option><option>肉蛋</option><option>水产</option><option>豆制品</option><option>零食</option><option>饮料</option><option>调味品</option></select></label><label>存放位置<select v-model="newFood.zoneId"><option v-for="zone in zones" :key="zone.id" :value="zone.id">{{ zone.name }}</option><option :value="null">常温储物区</option></select></label><label>数量<input v-model="newFood.amount" type="number" placeholder="可留空" /></label><label>单位<select v-model="newFood.unit"><option>克</option><option>千克</option><option>个</option><option>盒</option><option>瓶</option><option>毫升</option></select></label><label>入库日期<input v-model="newFood.date" type="date" /></label><label>参考保质期<div class="input-suffix"><input v-model="newFood.shelf" type="number" /><span>天</span></div></label></div><div class="modal-actions"><button type="button" class="secondary-btn" @click="showAdd = false">取消</button><button class="primary-btn">放入冰箱</button></div></form></div>
    <div v-if="showFoodEditor && selectedFood" class="modal-backdrop" @click.self="showFoodEditor = false"><form class="modal compact-modal" @submit.prevent="saveFood"><div class="modal-head"><div><p class="eyebrow">库存编辑</p><h2>{{ foodDraft.name }}</h2></div><button type="button" @click="showFoodEditor = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">名称<NameSuggestionInput v-model="foodDraft.name" context="ingredient" aria-label="食材名称" /></label><label>分类<select v-model="foodDraft.category"><option>蔬菜</option><option>水果</option><option>肉蛋</option><option>水产</option><option>豆制品</option><option>零食</option><option>饮料</option><option>调味品</option></select></label><label>数量<input v-model="foodDraft.amount" type="number" min="0" /></label><label>单位<input v-model="foodDraft.unit" /></label><label>存放位置<select v-model="foodDraft.zoneId"><option v-for="zone in zones" :key="zone.id" :value="zone.id">{{ zone.name }}</option><option :value="null">常温储物区</option></select></label></div><div class="modal-actions"><button type="button" class="danger-btn" @click="deleteFood(selectedFood)">删除食材</button><button class="primary-btn">保存更改</button></div></form></div>
    <div v-if="showMealEditor" class="modal-backdrop" @click.self="showMealEditor = false"><form class="modal compact-modal meal-modal" @submit.prevent="recordMeal"><div class="modal-head"><div><p class="eyebrow">饮食记录</p><h2>记录一餐</h2></div><button type="button" @click="showMealEditor = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">菜品名称<NameSuggestionInput v-model="mealDraft.name" context="dish" placeholder="例如：鸡胸肉豆腐煲" aria-label="菜品名称" /></label><label>餐次<select v-model="mealDraft.meal"><option>早餐</option><option>午餐</option><option>晚餐</option><option>加餐</option></select></label><label>数量 / 重量<input v-model="mealDraft.amount" placeholder="可选" /></label><label>单位<select v-model="mealDraft.unit"><option>克</option><option>份</option><option>个</option></select></label></div><div class="meal-estimate" :class="{ loading: isEstimatingMeal, error: mealEstimateError }"><span v-html="icon('spark', 20)"></span><div v-if="isEstimatingMeal"><b>正在查询营养数据</b><small>根据菜谱数据库或规则估算</small></div><div v-else-if="mealEstimate"><b>估算 {{ mealEstimate.calories }} 千卡</b><small>{{ mealEstimate.source }} · 蛋白质约 {{ mealEstimate.protein ?? '—' }} 克</small></div><div v-else-if="mealEstimateError"><b>{{ mealEstimateError }}</b><button type="button" class="text-btn" @click="estimateMealNutrition">重新估算</button></div><div v-else><b>输入菜品后查询营养数据</b><small>结果会标明数据库或规则来源</small></div></div><div class="modal-actions lowered-actions"><button type="button" class="secondary-btn" @click="showMealEditor = false">取消</button><button class="primary-btn" :disabled="!mealEstimate || isEstimatingMeal" >记录饮食</button></div></form></div>
    <div v-if="showShoppingEditor" class="modal-backdrop" @click.self="showShoppingEditor = false"><form class="modal compact-modal" @submit.prevent="saveShoppingItem"><div class="modal-head"><div><p class="eyebrow">采购清单</p><h2>{{ shopDraft.id ? '编辑项目' : '添加项目' }}</h2></div><button type="button" @click="showShoppingEditor = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">项目名称<NameSuggestionInput v-model="shopDraft.name" context="ingredient" placeholder="例如：燕麦片" aria-label="采购项目名称" /></label><label>分类<select v-model="shopDraft.group"><option>蔬果</option><option>主食</option><option>调味品</option><option>菜谱缺料</option><option>其他</option></select></label><label>数量 / 单位<input v-model="shopDraft.amount" placeholder="例如：1 袋" /></label><label class="wide">备注<input v-model="shopDraft.note" placeholder="例如：低于常用库存" /></label><label>采购状态<select v-model="shopDraft.status" :disabled="shopDraft.status === 'stored'"><option value="pending">待购买</option><option value="purchased">已购买</option><option v-if="shopDraft.status === 'stored'" value="stored">已入库</option></select></label></div><div class="modal-actions"><button type="button" class="secondary-btn" @click="showShoppingEditor = false">取消</button><button class="primary-btn">保存项目</button></div></form></div>
    <div v-if="showPurchase && selectedShopItem" class="modal-backdrop" @click.self="showPurchase = false"><form class="modal compact-modal" @submit.prevent="confirmPurchase"><div class="modal-head"><div><p class="eyebrow">购买入库</p><h2>{{ selectedShopItem.name }}</h2></div><button type="button" @click="showPurchase = false"><span v-html="icon('close')"></span></button></div><p class="purchase-note">确认后会写入库存，并将该采购项标记为“已入库”。</p><div class="form-grid"><label>数量<input v-model="purchaseDraft.amount" type="number" /></label><label>单位<select v-model="purchaseDraft.unit"><option>克</option><option>个</option><option>盒</option><option>瓶</option><option>袋</option></select></label><label>存放位置<select v-model="purchaseDraft.zoneId"><option v-for="zone in zones" :key="zone.id" :value="zone.id">{{ zone.name }}</option><option :value="null">常温储物区</option></select></label><label>参考保质期<input v-model="purchaseDraft.shelf" type="number" /></label></div><div class="modal-actions"><button type="button" class="secondary-btn" @click="showPurchase = false">取消</button><button class="primary-btn">确认入库</button></div></form></div>
    <div v-if="showInventoryRecipeSelector" class="modal-backdrop" @click.self="closeInventoryRecipeSelector"><form class="modal compact-modal inventory-recipe-modal" @submit.prevent="generateSelectedInventoryRecipes"><div class="modal-head"><div><p class="eyebrow">菜谱数据库筛选</p><h2>选择库存食材</h2></div><button type="button" :disabled="isGeneratingRecipe" aria-label="关闭食材选择" @click="closeInventoryRecipeSelector"><span v-html="icon('close')"></span></button></div><p class="recipe-generator-note">选择优先使用的食材，系统会结合库存和饮食偏好筛选 3 个方案。</p><div class="inventory-selection-toolbar"><strong>已选 {{ selectedInventoryFoods.length }} 项</strong><span><button type="button" class="text-btn" :disabled="!selectableInventoryFoods.length" @click="selectAllInventoryRecipeIngredients">全选</button><button type="button" class="text-btn" :disabled="!selectedInventoryFoods.length" @click="clearInventoryRecipeIngredients">清空</button></span></div><div v-if="selectableInventoryFoods.length" class="inventory-recipe-options"><label v-for="food in selectableInventoryFoods" :key="food.id" class="inventory-recipe-option" :class="{ selected: selectedInventoryIngredientIds.includes(food.id) }"><input type="checkbox" :checked="selectedInventoryIngredientIds.includes(food.id)" @change="toggleInventoryRecipeIngredient(food)" /><i>{{ food.icon }}</i><span><b>{{ food.name }}</b><small>{{ food.category }} · {{ zoneNameForFood(food) }}</small></span><em>{{ food.amount }}{{ food.unit }}</em></label></div><div v-else class="inventory-recipe-empty"><span v-html="icon('box', 24)"></span><p>暂无可用于筛选菜谱的库存食材。</p></div><div class="modal-actions"><button type="button" class="secondary-btn" :disabled="isGeneratingRecipe" @click="closeInventoryRecipeSelector">取消</button><button class="primary-btn" :disabled="!selectedInventoryFoods.length || isGeneratingRecipe">{{ isGeneratingRecipe ? '筛选中' : `使用已选食材筛选 (${selectedInventoryFoods.length})` }}</button></div></form></div>
    <div v-if="showRecipeNameGenerator" class="modal-backdrop" @click.self="showRecipeNameGenerator = false"><form class="modal compact-modal" @submit.prevent="generateNamedRecipe"><div class="modal-head"><div><p class="eyebrow">菜谱数据库筛选</p><h2>按菜名筛选</h2></div><button type="button" @click="showRecipeNameGenerator = false"><span v-html="icon('close')"></span></button></div><label>菜名<NameSuggestionInput v-model="recipeNameDraft" context="dish" placeholder="例如：番茄炒蛋" aria-label="菜名" /></label><p class="recipe-generator-note">系统会结合菜名、当前库存和饮食偏好筛选最多 3 个已发布菜谱。</p><div class="modal-actions"><button type="button" class="secondary-btn" @click="showRecipeNameGenerator = false">取消</button><button class="primary-btn" :disabled="isGeneratingRecipe">{{ isGeneratingRecipe ? '筛选中' : '筛选 3 张菜谱' }}</button></div></form></div>
    <transition name="toast"><div v-if="toast" class="toast"><span v-html="icon('check', 17)"></span>{{ toast }}</div></transition><AssistantPet v-model:open="assistantOpen" v-model:input="assistantInput" :page-name="assistantPageName" :messages="assistantMessages" :image="pixelPet" @send="sendAssistantMessage" />
  </div>
</template>
