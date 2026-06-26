<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div style="display: flex; align-items: center;">
            <span>用户管理</span>
            <el-input v-model="keyword" placeholder="搜索用户名/昵称/邮箱" clearable
              style="width: 220px; margin-left: 16px;"
              @clear="loadUsers" @keyup.enter="loadUsers">
              <template #append><el-button @click="loadUsers"><el-icon><Search /></el-icon></el-button></template>
            </el-input>
          </div>
          <el-button type="primary" @click="showCreateDialog">新建用户</el-button>
        </div>
      </template>

      <el-table :data="users" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="55" />
        <el-table-column prop="username" label="用户名" width="110" />
        <el-table-column prop="nickname" label="昵称" width="100" />
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag v-for="role in (row.roles || [])" :key="role" size="small" :type="role === 'ADMIN' ? 'danger' : ''" style="margin-right: 4px;">
              {{ role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="155" />
        <el-table-column label="操作" width="290" fixed="right">
            <template #default="{ row }">
            <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleToggleStatus(row)" :disabled="row.username === 'admin'">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="info" @click="showRoleDialog(row)">分配角色</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.username === 'admin'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end;"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        @size-change="loadUsers"
        @current-change="loadUsers"
      />
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新建用户'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" v-if="!isEdit">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="400px">
      <el-checkbox-group v-model="selectedRoles">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id" :value="role.id">
          {{ role.roleName }}（{{ role.roleCode }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignRoles" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const loading = ref(false)
const users = ref([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const roleDialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const currentUserId = ref(null)

const form = ref({ username: '', password: '', nickname: '', email: '', phone: '' })
const selectedRoles = ref([])
const allRoles = ref([])

onMounted(() => {
  loadUsers()
  loadRoles()
})

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await request.get('/auth/users', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value }
    })
    users.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const loadRoles = async () => {
  try {
    const res = await request.get('/role/list')
    allRoles.value = res.data?.records || res.data || []
  } catch (e) {
    console.error('加载角色失败', e)
  }
}

const resetForm = () => {
  form.value = { username: '', password: '', nickname: '', email: '', phone: '' }
}

const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  currentUserId.value = row.id
  form.value = {
    username: row.username,
    password: '',
    nickname: row.nickname,
    email: row.email,
    phone: row.phone
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.value.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!isEdit.value && !form.value.password) {
    ElMessage.warning('请输入密码')
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await request.put(`/auth/users/${currentUserId.value}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/auth/users', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    pageNum.value = 1
    loadUsers()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除用户 "${row.username}" 吗？`, '确认删除', { type: 'warning' })
    await request.delete(`/auth/users/${row.id}`)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleToggleStatus = async (row) => {
  try {
    const action = row.status === 1 ? '禁用' : '启用'
    await ElMessageBox.confirm(`确定${action}用户 "${row.username}" 吗？`, '确认', { type: 'warning' })
    await request.put(`/auth/users/${row.id}/status`)
    ElMessage.success(`${action}成功`)
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const showRoleDialog = async (row) => {
  currentUserId.value = row.id
  selectedRoles.value = []
  try {
    const rolesRes = await request.get('/role/list')
    const userRoles = row.roles || []
    allRoles.value = rolesRes.data?.records || rolesRes.data || []
    selectedRoles.value = allRoles.value
      .filter(r => userRoles.includes(r.roleCode))
      .map(r => r.id)
  } catch (e) {
    console.error(e)
  }
  roleDialogVisible.value = true
}

const handleAssignRoles = async () => {
  submitting.value = true
  try {
    await request.put(`/auth/users/${currentUserId.value}/roles`, { roleIds: selectedRoles.value })
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    loadUsers()
  } finally {
    submitting.value = false
  }
}
</script>
