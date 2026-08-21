<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import answeringImage from '../assets/assistant-answering.gif'
import draggingImage from '../assets/assistant-dragging.gif'
import idleLoveImage from '../assets/assistant-idle-love.gif'
import idlePeekImage from '../assets/assistant-idle-peek.gif'

const idleImages = [idlePeekImage, idleLoveImage]

const props = defineProps({
  open: { type: Boolean, required: true },
  pageName: { type: String, required: true },
  messages: { type: Array, required: true },
  proposals: { type: Array, default: () => [] },
  busyProposalId: { type: String, default: null },
  input: { type: String, default: '' },
  answering: { type: Boolean, default: false },
  capabilities: { type: Object, default: null },
})

const emit = defineEmits(['update:open', 'update:input', 'send', 'confirm', 'dismiss'])

const rootRef = ref(null)
const panelRef = ref(null)
const manualOpen = ref(false)
const isDragging = ref(false)
const idleImageIndex = ref(Math.floor(Math.random() * idleImages.length))
const suppressClick = ref(false)
const hasPosition = ref(false)
const triggerSize = reactive({ width: 108, height: 108 })
const panelSize = reactive({ width: 328, height: 360 })
const viewport = reactive({
  width: typeof window === 'undefined' ? 1280 : window.innerWidth,
  height: typeof window === 'undefined' ? 800 : window.innerHeight,
})
const position = reactive({ x: 0, y: 0 })
const dragState = {
  active: false,
  pointerId: null,
  startX: 0,
  startY: 0,
  startLeft: 0,
  startTop: 0,
  moved: false,
}

const safeMargin = 12
const dragThreshold = 5
const idleDelayMin = 25_000
const idleDelayRange = 10_000
let idleRotationTimer = null

const closeIcon = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>'
const sendIcon = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>'
const bookIcon = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V4H6.5A2.5 2.5 0 0 0 4 6.5v13Z"/><path d="M8 8h8M8 12h6"/></svg>'
const backIcon = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>'

const rootStyle = computed(() => ({
  left: `${Math.round(position.x)}px`,
  top: `${Math.round(position.y)}px`,
}))

const currentImage = computed(() => {
  if (isDragging.value) return draggingImage
  if (props.answering) return answeringImage
  return idleImages[idleImageIndex.value]
})

const panelStyle = computed(() => {
  const panelWidth = Math.min(panelSize.width || 328, Math.max(1, viewport.width - safeMargin * 2))
  const panelHeight = Math.min(panelSize.height || 360, Math.max(1, viewport.height - safeMargin * 2))
  const opensLeft = position.x + triggerSize.width / 2 > viewport.width / 2
  const opensAbove = position.y + triggerSize.height / 2 > viewport.height / 2
  const preferredLeft = opensLeft
    ? position.x + triggerSize.width - panelWidth
    : position.x
  const preferredTop = opensAbove
    ? position.y - panelHeight - safeMargin
    : position.y + triggerSize.height + safeMargin
  const maxLeft = Math.max(safeMargin, viewport.width - panelWidth - safeMargin)
  const maxTop = Math.max(safeMargin, viewport.height - panelHeight - safeMargin)

  return {
    left: `${Math.round(clamp(preferredLeft, safeMargin, maxLeft))}px`,
    top: `${Math.round(clamp(preferredTop, safeMargin, maxTop))}px`,
  }
})

function clamp(value, min, max) { return Math.min(Math.max(value, min), max) }

function rotateIdleImage() {
  const offset = 1 + Math.floor(Math.random() * (idleImages.length - 1))
  idleImageIndex.value = (idleImageIndex.value + offset) % idleImages.length
}

function scheduleIdleRotation() {
  const delay = idleDelayMin + Math.floor(Math.random() * (idleDelayRange + 1))
  idleRotationTimer = window.setTimeout(() => {
    rotateIdleImage()
    scheduleIdleRotation()
  }, delay)
}

function readTriggerSize() {
  const trigger = rootRef.value?.querySelector('.ai-pet-trigger')
  if (!trigger) return
  const rect = trigger.getBoundingClientRect()
  if (rect.width && rect.height) {
    triggerSize.width = rect.width
    triggerSize.height = rect.height
  }
}

function readPanelSize() {
  const panel = panelRef.value
  if (!panel) return
  const rect = panel.getBoundingClientRect()
  if (rect.width && rect.height) {
    panelSize.width = rect.width
    panelSize.height = rect.height
  }
}

