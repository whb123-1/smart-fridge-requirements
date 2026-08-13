<script setup>
import { computed, reactive, ref } from 'vue'
import { api } from './services/api'
import pixelPet from './assets/xianling-pixel-pet-transparent.png'

const page = ref('home')
const unit = ref('C')
const inventoryFilter = ref('全部')
const inventoryType = ref('食材')
const showAdd = ref(false)
const showLogin = ref(false)
const showProfile = ref(false)
const showNotice = ref(false)
const showAssistant = ref(false)
const toast = ref('')
const isListening = ref(false)
const search = ref('')
const loggedIn = ref(true)
const assistantInput = ref('')
const assistantMessages = reactive([
  { id: 1, role: 'assistant', text: '你好，我是鲜知助手。可以问我功能、冰箱状态，或让我们一起看看今天能做什么。' },
])

const nav = [
  ['home', '首页', 'home'], ['inventory', '库存', 'box'], ['recipes', '菜谱', 'book'],
  ['diet', '饮食', 'spark'], ['shopping', '购物', 'bag'], ['stats', '统计', 'chart'],
]

const zones = reactive([
  { id: 1, name: '冷藏区', temp: 3.5, humidity: 68, range: '2–5°C', state: 'normal', source: '米家传感器', update: '刚刚', items: 18, color: '#5b8f7a' },
  { id: 2, name: '保鲜抽屉', temp: 1.8, humidity: 86, range: '0–3°C', state: 'normal', source: '内置传感器', update: '2 分钟前', items: 9, color: '#86a56a' },
  { id: 3, name: '变温区', temp: 7.2, humidity: 61, range: '2–6°C', state: 'warning', source: '内置传感器', update: '4 分钟前', items: 6, color: '#e37b62' },
  { id: 4, name: '冷冻区', temp: -18.2, humidity: 42, range: '-24––18°C', state: 'normal', source: '米家传感器', update: '刚刚', items: 12, color: '#6492a7' },
])

const foods = reactive([
  { id: 1, name: '上海青', icon: '🥬', category: '蔬菜', zone: '保鲜抽屉', amount: 420, unit: '克', calories: 18, days: 1, status: 'urgent', percent: 16 },
  { id: 2, name: '鲜牛奶', icon: '🥛', category: '饮料', zone: '冷藏区', amount: 680, unit: '毫升', calories: 54, days: 2, status: 'soon', percent: 28 },
  { id: 3, name: '鸡胸肉', icon: '🍗', category: '肉蛋', zone: '冷藏区', amount: 520, unit: '克', calories: 133, days: 3, status: 'soon', percent: 42 },
  { id: 4, name: '北豆腐', icon: '◻️', category: '豆制品', zone: '冷藏区', amount: 2, unit: '盒', calories: 116, days: 4, status: 'fresh', percent: 58 },
  { id: 5, name: '鸡蛋', icon: '🥚', category: '肉蛋', zone: '冷藏区', amount: 8, unit: '个', calories: 144, days: 12, status: 'fresh', percent: 78 },
  { id: 6, name: '三文鱼', icon: '🐟', category: '水产', zone: '冷冻区', amount: 300, unit: '克', calories: 208, days: 26, status: 'fresh', percent: 88 },
  { id: 7, name: '无糖酸奶', icon: '🥣', category: '零食', zone: '冷藏区', amount: 3, unit: '杯', calories: 72, days: 6, status: 'fresh', percent: 64 },
  { id: 8, name: '低钠生抽', icon: '🍶', category: '调味品', zone: '常温储物区', amount: 320, unit: '毫升', calories: 63, days: 120, status: 'fresh', percent: 70 },
])

const recipes = reactive([
  { id: 1, name: '鸡胸肉豆腐煲', desc: '清淡不寡淡，正好消耗临期鸡胸肉', time: 25, kcal: 386, protein: 42, match: 100, level: '可直接制作', color: '#dfe9df', art: '🍲', tags: ['高蛋白', '少油'], favorite: true },
  { id: 2, name: '蒜蓉上海青', desc: '8 分钟快手菜，先吃掉最新鲜的蔬菜', time: 8, kcal: 96, protein: 4, match: 100, level: '可直接制作', color: '#e8edd6', art: '🥬', tags: ['低热量', '快手'], favorite: false },
  { id: 3, name: '香煎三文鱼温沙拉', desc: '缺少小番茄，可用苹果替代增加清甜', time: 22, kcal: 438, protein: 32, match: 88, level: '可替代制作', color: '#f1e1d6', art: '🥗', tags: ['均衡', '优质脂肪'], favorite: false },
  { id: 4, name: '酸奶燕麦杯', desc: '适合作为明早的轻盈早餐', time: 5, kcal: 268, protein: 13, match: 82, level: '缺 1 样食材', color: '#e2e8ec', art: '🥣', tags: ['早餐', '免烹饪'], favorite: true },
])

