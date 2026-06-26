<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">分布式文件管理系统</h2>
      </template>
      <el-tabs v-model="activeTab" class="center-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-width="70px" @submit.prevent="handleLogin">
            <el-form-item label="用户名">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item label-width="0" class="btn-item">
              <el-button type="primary" size="large" class="login-btn" @click="handleLogin" :loading="loading">登 录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" label-width="70px" @submit.prevent="handleRegister">
            <el-form-item label="用户名">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名"
                size="large"
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                size="large"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item label-width="0" class="btn-item">
              <el-button type="primary" size="large" class="login-btn" @click="handleRegister" :loading="loading">注 册</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const activeTab = ref('login')
const loading = ref(false)
const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', confirmPassword: '' })

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/auth/login', loginForm.value)
    localStorage.setItem('token', res.data.accessToken)
    localStorage.setItem('refreshToken', res.data.refreshToken)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await request.post('/auth/register', {
      username: registerForm.value.username,
      password: registerForm.value.password
    })
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.value.username = registerForm.value.username
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 440px;
}
.login-title {
  text-align: center;
  margin: 0;
  font-size: 22px;
  letter-spacing: 2px;
}
.login-btn {
  width: auto;
  min-width: 120px;
}
.btn-item {
  display: flex;
  justify-content: center;
}
.btn-item :deep(.el-form-item__label) {
  display: none;
}
.btn-item :deep(.el-form-item__content) {
  width: 100%;
  display: flex;
  justify-content: center;
  margin-left: 0 !important;
}
.center-tabs :deep(.el-tabs__nav-wrap) {
  display: flex;
  justify-content: center;
}
.center-tabs :deep(.el-tabs__nav-scroll) {
  display: flex;
  justify-content: center;
}
.center-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
</style>
