<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api } from '../services/api'

const props = defineProps({
  modelValue: { type: String, default: '' },
  context: { type: String, default: 'ingredient' },
  placeholder: { type: String, default: '' },
  ariaLabel: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'select'])

const suggestions = ref([])
const open = ref(false)
const activeIndex = ref(-1)
const loading = ref(false)
let timer
let requestVersion = 0

const label = computed(() => props.context === 'dish' ? '菜品推荐' : '食材推荐')

function clearSuggestions() {
  suggestions.value = []
  activeIndex.value = -1
  open.value = false
}

async function loadSuggestions(query) {
  const version = ++requestVersion
  const value = String(query || '').trim()
  if (!value) return clearSuggestions()
  loading.value = true
  try {
    const result = await api.getNameSuggestions({ query: value, context: props.context, limit: 6 })
    if (version !== requestVersion) return
    suggestions.value = result.suggestions || []
    activeIndex.value = -1
    open.value = Boolean(suggestions.value.length)
  } catch {
    if (version !== requestVersion) return
    clearSuggestions()
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function onInput(event) {
  const value = event.target.value
  emit('update:modelValue', value)
}

function selectSuggestion(suggestion) {
  emit('update:modelValue', suggestion.name)
  emit('select', suggestion)
  clearSuggestions()
}

function onKeydown(event) {
  if (!open.value || !suggestions.value.length) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, suggestions.value.length - 1)
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
  }
  if (event.key === 'Enter' && activeIndex.value >= 0) {
    event.preventDefault()
    selectSuggestion(suggestions.value[activeIndex.value])
  }
  if (event.key === 'Escape') clearSuggestions()
}

watch(() => props.modelValue, value => {
  clearTimeout(timer)
  if (!String(value || '').trim()) return clearSuggestions()
  timer = setTimeout(() => loadSuggestions(value), 260)
})

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="name-suggestion-field" :class="{ open }">
    <input
      :value="modelValue"
      :placeholder="placeholder"
      :aria-label="ariaLabel || placeholder"
      :disabled="disabled"
      autocomplete="off"
      @input="onInput"
      @keydown="onKeydown"
      @focus="modelValue && suggestions.length && (open = true)"
      @blur="setTimeout(() => { open = false }, 140)"
    />
    <span v-if="loading" class="suggestion-loading" aria-label="正在加载推荐"></span>
    <div v-if="open" class="suggestion-menu" role="listbox" :aria-label="label">
      <p>{{ label }}</p>
      <button
        v-for="(suggestion, index) in suggestions"
        :key="suggestion.id"
        type="button"
        role="option"
        :aria-selected="activeIndex === index"
        :class="{ active: activeIndex === index }"
        @mousedown.prevent="selectSuggestion(suggestion)"
      >{{ suggestion.name }}</button>
    </div>
  </div>
</template>