const shopping = reactive([
  { id: 1, name: '小番茄', note: '三文鱼温沙拉需要', amount: '250 克', checked: false, group: '蔬果' },
  { id: 2, name: '燕麦片', note: '低于常用库存', amount: '1 袋', checked: false, group: '主食' },
  { id: 3, name: '柠檬', note: '2 道收藏菜谱需要', amount: '2 个', checked: true, group: '蔬果' },
  { id: 4, name: '黑胡椒', note: '调味品即将用完', amount: '1 瓶', checked: false, group: '调味品' },
])

const preferences = reactive({ tastes: ['清淡', '少油', '微辣'], allergies: ['花生'], dislikes: ['香菜'], goal: '减脂', target: 1650 })
const newFood = reactive({ name: '', category: '蔬菜', amount: '', unit: '克', zone: '冷藏区', date: '2026-08-12', shelf: '7' })

const alerts = computed(() => foods.filter(f => f.days <= 3))
const filteredFoods = computed(() => foods.filter(f => {
  const typeOkay = inventoryType.value === '食材' ? !['零食', '饮料'].includes(f.category) : ['零食', '饮料'].includes(f.category)
  const zoneOkay = inventoryFilter.value === '全部' || f.zone === inventoryFilter.value
  const queryOkay = !search.value || f.name.includes(search.value)
  return typeOkay && zoneOkay && queryOkay
}))
const temp = c => unit.value === 'C' ? c.toFixed(1) : (c * 9 / 5 + 32).toFixed(1)
const tempSymbol = computed(() => unit.value === 'C' ? '°C' : '°F')
const assistantPageName = computed(() => ({ home: '首页', inventory: '库存', recipes: '菜谱', diet: '饮食', shopping: '购物', stats: '统计', settings: '设置' })[page.value] || '首页')

