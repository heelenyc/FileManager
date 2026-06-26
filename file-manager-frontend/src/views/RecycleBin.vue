<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
      <span style="color: #999;">已删除的文件，可恢复或彻底清除</span>
      <el-input
        v-model="keyword"
        placeholder="搜索文件名"
        style="width: 250px;"
        clearable
        @clear="loadRecycled"
        @keyup.enter="loadRecycled"
      >
        <template #append>
          <el-button @click="loadRecycled"><el-icon><Search /></el-icon></el-button>
        </template>
      </el-input>
    </div>

    <el-table :data="recycleList" v-loading="loading" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="200" />
      <el-table-column prop="fileSize" label="大小" width="120">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="contentType" label="类型" width="150" />
      <el-table-column prop="uploadUsername" label="上传者" width="100" />
      <el-table-column prop="createdAt" label="上传时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="success" size="small" @click="handleRestore(row)">恢复</el-button>
          <el-button type="danger" size="small" @click="handlePurge(row)">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end;"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50]"
      @size-change="loadRecycled"
      @current-change="loadRecycled"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const recycleList = ref([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

onMounted(() => {
  loadRecycled()
})

const loadRecycled = async () => {
  loading.value = true
  try {
    const res = await request.get('/file/recycle/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value }
    })
    recycleList.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const handleRestore = async (row) => {
  await ElMessageBox.confirm(`确定恢复文件 "${row.fileName}" 吗？`, '确认恢复', { type: 'info' })
  await request.put(`/file/${row.fileKey}/restore`)
  ElMessage.success('恢复成功')
  loadRecycled()
}

const handlePurge = async (row) => {
  await ElMessageBox.confirm(
    `确定彻底删除 "${row.fileName}" 吗？此操作不可逆！`,
    '危险操作',
    { type: 'error', confirmButtonText: '彻底删除', confirmButtonClass: 'el-button--danger' }
  )
  await request.delete(`/file/${row.fileKey}/purge`)
  ElMessage.success('已彻底删除')
  loadRecycled()
}

const formatSize = (bytes) => {
  if (!bytes) return '0 B'
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
