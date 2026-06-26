import axios, { type AxiosInstance, type AxiosResponse } from 'axios'

/** 统一后端响应结构 */
export interface ApiResult<T = any> {
  code: number
  msg?: string
  data?: T
}

const http: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 30000,
  withCredentials: true,
})

// 请求拦截：无需加 token（Cookie 自动携带）
http.interceptors.request.use((config) => {
  return config
})

// 响应拦截：解包 Result + 401 重定向
http.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const result = response.data
    if (result.code !== 0) {
      return Promise.reject(new Error(result.msg || 'Unknown error'))
    }
    return result.data as any
  },
  (error) => {
    if (error.response?.status === 401) {
      // 会话过期 → 跳 SSO 登录
      const redirect = window.location.pathname + window.location.search
      window.location.href = `/sso/login?menu_uri=${encodeURIComponent(redirect)}`
      return Promise.reject(new Error('Session expired, redirecting to login'))
    }
    const msg = error.response?.data?.msg || error.message || 'Network error'
    return Promise.reject(new Error(msg))
  }
)

export default http
