<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div style="display: flex; align-items: center;">
            <span>角色管理</span>
            <el-input v-model="keyword" placeholder="搜索角色编码/名称" clearable
              style="width: 220px; margin-left: 16px;"
              @clear="loadRoles" @keyup.enter="loadRoles">
              <template #append><el-button @click="loadRoles"><el-icon><Search /></el-icon></el-button></template>
            </el-input>
          </div>
          <el-button type="primary" @click="showCreateDialog">新建角色</el-button>
        </div>
      </template>

      <el-table :data="roles" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="角色编码" width="150" />
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="权限数量" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.permissionCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showEditDialog(row)" :disabled="row.roleCode === 'ADMIN'">编辑</el-button>
            <el-button size="small" type="info" @click="showPermDialog(row)" :disabled="row.roleCode === 'ADMIN'">分配权限</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.roleCode === 'ADMIN'">删除</el-button>
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
        @size-change="loadRoles"
        @current-change="loadRoles"
      />
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新建角色'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="角色编码">
          <el-input v-model="form.roleCode" :disabled="isEdit" placeholder="如：MANAGER" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" placeholder="如：运营经理" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="角色描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formStatus" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permDialogVisible" title="分配权限" width="500px">
      <div style="margin-bottom: 10px; color: #999;">当前角色：<strong>{{ currentRole?.roleName }}</strong></div>
      <el-checkbox-group v-model="selectedPerms">
        <div v-for="group in permGroups" :key="group.key" style="margin-bottom: 16px;">
          <div style="font-weight: bold; margin-bottom: 8px; color: #333;">{{ group.label }}</div>
          <el-checkbox v-for="perm in group.items" :key="perm.id" :label="perm.id" :value="perm.id" style="margin-right: 12px; margin-bottom: 4px;">
            {{ perm.permissionName }}
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignPerms" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const loading = ref(false)
const roles = ref([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const permDialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const currentRoleId = ref(null)
const currentRole = ref(null)

const form = ref({ roleCode: '', roleName: '', description: '' })
const formStatus = ref(true)
const selectedPerms = ref([])
const allPermissions = ref([])

// 按 sort_order 范围动态分组权限
const permGroups = computed(() => {
  const rangeGroups = [
    { min: 1, max: 9, key: 'file', label: '文件操作权限' },
    { min: 10, max: 19, key: 'user', label: '用户管理权限' },
    { min: 20, max: 29, key: 'role', label: '角色管理权限' },
    { min: 30, max: 39, key: 'node', label: '系统运维权限' },
    { min: 40, max: 49, key: 'recycle', label: '回收站管理权限' }
  ]
  const groups = rangeGroups.map(g => ({ ...g, items: [] }))
  allPermissions.value.forEach(p => {
    const g = groups.find(rg => p.sortOrder >= rg.min && p.sortOrder <= rg.max)
    if (g) g.items.push(p)
  })
  return groups.filter(g => g.items.length > 0)
})

onMounted(() => {
  loadRoles()
})

const loadRoles = async () => {
  loading.value = true
  try {
    const res = await request.get('/role/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value }
    })
    roles.value = res.data.records || []
    total.value = res.data.total || 0
    // 加载每个角色的权限数量（仅当前页）
    for (const role of roles.value) {
      try {
        const permRes = await request.get(`/role/${role.id}/permissions`)
        role.permissionCount = (permRes.data || []).length
      } catch (e) {}
    }
  } finally {
    loading.value = false
  }
}

const loadAllPermissions = async () => {
  try {
    const res = await request.get('/role/permissions/all')
    allPermissions.value = res.data || []
  } catch (e) {
    console.error('加载权限列表失败', e)
  }
}

const resetForm = () => {
  form.value = { roleCode: '', roleName: '', description: '' }
  formStatus.value = true
}

const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  currentRoleId.value = row.id
  form.value = { roleCode: row.roleCode, roleName: row.roleName, description: row.description }
  formStatus.value = row.status === 1
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.value.roleCode || !form.value.roleName) {
    ElMessage.warning('请填写角色编码和名称')
    return
  }

  submitting.value = true
  try {
    const data = { ...form.value, status: formStatus.value ? 1 : 0 }
    if (isEdit.value) {
      await request.put(`/role/${currentRoleId.value}`, data)
      ElMessage.success('更新成功')
    } else {
      await request.post('/role', data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    pageNum.value = 1
    loadRoles()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除角色 "${row.roleName}" 吗？`, '确认删除', { type: 'warning' })
    await request.delete(`/role/${row.id}`)
    ElMessage.success('删除成功')
    loadRoles()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const showPermDialog = async (row) => {
  currentRoleId.value = row.id
  currentRole.value = row
  selectedPerms.value = []

  try {
    const permRes = await request.get(`/role/${row.id}/permissions`)
    selectedPerms.value = (permRes.data || []).map(p => p.id)
  } catch (e) {}

  await loadAllPermissions()
  permDialogVisible.value = true
}

const handleAssignPerms = async () => {
  submitting.value = true
  try {
    await request.post(`/role/${currentRoleId.value}/permissions`, { permissionIds: selectedPerms.value })
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
    loadRoles()
  } finally {
    submitting.value = false
  }
}
</script>
