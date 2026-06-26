<template>
  <div class="page-container">
    <el-breadcrumb separator="/" style="margin-bottom:16px">
      <el-breadcrumb-item :to="{ path: '/dataset' }">知识库管理</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/documents', query: { dataset: datasetId } }">文档管理</el-breadcrumb-item>
      <el-breadcrumb-item>切片详情 <span v-if="docName" style="color:var(--text-3)">（{{ docName }}）</span></el-breadcrumb-item>
    </el-breadcrumb>

    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:16px">
      <div>
        <div class="page-title">解析与切片</div>
        <div class="page-sub">查看文档的分块结果 · 启用/禁用切片控制检索可见性</div>
      </div>
      <div style="display:flex;gap:8px">
        <el-button @click="goBack">← 返回文档管理</el-button>
        <el-button type="primary" @click="showSubmitAudit = true">提交审核</el-button>
      </div>
    </div>

    <!-- 提交审核弹窗 -->
    <el-dialog v-model="showSubmitAudit" title="提交审核" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        提交后文档进入审核流程。审核通过后切片自动发布（available=true），可被检索命中。
      </el-alert>
      <el-form label-width="100px">
        <el-form-item label="文档">
          <span>{{ docName || docId }}</span>
        </el-form-item>
        <el-form-item label="审核模板" required>
          <el-select v-model="selectedTemplateId" placeholder="选择审核模板" style="width:100%">
            <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitAudit = false">取消</el-button>
        <el-button type="primary" @click="onSubmitAudit" :loading="submitting">提交</el-button>
      </template>
    </el-dialog>

    <el-card v-if="!datasetId || !docId" shadow="never">
      <el-empty description="缺少 datasetId 或 docId，请从文档管理进入">
        <el-button type="primary" @click="$router.push('/dataset')">前往知识库管理</el-button>
      </el-empty>
    </el-card>

    <div v-else v-loading="loading">
      <el-alert v-if="chunks.length === 0 && !loading" type="info" :closable="false" style="margin-bottom:16px">
        该文档暂无切片。可能尚未解析完成，请稍后
        <el-link type="primary" @click="loadChunks">刷新</el-link>
      </el-alert>

      <el-row :gutter="16">
        <el-col :span="14">
          <el-card shadow="never">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span style="font-weight:600">切片列表（{{ chunks.length }}）</span>
                <el-button text size="small" @click="loadChunks">刷新</el-button>
              </div>
            </template>
            <div v-for="(chunk, idx) in chunks" :key="chunk.id || idx" class="chunk-card"
              :class="{ active: selectedChunk?.id === chunk.id }" @click="selectedChunk = chunk">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
                <span style="font-size:13px;font-weight:500">Chunk #{{ idx + 1 }}</span>
                <div style="display:flex;gap:8px;align-items:center">
                  <el-tag v-if="chunk.importantKeywords?.length" size="small" type="primary">{{ chunk.importantKeywords.length }} 关键词</el-tag>
                  <el-switch v-model="chunk.available" size="small" @change="(val: boolean) => onToggleAvailability(chunk, val)" />
                  <el-button text size="small" type="danger" @click.stop="onDeleteChunk(chunk, idx)">删除</el-button>
                </div>
              </div>
              <div class="chunk-content">{{ (chunk.content || '').substring(0, 200) }}{{ (chunk.content || '').length > 200 ? '...' : '' }}</div>
            </div>
            <el-empty v-if="chunks.length === 0 && !loading" description="无切片" :image-size="60" />
          </el-card>
        </el-col>

        <el-col :span="10">
          <el-card shadow="never" v-if="selectedChunk">
            <template #header><span style="font-weight:600">切片详情</span></template>
            <div style="margin-bottom:12px">
              <div style="font-size:12px;color:var(--text-3);margin-bottom:4px">内容</div>
              <div class="chunk-content-full">{{ selectedChunk.content }}</div>
            </div>
            <div v-if="selectedChunk.importantKeywords?.length" style="margin-bottom:12px">
              <div style="font-size:12px;color:var(--text-3);margin-bottom:4px">关键词</div>
              <div style="display:flex;flex-wrap:wrap;gap:4px">
                <el-tag v-for="kw in selectedChunk.importantKeywords" :key="kw" size="small">{{ kw }}</el-tag>
              </div>
            </div>
            <el-button size="small" @click="$router.push({ path: '/annotation', query: { datasetId, docId, chunkId: selectedChunk.id } })">去标注</el-button>
          </el-card>
          <el-card v-else shadow="never"><el-empty description="点击左侧切片查看详情" :image-size="60" /></el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { chunkApi, auditApi } from '@/api'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const datasetId = ref((route.query.datasetId as string) || '')
