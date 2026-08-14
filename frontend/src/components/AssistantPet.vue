<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, required: true },
  pageName: { type: String, required: true },
  messages: { type: Array, required: true },
  input: { type: String, default: '' },
  image: { type: String, required: true },
})

const emit = defineEmits(['update:open', 'update:input', 'send'])

const rootRef = ref(null)
const panelRef = ref(null)
const isDragging = ref(false)
const suppressClick = ref(false)
const hasPosition = ref(false)
const triggerSize = reactive({ width: 106, height: 120 })
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

const closeIcon = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>'
const sendIcon = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>'

const rootStyle = computed(() => ({
  left: `${Math.round(position.x)}px`,
  top: `${Math.round(position.y)}px`,
}))

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
  isDragging.value = true
  event.currentTarget?.setPointerCapture?.(event.pointerId)
}

function moveDrag(event) {
  if (!dragState.active || event.pointerId !== dragState.pointerId) return
  const deltaX = event.clientX - dragState.startX
  const deltaY = event.clientY - dragState.startY
  if (!dragState.moved && Math.hypot(deltaX, deltaY) >= dragThreshold) dragState.moved = true
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
    emit('update:open', false)
  }
}

onMounted(() => {
  readTriggerSize()
  placeInitially()
  window.addEventListener('resize', updateViewport)
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
  window.removeEventListener('keydown', handleKeydown)
})

watch(() => props.open, open => {
  if (open) nextTick(readPanelSize)
})
</script>

<template>
  <section ref="rootRef" class="ai-pet" :class="{ open, 'is-dragging': isDragging }" :style="rootStyle" aria-label="鲜知 AI 助手">
    <div v-if="open" ref="panelRef" class="ai-pet-panel" :style="panelStyle" role="dialog" aria-modal="false" aria-label="与鲜知助手对话">
      <header class="ai-pet-head">
        <div><img class="ai-pet-mini" :src="image" alt="" /><div><small>鲜知 AI 助手</small><b>正在了解{{ pageName }}</b></div></div>
        <button type="button" aria-label="关闭助手" @click="emit('update:open', false)"><span v-html="closeIcon"></span></button>
      </header>
      <div class="ai-pet-messages" aria-live="polite"><p v-for="message in messages" :key="message.id" :class="message.role">{{ message.text }}</p></div>
      <div class="ai-pet-prompts">
        <button type="button" @click="emit('send', '怎么添加食材')">添加食材</button>
        <button type="button" @click="emit('send', '冰箱状态如何')">冰箱状态</button>
        <button type="button" @click="emit('send', '帮我生成一道菜谱')">生成菜谱</button>
      </div>
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
      <img class="ai-pet-face" :src="image" alt="" />
    </button>
  </section>
</template>
