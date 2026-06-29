<template>
  <el-container style="min-height: 100vh;">
    <el-aside :width="isCollapse ? '64px' : '220px'" style="background: #304156; transition: width 0.3s;">
      <div style="padding: 16px; text-align: center; color: #fff; display: flex; align-items: center; justify-content: center; gap: 10px;">
        <img src="/logo.png" alt="logo" style="width: 32px; height: 32px; object-fit: contain;" />
        <span v-show="!isCollapse" style="font-size: 16px; font-weight: bold;">文件管理系统</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="isCollapse"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
      >
        <el-sub-menu index="/files">
          <template #title>
            <el-icon><Folder /></el-icon>
            <span>文件管理</span>
          </template>
          <el-menu-item index="/files">
            <el-icon><Document /></el-icon>
            <span>我的文件</span>
          </el-menu-item>
          <el-menu-item v-if="hasPermission('file:recycle')" index="/files/recycle">
            <el-icon><Delete /></el-icon>
            <span>回收站</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-if="hasPermission('user:view')" index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="hasPermission('role:view')" index="/roles">
          <el-icon><Lock /></el-icon>
          <span>角色管理</span>
        </el-menu-item>
        <el-menu-item v-if="hasPermission('role:assign-permission')" index="/permissions">
          <el-icon><Key /></el-icon>
          <span>权限管理</span>
        </el-menu-item>
        <el-menu-item v-if="hasPermission('node:view')" index="/nodes">
          <el-icon><Monitor /></el-icon>
          <span>节点监控</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e6e6e6;">
        <!-- 菜单收起按钮 -->
        <div 
          style="display: flex; align-items: center; cursor: pointer; color: #333; padding: 8px 16px;" 
          @click="isCollapse = !isCollapse"
        >
          <el-icon :size="18">
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
          <span style="margin-left: 6px; font-size: 14px;">{{ isCollapse ? '展开' : '收起' }}</span>
        </div>
        <el-dropdown trigger="click" @command="handleCommand">
          <span style="cursor: pointer; display: flex; align-items: center; color: #333;">
            <el-avatar :size="32" style="margin-right: 8px; background-color: #409eff;">
              {{ (currentUser?.nickname || currentUser?.username || '?').charAt(0).toUpperCase() }}
            </el-avatar>
            <span>{{ currentUser?.nickname || currentUser?.username }}</span>
            <el-tag v-for="role in currentUser?.roles" :key="role" size="small" style="margin-left: 4px;" :type="roleTagType(role)">
              {{ roleName(role) }}
            </el-tag>
            <el-icon style="margin-left: 4px;"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item command="password">
                <el-icon><Key /></el-icon>修改密码
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 修改密码对话框 -->
        <el-dialog v-model="pwdDialogVisible" title="修改密码" width="420px" append-to-body>
          <el-form :model="pwdForm" label-width="90px" ref="pwdRef">
            <el-form-item label="当前密码" prop="oldPassword" :rules="{ required: true, message: '请输入当前密码', trigger: 'blur' }">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword" :rules="[
              { required: true, message: '请输入新密码', trigger: 'blur' },
              { min: 6, message: '密码至少6位', trigger: 'blur' }
            ]">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword" :rules="[
              { required: true, message: '请再次输入新密码', trigger: 'blur' },
              { validator: confirmPwdValidator, trigger: 'blur' }
            ]">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="pwdDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleChangePassword" :loading="pwdLoading">确认修改</el-button>
          </template>
        </el-dialog>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const currentUser = ref(null)
const permissions = ref([])
const isCollapse = ref(false)

// 修改密码相关
const pwdDialogVisible = ref(false)
const pwdLoading = ref(false)
const pwdRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const confirmPwdValidator = (rule, value, callback) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

onMounted(async () => {
  try {
    const res = await request.get('/auth/current')
    currentUser.value = res.data
    permissions.value = res.data.permissions || []
    localStorage.setItem('userInfo', JSON.stringify(res.data))
  } catch (e) {
    console.error('获取用户信息失败', e)
  }
})

const hasPermission = (perm) => {
  return permissions.value.includes(perm)
}

const roleMap = {
  ADMIN: '管理员',
  USER: '普通用户',
  Developer: '研发人员'
}

const roleName = (code) => {
  return roleMap[code] || code
}

const roleTagType = (code) => {
  return code === 'ADMIN' ? 'danger' : 'primary'
}

const handleCommand = (command) => {
  if (command === 'logout') {
    handleLogout()
  } else if (command === 'password') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdDialogVisible.value = true
  } else if (command === 'profile') {
    ElMessage.info('个人中心功能开发中')
  }
}

const handleChangePassword = async () => {
  if (!pwdRef.value) return
  await pwdRef.value.validate().catch(() => {})
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  pwdLoading.value = true
  try {
    await request.put('/auth/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    pwdDialogVisible.value = false
    handleLogout()
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    pwdLoading.value = false
  }
}

const handleLogout = async () => {
  try {
    // 调用后端登出接口（清除 Redis 缓存）
    await request.post('/auth/logout')
  } catch (e) {
    // 登出接口失败不影响前端登出流程
    console.warn('登出接口调用失败:', e)
  }
  // 清除本地存储
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>