function icon(name, size = 20) {
  const paths = {
    home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5M9 21v-7h6v7"/>',
    box: '<path d="M4 7.5 12 3l8 4.5v9L12 21l-8-4.5z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9"/>',
    book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V4H6.5A2.5 2.5 0 0 0 4 6.5z"/><path d="M4 6.5v13M8 8h8"/>',
    spark: '<path d="m12 3 1.7 4.3L18 9l-4.3 1.7L12 15l-1.7-4.3L6 9l4.3-1.7z"/><path d="m18.5 15 .8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z"/>',
    bag: '<path d="M5 8h14l-1 13H6z"/><path d="M9 10V6a3 3 0 0 1 6 0v4"/>',
    chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    plus: '<path d="M12 5v14M5 12h14"/>', search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
    chevron: '<path d="m9 18 6-6-6-6"/>', check: '<path d="m5 12 4 4L19 6"/>',
    heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.7-7.5a5.5 5.5 0 0 0 1.1-8.9z"/>',
    mic: '<path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2M12 19v3"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>', settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1z"/>',
    logout: '<path d="M10 17l5-5-5-5M15 12H3M15 3h6v18h-6"/>', camera: '<path d="M4 7h3l2-3h6l2 3h3v13H4z"/><circle cx="12" cy="13" r="4"/>',
  }
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.spark}</svg>`
}

function notify(message) {
  toast.value = message
  setTimeout(() => { if (toast.value === message) toast.value = '' }, 2400)
}
async function addFood() {
  if (!newFood.name || !newFood.amount) return notify('请先填写食材名称和数量')
  const created = await api.addFood({ ...newFood })
  foods.unshift({ ...created, icon: '🥕', calories: 50, days: Number(newFood.shelf), status: 'fresh', percent: 92 })
  showAdd.value = false
  Object.assign(newFood, { name: '', amount: '' })
  notify('食材已放入冰箱')
}
function consume(food) {
  food.amount = Math.max(0, Number(food.amount) - 1)
  notify(`已记录 ${food.name} 的消耗`)
}
function login() {
  loggedIn.value = true; showLogin.value = false; notify('欢迎回到鲜知')
}
function toggleTaste(taste) {
  const i = preferences.tastes.indexOf(taste)
  i >= 0 ? preferences.tastes.splice(i, 1) : preferences.tastes.push(taste)
}
function getAssistantReply(question) {
  const text = question.toLowerCase()
  if (/菜谱|做什么|推荐|吃什么/.test(text)) return `你可以在“菜谱”板块查看库存匹配的推荐。当前优先推荐${recipes[0].name}，预计 ${recipes[0].time} 分钟，${recipes[0].kcal} 千卡。`
  if (/冰箱|温度|湿度|分区|状态/.test(text)) return `冰箱有 ${zones.length} 个分区，${zones.filter(zone => zone.state === 'normal').length} 个状态正常。变温区当前 ${temp(zones[2].temp)}${tempSymbol.value}，建议进入冰箱设置查看传感器与建议范围。`
  if (/库存|食材|添加|临期/.test(text)) return `目前有 ${foods.length} 项库存，其中 ${alerts.value.length} 项建议优先处理。点击左侧“查看库存”可查看明细；“添加食材”和“语音添加”可用于入库。`
  if (/饮食|热量|记录/.test(text)) return '饮食板块会汇总每日热量和主要营养信息。通过“记录一餐”添加饮食记录，再根据当天摄入调整晚餐选择。'
  if (/购物|采购/.test(text)) return `购物清单当前有 ${shopping.filter(item => !item.checked).length} 项待购买，会结合低库存和菜谱缺少的食材生成。`
  if (/功能|帮助|怎么用/.test(text)) return '首页围绕冰箱提供库存、添加、饮食、购物、统计和设置入口。点击任意分区可查看传感器设置，点击周围按钮可进入对应功能页。'
  return `我现在位于${assistantPageName.value}。可以问我“怎么添加食材”“冰箱状态如何”或“今天能做什么”。`
}
function sendAssistantMessage(preset = '') {
  const text = (preset || assistantInput.value).trim()
  if (!text) return
  assistantMessages.push({ id: Date.now(), role: 'user', text })
  assistantInput.value = ''
  assistantMessages.push({ id: Date.now() + 1, role: 'assistant', text: getAssistantReply(text) })
}
</script>

<template>
  <div class="app-shell" :class="{ 'home-shell': page === 'home', 'assistant-open': showAssistant }">
    <aside v-if="false" class="sidebar">
      <button class="brand" @click="page = 'home'" aria-label="鲜知首页">
        <span class="brand-mark"><i></i><i></i><i></i></span>
        <span><b>鲜知</b><small>智能冰箱管家</small></span>
      </button>
      <nav aria-label="主导航">
        <button v-for="item in nav" :key="item[0]" :class="{ active: page === item[0] }" @click="page = item[0]">
          <span v-html="icon(item[2])"></span><span>{{ item[1] }}</span>
          <em v-if="item[0] === 'shopping'">3</em>
        </button>
      </nav>
      <div class="side-bottom">
        <button :class="{ active: page === 'settings' }" @click="page = 'settings'"><span v-html="icon('settings')"></span>设置</button>
        <div class="eco-note"><span>本月少浪费</span><strong>4.8<small> kg</small></strong><p>比上月进步 18%</p></div>
      </div>
    </aside>

    <main :class="{ 'home-main': page === 'home' }">
      <header v-if="false" class="topbar">
        <div class="topbar-start">
          <button class="back-home" @click="page='home'" aria-label="返回主界面"><span v-html="icon('home',17)"></span><span>返回首页</span></button>
          <label class="global-search"><span v-html="icon('search', 18)"></span><input v-model="search" placeholder="搜索食材或菜谱…" /></label>
        </div>
        <div class="top-actions">
          <button class="icon-button notice-button" @click="showNotice = !showNotice" aria-label="通知"><span v-html="icon('bell')"></span><i></i></button>
          <button class="user-chip" @click="showProfile = !showProfile"><span class="avatar">夏</span><span><b>{{ loggedIn ? '林知夏' : '未登录' }}</b><small>{{ loggedIn ? '减脂计划 · 第 26 天' : '登录同步数据' }}</small></span><span v-html="icon('chevron', 16)"></span></button>
        </div>
        <div v-if="showNotice" class="pop-panel notice-panel"><b>今天的提醒</b><p><i class="dot coral"></i>上海青建议今天食用</p><p><i class="dot amber"></i>变温区温度偏高 1.2°C</p><p><i class="dot green"></i>饮食目标完成 76%</p></div>
        <div v-if="showProfile" class="pop-panel profile-panel"><button @click="page='settings';showProfile=false"><span v-html="icon('settings',17)"></span>账号与偏好</button><button @click="loggedIn=false;showProfile=false;notify('已退出登录')"><span v-html="icon('logout',17)"></span>退出登录</button></div>
      </header>

      <div class="content" :class="{ 'home-content': page === 'home' }">
        <button v-if="page !== 'home'" class="page-home-link" @click="page='home'" aria-label="Return home"><span v-html="icon('home',17)"></span><span>返回首页</span></button>
        <!-- 首页 -->
        <template v-if="page === 'home'">
          <section class="home-console" aria-label="冰箱总览">
            <div class="home-organic organic-left" aria-hidden="true"></div>
            <div class="home-organic organic-right" aria-hidden="true"></div>
            <div class="orbit-status orbit-panel">
              <span class="home-console-brand"><b>鲜知</b><small>智能冰箱管家</small></span>
              <div><span><i class="online-dot"></i>4 个传感器在线</span><span>刚刚同步</span><span class="unit-switch home-unit"><button :class="{on:unit==='C'}" @click="unit='C'">℃</button><button :class="{on:unit==='F'}" @click="unit='F'">℉</button></span></div>
            </div>

            <div class="orbit-left orbit-panel">
              <button class="inventory-orbit" @click="page='inventory'">
                <span class="inventory-orbit-icon" v-html="icon('box',22)"></span>
                <span><small>冰箱库存</small><b>查看库存</b><em>{{ foods.length }} 项在库</em></span>
                <span class="inventory-orbit-arrow" v-html="icon('chevron',18)"></span>
              </button>
              <div class="orbit-actions orbit-actions-left" aria-label="常用操作">
                <button class="orbit-action action-add" @click="showAdd=true"><span v-html="icon('plus',22)"></span><b>添加食材</b></button>
                <button class="orbit-action" :class="{listening:isListening}" @click="isListening=!isListening"><span v-html="icon('mic',22)"></span><b>{{ isListening ? '正在聆听' : '语音添加' }}</b></button>
                <button class="orbit-action" @click="page='diet'"><span v-html="icon('spark',22)"></span><b>记录一餐</b></button>
              </div>
            </div>

            <article class="center-fridge" aria-label="冰箱分区状态">
              <div class="fridge-crown"><span></span><b>鲜知</b><i></i></div>
              <div class="fridge-shell">
                <button v-for="(zone, i) in zones" :key="zone.id" class="fridge-zone" :class="['flat-zone-'+i, zone.state]" @click="page='settings'">
                  <span class="flat-zone-name"><i></i>{{ zone.name }}<small>{{ zone.items }} 件</small></span>
                  <strong v-if="i === 0">{{ temp(zone.temp) }}<em>{{ tempSymbol }}</em></strong>
                  <span v-else class="flat-zone-temp">{{ temp(zone.temp) }}{{ tempSymbol }}</span>
                  <span class="flat-zone-meta" v-if="i === 0">湿度 {{ zone.humidity }}%</span>
                  <span class="flat-zone-meta warning-copy" v-if="zone.state === 'warning'">高于建议 1.2°C</span>
                </button>
              </div>
              <div class="fridge-feet"><i></i><i></i></div>
            </article>

            <div class="orbit-actions orbit-actions-right orbit-panel" aria-label="管理操作">
              <button class="orbit-action" @click="page='shopping'"><span v-html="icon('bag',22)"></span><b>购物清单</b><em>3</em></button>
              <button class="orbit-action" @click="page='stats'"><span v-html="icon('chart',22)"></span><b>数据统计</b></button>
              <button class="orbit-action" @click="page='settings'"><span v-html="icon('settings',22)"></span><b>冰箱设置</b></button>
            </div>
            <div class="orbit-actions-mobile" aria-label="快捷操作">
              <button class="orbit-action action-add" @click="showAdd=true"><span v-html="icon('plus',22)"></span><b>添加食材</b></button>
              <button class="orbit-action" :class="{listening:isListening}" @click="isListening=!isListening"><span v-html="icon('mic',22)"></span><b>{{ isListening ? '正在聆听' : '语音添加' }}</b></button>
              <button class="orbit-action" @click="page='diet'"><span v-html="icon('spark',22)"></span><b>记录一餐</b></button>
              <button class="orbit-action" @click="page='shopping'"><span v-html="icon('bag',22)"></span><b>购物清单</b><em>3</em></button>
              <button class="orbit-action" @click="page='stats'"><span v-html="icon('chart',22)"></span><b>数据统计</b></button>
              <button class="orbit-action" @click="page='settings'"><span v-html="icon('settings',22)"></span><b>冰箱设置</b></button>
            </div>
          </section>
        </template>

        <!-- 库存 -->
        <template v-else-if="page === 'inventory'">
          <section class="page-intro"><div><p class="eyebrow">共 45 件</p><h1>冰箱库存</h1><p>按新鲜程度排好顺序，决定先吃什么。</p></div><div class="intro-actions"><button class="secondary-btn" @click="isListening=!isListening"><span v-html="icon('mic',18)"></span>语音添加</button><button class="primary-btn" @click="showAdd=true"><span v-html="icon('plus',18)"></span>添加食材</button></div></section>
          <div class="tabs"><button v-for="t in ['食材','零食饮料']" :class="{active:inventoryType===t}" @click="inventoryType=t">{{t}}</button></div>
          <section class="inventory-toolbar"><div class="filter-pills"><button v-for="f in ['全部','冷藏区','保鲜抽屉','变温区','冷冻区']" :class="{active:inventoryFilter===f}" @click="inventoryFilter=f">{{f}}</button></div><label class="mini-search"><span v-html="icon('search',16)"></span><input v-model="search" placeholder="搜索库存" /></label></section>
          <section class="inventory-table">
            <div class="table-head"><span>食材</span><span>存放位置</span><span>剩余数量</span><span>新鲜度 / 建议期限</span><span>热量</span><span></span></div>
            <div v-for="food in filteredFoods" :key="food.id" class="food-row"><span class="food-name"><i>{{food.icon}}</i><span><b>{{food.name}}</b><small>{{food.category}}</small></span></span><span><b>{{food.zone}}</b><small>环境正常</small></span><span><b>{{food.amount}} {{food.unit}}</b><small>单位可调整</small></span><span class="fresh-cell"><i><em :class="food.status" :style="{width:food.percent+'%'}"></em></i><small :class="food.status">{{food.days}} 天后建议食用完</small></span><span><b>{{food.calories}}</b><small>千卡 / 100克</small></span><span class="row-actions"><button @click="consume(food)">消耗</button><button>···</button></span></div>
            <div v-if="!filteredFoods.length" class="empty">没有找到符合条件的库存。<button @click="inventoryFilter='全部';search=''">清除筛选</button></div>
          </section>
          <p class="disclaimer">建议食用期限会参考包装、开封状态及分区温湿度动态估算。请同时检查外观、气味与包装标识。</p>
        </template>

        <!-- 菜谱 -->
        <template v-else-if="page === 'recipes'">
          <section class="page-intro"><div><p class="eyebrow">基于 45 件库存</p><h1>今晚，好好吃饭。</h1><p>所有推荐已避开花生与香菜，并优先匹配你的减脂目标。</p></div><button class="primary-btn" @click="notify('AI 正在根据你的描述生成菜谱')"><span v-html="icon('spark',18)"></span>指定菜品生成</button></section>
          <section class="recipe-hero"><i class="hero-organic hero-organic-one" aria-hidden="true"></i><i class="hero-organic hero-organic-two" aria-hidden="true"></i><div><span class="ai-label">今日首选 · 食材齐全</span><h2>鸡胸肉豆腐煲</h2><p>温润的一锅刚好适合今天。会用掉临期鸡胸肉和北豆腐，少油、低盐，蛋白质充足。</p><div class="hero-metrics"><span><small>烹饪</small><b>25 分钟</b></span><span><small>每份</small><b>386 千卡</b></span><span><small>蛋白质</small><b>42 克</b></span></div><button @click="notify('已进入烹饪模式，库存将在完成后扣减')">开始烹饪</button></div><div class="pot-art">🍲<i>100%<small>库存匹配</small></i></div></section>
          <div class="filter-pills recipe-filters"><button class="active">全部推荐</button><button>可直接制作</button><button>30 分钟内</button><button>高蛋白</button><button>低于 400 千卡</button></div>
          <section class="recipe-gallery"><article v-for="recipe in recipes" :key="recipe.id" class="recipe-card"><div class="recipe-art" :style="{background:recipe.color}"><span>{{recipe.art}}</span><b>{{recipe.match}}% 匹配</b><button @click="recipe.favorite=!recipe.favorite" :class="{saved:recipe.favorite}"><span v-html="icon('heart',18)"></span></button></div><div class="recipe-info"><small>{{recipe.level}}</small><h3>{{recipe.name}}</h3><p>{{recipe.desc}}</p><div><span>⏱ {{recipe.time}} 分钟</span><span>≈ {{recipe.kcal}} 千卡</span></div><footer><span v-for="tag in recipe.tags" :key="tag">{{tag}}</span></footer></div></article></section>
        </template>

        <!-- 饮食 -->
        <template v-else-if="page === 'diet'">
          <section class="page-intro"><div><p class="eyebrow">8 月 12 日 · 今日</p><h1>饮食记录</h1><p>不追求每一口精准，只看长期是否更均衡。</p></div><button class="primary-btn" @click="notify('已打开食物记录入口')"><span v-html="icon('plus',18)"></span>记录一餐</button></section>
          <section class="nutrition-grid"><article class="calorie-card"><div class="ring large" style="--p:76"><div><strong>1,248</strong><span>目标 1,650 千卡</span></div></div><div><p class="eyebrow">今日摄入</p><h2>还可摄入 402 千卡</h2><p>全天节奏不错，晚餐注意控制用油。</p></div></article><article class="ai-advice"><span v-html="icon('spark',24)"></span><div><p class="eyebrow">鲜知建议</p><h2>今晚把蔬菜和蛋白质补齐</h2><p>午餐脂肪偏高，建议晚餐用清蒸或炖煮。现有库存里的鸡胸肉、豆腐和上海青组合正合适。</p></div></article></section>
          <section class="meal-log"><div class="section-head"><div><p class="eyebrow">时间线</p><h2>今天吃过这些</h2></div></div><div v-for="meal in [{time:'08:10',name:'早餐',food:'无糖酸奶燕麦杯 · 1 份',kcal:268,icon:'🥣'},{time:'12:35',name:'午餐',food:'番茄牛肉盖饭 · 0.8 份',kcal:642,icon:'🍛'},{time:'15:20',name:'加餐',food:'苹果 · 1 个',kcal:92,icon:'🍎'}]" :key="meal.time" class="meal-row"><time>{{meal.time}}</time><i>{{meal.icon}}</i><span><b>{{meal.name}}</b><small>{{meal.food}}</small></span><strong>{{meal.kcal}} <small>千卡</small></strong><button>···</button></div><button class="add-meal" @click="notify('已打开食物记录入口')"><span v-html="icon('plus',18)"></span>记录晚餐</button></section>
        </template>

        <!-- 购物 -->
        <template v-else-if="page === 'shopping'">
          <section class="page-intro"><div><p class="eyebrow">自动补货</p><h1>购物清单</h1><p>根据低库存和想做的菜生成，买完就能自动入库。</p></div><button class="primary-btn" @click="shopping.push({id:Date.now(),name:'新项目',note:'手动添加',amount:'1 份',checked:false,group:'其他'})"><span v-html="icon('plus',18)"></span>添加项目</button></section>
          <section class="shopping-layout"><div class="shopping-list"><div class="shopping-head"><h2>{{shopping.filter(i=>!i.checked).length}} 项待购买</h2><span>预计 ¥86</span></div><label v-for="item in shopping" :key="item.id" class="shop-item" :class="{done:item.checked}"><input type="checkbox" v-model="item.checked" /><i><span v-html="icon('check',15)"></span></i><span><b>{{item.name}}</b><small>{{item.note}}</small></span><em>{{item.amount}}</em><button @click.prevent="shopping.splice(shopping.indexOf(item),1)">×</button></label></div><aside class="smart-list"><span v-html="icon('spark',24)"></span><h2>智能补货依据</h2><p>燕麦片和黑胡椒低于你设定的库存阈值；小番茄来自收藏菜谱。</p><div><span>低库存</span><b>2 项</b></div><div><span>菜谱缺料</span><b>2 项</b></div><div><span>已购买</span><b>{{shopping.filter(i=>i.checked).length}} 项</b></div><button class="secondary-btn" @click="notify('已将购买项目加入库存')">购买完成，加入库存</button></aside></section>
        </template>

        <!-- 统计 -->
        <template v-else-if="page === 'stats'">
          <section class="page-intro"><div><p class="eyebrow">8 月数据</p><h1>少浪费一点，就是进步。</h1><p>从消耗和丢弃记录里找到更合适的采购节奏。</p></div><div class="filter-pills"><button>本周</button><button class="active">本月</button><button>近 3 月</button></div></section>
          <section class="stat-cards"><article><small>食材消耗</small><strong>28.6 <em>kg</em></strong><p class="good">↑ 12% 有效利用</p></article><article><small>减少浪费</small><strong>4.8 <em>kg</em></strong><p class="good">比上月多挽救 0.7kg</p></article><article><small>过期 / 丢弃</small><strong>1.2 <em>kg</em></strong><p class="warn">叶菜占其中 46%</p></article></section>
          <section class="chart-grid"><article class="bar-chart"><div class="section-head"><div><p class="eyebrow">消耗趋势</p><h2>每周食材用量</h2></div><span>单位：kg</span></div><div class="bars"><div v-for="(v,i) in [52,68,61,86]" :key="i"><i :style="{height:v+'%'}"><b>{{(v/10).toFixed(1)}}</b></i><span>第 {{i+1}} 周</span></div></div></article><article class="waste-chart"><p class="eyebrow">浪费构成</p><h2>叶菜最容易买多</h2><div class="donut"><span><b>1.2</b><small>kg 丢弃</small></span></div><ul><li><i style="background:#e37b62"></i>叶菜 <b>46%</b></li><li><i style="background:#d9a15d"></i>乳制品 <b>24%</b></li><li><i style="background:#6c9181"></i>水果 <b>18%</b></li><li><i style="background:#c8d0cc"></i>其他 <b>12%</b></li></ul></article></section>
        </template>

        <!-- 设置 -->
        <template v-else-if="page === 'settings'">
          <section class="page-intro"><div><p class="eyebrow">个性化</p><h1>偏好与冰箱设置</h1><p>让每一次推荐更贴近你的生活。</p></div><button class="primary-btn" @click="api.updatePreferences(preferences);notify('设置已保存')">保存更改</button></section>
          <section class="settings-layout"><div class="settings-main"><article class="setting-card"><div class="setting-title"><span v-html="icon('spark',22)"></span><div><h2>饮食偏好</h2><p>推荐会自动遵循这些选择</p></div></div><label>口味偏好</label><div class="choice-row"><button v-for="t in ['清淡','少油','低盐','微辣','中辣']" :class="{selected:preferences.tastes.includes(t)}" @click="toggleTaste(t)"><span v-html="icon('check',14)"></span>{{t}}</button></div><div class="field-pair"><label>饮食目标<select v-model="preferences.goal"><option>减脂</option><option>增肌</option><option>均衡饮食</option><option>控制热量</option></select></label><label>每日热量目标<div class="input-suffix"><input v-model="preferences.target" type="number" /><span>千卡</span></div></label></div><label>过敏食材 <span class="required">严格避开</span></label><div class="tag-input"><span v-for="a in preferences.allergies">{{a}} <button @click="preferences.allergies.splice(preferences.allergies.indexOf(a),1)">×</button></span><input placeholder="输入后回车添加" /></div><label>忌口食材</label><div class="tag-input"><span v-for="a in preferences.dislikes">{{a}} <button @click="preferences.dislikes.splice(preferences.dislikes.indexOf(a),1)">×</button></span><input placeholder="输入后回车添加" /></div></article>
          <article class="setting-card"><div class="setting-title"><span v-html="icon('box',22)"></span><div><h2>冰箱分区与传感器</h2><p>分别管理温度、湿度与数据来源</p></div><button class="text-btn" @click="notify('已新建一个自定义分区')">+ 添加分区</button></div><div v-for="zone in zones" :key="zone.id" class="zone-setting"><span class="zone-icon" :style="{background:zone.color}"></span><span><b>{{zone.name}}</b><small>{{zone.source}} · {{zone.update}}更新</small></span><strong :class="zone.state">{{temp(zone.temp)}}{{tempSymbol}} <small>· {{zone.humidity}}%</small></strong><span class="range">建议 {{zone.range}}</span><button>编辑</button></div></article></div>
          <aside class="account-card"><span class="avatar large-avatar">夏</span><h2>林知夏</h2><p>xia@example.com</p><span class="plan">减脂计划 · 第 26 天</span><hr/><div><span>数据同步</span><b><i class="dot green"></i>正常</b></div><div><span>温度单位</span><span class="unit-switch"><button :class="{on:unit==='C'}" @click="unit='C'">℃</button><button :class="{on:unit==='F'}" @click="unit='F'">℉</button></span></div><button class="secondary-btn" @click="showLogin=true">切换账号</button><button class="logout-btn" @click="loggedIn=false;notify('已退出登录')">退出登录</button><p class="health-note">健康建议仅用于日常饮食管理，不用于疾病诊断或治疗。</p></aside></section>
        </template>
      </div>
    </main>

    <div v-if="showAdd" class="modal-backdrop" @click.self="showAdd=false">
      <form class="modal" @submit.prevent="addFood"><div class="modal-head"><div><p class="eyebrow">库存录入</p><h2>添加食材</h2></div><button type="button" @click="showAdd=false"><span v-html="icon('close')"></span></button></div><button type="button" class="voice-strip" :class="{listening:isListening}" @click="isListening=!isListening"><span v-html="icon('mic',22)"></span><span><b>{{isListening?'正在听你说…':'试试语音添加'}}</b><small>例如：“两盒牛奶，放冷藏区”</small></span></button><div class="form-grid"><label class="wide">名称<input v-model="newFood.name" placeholder="例如：鸡胸肉" autofocus /></label><label>分类<select v-model="newFood.category"><option>蔬菜</option><option>水果</option><option>肉蛋</option><option>水产</option><option>乳制品</option><option>调味品</option></select></label><label>存放位置<select v-model="newFood.zone"><option v-for="z in zones">{{z.name}}</option><option>常温储物区</option></select></label><label>数量<input v-model="newFood.amount" type="number" placeholder="0" /></label><label>单位<select v-model="newFood.unit"><option>克</option><option>千克</option><option>个</option><option>盒</option><option>瓶</option><option>毫升</option></select></label><label>入库日期<input v-model="newFood.date" type="date" /></label><label>参考保质期<div class="input-suffix"><input v-model="newFood.shelf" type="number"/><span>天</span></div></label></div><p class="estimate-note">保存后将结合所在分区的实际温湿度动态估算建议食用期限。</p><div class="modal-actions"><button type="button" class="secondary-btn" @click="showAdd=false">取消</button><button class="primary-btn">放入冰箱</button></div></form>
    </div>

    <div v-if="showLogin" class="modal-backdrop" @click.self="showLogin=false"><form class="modal login-modal" @submit.prevent="login"><div class="brand login-brand"><span class="brand-mark"><i></i><i></i><i></i></span><span><b>鲜知</b><small>让每一份新鲜被好好使用</small></span></div><h2>欢迎回来</h2><label>手机号或邮箱<input placeholder="输入账号" required /></label><label>密码<input type="password" placeholder="输入密码" required /></label><button class="primary-btn wide-button">登录</button><p>还没有账号？<button type="button" class="text-btn" @click="notify('注册入口已准备好')">创建账号</button></p></form></div>
    <transition name="toast"><div v-if="toast" class="toast"><span v-html="icon('check',17)"></span>{{toast}}</div></transition>
    <section class="ai-pet" :class="{ open: showAssistant }" aria-label="鲜知 AI 助手">
      <div v-if="showAssistant" class="ai-pet-panel" role="dialog" aria-label="与鲜知助手对话">
        <header class="ai-pet-head"><div><img class="ai-pet-mini" :src="pixelPet" alt="" /><div><small>鲜知 AI 助手</small><b>正在了解{{ assistantPageName }}</b></div></div><button @click="showAssistant=false" aria-label="关闭助手"><span v-html="icon('close',16)"></span></button></header>
        <div class="ai-pet-messages" aria-live="polite"><p v-for="message in assistantMessages" :key="message.id" :class="message.role">{{ message.text }}</p></div>
        <div class="ai-pet-prompts"><button @click="sendAssistantMessage('怎么添加食材')">添加食材</button><button @click="sendAssistantMessage('冰箱状态如何')">冰箱状态</button><button @click="sendAssistantMessage('今天能做什么')">今晚吃什么</button></div>
        <form class="ai-pet-input" @submit.prevent="sendAssistantMessage()"><input v-model="assistantInput" placeholder="问问鲜知助手" aria-label="输入问题" /><button :disabled="!assistantInput.trim()" aria-label="发送问题"><span v-html="icon('chevron',17)"></span></button></form>
      </div>
      <button class="ai-pet-trigger" @click="showAssistant=!showAssistant" :aria-expanded="showAssistant" aria-label="打开鲜知 AI 助手">
        <img class="ai-pet-face" :src="pixelPet" alt="" />
      </button>
    </section>
    <nav v-if="false" class="mobile-nav"><button v-for="item in nav.slice(0,5)" :class="{active:page===item[0]}" @click="page=item[0]"><span v-html="icon(item[2],19)"></span><small>{{item[1]}}</small></button></nav>
  </div>
</template>
