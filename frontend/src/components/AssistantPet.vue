<script setup>
defineProps({
  open: { type: Boolean, required: true },
  pageName: { type: String, required: true },
  messages: { type: Array, required: true },
  input: { type: String, default: '' },
  image: { type: String, required: true },
})

const emit = defineEmits(['update:open', 'update:input', 'send'])

const closeIcon = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>'
const sendIcon = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>'
</script>

<template>
  <section class="ai-pet" :class="{ open }" aria-label="鲜知 AI 助手">
    <div v-if="open" class="ai-pet-panel" role="dialog" aria-label="与鲜知助手对话">
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
    <button class="ai-pet-trigger" type="button" :aria-expanded="open" aria-label="打开鲜知 AI 助手" @click="emit('update:open', !open)">
      <img class="ai-pet-face" :src="image" alt="" />
    </button>
  </section>
</template>
