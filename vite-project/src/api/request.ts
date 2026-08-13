import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || '网络异常，请稍后再试')
    return Promise.reject(error)
  },
)

const http = {
  get: <T = any>(url: string, config?: AxiosRequestConfig) =>
    request.get(url, config) as Promise<T>,
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
    request.post(url, data, config) as Promise<T>,
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
    request.put(url, data, config) as Promise<T>,
  delete: <T = any>(url: string, config?: AxiosRequestConfig) =>
    request.delete(url, config) as Promise<T>,
  patch: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
    request.patch(url, data, config) as Promise<T>,
}

export default http
