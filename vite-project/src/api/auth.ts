import request from './request'

export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
  password: string
  nickname?: string
  email?: string
}

export const login = (data: LoginForm) => request.post('/auth/login', data)
export const register = (data: RegisterForm) => request.post('/auth/register', data)
export const getMe = () => request.get('/auth/me')
export const logout = () => request.post('/auth/logout')
