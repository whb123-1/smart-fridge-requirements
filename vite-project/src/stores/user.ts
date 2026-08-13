import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, getMe, logout as apiLogout } from '../api/auth'

interface UserInfo {
  id: number
  username: string
  nickname: string
  email?: string
  avatar?: string
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null') as UserInfo | null,
  }),
  actions: {
    async login(form: { username: string; password: string }) {
      const data = await apiLogin(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    async register(form: Record<string, any>) {
      await apiRegister({
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        email: form.email,
      })
    },
    async fetchMe() {
      this.user = await getMe()
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 忽略退出接口异常
      }
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
  },
})
