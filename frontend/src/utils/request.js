import axios from 'axios'
import { APIURL } from '@/config'
import router from '@/router'
import { ElMessage } from 'element-plus'

// Axios documentation: https://axios-http.com/docs/req_config

const config = {
  baseURL: APIURL,
  timeout: 10000,
}

const request = axios.create(config)

// Request interceptor: Add Authorization header automatically
request.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor: Handle errors globally
request.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      ElMessage.error('Unauthorized. Please login again.')
      sessionStorage.removeItem('token')
      setTimeout(() => {
        router.push('/login')
      }, 2000)
    } else if (error.response) {
      ElMessage.error(
        error.response.data?.detail || 
        error.response.data?.message || 
        'Request failed'
      )
    } else {
      ElMessage.error(error.message || 'Network error')
    }
    return Promise.reject(error)
  }
)

export const ajax = {
  get: (url, params) => {
    return request.get(url, { params })
  },
  post: (url, data) => {
    return request.post(url, data)
  },
  patch: (url, data) => {
    return request.patch(url, data)
  },
  put: (url, data) => {
    return request.put(url, data)
  },
  delete: (url, params) => {
    return request.delete(url, { params })
  },
}

