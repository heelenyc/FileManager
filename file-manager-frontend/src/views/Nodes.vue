<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between;">
          <span>存储节点监控</span>
          <div>
            <!-- 搜索框 -->
            <el-input
              v-model="searchNodeName"
              placeholder="搜索节点名称"
              clearable
              style="width: 150px"
              @clear="loadNodes"
              @keyup.enter="loadNodes"
            />
            <!-- 状态筛选 -->
            <el-select
              v-model="searchStatus"
              placeholder="筛选状态"
              clearable
              style="width: 120px; margin-left: 10px"
              @change="loadNodes"
            >
              <el-option label="在线" :value="1" />
              <el-option label="离线" :value="0" />
              <el-option label="隔离" :value="2" />
            </el-select>
            <!-- 搜索按钮 -->
            <el-button size="small" type="primary" @click="loadNodes" style="margin-left: 10px">搜索</el-button>
            <!-- 刷新按钮 -->
            <el-button size="small" @click="clearSearch">重置</el-button>
          </div>
        </div>
      </template>
      <el-table :data="nodes" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="nodeName" label="节点名称" width="120" />
        <el-table-column prop="nodeHost" label="主机" width="130" />
        <el-table-column prop="nodePort" label="端口" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="availableSpace" label="可用空间" width="120">
          <template #default="{ row }">{{ formatSize(row.availableSpace) }}</template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="160" />
        <el-table-column label="操作" width="280" fixed="right" v-if="hasPermission('node:manage')">
          <template #default="{ row }">
            <!-- 在线节点：可以隔离 -->
            <el-button 
              v-if="row.status === 1" 
              type="warning" 
              size="small" 
              @click="handleIsolate(row)"
            >隔离</el-button>
            <!-- 隔离节点：可以恢复 -->
            <el-button 
              v-if="row.status === 2" 
              type="success" 
              size="small" 
              @click="handleRecover(row)"
            >恢复</el-button>
            <!-- 离线或隔离节点：可以删除 -->
            <el-button 
              v-if="row.status !== 1" 
              type="danger" 
              size="small" 
              @click="handleDelete(row)"
            >删除</el-button>
            <!-- 离线或隔离节点：可以彻底删除 -->
            <el-button 
              v-if="row.status !== 1" 
              type="danger" 
              size="small" 
              plain
              @click="handlePhysicalDelete(row)"
            >彻底删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const nodes = ref([])
const loading = ref(false)
const searchNodeName = ref('')
const searchStatus = ref(null)
const permissions = ref([])

onMounted(async () => {
  // 从 API 获取最新用户权限（确保数据是最新的）
  try {
    const res = await request.get('/auth/current')
    permissions.value = res.data.permissions || []
  } catch (error) {
    // 如果 API 调用失败，从 localStorage 读取
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    permissions.value = userInfo.permissions || []
  }
  loadNodes()
})

// 权限检查函数
const hasPermission = (perm) => {
  return permissions.value.includes(perm)
}

const loadNodes = async () => {
  loading.value = true
  try {
    const params = {}
    if (searchNodeName.value) {
      params.nodeName = searchNodeName.value
    }
    if (searchStatus.value !== null && searchStatus.value !== undefined) {
      params.status = searchStatus.value
    }
    const res = await request.get('/node/list', { params })
    nodes.value = res.data || []
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取节点列表失败')
  } finally {
    loading.value = false
  }
}

const clearSearch = () => {
  searchNodeName.value = ''
  searchStatus.value = null
  loadNodes()
}

// 状态类型映射
const getStatusType = (status) => {
  switch (status) {
    case 1: return 'success'  // 在线
    case 2: return 'warning'  // 隔离
    default: return 'danger'  // 离线
  }
}

// 状态文本映射
const getStatusText = (status) => {
  switch (status) {
    case 1: return '在线'
    case 2: return '隔离'
    default: return '离线'
  }
}

// 隔离节点
const handleIsolate = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认隔离节点 "${row.nodeName}"？隔离后该节点将不再参与文件存储。`,
      '隔离节点',
      { type: 'warning' }
    )
    await request.post(`/node/isolate/${row.nodeName}`)
    ElMessage.success('节点已隔离')
    loadNodes()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '隔离失败')
    }
  }
}

// 恢复节点
const handleRecover = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认恢复节点 "${row.nodeName}"？恢复后该节点将重新参与文件存储。`,
      '恢复节点',
      { type: 'info' }
    )
    await request.post(`/node/recover/${row.nodeName}`)
    ElMessage.success('节点已恢复')
    loadNodes()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '恢复失败')
    }
  }
}

// 删除节点
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除节点 "${row.nodeName}"？此操作为逻辑删除，可恢复。`,
      '删除节点',
      { type: 'danger' }
    )
    await request.delete(`/node/${row.nodeName}`)
    ElMessage.success('节点已删除')
    loadNodes()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

// 彻底删除节点（物理删除）
const handlePhysicalDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认彻底删除节点 "${row.nodeName}"？此操作将物理删除记录，不可恢复！`,
      '彻底删除节点',
      { type: 'danger', confirmButtonText: '彻底删除', cancelButtonText: '取消' }
    )
    await request.delete(`/node/physical/${row.nodeName}`)
    ElMessage.success('节点已彻底删除')
    loadNodes()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '彻底删除失败')
    }
  }
}

const formatSize = (bytes) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(2) + ' ' + units[i]
}
</script>