const docId = ref((route.query.docId as string) || '')
const docName = ref((route.query.docName as string) || '')
const loading = ref(false)
const chunks = ref<any[]>([])
const selectedChunk = ref<any>(null)

// 提交审核
const showSubmitAudit = ref(false)
const submitting = ref(false)
const selectedTemplateId = ref<number | null>(null)
const templates = ref<any[]>([])

async function loadChunks() {
  if (!datasetId.value || !docId.value) return
  loading.value = true
  try {
    const resp = await chunkApi.list(datasetId.value, docId.value)
    chunks.value = resp?.chunks || resp?.list || []
    if (chunks.value.length > 0 && !selectedChunk.value) selectedChunk.value = chunks.value[0]
  } catch { chunks.value = [] }
  finally { loading.value = false }
}

async function onToggleAvailability(chunk: any, val: any) {
  try {
    await chunkApi.setAvailability(datasetId.value, docId.value, [chunk.id], val)
    ElMessage.success(val ? '已启用（参与检索）' : '已禁用（不参与检索）')
  } catch (e: any) {
    chunk.available = !val
    ElMessage.error('操作失败: ' + e.message)
  }
}

function goBack() {
  router.push({ path: '/documents', query: { dataset: datasetId.value } })
}

async function onDeleteChunk(chunk: any, idx: number) {
  try {
    await ElMessageBox.confirm('确认删除此切片？删除后不可恢复。', '删除确认', { type: 'warning' })
    await chunkApi.delete(datasetId.value, docId.value, [chunk.id])
    ElMessage.success('已删除')
    chunks.value.splice(idx, 1)
    if (selectedChunk.value?.id === chunk.id) selectedChunk.value = chunks.value[0] || null
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + e.message)
  }
}

async function loadTemplates() {
  try {
    templates.value = await auditApi.templates.list() || []
    if (templates.value.length > 0) selectedTemplateId.value = templates.value[0].id
  } catch { /* 静默 */ }
}

async function onSubmitAudit() {
  if (!selectedTemplateId.value) { ElMessage.warning('请选择审核模板'); return }
  submitting.value = true
  try {
    await auditApi.submit(datasetId.value, docId.value, selectedTemplateId.value)
    ElMessage.success('已提交审核')
    showSubmitAudit.value = false
    router.push('/audit-workbench')
  } catch (e: any) {
    ElMessage.error('提交失败: ' + e.message)
  } finally { submitting.value = false }
}

onMounted(() => {
  loadChunks()
  loadTemplates()
  if (route.query.action === 'submit-audit') showSubmitAudit.value = true
})
</script>

<style scoped>
.chunk-card { padding: 12px 16px; border: 1px solid var(--border-2); border-radius: 6px; margin-bottom: 8px; cursor: pointer; transition: all 0.15s; }
.chunk-card:hover { border-color: var(--primary-border); background: var(--primary-bg); }
.chunk-card.active { border-color: var(--primary); background: var(--primary-bg); }
.chunk-content { font-size: 13px; color: var(--text-2); line-height: 1.6; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; }
.chunk-content-full { font-size: 13px; color: var(--text-1); line-height: 1.8; background: var(--fill-1); padding: 12px; border-radius: 6px; max-height: 300px; overflow-y: auto; }
</style>
