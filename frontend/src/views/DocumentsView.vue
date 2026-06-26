<template>
  <div class="page-container">
    <!-- 面包屑 -->
    <el-breadcrumb separator="/" style="margin-bottom:16px">
      <el-breadcrumb-item :to="{ path: '/dataset' }">知识库管理</el-breadcrumb-item>
      <el-breadcrumb-item>文档管理 <span v-if="datasetName" style="color:var(--text-3)">（{{ datasetName }}）</span></el-breadcrumb-item>
    </el-breadcrumb>

    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:16px">
      <div>
        <div class="page-title">文档管理</div>
        <div class="page-sub">上传文档 → 等待解析 → 查看切片 · 文档流转的核心操作页</div>
      </div>
      <div v-if="datasetId" style="display:flex;gap:8px">
        <el-input v-model="searchKeywords" placeholder="搜索文件名" clearable style="width:200px" @keyup.enter="loadDocs" @clear="loadDocs">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-upload :show-file-list="false" :before-upload="onUpload" :http-request="() => {}" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.html">
          <el-button type="primary" :loading="uploading">+ 上传文档</el-button>
        </el-upload>
      </div>
    </div>

    <!-- 缺 datasetId 引导 -->
    <el-card v-if="!datasetId" shadow="never">
      <el-empty description="请先从知识库管理选择一个知识库">
        <el-button type="primary" @click="$router.push('/dataset')">前往知识库管理</el-button>
      </el-empty>
    </el-card>

    <!-- 文档列表 -->
    <el-card v-else shadow="never" v-loading="loading">
      <el-table :data="docs" size="default" stripe>
        <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="切片数" width="80" align="center">
          <template #default="{ row }">{{ row.chunkCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="解析状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.run)">{{ statusLabel(row.run) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="auditStatusType(row.auditStatus)" effect="plain">{{ auditStatusLabel(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="goDetail(row)">查看切片</el-button>
            <el-button v-if="row.auditStatus === 'DRAFT' || !row.auditStatus" type="warning" text size="small" @click="goSubmitAudit(row)">提交审核</el-button>
            <el-button v-if="row.auditStatus === 'PENDING'" type="success" text size="small" @click="$router.push('/audit-workbench')">去审核</el-button>
            <el-button text size="small" @click="onReparse(row)" :loading="row._reloading" :disabled="row.run === 'RUNNING'">重新解析</el-button>
            <el-button text size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { documentApi, datasetApi } from '@/api'

const route = useRoute()
const router = useRouter()
const datasetId = ref((route.query.dataset as string) || '')
const datasetName = ref('')
const loading = ref(false)
const uploading = ref(false)
const docs = ref<any[]>([])
const searchKeywords = ref('')

// 监听路由参数变化（从知识库管理跳转过来时刷新）
watch(() => route.query.dataset, (val) => {
  if (val) {
    datasetId.value = val as string
    loadDocs()
    loadDatasetName()
  }
})

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1048576).toFixed(1) + 'MB'
}

function statusType(run: string) {
  if (!run) return 'info'
  const r = run.toUpperCase()
  if (r === 'DONE') return 'success'
  if (r === 'RUNNING') return 'warning'
  if (r === 'FAIL') return 'danger'
  return 'info'
}

function statusLabel(run: string): string {
  if (!run) return '待解析'
  const map: Record<string, string> = { DONE: '已完成', RUNNING: '解析中', FAIL: '失败' }
  return map[run.toUpperCase()] || run
}

async function loadDatasetName() {
  if (!datasetId.value) return
  try {
    const resp = await datasetApi.list(1, 200)
    const ds = resp?.datasets?.find((d: any) => d.id === datasetId.value)
    datasetName.value = ds?.name || datasetId.value
  } catch { datasetName.value = datasetId.value }
}

async function loadDocs() {
  if (!datasetId.value) return
  loading.value = true
  try {
    const resp = await documentApi.list(datasetId.value, 1, 100, searchKeywords.value || undefined)
    docs.value = resp?.docs || resp?.list || []
  } catch (e: any) {
    docs.value = []
  } finally { loading.value = false }
}

async function onUpload(file: File) {
  if (!datasetId.value) {
    ElMessage.warning('请先从知识库管理选择一个知识库')
    return false
  }
  uploading.value = true
  try {
    await documentApi.upload(datasetId.value, file, true)
    ElMessage.success('上传成功，系统正在自动解析...')
    loadDocs()
  } catch (e: any) {
    ElMessage.error('上传失败: ' + e.message)
  } finally {
    uploading.value = false
  }
  return false
}

function goDetail(row: any) {
  router.push({ path: '/detail', query: { datasetId: datasetId.value, docId: row.id, docName: row.name } })
}

function goSubmitAudit(row: any) {
  router.push({ path: '/detail', query: { datasetId: datasetId.value, docId: row.id, docName: row.name, action: 'submit-audit' } })
}

function auditStatusType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info', PENDING: 'warning', APPROVED: 'primary',
    PUBLISHED: 'success', REJECTED: 'danger', WITHDRAWN: 'info'
  }
  return (map[status] || 'info') as any
}

function auditStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿', PENDING: '审核中', APPROVED: '已通过',
    PUBLISHED: '已发布', REJECTED: '已退回', WITHDRAWN: '已撤回'
  }
  return map[status] || '草稿'
}

async function onReparse(row: any) {
  row._reloading = true
  try {
    await documentApi.reparse(datasetId.value, row.id)
    ElMessage.success('已触发重新解析')
    setTimeout(loadDocs, 2000)
  } catch (e: any) { ElMessage.error('失败: ' + e.message) }
  finally { row._reloading = false }
}

async function onDownload(row: any) {
  try {
    const blob = await documentApi.download(datasetId.value, row.id)
    const url = URL.createObjectURL(blob as Blob)
    const a = document.createElement('a')
    a.href = url; a.download = row.name; a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) { ElMessage.error('下载失败: ' + e.message) }
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？此操作不可恢复。`, '删除确认', { type: 'warning' })
  await documentApi.delete(datasetId.value, [row.id])
  ElMessage.success('已删除')
  loadDocs()
}

onMounted(() => {
  if (datasetId.value) {
    loadDocs()
    loadDatasetName()
  }
})
</script>
