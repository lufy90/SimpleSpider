<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span>Login</span>
        </div>
      </template>
      <div class="login-brand">
        <img src="/favicon.svg" alt="SimpleSpider" class="login-logo" />
      </div>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-width="80px">
        <el-form-item label="Username" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="Enter username"
            autocomplete="username"
            autocapitalize="off"
            spellcheck="false"
          />
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="Enter password"
            autocomplete="current-password"
            autocapitalize="off"
            spellcheck="false"
            inputmode="text"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="rememberLogin">
            Remember username and password
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="loading">
            Login
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { APIURL } from '@/config'

const REMEMBER_KEY = 'login_remember'
const USERNAME_KEY = 'login_username'
const PASSWORD_KEY = 'login_password'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)
const rememberLogin = ref(false)

const loginForm = ref({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: 'Please enter username', trigger: 'blur' }],
  password: [{ required: true, message: 'Please enter password', trigger: 'blur' }],
}

function loadSavedCredentials() {
  if (localStorage.getItem(REMEMBER_KEY) !== 'true') return
  rememberLogin.value = true
  loginForm.value.username = localStorage.getItem(USERNAME_KEY) || ''
  loginForm.value.password = localStorage.getItem(PASSWORD_KEY) || ''
}

function persistCredentials() {
  if (rememberLogin.value) {
    localStorage.setItem(REMEMBER_KEY, 'true')
    localStorage.setItem(USERNAME_KEY, loginForm.value.username)
    localStorage.setItem(PASSWORD_KEY, loginForm.value.password)
    return
  }
  localStorage.removeItem(REMEMBER_KEY)
  localStorage.removeItem(USERNAME_KEY)
  localStorage.removeItem(PASSWORD_KEY)
}

onMounted(() => {
  loadSavedCredentials()
})

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await axios.post(`${APIURL}/token/`, {
          username: loginForm.value.username,
          password: loginForm.value.password,
        })

        if (response && response.data && response.data.access_token) {
          const token = String(response.data.access_token)
          sessionStorage.setItem('token', token)
          persistCredentials()
          ElMessage.success('Login successful')
          router.push('/dyauthor')
        } else {
          ElMessage.error('Invalid response from server')
        }
      } catch (error) {
        const errorMsg = error.response?.data?.detail || error.response?.data?.message || 'Login failed. Please check your credentials.'
        ElMessage.error(errorMsg)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: calc(100vh - 100px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 400px;
}

.login-card :deep(.el-button) {
  width: 100%;
}

.login-brand {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.login-logo {
  width: 72px;
  height: 72px;
  border-radius: 12px;
}

.card-header {
  text-align: center;
  font-size: 20px;
  font-weight: bold;
}
</style>