function clampPosition() {
  position.x = clamp(position.x, safeMargin, Math.max(safeMargin, viewport.width - triggerSize.width - safeMargin))
  position.y = clamp(position.y, safeMargin, Math.max(safeMargin, viewport.height - triggerSize.height - safeMargin))
}

function placeInitially() {
  if (hasPosition.value) {
    clampPosition()
    return
  }
  position.x = Math.max(safeMargin, viewport.width - triggerSize.width - 28)
  position.y = Math.max(safeMargin, viewport.height - triggerSize.height - 24)
  hasPosition.value = true
}

function updateViewport() {
  viewport.width = window.innerWidth
  viewport.height = window.innerHeight
  readTriggerSize()
  clampPosition()
  if (props.open) nextTick(readPanelSize)
}

function startDrag(event) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  dragState.active = true
  dragState.pointerId = event.pointerId
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.startLeft = position.x
  dragState.startTop = position.y
  dragState.moved = false
  event.currentTarget?.setPointerCapture?.(event.pointerId)
}

function moveDrag(event) {
  if (!dragState.active || event.pointerId !== dragState.pointerId) return
  const deltaX = event.clientX - dragState.startX
  const deltaY = event.clientY - dragState.startY
  if (!dragState.moved && Math.hypot(deltaX, deltaY) >= dragThreshold) {
    dragState.moved = true
    isDragging.value = true
  }
  if (!dragState.moved) return
  event.preventDefault()
  position.x = clamp(dragState.startLeft + deltaX, safeMargin, Math.max(safeMargin, viewport.width - triggerSize.width - safeMargin))
  position.y = clamp(dragState.startTop + deltaY, safeMargin, Math.max(safeMargin, viewport.height - triggerSize.height - safeMargin))
}

function finishDrag(event) {
  if (!dragState.active || event.pointerId !== dragState.pointerId) return
  event.currentTarget?.releasePointerCapture?.(event.pointerId)
  const didMove = dragState.moved
  dragState.active = false
  dragState.pointerId = null
  dragState.moved = false
  isDragging.value = false
  if (didMove) {
    suppressClick.value = true
    window.setTimeout(() => { suppressClick.value = false }, 0)
  }
}

function toggleOpen() {
  if (suppressClick.value) {
    suppressClick.value = false
    return
  }
  emit('update:open', !props.open)
}

function handleKeydown(event) {
  if (event.key === 'Escape' && props.open) {
    event.preventDefault()
    if (manualOpen.value) {
      manualOpen.value = false
      return
    }
    emit('update:open', false)
  }
}

onMounted(() => {
  readTriggerSize()
  placeInitially()
  scheduleIdleRotation()
  window.addEventListener('resize', updateViewport)
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  if (idleRotationTimer) window.clearTimeout(idleRotationTimer)
  window.removeEventListener('resize', updateViewport)
  window.removeEventListener('keydown', handleKeydown)
})

watch(() => props.open, open => {
  if (open) nextTick(readPanelSize)
  else manualOpen.value = false
})
watch(manualOpen, () => nextTick(readPanelSize))
</script>

