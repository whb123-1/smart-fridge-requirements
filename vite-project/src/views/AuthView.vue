<template>
  <el-tabs v-model="tab">
    <el-tab-pane label="登录" name="login">
      <el-form label-width="0" @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="密码"
            size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading"
          @click="onLogin">登录</el-button>
      </el-form>
    </el-tab-pane>
    <el-tab-pane label="注册" name="register">
      <el-form label-width="0" @keyup.enter="onRegister">
        <el-form-item>
          <el-input v-model="regForm.username" placeholder="用户名（3-20 个字符）" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="regForm.nickname" placeholder="昵称（选填）" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="regForm.email" placeholder="邮箱（选填）" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="regForm.password" type="password" placeholder="密码（至少 6 位）"
            size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-input v-model="regForm.confirm" type="password" placeholder="确认密码"
            size="large" show-password />
        </el-form-item>
        <el-button type="success" size="large" style="width: 100%" :loading="loading"
          @click="onRegister">注册</el-button>
      </el-form>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const tab = ref('login')
const loading = ref(false)
const router = useRouter()
const userStore = useUserStore()
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({
  username: '', nickname: '', email: '', password: '', confirm: '',
})

const onLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login({ ...loginForm })
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

const onRegister = async () => {
  if (!regForm.username || !regForm.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (regForm.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (regForm.password !== regForm.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await userStore.register({
      username: regForm.username,
      password: regForm.password,
      nickname: regForm.nickname || undefined,
      email: regForm.email || undefined,
    })
    ElMessage.success('注册成功，请登录')
    tab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>
