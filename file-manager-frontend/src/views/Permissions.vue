<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <div style="display: flex; align-items: center;">
          <span>权限管理</span>
          <el-input v-model="keyword" placeholder="搜索权限编码/名称" clearable
            style="width: 220px; margin-left: 16px;"
            @clear="loadData" @keyup.enter="loadData">
            <template #append><el-button @click="loadData"><el-icon><Search /></el-icon></el-button></template>
          </el-input>
        </div>
        <el-button type="primary" @click="showCreateDialog">新建权限</el-button>
      </div>
    </template>

    <!-- 权限列表 -->
    <el-table :data="permissions" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="permissionCode" label="权限编码" width="180" />
      <el-table-column prop="permissionName" label="权限名称" width="150" />
      <el-table-column label="资源类型" width="120">
        <template #default="{ row }">
          {{ row.resourceType === 1 ? '菜单' : '按钮/接口' }}
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
      @size-change="loadData"
      @current-change="loadData"
    />

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑权限' : '新建权限'" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="权限编码">
          <el-input v-model="form.permissionCode" :disabled="isEdit" placeholder="如：file:upload" />
        </el-form-item>
        <el-form-item label="权限名称">
          <el-input v-model="form.permissionName" placeholder="如：文件上传" />
        </el-form-item>
        <el-form-item label="资源类型">
          <el-radio-group v-model="form.resourceType">
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮/接口</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const permissions = ref([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const form = reactive({
  id: null,
  permissionCode: '',
  permissionName: '',
  resourceType: 2,
  sortOrder: 0
})

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/permission/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value }
    })
    permissions.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  isEdit.value = false
  form.id = null
  form.permissionCode = ''
  form.permissionName = ''
  form.resourceType = 2
  form.sortOrder = 0
  dialogVisible.value = true
}

function showEditDialog(row) {
  isEdit.value = true
  form.id = row.id
  form.permissionCode = row.permissionCode
  form.permissionName = row.permissionName
  form.resourceType = row.resourceType
  form.sortOrder = row.sortOrder
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.permissionCode || !form.permissionName) {
    ElMessage.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await request.put(`/permission/${form.id}`, { ...form })
      ElMessage.success('更新成功')
    } else {
      await request.post('/permission', { ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    pageNum.value = 1
    await loadData()
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除权限「${row.permissionName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  try {
    await request.delete(`/permission/${row.id}`)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // 真正的错误
    }
  }
}
</script>
