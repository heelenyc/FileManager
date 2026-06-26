<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between;">
          <span>存储节点监控</span>
          <div>
            <el-button size="small" @click="loadNodes">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="nodes" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="nodeName" label="节点名称" width="150" />
        <el-table-column prop="nodeHost" label="主机" width="150" />
        <el-table-column prop="nodePort" label="端口" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="availableSpace" label="可用空间" width="150">
          <template #default="{ row }">{{ formatSize(row.availableSpace) }}</template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
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

onMounted(() => loadNodes())

const loadNodes = async () => {
  loading.value = true
  try {
    const res = await request.get('/node/list')
    nodes.value = res.data || []
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取节点列表失败')
  } finally {
    loading.value = false
  }
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