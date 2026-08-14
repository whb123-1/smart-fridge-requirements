<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { api } from './services/api'
import pixelPet from './assets/xianling-pixel-pet-transparent.png'
import FridgeModel from './components/FridgeModel.vue'
import AssistantPet from './components/AssistantPet.vue'
import NameSuggestionInput from './components/NameSuggestionInput.vue'

const page = ref('home')
const unit = ref('C')
const showAdd = ref(false)
const showFoodEditor = ref(false)
const showMealEditor = ref(false)
const showPurchase = ref(false)
const showShoppingEditor = ref(false)
const showSensorEditor = ref(false)
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
const selectedFood = ref(null)
const selectedShopItem = ref(null)
const sensorZone = ref(null)
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

const foodDraft = reactive({})
const shopDraft = reactive({ id: null, name: '', group: '其他', amount: '1 份', note: '', status: 'pending' })
const sensorDraft = reactive({ name: '', type: 'temperature' })
const newFood = reactive({ name: '', category: '蔬菜', amount: '', unit: '克', zone: '冷藏区', date: '2026-08-14', shelf: '7', reminder: '2026-08-20' })
const mealDraft = reactive({ name: '', meal: '晚餐', amount: '', unit: '克' })
const purchaseDraft = reactive({ amount: '', unit: '克', zone: '冷藏区', shelf: '7' })

const zones = reactive([
  {
    id: 1, name: '冷藏区', temp: 3.5, humidity: 68, targetTemperature: 4, targetHumidity: 70, state: 'normal', update: '刚刚', items: 18, color: '#5f94b5',
    sensors: [{ id: 'T-101', name: '冷藏温度探头', type: 'temperature', value: 3.5, unit: '°C', update: '刚刚' }, { id: 'H-101', name: '冷藏湿度探头', type: 'humidity', value: 68, unit: '%', update: '刚刚' }],
  },
  {
    id: 2, name: '保鲜抽屉', temp: 1.8, humidity: 86, targetTemperature: 2, targetHumidity: 85, state: 'normal', update: '2 分钟前', items: 9, color: '#6f9fc2',
    sensors: [{ id: 'T-102', name: '保鲜温度探头', type: 'temperature', value: 1.8, unit: '°C', update: '2 分钟前' }, { id: 'H-102', name: '保鲜湿度探头', type: 'humidity', value: 86, unit: '%', update: '2 分钟前' }],
  },
  {
    id: 3, name: '变温区', temp: 7.2, humidity: 61, targetTemperature: 4, targetHumidity: 65, state: 'warning', update: '4 分钟前', items: 6, color: '#779fc9',
    sensors: [{ id: 'T-103', name: '变温温度探头', type: 'temperature', value: 7.2, unit: '°C', update: '4 分钟前' }, { id: 'H-103', name: '变温湿度探头', type: 'humidity', value: 61, unit: '%', update: '4 分钟前' }],
  },
  {
    id: 4, name: '冷冻区', temp: -16.4, humidity: 42, targetTemperature: -20, targetHumidity: 45, state: 'warning', update: '8 分钟前', items: 12, color: '#536d9a',
    sensors: [{ id: 'T-104', name: '冷冻温度探头', type: 'temperature', value: -16.4, unit: '°C', update: '8 分钟前' }, { id: 'H-104', name: '冷冻湿度探头', type: 'humidity', value: 42, unit: '%', update: '8 分钟前' }],
  },
])

const foods = reactive([
  { id: 1, name: '上海青', icon: '🥬', category: '蔬菜', zone: '保鲜抽屉', amount: 420, unit: '克', calories: 18, days: 1, status: 'urgent', percent: 16, received: '2026-08-11', reminder: '2026-08-14', source: 'AI 按实测温湿度估算' },
  { id: 2, name: '鲜牛奶', icon: '🥛', category: '饮料', zone: '冷藏区', amount: 680, unit: '毫升', calories: 54, days: 2, status: 'soon', percent: 28, received: '2026-08-12', reminder: '2026-08-15', source: 'AI 按实测温湿度估算' },
  { id: 3, name: '鸡胸肉', icon: '🍗', category: '肉蛋', zone: '冷藏区', amount: 520, unit: '克', calories: 133, days: 3, status: 'soon', percent: 42, received: '2026-08-12', reminder: '2026-08-16', source: 'AI 按实测温湿度估算' },
  { id: 4, name: '北豆腐', icon: '◻️', category: '豆制品', zone: '冷藏区', amount: 2, unit: '盒', calories: 116, days: 4, status: 'fresh', percent: 58, received: '2026-08-11', reminder: '2026-08-17', source: '按参考温湿度估算' },
  { id: 5, name: '鸡蛋', icon: '🥚', category: '肉蛋', zone: '冷藏区', amount: 8, unit: '个', calories: 144, days: 12, status: 'fresh', percent: 78, received: '2026-08-07', reminder: '2026-08-25', source: 'AI 按实测温湿度估算' },
  { id: 6, name: '三文鱼', icon: '🐟', category: '水产', zone: '冷冻区', amount: 300, unit: '克', calories: 208, days: 26, status: 'fresh', percent: 88, received: '2026-08-10', reminder: '2026-09-09', source: 'AI 按实测温湿度估算' },
  { id: 7, name: '无糖酸奶', icon: '🥣', category: '零食', zone: '冷藏区', amount: 3, unit: '杯', calories: 72, days: 6, status: 'fresh', percent: 64, received: '2026-08-10', reminder: '2026-08-19', source: '按参考温湿度估算' },
  { id: 8, name: '低钠生抽', icon: '🍶', category: '调味品', zone: '常温储物区', amount: 320, unit: '毫升', calories: 63, days: 120, status: 'fresh', percent: 70, received: '2026-07-01', reminder: '2026-12-12', source: '用户设置提醒' },
])

const recipes = reactive([
  { id: 1, name: '鸡胸肉豆腐煲', desc: '清淡不寡淡，正好优先处理临期鸡胸肉', time: 25, kcal: 386, protein: 42, match: 100, level: '可直接制作', color: '#dfe9df', art: '🍲', tags: ['高蛋白', '低油'], favorite: true, collected: false, missing: [], base: 300, ingredients: [{ name: '鸡胸肉', amount: 300, unit: '克' }, { name: '北豆腐', amount: 1, unit: '盒' }, { name: '上海青', amount: 200, unit: '克' }], seasonings: [{ name: '低钠生抽', amount: 10, unit: '毫升' }], steps: ['鸡胸肉切块，用少量生抽和姜片腌制 5 分钟。', '豆腐切块，与鸡胸肉一起小火煎至表面微黄。', '加入清水炖煮 12 分钟，放入上海青煮熟即可。'] },
  { id: 2, name: '蒜蓉上海青', desc: '8 分钟完成，优先处理新鲜度较低的蔬菜', time: 8, kcal: 96, protein: 4, match: 100, level: '可直接制作', color: '#e8edd6', art: '🥬', tags: ['低热量', '优先处理临期'], favorite: false, collected: false, missing: [], base: 300, ingredients: [{ name: '上海青', amount: 300, unit: '克' }], seasonings: [{ name: '蒜', amount: 8, unit: '克' }], steps: ['上海青洗净，蒜切末。', '热锅少油，下蒜末炒香。', '放入上海青大火快炒，加盐后出锅。'] },
  { id: 3, name: '香煎三文鱼温沙拉', desc: '缺少小番茄，可用苹果替代增加清甜', time: 22, kcal: 438, protein: 32, match: 88, level: '可替代制作', color: '#f1e1d6', art: '🥗', tags: ['优质脂肪', '可替代'], favorite: false, collected: true, missing: ['小番茄'], base: 250, ingredients: [{ name: '三文鱼', amount: 250, unit: '克' }, { name: '小番茄', amount: 120, unit: '克' }], seasonings: [{ name: '黑胡椒', amount: 2, unit: '克' }], steps: ['三文鱼擦干水分，撒黑胡椒。', '平底锅小火煎至两面熟透。', '配上蔬菜和柠檬汁拌匀。'] },
])

const shopping = reactive([
  { id: 1, name: '小番茄', note: '三文鱼温沙拉需要', amount: '250 克', status: 'pending', group: '蔬果' },
  { id: 2, name: '燕麦片', note: '低于常用库存', amount: '1 袋', status: 'pending', group: '主食' },
  { id: 3, name: '柠檬', note: '2 道收藏菜谱需要', amount: '2 个', status: 'purchased', group: '蔬果' },
  { id: 4, name: '黑胡椒', note: '调味品即将用完', amount: '1 瓶', status: 'pending', group: '调味品' },
])

