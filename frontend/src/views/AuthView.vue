<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import xianzhiLogo from '../assets/xianzhi-logo.png'
import { authenticatedLanding } from '../navigation.js'
import { login, register } from '../session.js'

const router = useRouter()
const mode = ref('login')
const busy = ref(false)
const error = ref('')
const form = reactive({ identifier: '', username: '', email: '', password: '', displayName: '' })
const isRegister = computed(() => mode.value === 'register')

function switchMode(next) {
  mode.value = next
  error.value = ''
}

async function submit() {
  if (busy.value) return
  error.value = ''
  busy.value = true
  try {
    const result = isRegister.value
      ? await register({
          username: form.username.trim().toLowerCase(),
          email: form.email,
          password: form.password,
          displayName: form.displayName,
        })
      : await login({ identifier: form.identifier, password: form.password })
    const landing = authenticatedLanding(result.onboardingRequired, result.user)
    if (landing === '/onboarding') await router.push(landing)
    else await router.replace(landing)
  } catch (exception) {
    error.value = exception.code === 'INVALID_CREDENTIALS'
      ? '邮箱/用户名或密码不正确'
      : exception.code === 'USERNAME_ALREADY_REGISTERED'
        ? '该用户名已被占用，请更换后重试'
        : exception.code === 'EMAIL_ALREADY_REGISTERED'
          ? '该邮箱已经注册，请直接登录'
          : exception.fields?.username
            ? '用户名只能包含 3-32 位小写字母、数字和下划线'
            : exception.message || '暂时无法连接账户服务'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-view">
    <section class="auth-identity" aria-label="鲜知智慧冰箱">
      <p class="auth-mark">鲜知 · 智慧冰箱</p>
      <div class="auth-fridge-line" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
      <h1>先认识你的冰箱，<br />再照看每一份新鲜。</h1>
      <p>账户用于隔离冰箱配置和个人数据。首次进入后，只需完成一次分区设置。</p>
      <img class="auth-logo" :src="xianzhiLogo" alt="鲜知智慧冰箱 Logo" />
    </section>

    <section class="auth-panel" aria-labelledby="auth-title">
      <div class="auth-tabs" role="tablist" aria-label="账户方式">
        <button :class="{ active: mode === 'login' }" role="tab" :aria-selected="mode === 'login'" @click="switchMode('login')">登录</button>
        <button :class="{ active: mode === 'register' }" role="tab" :aria-selected="mode === 'register'" @click="switchMode('register')">注册</button>
      </div>
      <form @submit.prevent="submit">
        <div class="auth-panel-heading">
          <p>个人冰箱账户</p>
          <h2 id="auth-title">{{ isRegister ? '创建账户' : '欢迎回来' }}</h2>
        </div>
        <label v-if="isRegister">称呼<input v-model.trim="form.displayName" autocomplete="name" maxlength="80" required placeholder="例如：林知夏" /></label>
        <label v-if="isRegister">用户名<input v-model.trim="form.username" autocomplete="username" minlength="3" maxlength="32" pattern="[a-z0-9_]{3,32}" required placeholder="例如：lin_zhixia" @input="form.username = form.username.toLowerCase()" /></label>
        <label v-if="isRegister">邮箱<input v-model.trim="form.email" autocomplete="email" type="email" maxlength="320" required placeholder="name@example.com" /></label>
        <label v-else>邮箱或用户名<input v-model.trim="form.identifier" autocomplete="username" maxlength="320" required placeholder="name@example.com 或用户名" /></label>
        <label>密码<input v-model="form.password" :autocomplete="isRegister ? 'new-password' : 'current-password'" type="password" :minlength="isRegister ? 8 : 6" maxlength="128" required :placeholder="isRegister ? '至少 8 位' : '请输入密码'" /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="auth-submit" :disabled="busy">{{ busy ? '正在连接' : isRegister ? '创建账户并配置冰箱' : '登录' }}</button>
      </form>
    </section>
  </main>
</template>
