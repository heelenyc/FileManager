<template>
  <div>
    <!-- 工具栏 -->
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
      <div style="display: flex; align-items: center;">
        <el-upload
          v-if="hasPermission('file:upload')"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="handleUpload"
        >
          <el-button type="primary"><el-icon><Upload /></el-icon> 上传文件</el-button>
        </el-upload>
      </div>
      <el-input
        v-model="keyword"
        placeholder="搜索文件名"
        style="width: 250px;"
        clearable
        @clear="loadFiles"
        @keyup.enter="loadFiles"
      >
        <template #append>
          <el-button @click="loadFiles"><el-icon><Search /></el-icon></el-button>
        </template>
      </el-input>
    </div>

    <el-table :data="fileList" v-loading="loading" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="200" />
      <el-table-column prop="fileSize" label="大小" width="120">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="contentType" label="类型" width="150" />
      <el-table-column prop="uploadUsername" label="上传者" width="100" />
      <el-table-column prop="chunkCount" label="分片数" width="80" />
      <el-table-column prop="createdAt" label="上传时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="hasPermission('file:download')" type="primary" size="small" @click="handleDownload(row)">下载</el-button>
          <el-button v-if="hasPermission('file:delete')" type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
      @size-change="loadFiles"
      @current-change="loadFiles"
    />

    <!-- 大文件分片上传对话框 -->
    <el-dialog v-model="showChunkUpload" :title="'正在上传：' + (chunkFile?.name || '')" width="500px" :close-on-click-modal="false">
      <div v-if="chunkProgress.total > 0">
        <el-progress :percentage="Math.round(chunkProgress.uploaded / chunkProgress.total * 100)" />
        <span style="color: #666; margin-top: 8px; display: block;">
          {{ chunkProgress.uploaded }} / {{ chunkProgress.total }} 分片（{{ formatSize(chunkFile?.size || 0) }}）
        </span>
      </div>
      <div v-else style="text-align: center; color: #999;">准备中...</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const fileList = ref([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const permissions = ref([])

// 上传相关
const showChunkUpload = ref(false)
const chunkFile = ref(null)
const chunkUploading = ref(false)
const chunkProgress = ref({ uploaded: 0, total: 0 })
const chunkSize = ref(64 * 1024 * 1024)

onMounted(async () => {
  // 获取用户权限
  try {
    const res = await request.get('/auth/current')
    permissions.value = res.data.permissions || []
  } catch (error) {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    permissions.value = userInfo.permissions || []
  }

  // 获取配置
  try {
    const res = await request.get('/config')
    if (res?.data?.chunkSize) {
      chunkSize.value = res.data.chunkSize
    }
  } catch (e) {}
  loadFiles()
})

// 权限检查函数
const hasPermission = (perm) => {
  return permissions.value.includes(perm)
}

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await request.get('/file/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value }
    })
    fileList.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

// ---------- 上传 ----------
const beforeUpload = () => true

const handleUpload = async ({ file }) => {
  if (file.size > chunkSize.value) {
    chunkFile.value = file
    chunkProgress.value = { uploaded: 0, total: 0 }
    showChunkUpload.value = true
    await startChunkUpload()
    return
  }
  const formData = new FormData()
  formData.append('file', file)
  try {
    await request.post('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('上传成功')
    loadFiles()
  } catch (e) {}
}

const handleDownload = async (row) => {
  try {
    const res = await request.post(`/file/download/presign/${row.fileKey}`, null, {
      params: { expireSeconds: 3600 }
    })
    window.open(res.data, '_blank')
  } catch (e) {
    ElMessage.error('生成下载链接失败')
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除文件 "${row.fileName}" 吗？`, '确认删除', { type: 'warning' })
  await request.delete(`/file/${row.fileKey}`)
  ElMessage.success('删除成功（已移入回收站）')
  loadFiles()
}

const startChunkUpload = async () => {
  if (!chunkFile.value) return
  chunkUploading.value = true
  showChunkUpload.value = true

  try {
    const initRes = await request.post('/file/chunk/init', null, {
      params: {
        fileName: chunkFile.value.name,
        fileSize: chunkFile.value.size,
        contentType: chunkFile.value.type
      }
    })
    const { fileKey, chunkCount } = initRes.data
    chunkProgress.value.total = chunkCount

    for (let i = 0; i < chunkCount; i++) {
      const start = i * chunkSize.value
      const end = Math.min(start + chunkSize.value, chunkFile.value.size)
      const chunkBlob = chunkFile.value.slice(start, end)

      const formData = new FormData()
      formData.append('file', chunkBlob)
      formData.append('fileKey', fileKey)
      formData.append('chunkIndex', i)
      formData.append('totalChunks', chunkCount)

      await request.post('/file/chunk/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      chunkProgress.value.uploaded = i + 1
    }

    await request.post('/file/chunk/complete', null, { params: { fileKey } })
    ElMessage.success('大文件上传成功')
    showChunkUpload.value = false
    chunkFile.value = null
    loadFiles()
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    chunkUploading.value = false
  }
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
