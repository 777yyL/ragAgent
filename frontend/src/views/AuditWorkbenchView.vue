<template>
  <div class="page-container">
    <div class="page-title">审核工作台</div>
    <div class="page-sub">三级审核流程：企业初审 → 区域复审 → 集团终审 → 发布（ADMIN 可越权审核所有节点）</div>

    <el-row :gutter="16">
      <!-- 左：待审列表 -->
      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header><span style="font-weight:600">待审列表（{{ pendingList.length }}）</span></template>
          <div v-for="item in pendingList" :key="item.instanceId" class="audit-item"
            :class="{ active: selected?.instanceId === item.instanceId }"
            @click="loadDetail(item.instanceId)">
            <div style="font-size:13px;font-weight:500;margin-bottom:4px">{{ item.docTitle || `文档#${item.docId}` }}</div>
            <div style="display:flex;gap:8px;align-items:center">
              <el-tag size="small" :type="nodeTagType(item.currentNode, item.totalNodes)">{{ nodeLabel(item.currentNode) }}</el-tag>
              <span style="font-size:12px;color:var(--text-3)">{{ item.status }}</span>
            </div>
          </div>
          <el-empty v-if="!pendingList.length && !loading" description="无待审项" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 右：审核详情 -->
      <el-col :span="16">
        <el-card shadow="never" v-if="selected">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600">{{ selected.docInfo?.title || '审核详情' }}</span>
              <el-tag v-if="selected.totalNodes" size="small">节点 {{ selected.currentNode }}/{{ selected.totalNodes }}</el-tag>
            </div>
          </template>

          <!-- 审核进度 -->
          <el-steps v-if="selected.template?.nodes" :active="selected.currentNode - 1" finish-status="success" style="margin-bottom:16px">
            <el-step v-for="(node, i) in selected.template.nodes" :key="i" :title="node.name || `节点${i+1}`" />
          </el-steps>

          <!-- 切片内容预览 -->
          <div v-if="selected.chunks?.length" style="margin-bottom:16px">
            <div style="font-size:13px;font-weight:600;margin-bottom:8px">文档切片（{{ selected.chunks.length }} 条）</div>
            <div class="chunk-preview-list">
              <div v-for="(chunk, idx) in selected.chunks.slice(0, 5)" :key="idx" class="chunk-preview-item">
                <div style="font-size:12px;color:var(--text-3);margin-bottom:2px">#{{ idx + 1 }}</div>
                <div style="font-size:13px;color:var(--text-2);line-height:1.6">{{ (chunk.content || '').substring(0, 150) }}{{ (chunk.content || '').length > 150 ? '...' : '' }}</div>
              </div>
              <el-button v-if="selected.chunks.length > 5" text size="small" @click="showAllChunks = !showAllChunks">
                {{ showAllChunks ? '收起' : `查看全部 ${selected.chunks.length} 条` }}
              </el-button>
              <template v-if="showAllChunks">
                <div v-for="(chunk, idx) in selected.chunks.slice(5)" :key="idx + 5" class="chunk-preview-item">
                  <div style="font-size:12px;color:var(--text-3);margin-bottom:2px">#{{ idx + 6 }}</div>
                  <div style="font-size:13px;color:var(--text-2);line-height:1.6">{{ (chunk.content || '').substring(0, 150) }}...</div>
                </div>
              </template>
            </div>
          </div>

          <!-- 审核操作 -->
          <el-divider />
          <el-input v-model="comment" type="textarea" :rows="2" placeholder="审核意见（退回时必填）" style="margin-bottom:12px" />
          <div style="display:flex;gap:12px;justify-content:flex-end">
            <el-button @click="onAction('reject')" type="danger" plain :loading="acting" :disabled="!canAct">退回</el-button>
            <el-button @click="onAction('approve')" type="primary" :loading="acting" :disabled="!canAct">通过</el-button>
          </div>

          <!-- 审核记录 -->
          <el-divider>审核记录</el-divider>
          <el-timeline v-if="selected.nodeRecords?.length">
            <el-timeline-item v-for="rec in selected.nodeRecords" :key="rec.id" :timestamp="rec.actedAt" placement="top"
              :type="rec.action === 'APPROVE' ? 'success' : 'danger'">
              <el-tag size="small" :type="rec.action === 'APPROVE' ? 'success' : 'danger'">{{ actionLabel(rec.action) }}</el-tag>
              <span style="margin-left:8px;font-size:13px">{{ rec.comment || '无' }}</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审核记录" :image-size="60" />
        </el-card>
        <el-card v-else shadow="never"><el-empty description="请从左侧选择待审项" /></el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { auditApi } from '@/api'

const loading = ref(false)
const acting = ref(false)
const pendingList = ref<any[]>([])
const selected = ref<any>(null)
const comment = ref('')
const showAllChunks = ref(false)

const canAct = computed(() => selected.value?.status === 'PENDING')

function nodeLabel(node: number): string {
  const map: Record<number, string> = { 1: '企业初审', 2: '区域复审', 3: '集团终审' }
  return map[node] || `节点${node}`
}

function nodeTagType(node: number, total: number): string {
  if (total && node >= total) return 'danger' // 终审
  if (node === 1) return 'warning'
  return 'primary'
}

function actionLabel(action: string): string {
  const map: Record<string, string> = { APPROVE: '通过', REJECT: '退回', WITHDRAW: '撤回', SUBMIT: '提交' }
  return map[action] || action
}

async function loadPending() {
  loading.value = true
  try {
    const resp = await auditApi.pending({ status: 'PENDING' })
    if (Array.isArray(resp)) pendingList.value = resp
    else if (resp?.list) pendingList.value = resp.list
    else if (resp?.data) pendingList.value = resp.data
    else pendingList.value = []
  } catch { pendingList.value = [] }
  finally { loading.value = false }
}

async function loadDetail(id: number) {
  if (!id || isNaN(id) || id <= 0) return
  try {
    selected.value = await auditApi.detail(id)
    comment.value = ''
    showAllChunks.value = false
  } catch (e: any) { ElMessage.error('加载失败: ' + e.message) }
}

async function onAction(action: 'approve' | 'reject') {
  if (!selected.value?.instanceId) { ElMessage.warning('请先选择待审项'); return }
  if (action === 'reject' && !comment.value.trim()) { ElMessage.warning('退回时请填写审核意见'); return }
  acting.value = true
  try {
    if (action === 'approve') await auditApi.approve(selected.value.instanceId, comment.value)
    else await auditApi.reject(selected.value.instanceId, comment.value)
    ElMessage.success(action === 'approve' ? '已通过' : '已退回')
    selected.value = null
    comment.value = ''
    loadPending()
  } catch (e: any) { ElMessage.error('操作失败: ' + e.message) }
  finally { acting.value = false }
}

onMounted(loadPending)
</script>

<style scoped>
.audit-item { padding: 12px; border-radius: 6px; cursor: pointer; border: 1px solid var(--border-2); margin-bottom: 8px; transition: all 0.15s; }
.audit-item:hover { border-color: var(--primary-border); background: var(--primary-bg); }
.audit-item.active { border-color: var(--primary); background: var(--primary-bg); box-shadow: 0 0 0 2px rgba(199,0,11,0.12); }
.chunk-preview-list { max-height: 400px; overflow-y: auto; }
.chunk-preview-item { padding: 8px 12px; background: var(--fill-1); border-radius: 4px; margin-bottom: 6px; }
</style>