const restockCandidates = reactive([
  { id: 'restock-oats', name: '燕麦片', group: '主食', current: '0 袋', threshold: '1 袋', amount: '1 袋', note: '常用库存低于阈值' },
  { id: 'restock-pepper', name: '黑胡椒', group: '调味品', current: '余量未记录', threshold: '常用 1 瓶', amount: '1 瓶', note: '调味品即将用完' },
  { id: 'restock-tomato', name: '小番茄', group: '蔬果', current: '库存缺少', threshold: '菜谱需要', amount: '250 克', note: '所选菜谱缺少食材' },
])

const preferences = reactive({ tastes: ['清淡', '少油', '微辣'], cuisine: ['家常菜', '粤菜'], allergies: ['花生'], dislikes: ['香菜'], goal: '减脂', target: 1650 })
const profile = reactive({ name: '林知夏', email: 'xia@example.com', currentPassword: '', password: '', confirmPassword: '' })
const mealRecords = reactive([
  { id: 1, time: '08:10', name: '早餐', food: '无糖酸奶燕麦杯 · 1 份', kcal: 268, icon: '🥣' },
  { id: 2, time: '12:35', name: '午餐', food: '番茄牛肉盖饭 · 0.8 份', kcal: 642, icon: '🍛' },
  { id: 3, time: '15:20', name: '加餐', food: '苹果 · 1 个', kcal: 92, icon: '🍎' },
])
const histories = reactive({
  '饮食记录': [{ id: 1, title: '番茄牛肉盖饭', meta: '今天 12:35 · 午餐', note: '642 千卡' }, { id: 2, title: '无糖酸奶燕麦杯', meta: '今天 08:10 · 早餐', note: '268 千卡' }],
  '做菜记录': [{ id: 1, title: '蒜蓉上海青', meta: '昨天 18:42 · 已完成', note: '96 千卡' }],
  '采购记录': [{ id: 1, title: '鸡蛋、无糖酸奶', meta: '2026-08-12 · 已入库', note: '2 项' }],
})
const assistantMessages = reactive([{ id: 1, role: 'assistant', text: '你好，我是鲜知助手。可以帮你推荐菜谱、检查保质期、评估饮食或生成购物清单。' }])

const alerts = computed(() => foods.filter(food => food.days <= 3))
const warningZones = computed(() => zones.filter(zone => zone.state === 'warning'))
const totalCalories = computed(() => mealRecords.reduce((sum, meal) => sum + Number(meal.kcal || 0), 0))
const caloriePercent = computed(() => Math.min(100, Math.round(totalCalories.value / preferences.target * 100)))
const pendingShoppingCount = computed(() => shopping.filter(item => item.status === 'pending').length)
const inventoryHistory = computed(() => [...histories['采购记录'].map(item => ({ ...item, type: '入库', historyKey: '采购记录' })), ...histories['做菜记录'].map(item => ({ ...item, type: '使用', historyKey: '做菜记录' }))].sort((a, b) => b.id - a.id))
const filteredFoods = computed(() => foods.filter(food => {
  const typeOkay = inventoryType.value === '全部' || (inventoryType.value === '食材' ? !['零食', '饮料'].includes(food.category) : ['零食', '饮料'].includes(food.category))
  const zoneOkay = inventoryFilter.value === '全部' || food.zone === inventoryFilter.value
  return typeOkay && zoneOkay && (!search.value || food.name.includes(search.value))
}))
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
const assistantPageName = computed(() => ({ home: '首页', inventory: '库存', expiry: '保质期', recipes: '菜谱生成', cooking: '做菜', diet: '饮食健康', shopping: '采购', settings: '设置', environment: '环境提醒' })[page.value])

const categoryIcons = Object.freeze({ 蔬菜: '🥬', 水果: '🍎', 水产: '🐟', 豆制品: '◻️', 零食: '🍪', 饮料: '🥛', 调味品: '🍶' })