<template>
  <section ref="rootRef" class="ai-pet" :class="{ open, 'is-dragging': isDragging }" :style="rootStyle" aria-label="鲜知 AI 助手">
    <div v-if="open" ref="panelRef" class="ai-pet-panel" :style="panelStyle" role="dialog" aria-modal="false" aria-label="与鲜知助手对话">
      <header class="ai-pet-head">
        <div><img class="ai-pet-mini" :src="currentImage" alt="" draggable="false" /><div><small>鲜知 AI 管家</small><b>可操作{{ pageName }}及全站功能</b></div></div>
        <button type="button" aria-label="关闭助手" @click="emit('update:open', false)"><span v-html="closeIcon"></span></button>
      </header>
      <template v-if="manualOpen">
        <section class="ai-manual" aria-label="AI 使用说明书">
          <div class="ai-manual-cover"><span v-html="bookIcon"></span><div><small>鲜知指南 01 · 功能清单</small><h2>AI 使用说明书</h2><p>助手读取当前账号的库存、期限、环境、菜谱计划和偏好；可回答问题，也可生成待确认操作。</p></div></div>
          <p class="ai-capability-status" :class="{ enabled: capabilities?.webRecipeSearch }"><b>{{ capabilities?.webSearchStatus || '正在读取联网能力' }}</b><span>本地菜谱 {{ capabilities?.localRecipes ? '可用' : '不可用' }} · AI 生成 {{ capabilities?.aiGeneration ? '可用' : '未启用' }} · 食材同义归一 {{ capabilities?.foodNormalization ? '已启用' : '未启用' }}</span></p>
          <ol>
            <li><b>查库存与保质期</b><span>查询现有食材、剩余数量、存放分区、低库存、临期与已过期项目；可按食材名或时间范围提问。</span><em>示例：番茄还剩多少？三天内哪些食材要先吃？</em></li>
            <li><b>查冰箱环境</b><span>查询各分区温度、湿度、传感器状态与环境风险，并解释环境异常可能影响哪些库存。</span><em>示例：冷藏室温度正常吗？为什么鸡蛋期限变短了？</em></li>
            <li><b>本地与联网找菜谱</b><span>先匹配现有菜谱库，结果不足时可使用 Tavily 检索公开网页；返回标题、摘要、原始链接、站点和检索时间。明确菜名、主料与做法优先于口味、菜系和热量等软偏好。</span><em>示例：联网找红烧鹅，主料必须是鹅肉并保留来源。</em></li>
            <li><b>整理外部菜谱草稿</b><span>AI 可基于公开来源整理主料、用量、步骤、营养和风险提示；网页内容只作为不可信参考。缺少可验证来源、食材或做法时只展示搜索结果，不制作可入库草稿。</span><em>外部草稿必须由你勾选确认后才会加入菜谱库。</em></li>
            <li><b>管理与规范化库存</b><span>可发起添加食材、调整数量、删除库存等操作。番茄/西红柿等已审核同义词会自动归到同一规范食材；每个批次的原始录入名、数量、单位、位置和期限仍分别保留。</span><em>低置信度或未审核别名不会自动合并。</em></li>
            <li><b>管理采购清单</b><span>可发起添加采购项、更新已买或待买状态、删除、入库与导出清单，也能根据低库存给出补货建议。</span><em>示例：把 2 盒牛奶加入采购清单。</em></li>
            <li><b>记录饮食与调整设置</b><span>可发起记录或删除一餐，更新口味、菜系、过敏、忌口和营养目标，也可调整冰箱分区设置。</span><em>示例：记录午餐番茄炒蛋；以后不要推荐花生。</em></li>
            <li><b>确认后才会执行</b><span>页面跳转以外的新增、修改、删除和状态变更会先显示操作卡；确认后执行，取消则不改数据。操作卡 30 分钟后失效，库存变化后也需重新生成。</span></li>
          </ol>
          <p class="ai-manual-note"><b>数据与安全边界</b> 过敏原和明确忌口始终是硬约束；软偏好冲突会警告但不覆盖明确菜名。AI 生成菜谱、营养估算和动态期限用于日常参考；涉及过敏、食品安全或医疗判断时，请以包装说明和专业意见为准。</p>
        </section>
      </template>
      <template v-else>
        <div class="ai-pet-messages" aria-live="polite"><p v-for="message in messages" :key="message.id" :class="message.role">{{ message.text }}</p></div>
        <div v-if="proposals.length" class="ai-pet-actions" aria-label="待确认操作">
          <article v-for="proposal in proposals" :key="proposal.id">
            <span>AI 准备执行</span>
            <b>{{ proposal.title }}</b>
            <div><button type="button" class="dismiss" :disabled="busyProposalId === proposal.id" @click="emit('dismiss', proposal)">取消</button><button type="button" class="confirm" :disabled="Boolean(busyProposalId)" @click="emit('confirm', proposal)">{{ busyProposalId === proposal.id ? '执行中…' : '确认执行' }}</button></div>
          </article>
        </div>
      </template>
      <button type="button" class="ai-manual-toggle" :aria-expanded="manualOpen" @click="manualOpen = !manualOpen"><span v-html="manualOpen ? backIcon : bookIcon"></span><span><b>{{ manualOpen ? '返回对话' : 'AI 使用说明' }}</b><small>{{ manualOpen ? '继续向鲜知提问' : '能力、确认机制与数据边界' }}</small></span></button>
      <form class="ai-pet-input" @submit.prevent="emit('send', '')">
        <input :value="input" placeholder="问问鲜知助手" aria-label="输入问题" @input="emit('update:input', $event.target.value)" />
        <button :disabled="!input.trim()" aria-label="发送问题"><span v-html="sendIcon"></span></button>
      </form>
    </div>
    <button
      class="ai-pet-trigger"
      type="button"
      :aria-expanded="open"
      :aria-grabbed="isDragging"
      aria-label="打开鲜知 AI 助手"
      @pointerdown="startDrag"
      @pointermove="moveDrag"
      @pointerup="finishDrag"
      @pointercancel="finishDrag"
      @click="toggleOpen"
    >
      <img class="ai-pet-face" :src="currentImage" alt="" draggable="false" />
    </button>
  </section>
</template>
