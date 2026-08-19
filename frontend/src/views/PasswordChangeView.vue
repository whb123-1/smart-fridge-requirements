<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api.js'
import { authenticatedLanding } from '../navigation.js'
import { logout, session } from '../session.js'

const router = useRouter()
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (form.newPassword !== form.confirmPassword) { error.value = '两次输入的新密码不一致'; return }
  if (form.newPassword === form.currentPassword) { error.value = '新密码不能与临时密码相同'; return }
  busy.value = true
  try {
    await api.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
    session.user = { ...session.user, passwordChangeRequired: false }
    await router.replace(authenticatedLanding(session.onboardingRequired, session.user))
  } catch (exception) {
    error.value = exception.code === 'INVALID_CURRENT_PASSWORD' ? '当前或临时密码不正确' : exception.message || '密码更新失败'
  } finally { busy.value = false }
}

async function exit() { await logout(); await router.replace('/login') }
</script>

<template>
  <main class="password-page">
    <section class="password-context">
      <p>鲜知 · 账户安全</p>
      <h1>先换一把只有你知道的钥匙。</h1>
      <div class="security-line" aria-hidden="true"><i></i><i></i><i></i></div>
      <small>临时密码仅用于首次进入。更新后，其他已登录会话会立即失效。</small>
    </section>
    <section class="password-panel" aria-labelledby="password-title">
      <form @submit.prevent="submit">
        <p>必须完成</p><h2 id="password-title">设置新密码</h2>
        <label>当前或临时密码<input v-model="form.currentPassword" type="password" autocomplete="current-password" minlength="8" maxlength="128" required autofocus /></label>
        <label>新密码<input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="128" required /></label>
        <label>再次输入新密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="128" required /></label>
        <p v-if="error" class="password-error" role="alert">{{ error }}</p>
        <button class="password-submit" :disabled="busy">{{ busy ? '正在更新' : '更新密码并继续' }}</button>
        <button class="password-exit" type="button" @click="exit">退出登录</button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.password-page{--canvas:#dbe8fb;--surface:#f8fbff;--ink:#203a5a;--accent:#5dabb9;min-height:100vh;display:grid;grid-template-columns:minmax(0,1.05fr) minmax(380px,.75fr);background:var(--canvas);color:var(--ink)}
.password-context{display:grid;align-content:center;gap:18px;padding:clamp(42px,8vw,120px);border-right:1px solid #bcd0e6}.password-context p,.password-panel form>p:first-child{margin:0;color:#477d8d;font-size:11px;font-weight:800;letter-spacing:.14em}.password-context h1{max-width:650px;margin:0;font-size:clamp(38px,5vw,70px);line-height:1.18}.password-context small{max-width:480px;color:#627c97;line-height:1.8}.security-line{width:min(430px,80%);height:9px;display:grid;grid-template-columns:1fr 1.4fr .7fr;border:1px solid rgba(32,58,90,.16)}.security-line i{background:#5dabb9;border-right:1px solid #dbe8fb}.security-line i:nth-child(2){background:#90b8d7}.security-line i:last-child{background:#4f769f;border:0}.password-panel{display:grid;place-items:center;padding:38px;background:var(--surface)}form{width:min(410px,100%)}h2{margin:6px 0 32px;font-size:31px}label{display:grid;gap:8px;margin:0 0 17px;color:#536b85;font-size:12px;font-weight:700}input{height:49px;padding:0 14px;border:1px solid #bfd1e7;background:white;color:var(--ink);border-radius:5px}input:focus-visible,button:focus-visible{outline:3px solid rgba(93,171,185,.3);outline-offset:2px}.password-submit{width:100%;height:50px;margin-top:8px;background:#4d78a8;color:white;font-weight:800;border-radius:5px}.password-submit:disabled{opacity:.6}.password-exit{width:100%;padding:14px;color:#627c97}.password-error{padding:10px 12px;border-left:3px solid #bd5964;background:#fff0f2;color:#933845;font-size:12px}@media(max-width:760px){.password-page{grid-template-columns:1fr}.password-context{min-height:310px;padding:36px 24px;border-right:0;border-bottom:1px solid #bcd0e6}.password-context h1{font-size:37px}.password-panel{padding:42px 22px}}
</style>