function icon(name, size = 20) {
  const paths = {
    box: '<path d="M4 7.5 12 3l8 4.5v9L12 21l-8-4.5z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9"/>', book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V4H6.5A2.5 2.5 0 0 0 4 6.5z"/><path d="M4 6.5v13M8 8h8"/>', spark: '<path d="m12 3 1.7 4.3L18 9l-4.3 1.7L12 15l-1.7-4.3L6 9l4.3-1.7z"/><path d="m18.5 15 .8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z"/>', bag: '<path d="M5 8h14l-1 13H6z"/><path d="M9 10V6a3 3 0 0 1 6 0v4"/>', plus: '<path d="M12 5v14M5 12h14"/>', minus: '<path d="M5 12h14"/>', search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>', bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>', check: '<path d="m5 12 4 4L19 6"/>', heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.7-7.5a5.5 5.5 0 0 0 1.1-8.9z"/>', close: '<path d="M6 6l12 12M18 6 6 18"/>', settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6v.2h.2v4H21a1.7 1.7 0 0 0-1.6 1z"/>', clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>', pan: '<path d="M3 13h13a5 5 0 0 1-10 0H3z"/><path d="M16 13h5M8 7c0-2 2-2 2-4M13 7c0-2 2-2 2-4"/>', trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3"/>', edit: '<path d="m4 20 4.3-1 10-10a2.1 2.1 0 0 0-3-3l-10 10z"/>', download: '<path d="M12 3v12M7 10l5 5 5-5M5 21h14"/>', thermometer: '<path d="M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0Z"/><path d="M12 9v7"/>', drop: '<path d="M12 3s5 5.3 5 10a5 5 0 0 1-10 0c0-4.7 5-10 5-10Z"/>', alert: '<path d="M12 3 2.8 20h18.4L12 3Z"/><path d="M12 9v4M12 17h.01"/>', arrow: '<path d="m14.5 5-7 7 7 7"/><path d="M8 12h12"/>', list: '<path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01"/>',
  }
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.spark}</svg>`
}

function notify(message) { toast.value = message; setTimeout(() => { if (toast.value === message) toast.value = '' }, 2600) }
function go(target) { page.value = target; search.value = '' }
function temp(value) { return unit.value === 'C' ? Number(value).toFixed(1) : (Number(value) * 9 / 5 + 32).toFixed(1) }
function tempUnit() { return unit.value === 'C' ? '°C' : '°F' }
function dateAfter(days) { const date = new Date('2026-08-14T00:00:00'); date.setDate(date.getDate() + Number(days)); return date.toISOString().slice(0, 10) }
function statusFor(days) { return Number(days) <= 1 ? 'urgent' : Number(days) <= 3 ? 'soon' : 'fresh' }
function foodIcon(category, name = '') { return category === '肉蛋' ? (name.includes('蛋') ? '🥚' : '🥩') : categoryIcons[category] || '🥕' }
function normalizeFoodAmount(value) { if (value === '' || value === null || value === undefined) return ''; const amount = Number(value); return Number.isFinite(amount) ? Math.max(0, amount) : '' }
function statusLabel(status) { return ({ pending: '待购买', purchased: '已购买', stored: '已入库' })[status] || '待购买' }
function zoneDeviation(zone) { return zone.temp - zone.targetTemperature }

async function addFood() {
  if (!newFood.name) return notify('请填写食材名称')
  const amount = normalizeFoodAmount(newFood.amount)
  const created = await api.addFood({ ...newFood, amount })
  foods.unshift({ ...created, id: created.id || Date.now(), icon: foodIcon(created.category, created.name), calories: 50, days: Number(newFood.shelf || 7), status: statusFor(newFood.shelf), percent: 82, received: newFood.date, reminder: newFood.reminder || dateAfter(newFood.shelf), source: '按参考温湿度估算' })
  histories['采购记录'].unshift({ id: Date.now(), title: newFood.name, meta: '今天 · 手动入库', note: amount === '' ? '未记录' : `${amount} ${newFood.unit}` })
  Object.assign(newFood, { name: '', amount: '' })
  showAdd.value = false
  notify('食材已放入冰箱')
}

function editFood(food) { selectedFood.value = food; Object.assign(foodDraft, food); showFoodEditor.value = true }
async function saveFood() {
  if (!foodDraft.name) return notify('请填写食材名称')
  const food = selectedFood.value
  if (!food) return
  const draft = { ...foodDraft, amount: normalizeFoodAmount(foodDraft.amount), icon: foodIcon(foodDraft.category, foodDraft.name) }
  try { Object.assign(food, await api.updateFood(draft)); showFoodEditor.value = false; notify(`${food.name} 已更新`) } catch { notify('保存失败，请稍后重试') }
}
function deleteFood(food) { const index = foods.indexOf(food); if (index >= 0) foods.splice(index, 1); showFoodEditor.value = false; notify(`${food.name} 已移出库存`) }
async function updateFoodAmount(food, value) {
  const previousAmount = food.amount
  const amount = normalizeFoodAmount(value)
  const version = (foodUpdateVersions.get(food.id) || 0) + 1
  foodUpdateVersions.set(food.id, version)
  food.amount = amount
  try { const updated = await api.updateFood({ ...food }); if (foodUpdateVersions.get(food.id) === version) Object.assign(food, updated) } catch { if (foodUpdateVersions.get(food.id) === version) food.amount = previousAmount; notify('数量保存失败，请稍后重试') }
}
function adjustFoodAmount(food, change) { return updateFoodAmount(food, Math.max(0, Number(food.amount || 0) + change)) }

async function generateRecipes(prompt = '', inventory = foods, selectedCount = 0) {
  if (isGeneratingRecipe.value) return false
  isGeneratingRecipe.value = true
  try {
    const result = await api.generateRecipeBatch({ inventory: inventory.map(food => ({ name: food.name, amount: food.amount, unit: food.unit, category: food.category })), preferences: { ...preferences }, prompt, count: 3 })
    generatedRecipes.value = result.recipes || []
    selectedGeneratedIds.value = []
    generatedRecipeSelectionCount.value = selectedCount
    recipeFilter.value = '全部推荐'
    notify(`AI 已生成 ${generatedRecipes.value.length} 张候选菜谱`)
    return true
  } catch {
    notify('AI 生成失败，请检查服务后重试')
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
function saveGeneratedRecipes() {
  const selected = generatedRecipes.value.filter(recipe => selectedGeneratedIds.value.includes(recipe.id))
  if (!selected.length) return notify('请至少选择一张菜谱')
  selected.forEach(recipe => { if (!recipes.some(item => item.name === recipe.name)) recipes.unshift({ ...recipe, collected: true }) })
  generatedRecipes.value = []
  selectedGeneratedIds.value = []
  generatedRecipeSelectionCount.value = 0
  notify(`已保存 ${selected.length} 张菜谱`)
}
function discardGeneratedRecipes() {
  generatedRecipes.value = []
  selectedGeneratedIds.value = []
  generatedRecipeSelectionCount.value = 0
  notify('已放弃本次候选菜谱')
}
function deleteRecipe(recipe) { const index = recipes.indexOf(recipe); if (index >= 0) recipes.splice(index, 1); if (cookingRecipe.value?.id === recipe.id) cookingRecipe.value = null; notify(`已删除「${recipe.name}」`) }
function openCooking(recipe) { cookingRecipe.value = recipe; cookingWeight.value = recipe.base; activeCookingStep.value = 0; go('cooking') }
function completeCooking() {
  if (!cookingRecipe.value) return
  scaledIngredients.value.forEach(ingredient => { const food = foods.find(item => item.name === ingredient.name); if (food && ['克', '毫升'].includes(food.unit)) food.amount = Math.max(0, food.amount - ingredient.display) })
  histories['做菜记录'].unshift({ id: Date.now(), title: cookingRecipe.value.name, meta: '今天 · 已完成', note: `${scaledKcal.value} 千卡` })
  mealRecords.push({ id: Date.now(), time: '18:40', name: '晚餐', food: `${cookingRecipe.value.name} · 1 份`, kcal: scaledKcal.value, icon: cookingRecipe.value.art })
  histories['饮食记录'].unshift({ id: Date.now() + 1, title: cookingRecipe.value.name, meta: '今天 18:40 · 晚餐', note: `${scaledKcal.value} 千卡` })
  notify('已完成制作，已记录饮食并扣减可计量库存')
}
function addMissing(recipe) { recipe.missing.forEach(name => { if (!shopping.some(item => item.name === name)) shopping.push({ id: Date.now() + shopping.length, name, note: `${recipe.name} 缺少`, amount: '1 份', status: 'pending', group: '菜谱缺料' }) }); notify('缺少食材已加入购物清单') }

let mealEstimateTimer
let mealEstimateVersion = 0
function clearMealEstimate() { clearTimeout(mealEstimateTimer); mealEstimate.value = null; mealEstimateError.value = ''; isEstimatingMeal.value = false }
async function estimateMealNutrition() {
  const name = mealDraft.name.trim()
  if (!name) return clearMealEstimate()
  const version = ++mealEstimateVersion
  isEstimatingMeal.value = true
  mealEstimateError.value = ''
  try { const result = await api.estimateMealNutrition({ dishName: name, amount: mealDraft.amount, unit: mealDraft.unit }); if (version === mealEstimateVersion) mealEstimate.value = result } catch { if (version === mealEstimateVersion) mealEstimateError.value = '热量估算失败，请重试' } finally { if (version === mealEstimateVersion) isEstimatingMeal.value = false }
}
watch(() => [mealDraft.name, mealDraft.amount, mealDraft.unit], () => {
  clearTimeout(mealEstimateTimer)
  mealEstimate.value = null
  mealEstimateError.value = ''
  if (!mealDraft.name.trim()) return
  mealEstimateTimer = setTimeout(estimateMealNutrition, 420)
})
function recordMeal() {
  if (!mealDraft.name || !mealEstimate.value || isEstimatingMeal.value) return notify('请等待 AI 完成热量估算')
  const record = { id: Date.now(), time: '18:45', name: mealDraft.meal, food: `${mealDraft.name}${mealDraft.amount ? ` · ${mealDraft.amount} ${mealDraft.unit}` : ''}`, kcal: mealEstimate.value.calories, icon: '🍽️' }
  mealRecords.push(record)
  histories['饮食记录'].unshift({ id: record.id, title: record.food, meta: `今天 ${record.time} · ${record.name}`, note: `${record.kcal} 千卡` })
  Object.assign(mealDraft, { name: '', amount: '', unit: '克' })
  clearMealEstimate()
  showMealEditor.value = false
  notify('饮食记录已添加')
}

function openShoppingEditor(item = null) {
  Object.assign(shopDraft, item ? { ...item } : { id: null, name: '', group: '其他', amount: '1 份', note: '手动添加', status: 'pending' })
  showShoppingEditor.value = true
}
async function saveShoppingItem() {
  if (!shopDraft.name.trim()) return notify('请填写采购项目名称')
  const payload = { ...shopDraft, name: shopDraft.name.trim() }
  try {
    const saved = await api.updateShoppingItem(payload)
    const existing = shopping.find(item => item.id === shopDraft.id)
    const target = existing || { ...saved, id: Date.now() }
    if (existing) Object.assign(existing, saved)
    else shopping.unshift(target)
    showShoppingEditor.value = false
    if (payload.status === 'stored') {
      target.status = existing?.status === 'purchased' ? 'purchased' : 'pending'
      startPurchase(target)
      return
    }
    notify(existing ? '采购项目已更新' : '采购项目已添加')
  } catch { notify('采购项目保存失败，请稍后重试') }
}
function updateShoppingStatus(item, status) { if (status === 'stored' && item.status !== 'stored') return startPurchase(item); item.status = status; notify(`${item.name} 已标记为${statusLabel(status)}`) }
function startPurchase(item) { selectedShopItem.value = item; Object.assign(purchaseDraft, { amount: item.amount.match(/[\d.]+/)?.[0] || '', unit: item.amount.includes('个') ? '个' : item.amount.includes('瓶') ? '瓶' : item.amount.includes('袋') ? '袋' : '克', zone: '冷藏区', shelf: '7' }); showPurchase.value = true }
function confirmPurchase() {
  const item = selectedShopItem.value
  if (!item) return
  foods.unshift({ id: Date.now(), name: item.name, icon: foodIcon(item.group === '调味品' ? '调味品' : '蔬菜', item.name), category: item.group === '调味品' ? '调味品' : '蔬菜', zone: purchaseDraft.zone, amount: Number(purchaseDraft.amount) || 1, unit: purchaseDraft.unit, calories: 50, days: Number(purchaseDraft.shelf), status: statusFor(purchaseDraft.shelf), percent: 92, received: '2026-08-14', reminder: dateAfter(purchaseDraft.shelf), source: '按参考温湿度估算' })
  item.status = 'stored'
  histories['采购记录'].unshift({ id: Date.now(), title: item.name, meta: '今天 · 已购买并入库', note: `${purchaseDraft.amount} ${purchaseDraft.unit}` })
  showPurchase.value = false
  notify(`${item.name} 已入库`)
}
function toggleRestock(id) { const index = selectedRestockIds.value.indexOf(id); index >= 0 ? selectedRestockIds.value.splice(index, 1) : selectedRestockIds.value.push(id) }
function addSelectedRestock() {
  const selected = restockCandidates.filter(item => selectedRestockIds.value.includes(item.id))
  if (!selected.length) return notify('请勾选要加入的补货项')
  selected.forEach(item => { if (!shopping.some(shopItem => shopItem.name === item.name)) shopping.push({ id: Date.now() + shopping.length, name: item.name, note: item.note, amount: item.amount, status: 'pending', group: item.group }) })
  selectedRestockIds.value = []
  notify(`已加入 ${selected.length} 项补货建议`)
}
function removeShoppingItem(item) { const index = shopping.indexOf(item); if (index >= 0) shopping.splice(index, 1); notify('采购项目已删除') }
function removePurchaseHistory(item) { const index = histories['采购记录'].indexOf(item); if (index >= 0) histories['采购记录'].splice(index, 1); notify('入库记录已删除') }
function exportShopping() { const text = `鲜知购物清单\n${shopping.filter(item => item.status !== 'stored').map(item => `- ${item.group}｜${item.name} ${item.amount}（${statusLabel(item.status)}）`).join('\n')}`; const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' })); const link = document.createElement('a'); link.href = url; link.download = '鲜知购物清单.txt'; link.click(); URL.revokeObjectURL(url); notify('购物清单已导出') }

function openSensorEditor(zone) { sensorZone.value = zone; Object.assign(sensorDraft, { name: '', type: 'temperature' }); showSensorEditor.value = true }
function saveSensor() {
  if (!sensorDraft.name.trim() || !sensorZone.value) return notify('请填写传感器名称')
  const zone = sensorZone.value
  const isTemperature = sensorDraft.type === 'temperature'
  zone.sensors.push({ id: `${isTemperature ? 'T' : 'H'}-${100 + Math.floor(Math.random() * 900)}`, name: sensorDraft.name.trim(), type: sensorDraft.type, value: isTemperature ? zone.temp : zone.humidity, unit: isTemperature ? '°C' : '%', update: '待同步' })
  showSensorEditor.value = false
  notify(`${zone.name} 已添加${isTemperature ? '温度' : '湿度'}传感器`)
}
function addZone() { const id = Math.max(...zones.map(zone => zone.id)) + 1; zones.push({ id, name: `自定义分区 ${id}`, temp: 4, humidity: 65, targetTemperature: 4, targetHumidity: 65, state: 'normal', update: '未接入', items: 0, color: '#8faed3', sensors: [] }); notify('已添加自定义冰箱分区') }
function toggleTag(collection, value) { const index = collection.indexOf(value); index >= 0 ? collection.splice(index, 1) : collection.push(value) }
function removeInventoryHistory(record) { const records = histories[record.historyKey]; const index = records.findIndex(item => item.id === record.id); if (index >= 0) records.splice(index, 1); notify('库存动态已删除') }
function saveProfile() { if (profile.password && (profile.password.length < 8 || profile.password !== profile.confirmPassword)) return notify('新密码至少 8 位，且两次输入一致'); api.updatePreferences(preferences); profile.currentPassword = ''; profile.password = ''; profile.confirmPassword = ''; notify('账户、偏好与分区目标已保存') }
function navigateToZone(zone) { inventoryFilter.value = zone.name; inventoryType.value = '全部'; go('inventory') }

async function assistantRoute(question) {
  const text = question.toLowerCase()
  if (/菜谱|做什么|推荐|吃什么|生成|想吃|想做/.test(text)) { await generateRecipes(question); go('recipes'); return `已生成 ${generatedRecipes.value.length} 张候选菜谱，勾选后即可保存。` }
  if (/过期|保质|保鲜/.test(text)) { go('expiry'); return `已打开保质期模块。现在有 ${alerts.value.length} 项食材建议优先处理。` }
  if (/饮食|健康|热量|卡路里/.test(text)) { go('diet'); return `已打开饮食健康。今日已记录 ${totalCalories.value} 千卡。` }
  if (/购物|采购|买|缺/.test(text)) { go('shopping'); return `已打开购物清单，当前有 ${pendingShoppingCount.value} 项待购买。` }
  if (/温度|湿度|分区|异常/.test(text)) { go('environment'); return `已打开环境提醒。当前有 ${warningZones.value.length} 个异常分区。` }
  if (/库存|冰箱|食材/.test(text)) { go('inventory'); return `已打开库存。当前有 ${foods.length} 项食材，${alerts.value.length} 项临期。` }
  return '可以直接问我“今天吃什么”“检查保质期”或“冰箱状态如何”。'
}
async function sendAssistantMessage(preset = '') {
  const text = (preset || assistantInput.value).trim()
  if (!text) return
  const pageBeforeAssistantRoute = page.value
  assistantMessages.push({ id: Date.now(), role: 'user', text })
  assistantInput.value = ''
  assistantMessages.push({ id: Date.now() + 1, role: 'assistant', text: await assistantRoute(text) })
  if (page.value !== pageBeforeAssistantRoute) assistantOpen.value = false
}
</script>

<template>
  <div class="app-shell" :class="{ 'home-shell': page === 'home' }">
    <main :class="{ 'home-main': page === 'home' }">
      <div class="content" :class="{ 'home-content': page === 'home' }">
        <button v-if="page !== 'home'" class="page-home-link" title="返回冰箱首页" aria-label="返回冰箱首页" @click="go('home')"><span v-html="icon('arrow', 18)"></span><span>返回首页</span></button>

        <template v-if="page === 'home'">
          <section class="home-console" aria-label="冰箱总览">
            <div class="orbit-status orbit-panel orbit-status-end"><div><span><i class="online-dot"></i>{{ zones.reduce((sum, zone) => sum + zone.sensors.length, 0) }} 个传感器在线</span><span>刚刚同步</span><span class="unit-switch home-unit"><button :class="{ on: unit === 'C' }" @click="unit = 'C'">℃</button><button :class="{ on: unit === 'F' }" @click="unit = 'F'">℉</button></span></div></div>
            <div class="orbit-left orbit-panel"><div class="home-task-grid"><button class="home-task fridge-control tone-chill" @click="go('inventory')"><span v-html="icon('box', 21)"></span><b>库存</b><small>{{ foods.length }} 项在库</small></button><button class="home-task fridge-control tone-freeze" @click="go('expiry')"><span v-html="icon('clock', 21)"></span><b>保质期</b><small>{{ alerts.length }} 项待处理</small></button><button class="home-task fridge-control tone-fresh" @click="go('recipes')"><span v-html="icon('book', 21)"></span><b>菜谱</b><small>{{ recipes.length }} 道已保存</small></button></div><button v-if="warningZones.length" class="home-warning" @click="go('environment')"><span v-html="icon('alert', 18)"></span><span><small>分区温度异常</small><b>{{ warningZones.length }} 个分区需要检查</b><em>查看全部环境提醒</em></span></button></div>
            <article class="center-fridge" aria-label="冰箱分区状态"><FridgeModel :zones="zones" :foods="foods" @zone-navigate="navigateToZone" /></article>
            <div class="orbit-right-rail orbit-panel"><div class="home-task-grid"><button class="home-task fridge-control tone-variable" @click="go('diet')"><span v-html="icon('spark', 21)"></span><b>饮食健康</b><small>{{ totalCalories }} / {{ preferences.target }} 千卡</small></button><button class="home-task fridge-control tone-fresh" @click="go('shopping')"><span v-html="icon('bag', 21)"></span><b>采购</b><small>{{ pendingShoppingCount }} 项待购买</small></button><button class="home-task fridge-control tone-smart" @click="go('settings')"><span v-html="icon('settings', 21)"></span><b>设置</b><small>{{ zones.length }} 个冰箱分区</small></button></div></div>
          </section>
        </template>

        <template v-else-if="page === 'environment'">
          <section class="page-intro"><div><p class="eyebrow">实时监测与异常聚合</p><h1>环境提醒</h1><p>温度异常的分区会统一显示，先检查门封、制冷和传感器位置。</p></div><button class="secondary-btn" @click="go('settings')"><span v-html="icon('settings', 18)"></span>管理分区目标</button></section>
          <section class="environment-summary"><article><span v-html="icon('alert', 23)"></span><div><strong>{{ warningZones.length }}</strong><small>个分区温度异常</small></div></article><p>温度恢复正常后，已受影响食材的建议食用期限不会自动延长。</p></section>
          <section v-if="warningZones.length" class="environment-list"><article v-for="zone in warningZones" :key="zone.id"><div class="environment-zone-icon" :style="{ background: zone.color }"><span v-html="icon('thermometer', 21)"></span></div><div><p class="eyebrow">{{ zone.update }} 更新</p><h2>{{ zone.name }}</h2><p>当前 {{ temp(zone.temp) }} {{ tempUnit() }}，理想 {{ temp(zone.targetTemperature) }} {{ tempUnit() }}，偏差 {{ Math.abs(zoneDeviation(zone)).toFixed(1) }} °C。</p><small>该分区有 {{ foods.filter(food => food.zone === zone.name).length }} 项库存，建议优先检查临期食材。</small></div><button class="secondary-btn" @click="navigateToZone(zone)">查看库存</button></article></section>
          <section v-else class="empty-state"><span v-html="icon('check', 30)"></span><h1>所有分区温度正常</h1><p>当前传感器读数均在设定范围内。</p></section>
        </template>

        <template v-else-if="page === 'inventory'">
          <section class="page-intro"><div><p class="eyebrow">{{ foods.length }} 项库存</p><h1>冰箱库存</h1><p>按分区、类别和新鲜程度整理每一份食材。</p></div><div class="intro-actions"><button class="secondary-btn" :class="{ listening: isListening }" @click="isListening = !isListening">语音添加</button><button class="primary-btn" @click="showAdd = true"><span v-html="icon('plus', 18)"></span>添加食材</button></div></section>
          <div class="tabs"><button v-for="type in ['全部', '食材', '零食饮料']" :key="type" :class="{ active: inventoryType === type }" @click="inventoryType = type">{{ type }}</button></div><section class="inventory-toolbar"><div class="filter-pills"><button v-for="zone in ['全部', '冷藏区', '保鲜抽屉', '变温区', '冷冻区', '常温储物区']" :key="zone" :class="{ active: inventoryFilter === zone }" @click="inventoryFilter = zone">{{ zone }}</button></div><label class="mini-search"><span v-html="icon('search', 16)"></span><input v-model="search" placeholder="搜索库存" /></label></section>
          <section class="inventory-table"><div class="table-head"><span>食材</span><span>存放位置</span><span>剩余数量</span><span>新鲜度 / 建议期限</span><span>热量</span><span></span></div><div v-for="food in filteredFoods" :key="food.id" class="food-row"><span class="food-name"><i>{{ food.icon }}</i><span><b>{{ food.name }}</b><small>{{ food.category }}</small></span></span><span><b>{{ food.zone }}</b><small>{{ food.source }}</small></span><span class="quantity-cell"><span class="quantity-stepper"><button :disabled="Number(food.amount || 0) <= 0" @click="adjustFoodAmount(food, -1)"><span v-html="icon('minus', 14)"></span></button><input :value="food.amount" type="number" min="0" @change="updateFoodAmount(food, $event.target.value)" /><button @click="adjustFoodAmount(food, 1)"><span v-html="icon('plus', 14)"></span></button></span><small>{{ food.unit }}</small></span><span class="fresh-cell"><i><em :class="food.status" :style="{ width: food.percent + '%' }"></em></i><small :class="food.status">{{ food.days }} 天后建议食用完</small></span><span><b>{{ food.calories }}</b><small>千卡 / 100克</small></span><span class="row-actions"><button title="编辑食材" @click="editFood(food)"><span v-html="icon('edit', 16)"></span></button></span></div></section>
          <section class="module-history compact-history inventory-history"><div class="section-head"><div><p class="eyebrow">库存动态</p><h2>最近入库与使用</h2></div></div><article v-for="item in inventoryHistory.slice(0, 5)" :key="`${item.historyKey}-${item.id}`"><span v-html="icon(item.type === '入库' ? 'box' : 'pan', 18)"></span><div><b>{{ item.title }}</b><small>{{ item.meta }}</small></div><em>{{ item.note }}</em><button title="删除记录" @click="removeInventoryHistory(item)"><span v-html="icon('trash', 15)"></span></button></article></section>
        </template>

        <template v-else-if="page === 'expiry'">
          <section class="page-intro"><div><p class="eyebrow">AI 保鲜管理</p><h1>保质期提醒</h1><p>按紧急程度排序；环境异常时会缩短建议食用期限。</p></div><button class="primary-btn" @click="go('inventory')">查看全部库存</button></section><section class="expiry-summary"><article><span class="status-dot urgent"></span><strong>{{ foods.filter(food => food.status === 'urgent').length }}</strong><small>今天优先处理</small></article><article><span class="status-dot soon"></span><strong>{{ foods.filter(food => food.status === 'soon').length }}</strong><small>3 天内到期</small></article><article><span class="status-dot fresh"></span><strong>{{ foods.filter(food => food.status === 'fresh').length }}</strong><small>保存良好</small></article></section><div class="filter-pills expiry-filters"><button v-for="filter in [['全部', '全部'], ['紧急', 'urgent'], ['即将到期', 'soon'], ['良好', 'fresh']]" :key="filter[1]" :class="{ active: expiryFilter === filter[1] }" @click="expiryFilter = filter[1]">{{ filter[0] }}</button></div><section class="expiry-list"><article v-for="food in expiryFoods" :key="food.id" :class="food.status"><i>{{ food.icon }}</i><div><b>{{ food.name }}</b><small>入库 {{ food.received }} · 预计到期 {{ dateAfter(food.days) }} · {{ food.zone }}</small><em>{{ food.source }}</em></div><strong>{{ food.days === 0 ? '今天处理' : `${food.days} 天内` }}</strong><label>提醒日期<input v-model="food.reminder" type="date" /></label><button title="删除食材" @click="deleteFood(food)"><span v-html="icon('trash', 17)"></span></button></article></section>
        </template>

        <template v-else-if="page === 'recipes'">
          <section class="page-intro"><div><p class="eyebrow">AI 根据库存与偏好生成</p><h1>菜谱生成</h1><p>已避开 {{ preferences.allergies.concat(preferences.dislikes).join('、') }}，并优先匹配{{ preferences.goal }}目标。</p></div><div class="intro-actions"><button class="secondary-btn" @click="openInventoryRecipeSelector"><span v-html="icon('list', 18)"></span>选择库存食材</button><button class="secondary-btn" @click="showRecipeNameGenerator = true"><span v-html="icon('edit', 18)"></span>按菜名生成</button><button class="primary-btn" :disabled="isGeneratingRecipe" @click="generateInventoryRecipe"><span v-html="icon('spark', 18)"></span>{{ isGeneratingRecipe ? 'AI 生成中' : 'AI 生成 3 张菜谱' }}</button></div></section>
          <section v-if="generatedRecipes.length" class="generated-recipes"><div class="generated-recipes-head"><div><p class="eyebrow">AI 候选结果</p><h2>{{ generatedRecipeSelectionCount ? `按已选 ${generatedRecipeSelectionCount} 项库存食材生成` : '选择想保存的菜谱' }}</h2><p>可多选；未保存的候选结果会在关闭后丢弃。</p></div><div class="generated-recipe-actions"><button class="secondary-btn" @click="discardGeneratedRecipes">取消本次生成</button><button class="primary-btn" :disabled="!selectedGeneratedIds.length" @click="saveGeneratedRecipes">保存已选 {{ selectedGeneratedIds.length ? `(${selectedGeneratedIds.length})` : '' }}</button></div></div><div class="recipe-gallery candidate-gallery"><article v-for="recipe in generatedRecipes" :key="recipe.id" class="recipe-card candidate-card" :class="{ selected: selectedGeneratedIds.includes(recipe.id) }"><button class="candidate-check" :aria-pressed="selectedGeneratedIds.includes(recipe.id)" :title="selectedGeneratedIds.includes(recipe.id) ? '取消选择' : '选择菜谱'" @click="toggleGeneratedRecipe(recipe)"><span v-html="icon('check', 16)"></span></button><div class="recipe-art" :style="{ background: recipe.color }"><span>{{ recipe.art }}</span><b>{{ recipe.match }}% 匹配</b></div><div class="recipe-info"><small>{{ recipe.level }}</small><h3>{{ recipe.name }}</h3><p>{{ recipe.desc }}</p><div class="recipe-metrics"><span>⏱ {{ recipe.time }} 分钟</span><span>≈ {{ recipe.kcal }} 千卡</span></div><div class="recipe-ingredients"><small>所需食材</small><span v-for="ingredient in recipe.ingredients" :key="ingredient.name" :class="{ missing: recipe.missing.includes(ingredient.name) }">{{ ingredient.name }} {{ ingredient.amount }}{{ ingredient.unit }}</span></div><div class="recipe-card-actions"><button @click="openCooking(recipe)">查看做法</button><button v-if="recipe.missing.length" @click="addMissing(recipe)">加入缺料</button></div></div></article></div></section>
          <div class="filter-pills recipe-filters"><button v-for="filter in ['全部推荐', '可直接制作', '30 分钟内', '高蛋白', '低于 400 千卡', '收藏']" :key="filter" :class="{ active: recipeFilter === filter }" @click="recipeFilter = filter">{{ filter }}</button></div><section v-if="visibleRecipes.length" class="recipe-gallery"><article v-for="recipe in visibleRecipes" :key="recipe.id" class="recipe-card"><div class="recipe-art" :style="{ background: recipe.color }"><span>{{ recipe.art }}</span><b>{{ recipe.match }}% 匹配</b><div class="recipe-art-actions"><button title="收藏菜谱" :class="{ saved: recipe.collected }" @click="recipe.collected = !recipe.collected"><span v-html="icon('heart', 18)"></span></button><button title="删除菜谱" @click="deleteRecipe(recipe)"><span v-html="icon('trash', 17)"></span></button></div></div><div class="recipe-info"><small>{{ recipe.level }}</small><h3>{{ recipe.name }}</h3><p>{{ recipe.desc }}</p><div class="recipe-metrics"><span>⏱ {{ recipe.time }} 分钟</span><span>≈ {{ recipe.kcal }} 千卡</span></div><div class="recipe-card-actions"><button @click="openCooking(recipe)">查看做法</button><button v-if="recipe.missing.length" @click="addMissing(recipe)">加入缺料</button></div></div></article></section><section v-else class="recipe-empty"><span v-html="icon('book', 30)"></span><h2>还没有已保存的菜谱</h2><p>让 AI 根据库存一次生成 3 个方案，选择后保存。</p></section>
        </template>

        <template v-else-if="page === 'cooking'">
          <section v-if="!cookingRecipe" class="empty-state"><span v-html="icon('pan', 32)"></span><h1>选择一道菜开始制作</h1><button class="primary-btn" @click="go('recipes')">浏览菜谱</button></section><template v-else><section class="page-intro cooking-intro"><div><p class="eyebrow">做菜模式 · AI 实时换算</p><h1>{{ cookingRecipe.name }}</h1><p>{{ cookingRecipe.time }} 分钟 · 食材{{ cookingRecipe.missing.length ? `缺少 ${cookingRecipe.missing.join('、')}` : '齐全' }}</p></div><button class="secondary-btn" @click="go('recipes')">切换菜品</button></section><section class="cooking-layout"><aside class="cooking-control"><label>主料重量<input v-model.number="cookingWeight" type="number" min="1" /><span>克</span></label><div class="cooking-kcal"><small>本次总热量</small><strong>{{ scaledKcal }}</strong><span>千卡</span></div><h3>所需食材</h3><div v-for="item in scaledIngredients" :key="item.name" class="ingredient-line"><span>{{ item.name }}</span><b>{{ item.display }} {{ item.unit }}</b></div><h3>调味品</h3><div v-for="item in cookingRecipe.seasonings" :key="item.name" class="ingredient-line"><span>{{ item.name }}</span><b>{{ item.amount }} {{ item.unit }}</b></div></aside><section class="cooking-steps"><div class="cooking-progress"><button v-for="(step, index) in cookingRecipe.steps" :key="index" :class="{ active: activeCookingStep === index, done: index < activeCookingStep }" @click="activeCookingStep = index"><span>{{ index + 1 }}</span></button></div><article><p>步骤 {{ activeCookingStep + 1 }} / {{ cookingRecipe.steps.length }}</p><h2>{{ cookingRecipe.steps[activeCookingStep] }}</h2><div><button class="secondary-btn" :disabled="activeCookingStep === 0" @click="activeCookingStep--">上一步</button><button v-if="activeCookingStep < cookingRecipe.steps.length - 1" class="primary-btn" @click="activeCookingStep++">下一步</button><button v-else class="primary-btn" @click="completeCooking">完成制作</button></div></article></section></section></template>
        </template>

        <template v-else-if="page === 'diet'">
          <section class="page-intro"><div><p class="eyebrow">2026 年 8 月 14 日 · 今日</p><h1>饮食健康</h1><p>记录每餐与份量，AI 从热量和营养维度给出日常建议。</p></div><button class="primary-btn" @click="showMealEditor = true"><span v-html="icon('plus', 18)"></span>记录一餐</button></section><section class="nutrition-grid"><article class="calorie-card"><div class="ring large" :style="{ '--p': caloriePercent }"><div><strong>{{ totalCalories.toLocaleString() }}</strong><span>目标 {{ preferences.target.toLocaleString() }} 千卡</span></div></div><div><p class="eyebrow">今日摄入 · {{ caloriePercent }}%</p><h2>{{ totalCalories > preferences.target ? `已超出 ${totalCalories - preferences.target} 千卡` : `还可摄入 ${preferences.target - totalCalories} 千卡` }}</h2><p>晚餐优先选择低油烹饪，蛋白质和蔬菜仍可适量补充。</p></div></article><article class="ai-advice"><span v-html="icon('spark', 24)"></span><div><p class="eyebrow">AI 健康评分 · 82 / 100</p><h2>今晚把蔬菜和蛋白质补齐</h2><p>营养均衡 84 分，蔬菜摄入 72 分，蛋白质 88 分，热量控制 85 分。</p></div></article></section><section class="meal-log"><div class="section-head"><div><p class="eyebrow">时间线</p><h2>今天吃过这些</h2></div><button class="text-btn" @click="showMealEditor = true">添加记录</button></div><div v-for="meal in mealRecords" :key="meal.id" class="meal-row"><time>{{ meal.time }}</time><i>{{ meal.icon }}</i><span><b>{{ meal.name }}</b><small>{{ meal.food }}</small></span><strong>{{ meal.kcal }} <small>千卡</small></strong><button title="删除记录" @click="mealRecords.splice(mealRecords.indexOf(meal), 1)"><span v-html="icon('trash', 16)"></span></button></div></section><section class="diet-insights"><div class="section-head"><div><p class="eyebrow">本周趋势</p><h2>消耗与营养概览</h2></div><span>单位：kg</span></div><div class="diet-insight-grid"><article class="bar-chart semantic-bars"><div class="bars"><div v-for="item in [{ name: '蔬菜', value: 86, tone: 'vegetable' }, { name: '肉蛋', value: 68, tone: 'protein' }, { name: '乳制品', value: 48, tone: 'dairy' }, { name: '主食', value: 36, tone: 'staple' }]" :key="item.name" :class="item.tone"><i :style="{ height: item.value + '%' }"><b>{{ (item.value / 10).toFixed(1) }}</b></i><span>{{ item.name }}</span></div></div></article><article class="ranking-chart"><p class="eyebrow">常吃食材</p><h2>本周前 5 名</h2><ol><li v-for="(item, index) in [['鸡胸肉', '1.8 kg'], ['上海青', '1.4 kg'], ['无糖酸奶', '0.9 kg'], ['鸡蛋', '0.7 kg'], ['三文鱼', '0.5 kg']]" :key="item[0]"><b>{{ index + 1 }}</b><span>{{ item[0] }}</span><em>{{ item[1] }}</em></li></ol></article></div></section>
        </template>

        <template v-else-if="page === 'shopping'">
          <section class="page-intro"><div><p class="eyebrow">自动补货与菜谱缺料</p><h1>采购清单</h1><p>采购状态由你决定；入库时再确认实际数量和存放位置。</p></div><div class="intro-actions"><button class="secondary-btn" @click="exportShopping"><span v-html="icon('download', 18)"></span>导出 txt</button><button class="primary-btn" @click="openShoppingEditor()"><span v-html="icon('plus', 18)"></span>添加项目</button></div></section><div class="filter-pills shopping-filters"><button v-for="group in ['全部', '蔬果', '主食', '调味品', '菜谱缺料', '其他']" :key="group" :class="{ active: shoppingGroup === group }" @click="shoppingGroup = group">{{ group }}</button></div><section class="shopping-layout"><div class="shopping-list"><div class="shopping-head"><h2>{{ pendingShoppingCount }} 项待购买</h2><span>预计 ¥86</span></div><article v-for="item in visibleShopping" :key="item.id" class="shop-item" :class="`status-${item.status}`"><span class="shop-group">{{ item.group }}</span><span><b>{{ item.name }}</b><small>{{ item.note }}</small></span><em>{{ item.amount }}</em><select :value="item.status" :aria-label="`${item.name} 的采购状态`" @change="updateShoppingStatus(item, $event.target.value)"><option value="pending">待购买</option><option value="purchased">已购买</option><option value="stored">已入库</option></select><button title="编辑项目" @click="openShoppingEditor(item)"><span v-html="icon('edit', 16)"></span></button><button title="删除项目" @click="removeShoppingItem(item)"><span v-html="icon('trash', 16)"></span></button></article></div><aside class="smart-list restock-list"><span v-html="icon('spark', 24)"></span><h2>AI 补货依据</h2><p>根据常用库存、当前缺少食材和已保存菜谱生成，勾选后才会加入清单。</p><label v-for="item in restockCandidates" :key="item.id" class="restock-candidate"><input type="checkbox" :checked="selectedRestockIds.includes(item.id)" @change="toggleRestock(item.id)" /><span><b>{{ item.name }}</b><small>{{ item.current }} · 阈值 {{ item.threshold }}</small><em>{{ item.note }}，建议买 {{ item.amount }}</em></span></label><button class="secondary-btn" :disabled="!selectedRestockIds.length" @click="addSelectedRestock">加入已选补货项</button></aside></section><section class="module-history compact-history"><div class="section-head"><div><p class="eyebrow">采购历史</p><h2>最近入库</h2></div></div><article v-for="item in histories['采购记录'].slice(0, 4)" :key="item.id"><span v-html="icon('bag', 18)"></span><div><b>{{ item.title }}</b><small>{{ item.meta }}</small></div><em>{{ item.note }}</em><button title="删除入库记录" @click="removePurchaseHistory(item)"><span v-html="icon('trash', 15)"></span></button></article></section>
        </template>

        <template v-else-if="page === 'settings'">
          <section class="page-intro"><div><p class="eyebrow">账户、偏好与冰箱分区</p><h1>设置</h1><p>过敏与忌口会作为菜谱推荐的硬性排除条件。</p></div><button class="primary-btn" @click="saveProfile">保存更改</button></section><section class="settings-layout"><div class="settings-main"><article class="setting-card"><div class="setting-title"><span v-html="icon('spark', 22)"></span><div><h2>饮食偏好</h2><p>推荐会自动遵循这些选择</p></div></div><label>口味偏好</label><div class="choice-row"><button v-for="taste in ['清淡', '少油', '低盐', '微辣', '中辣', '少糖']" :key="taste" :class="{ selected: preferences.tastes.includes(taste) }" @click="toggleTag(preferences.tastes, taste)"><span v-html="icon('check', 14)"></span>{{ taste }}</button></div><label>菜系偏好</label><div class="choice-row"><button v-for="cuisine in ['家常菜', '粤菜', '川菜', '日料', '轻食']" :key="cuisine" :class="{ selected: preferences.cuisine.includes(cuisine) }" @click="toggleTag(preferences.cuisine, cuisine)"><span v-html="icon('check', 14)"></span>{{ cuisine }}</button></div><div class="field-pair"><label>饮食目标<select v-model="preferences.goal"><option>减脂</option><option>增肌</option><option>均衡饮食</option><option>控制热量</option></select></label><label>每日热量目标<div class="input-suffix"><input v-model.number="preferences.target" type="number" /><span>千卡</span></div></label></div></article>
            <article class="setting-card"><div class="setting-title"><span v-html="icon('box', 22)"></span><div><h2>冰箱分区与传感器</h2><p>当前值来自传感器，只能设置理想温湿度。</p></div><button class="text-btn" @click="addZone">+ 添加分区</button></div><div v-for="zone in zones" :key="zone.id" class="zone-editor"><div class="zone-editor-top zone-target-editor"><span class="zone-icon" :style="{ background: zone.color }"></span><input v-model="zone.name" aria-label="分区名称" /><label>理想温度<input v-model.number="zone.targetTemperature" type="number" step="0.1" /><small>°C</small></label><label>理想湿度<input v-model.number="zone.targetHumidity" type="number" /><small>%</small></label><button title="移除分区" @click="zones.length > 1 ? zones.splice(zones.indexOf(zone), 1) : notify('至少保留一个分区')"><span v-html="icon('trash', 16)"></span></button></div><div class="current-readings"><span><i v-html="icon('thermometer', 14)"></i>当前温度 <b>{{ zone.temp.toFixed(1) }} °C</b></span><span><i v-html="icon('drop', 14)"></i>当前湿度 <b>{{ zone.humidity }} %</b></span><small>{{ zone.update }} 更新 · 实测值不可手动修改</small></div><div class="sensor-list"><div v-for="sensor in zone.sensors" :key="sensor.id" class="sensor-chip"><span v-html="icon(sensor.type === 'temperature' ? 'thermometer' : 'drop', 15)"></span><div><b>{{ sensor.name }}</b><small>{{ sensor.type === 'temperature' ? '温度传感器' : '湿度传感器' }} · {{ sensor.update }}</small></div><strong>{{ sensor.value }} {{ sensor.unit }}</strong><button title="移除传感器" @click="zone.sensors.splice(zone.sensors.indexOf(sensor), 1)">×</button></div><button class="add-sensor-btn" @click="openSensorEditor(zone)"><span v-html="icon('plus', 15)"></span>添加传感器</button></div></div></article></div><aside class="account-card"><span class="avatar large-avatar">{{ profile.name.slice(0, 1) }}</span><h2>账户信息</h2><label>姓名<input v-model="profile.name" /></label><label>邮箱<input v-model="profile.email" type="email" /></label><hr /><h3>修改密码</h3><label>原密码<input v-model="profile.currentPassword" type="password" /></label><label>新密码<input v-model="profile.password" type="password" placeholder="至少 8 位" /></label><label>确认新密码<input v-model="profile.confirmPassword" type="password" /></label><div class="account-unit"><span>温度单位</span><span class="unit-switch"><button :class="{ on: unit === 'C' }" @click="unit = 'C'">℃</button><button :class="{ on: unit === 'F' }" @click="unit = 'F'">℉</button></span></div></aside></section>
        </template>
      </div>
    </main>

    <div v-if="showAdd" class="modal-backdrop" @click.self="showAdd = false"><form class="modal" @submit.prevent="addFood"><div class="modal-head"><div><p class="eyebrow">库存录入</p><h2>添加食材</h2></div><button type="button" @click="showAdd = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">名称<NameSuggestionInput v-model="newFood.name" context="ingredient" placeholder="例如：鸡胸肉" aria-label="食材名称" /></label><label>分类<select v-model="newFood.category"><option>蔬菜</option><option>水果</option><option>肉蛋</option><option>水产</option><option>豆制品</option><option>零食</option><option>饮料</option><option>调味品</option></select></label><label>存放位置<select v-model="newFood.zone"><option v-for="zone in zones" :key="zone.id">{{ zone.name }}</option><option>常温储物区</option></select></label><label>数量<input v-model="newFood.amount" type="number" placeholder="可留空" /></label><label>单位<select v-model="newFood.unit"><option>克</option><option>千克</option><option>个</option><option>盒</option><option>瓶</option><option>毫升</option></select></label><label>入库日期<input v-model="newFood.date" type="date" /></label><label>参考保质期<div class="input-suffix"><input v-model="newFood.shelf" type="number" /><span>天</span></div></label></div><div class="modal-actions"><button type="button" class="secondary-btn" @click="showAdd = false">取消</button><button class="primary-btn">放入冰箱</button></div></form></div>
    <div v-if="showFoodEditor && selectedFood" class="modal-backdrop" @click.self="showFoodEditor = false"><form class="modal compact-modal" @submit.prevent="saveFood"><div class="modal-head"><div><p class="eyebrow">库存编辑</p><h2>{{ foodDraft.name }}</h2></div><button type="button" @click="showFoodEditor = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">名称<NameSuggestionInput v-model="foodDraft.name" context="ingredient" aria-label="食材名称" /></label><label>分类<select v-model="foodDraft.category"><option>蔬菜</option><option>水果</option><option>肉蛋</option><option>水产</option><option>豆制品</option><option>零食</option><option>饮料</option><option>调味品</option></select></label><label>数量<input v-model="foodDraft.amount" type="number" min="0" /></label><label>单位<input v-model="foodDraft.unit" /></label><label>存放位置<select v-model="foodDraft.zone"><option v-for="zone in zones" :key="zone.id">{{ zone.name }}</option><option>常温储物区</option></select></label></div><div class="modal-actions"><button type="button" class="danger-btn" @click="deleteFood(selectedFood)">删除食材</button><button class="primary-btn">保存更改</button></div></form></div>
    <div v-if="showMealEditor" class="modal-backdrop" @click.self="showMealEditor = false"><form class="modal compact-modal meal-modal" @submit.prevent="recordMeal"><div class="modal-head"><div><p class="eyebrow">饮食记录</p><h2>记录一餐</h2></div><button type="button" @click="showMealEditor = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">菜品名称<NameSuggestionInput v-model="mealDraft.name" context="dish" placeholder="例如：鸡胸肉豆腐煲" aria-label="菜品名称" /></label><label>餐次<select v-model="mealDraft.meal"><option>早餐</option><option>午餐</option><option>晚餐</option><option>加餐</option></select></label><label>数量 / 重量<input v-model="mealDraft.amount" placeholder="可选" /></label><label>单位<select v-model="mealDraft.unit"><option>克</option><option>份</option><option>个</option></select></label></div><div class="meal-estimate" :class="{ loading: isEstimatingMeal, error: mealEstimateError }"><span v-html="icon('spark', 20)"></span><div v-if="isEstimatingMeal"><b>AI 正在估算热量</b><small>根据菜品名称和份量计算中</small></div><div v-else-if="mealEstimate"><b>AI 估算 {{ mealEstimate.calories }} 千卡</b><small>{{ mealEstimate.source }} · 蛋白质约 {{ mealEstimate.protein }} 克</small></div><div v-else-if="mealEstimateError"><b>{{ mealEstimateError }}</b><button type="button" class="text-btn" @click="estimateMealNutrition">重新估算</button></div><div v-else><b>输入菜品后由 AI 自动估算热量</b><small>热量不能手动修改</small></div></div><div class="modal-actions lowered-actions"><button type="button" class="secondary-btn" @click="showMealEditor = false">取消</button><button class="primary-btn" :disabled="!mealEstimate || isEstimatingMeal" >记录饮食</button></div></form></div>
    <div v-if="showShoppingEditor" class="modal-backdrop" @click.self="showShoppingEditor = false"><form class="modal compact-modal" @submit.prevent="saveShoppingItem"><div class="modal-head"><div><p class="eyebrow">采购清单</p><h2>{{ shopDraft.id ? '编辑项目' : '添加项目' }}</h2></div><button type="button" @click="showShoppingEditor = false"><span v-html="icon('close')"></span></button></div><div class="form-grid"><label class="wide">项目名称<NameSuggestionInput v-model="shopDraft.name" context="ingredient" placeholder="例如：燕麦片" aria-label="采购项目名称" /></label><label>分类<select v-model="shopDraft.group"><option>蔬果</option><option>主食</option><option>调味品</option><option>菜谱缺料</option><option>其他</option></select></label><label>数量 / 单位<input v-model="shopDraft.amount" placeholder="例如：1 袋" /></label><label class="wide">备注<input v-model="shopDraft.note" placeholder="例如：低于常用库存" /></label><label>采购状态<select v-model="shopDraft.status"><option value="pending">待购买</option><option value="purchased">已购买</option><option value="stored">已入库</option></select></label></div><div class="modal-actions"><button type="button" class="secondary-btn" @click="showShoppingEditor = false">取消</button><button class="primary-btn">保存项目</button></div></form></div>
    <div v-if="showPurchase && selectedShopItem" class="modal-backdrop" @click.self="showPurchase = false"><form class="modal compact-modal" @submit.prevent="confirmPurchase"><div class="modal-head"><div><p class="eyebrow">购买入库</p><h2>{{ selectedShopItem.name }}</h2></div><button type="button" @click="showPurchase = false"><span v-html="icon('close')"></span></button></div><p class="purchase-note">确认后会写入库存，并将该采购项标记为“已入库”。</p><div class="form-grid"><label>数量<input v-model="purchaseDraft.amount" type="number" /></label><label>单位<select v-model="purchaseDraft.unit"><option>克</option><option>个</option><option>盒</option><option>瓶</option><option>袋</option></select></label><label>存放位置<select v-model="purchaseDraft.zone"><option v-for="zone in zones" :key="zone.id">{{ zone.name }}</option><option>常温储物区</option></select></label><label>参考保质期<input v-model="purchaseDraft.shelf" type="number" /></label></div><div class="modal-actions"><button type="button" class="secondary-btn" @click="showPurchase = false">取消</button><button class="primary-btn">确认入库</button></div></form></div>
    <div v-if="showSensorEditor && sensorZone" class="modal-backdrop" @click.self="showSensorEditor = false"><form class="modal compact-modal sensor-modal" @submit.prevent="saveSensor"><div class="modal-head"><div><p class="eyebrow">{{ sensorZone.name }}</p><h2>添加传感器</h2></div><button type="button" @click="showSensorEditor = false"><span v-html="icon('close')"></span></button></div><label>传感器名称<input v-model="sensorDraft.name" placeholder="例如：左侧上层温度探头" autofocus /></label><fieldset class="sensor-type-choice"><legend>传感器类型</legend><label><input v-model="sensorDraft.type" value="temperature" type="radio" /> 温度传感器</label><label><input v-model="sensorDraft.type" value="humidity" type="radio" /> 湿度传感器</label></fieldset><p class="sensor-form-note">当前读数将由设备同步，添加时不能手动填写温度或湿度。</p><div class="modal-actions"><button type="button" class="secondary-btn" @click="showSensorEditor = false">取消</button><button class="primary-btn">添加传感器</button></div></form></div>
    <div v-if="showInventoryRecipeSelector" class="modal-backdrop" @click.self="closeInventoryRecipeSelector"><form class="modal compact-modal inventory-recipe-modal" @submit.prevent="generateSelectedInventoryRecipes"><div class="modal-head"><div><p class="eyebrow">AI 菜谱生成</p><h2>选择库存食材</h2></div><button type="button" :disabled="isGeneratingRecipe" aria-label="关闭食材选择" @click="closeInventoryRecipeSelector"><span v-html="icon('close')"></span></button></div><p class="recipe-generator-note">选择这次想优先使用的食材和调味品，AI 会结合饮食偏好生成 3 个方案。</p><div class="inventory-selection-toolbar"><strong>已选 {{ selectedInventoryFoods.length }} 项</strong><span><button type="button" class="text-btn" :disabled="!selectableInventoryFoods.length" @click="selectAllInventoryRecipeIngredients">全选</button><button type="button" class="text-btn" :disabled="!selectedInventoryFoods.length" @click="clearInventoryRecipeIngredients">清空</button></span></div><div v-if="selectableInventoryFoods.length" class="inventory-recipe-options"><label v-for="food in selectableInventoryFoods" :key="food.id" class="inventory-recipe-option" :class="{ selected: selectedInventoryIngredientIds.includes(food.id) }"><input type="checkbox" :checked="selectedInventoryIngredientIds.includes(food.id)" @change="toggleInventoryRecipeIngredient(food)" /><i>{{ food.icon }}</i><span><b>{{ food.name }}</b><small>{{ food.category }} · {{ food.zone }}</small></span><em>{{ food.amount }}{{ food.unit }}</em></label></div><div v-else class="inventory-recipe-empty"><span v-html="icon('box', 24)"></span><p>暂无可用于生成菜谱的库存食材。</p></div><div class="modal-actions"><button type="button" class="secondary-btn" :disabled="isGeneratingRecipe" @click="closeInventoryRecipeSelector">取消</button><button class="primary-btn" :disabled="!selectedInventoryFoods.length || isGeneratingRecipe">{{ isGeneratingRecipe ? 'AI 生成中' : `使用已选食材生成 (${selectedInventoryFoods.length})` }}</button></div></form></div>
    <div v-if="showRecipeNameGenerator" class="modal-backdrop" @click.self="showRecipeNameGenerator = false"><form class="modal compact-modal" @submit.prevent="generateNamedRecipe"><div class="modal-head"><div><p class="eyebrow">AI 菜谱生成</p><h2>按菜名生成</h2></div><button type="button" @click="showRecipeNameGenerator = false"><span v-html="icon('close')"></span></button></div><label>菜名<NameSuggestionInput v-model="recipeNameDraft" context="dish" placeholder="例如：番茄虾仁意面" aria-label="菜名" /></label><p class="recipe-generator-note">AI 会结合菜名、当前库存和饮食偏好生成 3 个可选择的完整方案。</p><div class="modal-actions"><button type="button" class="secondary-btn" @click="showRecipeNameGenerator = false">取消</button><button class="primary-btn" :disabled="isGeneratingRecipe">{{ isGeneratingRecipe ? 'AI 生成中' : '生成 3 张菜谱' }}</button></div></form></div>
    <transition name="toast"><div v-if="toast" class="toast"><span v-html="icon('check', 17)"></span>{{ toast }}</div></transition><AssistantPet v-model:open="assistantOpen" v-model:input="assistantInput" :page-name="assistantPageName" :messages="assistantMessages" :image="pixelPet" @send="sendAssistantMessage" />
  </div>
</template>
