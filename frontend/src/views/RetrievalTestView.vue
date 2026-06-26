<template>
  <div class="page-container">
    <div class="page-head">
      <div class="page-title">检索测试</div>
      <div class="page-sub">验证知识库召回质量，调优检索参数与策略</div>
    </div>

    <div class="test-layout">
      <!-- 左侧：配置面板 -->
      <div class="config-panel">
        <el-card shadow="never">
          <div class="config-group">
            <label class="config-label">知识库</label>
            <el-select v-model="form.datasetId" placeholder="选择知识库" style="width:100%">
              <el-option v-for="ds in datasets" :key="ds.id" :label="`${ds.name}（${ds.documentCount} 篇）`" :value="ds.id" />
            </el-select>
          </div>

          <div class="config-group">
            <label class="config-label">查询关键词</label>
            <el-input v-model="form.question" placeholder="输入关键词或问题" @keyup.enter="onTest" clearable />
          </div>

          <div class="config-group">
            <div class="slider-row">
              <span class="slider-name">Top K</span>
              <span class="slider-value">{{ form.topK }}</span>
            </div>
            <el-slider v-model="form.topK" :min="1" :max="20" />
          </div>

          <div class="config-group">
            <div class="slider-row">
              <span class="slider-name">相似度阈值</span>
              <span class="slider-value">{{ form.similarityThreshold.toFixed(2) }}</span>
            </div>
            <el-slider v-model="similarityPercent" :min="0" :max="100" :step="1" />
          </div>

          <div class="config-group">
            <label class="config-label">检索模式</label>
            <el-radio-group v-model="form.searchMode" style="display:flex; width:100%">
              <el-radio-button label="vector" style="flex:1">向量</el-radio-button>
              <el-radio-button label="bm25" style="flex:1">BM25</el-radio-button>
              <el-radio-button label="hybrid" style="flex:1">混合</el-radio-button>
            </el-radio-group>
          </div>

          <div class="config-group">
            <label class="config-label">Embedding 模型</label>
            <div class="model-display">
              <div class="model-name">bge-large-zh-v1.5</div>
              <div class="model-info">1024 维 · 中文优化 · 本地部署</div>
            </div>
          </div>

          <div class="config-group">
            <label class="config-label">Rerank 模型</label>
            <div class="model-display">
              <div class="model-name">bge-reranker-v2-m3</div>
              <div class="model-info">多语言 · 支持长文本 · 本地部署</div>
            </div>
          </div>

          <div class="config-actions">
            <el-button type="primary" style="flex:1" :loading="loading" @click="onTest">开始测试</el-button>
            <el-button style="flex:1" @click="onReset">重置</el-button>
          </div>
        </el-card>
      </div>

      <!-- 右侧：结果区 -->
      <div class="result-area">
        <!-- 统计条 -->
        <div class="result-bar">
          <div class="result-stat-item">
            <span>召回数</span>
            <strong>{{ result?.total ?? 0 }}</strong>
          </div>
          <div class="result-stat-divider" />
          <div class="result-stat-item">
            <span>耗时</span>
            <strong>{{ result ? (result.responseMs / 1000).toFixed(2) + 's' : '-' }}</strong>
          </div>
          <div class="result-stat-divider" />
          <div class="result-stat-item">
            <span>平均分</span>
            <strong>{{ avgScore }}</strong>
          </div>
          <div class="result-stat-divider" />
          <div class="result-stat-item">
            <span>高于阈值</span>
            <strong>{{ aboveThreshold }}</strong>
            <span style="color:var(--text-3)">/ {{ result?.total ?? 0 }}</span>
          </div>
        </div>

        <!-- 结果列表 -->
        <div class="recall-list" v-loading="loading">
          <el-empty
            v-if="!loading && !result?.chunks?.length"
            description="点击「开始测试」查看召回结果"
          />
          <el-card
            v-for="(chunk, idx) in result?.chunks || []"
            :key="chunk.chunkId || idx"
            shadow="never"
            class="recall-item"
          >
            <div class="recall-head">
              <div class="recall-title">
                #{{ idx + 1 }} {{ chunk.documentName || '未命名文档' }}
              </div>
              <el-tag size="small" :type="similarityTagType(chunk.similarity)">
                {{ chunk.similarity.toFixed(2) }}
              </el-tag>
            </div>
            <div class="recall-body" v-html="highlight(chunk.content)"></div>
            <div class="recall-meta">
              <el-tag size="small" type="primary" effect="light">{{ getCategory(chunk) }}</el-tag>
              <span>来源：{{ chunk.documentName || '-' }}</span>
              <span v-if="getPosition(chunk)">位置：{{ getPosition(chunk) }}</span>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 历史测试记录 -->
    <el-card shadow="never" class="history-card">
      <template #header>
        <div class="card-head">
          <div class="card-title">历史测试记录</div>
          <el-button text size="small" @click="loadHistory">刷新</el-button>
        </div>
      </template>
      <el-table :data="history" size="default">
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column prop="question" label="关键词" min-width="180">
          <template #default="{ row }">
            <span class="text-primary">{{ row.question }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="searchMode" label="检索模式" width="100" />
        <el-table-column prop="topK" label="Top K" width="80" />
        <el-table-column prop="total" label="召回数" width="80" />
        <el-table-column prop="avgScore" label="平均分" width="90">
          <template #default="{ row }">
            <span class="text-primary">{{ row.avgScore ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="responseMs" label="耗时" width="90">
          <template #default="{ row }">{{ row.responseMs ? row.responseMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button text size="small" @click="onReapply(row)">应用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { searchApi, datasetApi, type Dataset, type SearchResult, type SearchResultChunk } from '@/api'

type SearchMode = 'vector' | 'bm25' | 'hybrid'

const route = useRoute()
const datasets = ref<Dataset[]>([])
const loading = ref(false)
const result = ref<SearchResult | null>(null)
const history = ref<any[]>([])

const form = reactive({
  datasetId: (route.query.datasetId as string) || (route.query.dataset as string) || '',
  question: '过热器管壁温度超限',
  topK: 8,
  similarityThreshold: 0.55,
  searchMode: 'vector' as SearchMode,
})

const similarityPercent = computed({
  get: () => Math.round(form.similarityThreshold * 100),
  set: (v: number) => { form.similarityThreshold = v / 100 },
})

const avgScore = computed(() => {
  const list = result.value?.chunks || []
  if (!list.length) return '-'
  return (list.reduce((s, c) => s + c.similarity, 0) / list.length).toFixed(2)
})

const aboveThreshold = computed(() => {
  const list = result.value?.chunks || []
  return list.filter(c => c.similarity >= form.similarityThreshold).length
})

async function loadDatasets() {
  try {
    const resp = await datasetApi.list(1, 100)
    datasets.value = resp?.datasets || []
    if (!form.datasetId && datasets.value.length) {
      form.datasetId = datasets.value[0].id
    }
  } catch (e: any) {
    ElMessage.error('加载知识库失败: ' + (e?.message || ''))
  }
}

async function onTest() {
  if (!form.datasetId) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!form.question.trim()) {
    ElMessage.warning('请输入查询关键词')
    return
  }
  loading.value = true
  try {
    result.value = await searchApi.retrievalTest({
      datasetId: form.datasetId,
      question: form.question,
      topK: form.topK,
      similarityThreshold: form.similarityThreshold,
      searchMode: form.searchMode,
    })
    ElMessage.success(`召回 ${result.value?.total ?? 0} 条`)
    loadHistory()
  } catch (e: any) {
    ElMessage.error('检索失败: ' + (e?.message || ''))
    result.value = { chunks: [], total: 0, responseMs: 0 }
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    const resp: any = await searchApi.testHistory(1, 20)
    const list = resp?.list || resp?.data || resp?.records || resp || []
    history.value = Array.isArray(list) ? list : []
  } catch {
    history.value = []
  }
}

function onReset() {
  form.question = ''
  form.topK = 8
  form.similarityThreshold = 0.55
  form.searchMode = 'vector'
  result.value = null
}

function onReapply(row: any) {
  if (row.question) form.question = row.question
  if (row.topK) form.topK = Number(row.topK)
  if (row.searchMode) form.searchMode = row.searchMode as SearchMode
  if (row.similarityThreshold) form.similarityThreshold = Number(row.similarityThreshold)
  ElMessage.success('已应用历史参数，可点击「开始测试」')
}

function highlight(content: string): string {
  if (!content) return ''
  const q = form.question.trim()
  const base = content.length > 400 ? content.substring(0, 400) + '...' : content
  if (!q) return base
  const reg = new RegExp(`(${q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return base.replace(reg, '<mark>$1</mark>')
}

function similarityTagType(sim: number): 'success' | 'primary' | 'info' {
  if (sim >= 0.8) return 'success'
  if (sim >= 0.6) return 'primary'
  return 'info'
}

function getCategory(chunk: SearchResultChunk): string {
  return chunk.metadata?.category || chunk.metadata?.type || '知识片段'
}

function getPosition(chunk: SearchResultChunk): string {
  if (!chunk.positions?.length) return ''
  const p = chunk.positions[0]
  return typeof p === 'string' ? p : `第 ${p.page ?? '-'} 页 · 第 ${p.section ?? '-'} 段`
}

onMounted(async () => {
  await loadDatasets()
  loadHistory()
})
</script>

<style scoped>
.page-head { margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; color: var(--text-1); margin-bottom: 4px; }
.page-sub { font-size: 13px; color: var(--text-3); }

.test-layout { display: flex; gap: 16px; align-items: flex-start; }

.config-panel {
  width: 320px; flex-shrink: 0; position: sticky; top: 56px;
}
.config-group { margin-bottom: 20px; }
.config-group:last-of-type { margin-bottom: 0; }
.config-label {
  display: block; font-size: 13px; font-weight: 500; color: var(--text-1);
  margin-bottom: 8px;
}

.slider-row {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8px;
}
.slider-name { font-size: 13px; color: var(--text-2); }
.slider-value {
  font-size: 13px; font-weight: 600; color: var(--primary-active);
  background: var(--primary-bg); padding: 0 8px; height: 22px;
  border-radius: 4px; display: inline-flex; align-items: center;
}

.model-display {
  background: var(--fill-1); border-radius: 6px;
  padding: 12px; font-size: 13px;
}
.model-name { color: var(--text-1); font-weight: 500; }
.model-info { color: var(--text-3); font-size: 12px; margin-top: 4px; }

.config-actions {
  display: flex; gap: 8px; margin-top: 20px;
  padding-top: 20px; border-top: 1px solid var(--border-2);
}

.result-area { flex: 1; min-width: 0; }
.result-bar {
  display: flex; align-items: center; gap: 24px;
  background: var(--white); border-radius: 8px;
  padding: 12px 20px; margin-bottom: 16px;
  box-shadow: var(--shadow-1);
}
.result-stat-item {
  display: flex; align-items: center; gap: 8px;
  font-size: 13px; color: var(--text-3);
}
.result-stat-item strong {
  font-size: 18px; font-weight: 600; color: var(--primary-active);
}
.result-stat-divider {
  width: 1px; height: 24px; background: var(--border);
}

.recall-list { display: flex; flex-direction: column; gap: 12px; }
.recall-item {
  border: 1px solid var(--border-2); transition: all 0.15s;
}
.recall-item:hover { border-color: var(--primary-border); box-shadow: var(--shadow-2); }
.recall-item :deep(.el-card__body) { padding: 16px 20px; }
.recall-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.recall-title { font-size: 14px; font-weight: 500; color: var(--text-1); }
.recall-body {
  font-size: 13px; color: var(--text-2); line-height: 1.7;
  background: var(--fill-1); border-radius: 6px;
  padding: 12px 16px; margin-bottom: 12px;
  border-left: 3px solid var(--primary-border);
}
.recall-body :deep(mark) {
  background: var(--primary-bg); color: var(--primary-active);
  padding: 0 2px; border-radius: 2px;
}
.recall-meta {
  display: flex; align-items: center; gap: 12px;
  font-size: 12px; color: var(--text-3); flex-wrap: wrap;
}

.history-card { margin-top: 20px; }
.card-head {
  display: flex; justify-content: space-between; align-items: center;
}
.card-title { font-size: 16px; font-weight: 600; }
.text-primary { color: var(--primary-active); font-weight: 500; }
</style>
