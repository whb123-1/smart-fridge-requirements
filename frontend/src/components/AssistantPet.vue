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
          <div class="ai-manual-cover"><span v-html="bookIcon"></span><div><small>鲜知指南 01</small><h2>AI 使用说明书</h2><p>把目标、对象和必要条件说清楚，助手会结合当前页面与已登录账号的数据处理。</p></div></div>
          <ol>
            <li><b>查询与建议</b><span>可询问库存、保质期、环境状态、菜谱与采购信息。</span></li>
            <li><b>明确描述</b><span>带上食材名、数量、分区或时间，结果会更准确。</span></li>
            <li><b>操作需确认</b><span>添加、调整、删除和状态变更会先生成待确认操作。</span></li>
            <li><b>数据有边界</b><span>回答基于当前账号记录；营养和期限结果用于日常参考。</span></li>
          </ol>
          <p class="ai-manual-note">涉及过敏、食品安全或医疗判断时，请以包装说明和专业意见为准。</p>
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
