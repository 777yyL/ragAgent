<template>
  <div class="page-container">
    <div class="page-head">
      <div class="page-title">知识标注工作台</div>
      <div class="page-sub">为知识切片标注关键词、分类、摘要、预期问题，提升检索召回率与准确度</div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <el-card v-for="s in stats" :key="s.label" shadow="never" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
      </el-card>
    </div>

    <div class="content-row">
      <!-- 左侧：chunk 列表 -->
      <div class="col-left">
        <el-card shadow="never" class="filter-card">
          <el-select v-model="datasetId" placeholder="选择知识库" size="default" style="width:100%" @change="loadChunks">
            <el-option v-for="ds in datasets" :key="ds.id" :label="`${ds.name}（${ds.chunkCount} 片）`" :value="ds.id" />
          </el-select>

          <el-tabs v-model="activeTab" class="filter-tabs">
            <el-tab-pane :name="'pending'">
              <template #label>
                <span>待标注 <el-badge :value="pendingCount" :max="999" type="warning" /></span>
              </template>
            </el-tab-pane>
            <el-tab-pane :name="'done'">
              <template #label>
                <span>已标注 <el-badge :value="doneCount" :max="999" type="primary" /></span>
              </template>
            </el-tab-pane>
          </el-tabs>

          <div class="chunk-list-info">
            共 {{ filteredChunks.length }} 个切片
          </div>

          <div class="chunk-list" v-loading="loading">
            <el-empty v-if="!filteredChunks.length && !loading" :image-size="60" description="暂无切片" />
            <div
              v-for="chunk in filteredChunks"
              :key="chunk.id"
              class="chunk-item"
              :class="{ active: selectedId === chunk.id }"
              @click="onSelectChunk(chunk)"
            >
              <div class="chunk-head">
                <span class="chunk-id">Chunk #{{ chunk.seq }}</span>
                <el-tag size="small" :type="chunk.annotated ? 'success' : 'warning'">
                  {{ chunk.annotated ? '已标注' : '待标注' }}
                </el-tag>
              </div>
              <div class="chunk-preview">{{ chunk.content }}</div>
              <div v-if="chunk.annotated && chunk.keywords?.length" class="chunk-status-row">
                <el-tag v-for="(k, i) in chunk.keywords.slice(0, 3)" :key="i" size="small" type="primary" effect="light">{{ k }}</el-tag>
                <el-tag v-if="chunk.keywords.length > 3" size="small" type="info" effect="plain">
                  +{{ chunk.keywords.length - 3 }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 右侧：标注编辑区 -->
      <div class="col-right">
        <el-card shadow="never" v-if="selectedChunk">
          <template #header>
            <div class="annotation-header">
              <div class="annotation-title">标注 Chunk #{{ selectedChunk.seq }}</div>
              <el-tag size="small" type="info" effect="plain">
                {{ selectedChunk.documentName || '当前文档' }}
              </el-tag>
            </div>
          </template>

          <!-- chunk 原文 -->
          <div class="annotation-section">
            <div class="section-label">切片内容</div>
            <div class="chunk-content-box">{{ selectedChunk.content }}</div>
          </div>

          <!-- 关键词 -->
          <div class="annotation-section">
            <div class="section-label">
              关键词标注
              <el-tag size="small" type="primary" effect="light">AI 自动生成</el-tag>
              <el-button text size="small" style="margin-left:auto" @click="onRegenerateKeywords">重新生成</el-button>
            </div>
            <div class="keyword-cloud">
              <el-tag
                v-for="(k, i) in form.keywords"
                :key="i"
                closable
                size="default"
                effect="plain"
                class="keyword-chip"
                @close="removeKeyword(i)"
              >
                {{ k }}
              </el-tag>
              <el-input
                v-if="keywordInputVisible"
                ref="keywordInputRef"
                v-model="keywordInputValue"
                size="small"
                style="width:160px"
                placeholder="回车添加"
                @keyup.enter="confirmKeyword"
                @blur="confirmKeyword"
              />
              <el-button v-else size="small" @click="showKeywordInput">+ 添加</el-button>
            </div>
          </div>

          <!-- 分类 + 重要度 -->
          <div class="form-grid-2">
            <div class="form-row">
              <div class="form-label">业务分类</div>
              <el-select v-model="form.category" size="default" style="width:100%">
                <el-option label="运行规程" value="运行规程" />
                <el-option label="故障案例" value="故障案例" />
                <el-option label="行业标准" value="行业标准" />
                <el-option label="技术报告" value="技术报告" />
              </el-select>
            </div>
            <div class="form-row">
              <div class="form-label">重要度（影响检索权重）</div>
              <div class="importance-slider">
                <el-slider v-model="form.importance" :min="1" :max="10" style="flex:1" />
                <span class="importance-value">{{ form.importance }}/10</span>
              </div>
            </div>
          </div>

          <!-- 摘要 -->
          <div class="annotation-section">
            <div class="section-label">
              摘要
              <el-tag size="small" type="primary" effect="light">AI 自动生成</el-tag>
            </div>
            <el-input
              v-model="form.summary"
              type="textarea"
              :rows="3"
              placeholder="请输入切片摘要"
            />
          </div>

          <!-- 预期问题 -->
          <div class="annotation-section">
            <div class="section-label">
              预期问题（用于问答增强）
              <el-tag size="small" type="primary" effect="light">AI 自动生成</el-tag>
              <el-button text size="small" style="margin-left:auto" @click="addQuestion">手动添加</el-button>
            </div>
            <div
              v-for="(q, i) in form.questions"
              :key="i"
              class="qa-item"
            >
              <div class="qa-q">
                <span>Q{{ i + 1 }}: {{ q.question }}</span>
                <el-button text size="small" type="danger" @click="removeQuestion(i)">删除</el-button>
              </div>
              <div class="qa-a">A: {{ q.answer }}</div>
            </div>
            <el-empty v-if="!form.questions.length" :image-size="40" description="暂无预期问题" />
          </div>

          <!-- 操作栏 -->
          <div class="action-bar">
            <el-button @click="onSkip">跳过</el-button>
            <el-button @click="onSaveDraft">暂存</el-button>
            <el-button type="success" @click="onConfirm">确认标注并下一个</el-button>
          </div>
        </el-card>
        <el-card v-else shadow="never">
          <el-empty description="请从左侧选择一个切片开始标注" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { chunkApi, datasetApi, type Dataset } from '@/api'

interface ChunkItem {
  id: string
  seq: number
  content: string
  annotated: boolean
  keywords: string[]
  documentName?: string
  important_keywords?: string[]
  questions?: { question: string; answer: string }[]
  summary?: string
  tag_kwd?: string[]
}

interface Question {
  question: string
  answer: string
}

const route = useRoute()

const datasets = ref<Dataset[]>([])
const datasetId = ref((route.query.datasetId as string) || (route.query.dataset as string) || '')
const loading = ref(false)
const chunks = ref<ChunkItem[]>([])
const selectedId = ref<string>('')
const activeTab = ref<'pending' | 'done'>('pending')

const keywordInputVisible = ref(false)
const keywordInputValue = ref('')
const keywordInputRef = ref<any>(null)

const form = reactive({
  keywords: [] as string[],
  category: '',
  importance: 8,
  summary: '',
  questions: [] as Question[],
})

const selectedChunk = computed(() => chunks.value.find(c => c.id === selectedId.value) || null)
const pendingCount = computed(() => chunks.value.filter(c => !c.annotated).length)
const doneCount = computed(() => chunks.value.filter(c => c.annotated).length)
const filteredChunks = computed(() =>
  chunks.value.filter(c => activeTab.value === 'pending' ? !c.annotated : c.annotated)
)

const stats = computed(() => [
  { label: '待标注切片', value: pendingCount.value, color: 'var(--warning)' },
  { label: '已标注切片', value: doneCount.value, color: 'var(--success)' },
  {
    label: '标注覆盖率',
    value: chunks.value.length
      ? Math.round((doneCount.value / chunks.value.length) * 100) + '%'
      : '0%',
    color: 'var(--primary)',
  },
  {
    label: '平均关键词/切片',
    value: chunks.value.length
      ? (chunks.value.reduce((s, c) => s + (c.keywords?.length || 0), 0) / chunks.value.length).toFixed(1)
      : '0.0',
    color: 'var(--text-1)',
  },
])

async function loadDatasets() {
  try {
    const resp = await datasetApi.list(1, 100)
    datasets.value = resp?.datasets || []
    if (!datasetId.value && datasets.value.length) {
      datasetId.value = datasets.value[0].id
    }
  } catch (e: any) {
    ElMessage.error('加载知识库失败: ' + (e?.message || ''))
  }
}

async function loadChunks() {
  if (!datasetId.value) return
  loading.value = true
  try {
    // 以文档为单位遍历：取第一个文档的切片做演示；实际可扩展为多文档汇总
    const docResp: any = await fetchFirstDocChunks(datasetId.value)
    const list = docResp?.chunks || docResp?.list || docResp?.data || docResp || []
    chunks.value = (Array.isArray(list) ? list : []).map((raw: any, idx: number) => {
      const keywords = raw.important_keywords || raw.keywords || []
      const questions = raw.questions || []
      const summary = raw.summary || ''
      const annotated = keywords.length > 0 || questions.length > 0 || !!summary
      return {
        id: String(raw.id ?? raw.chunk_id ?? idx),
        seq: Number(raw.seq ?? raw.order ?? idx + 1),
        content: raw.content || raw.content_with_weight || '',
        annotated,
        keywords,
        documentName: raw.documentName || raw.doc_name || '',
        important_keywords: keywords,
        questions,
        summary,
        tag_kwd: raw.tag_kwd || [],
      }
    })
    if (activeTab.value === 'pending' && !filteredChunks.value.length && doneCount.value > 0) {
      activeTab.value = 'done'
    }
    if (filteredChunks.value.length && !selectedId.value) {
      onSelectChunk(filteredChunks.value[0])
    }
  } catch (e: any) {
    ElMessage.error('加载切片失败: ' + (e?.message || ''))
    chunks.value = []
  } finally {
    loading.value = false
  }
}

// 简化：通过 documentApi 不便获取 docId，这里复用 chunkApi.list 时 dataset 维度
// 实际后端如有 chunk list by dataset 接口可替换
async function fetchFirstDocChunks(dsId: string): Promise<any> {
  // 兜底：若 query 中带 docId 直接用；否则返回空，等用户去 documents 选定后跳转过来
  const docId = (route.query.docId as string) || (route.query.doc as string) || ''
  if (docId) return await chunkApi.list(dsId, docId)
  return { chunks: [] }
}

function onSelectChunk(chunk: ChunkItem) {
  selectedId.value = chunk.id
  form.keywords = [...(chunk.keywords || [])]
  form.category = chunk.tag_kwd?.[0] || ''
  form.importance = 8
  form.summary = chunk.summary || ''
  form.questions = (chunk.questions || []).map(q => ({ question: q.question, answer: q.answer }))
}

function showKeywordInput() {
  keywordInputVisible.value = true
  keywordInputValue.value = ''
  nextTick(() => keywordInputRef.value?.focus?.())
}

function confirmKeyword() {
  const v = keywordInputValue.value.trim()
  if (v && !form.keywords.includes(v)) {
    form.keywords.push(v)
  }
  keywordInputVisible.value = false
  keywordInputValue.value = ''
}

function removeKeyword(i: number) {
  form.keywords.splice(i, 1)
}

function addQuestion() {
  form.questions.push({ question: '新问题（点击编辑）', answer: '对应答案' })
}

function removeQuestion(i: number) {
  form.questions.splice(i, 1)
}

function onRegenerateKeywords() {
  ElMessage.info('AI 关键词重新生成接口待接入')
}

async function persistAnnotated(status: 'draft' | 'confirmed') {
  if (!selectedChunk.value) return
  const c = selectedChunk.value
  const docId = (route.query.docId as string) || (route.query.doc as string) || ''
  if (!docId) {
    ElMessage.warning('缺少 docId 参数，无法保存')
    return
  }
  try {
    await chunkApi.update(datasetId.value, docId, c.id, {
      important_keywords: form.keywords,
      questions: form.questions,
      summary: form.summary,
      tag_kwd: form.category ? [form.category] : [],
      annotation_status: status,
    })
    c.keywords = [...form.keywords]
    c.questions = [...form.questions]
    c.summary = form.summary
    c.annotated = status === 'confirmed'
    if (status === 'confirmed') {
      ElMessage.success('已确认标注')
      const list = filteredChunks.value
      const idx = list.findIndex(x => x.id === c.id)
      const next = list[idx + 1]
      if (next) onSelectChunk(next)
    } else {
      ElMessage.success('已暂存')
    }
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.message || ''))
  }
}

function onSkip() {
  const list = filteredChunks.value
  const idx = list.findIndex(x => x.id === selectedId.value)
  const next = list[idx + 1]
  if (next) onSelectChunk(next)
  else ElMessage.info('已是最后一个')
}

function onSaveDraft() {
  persistAnnotated('draft')
}

function onConfirm() {
  persistAnnotated('confirmed')
}

onMounted(async () => {
  await loadDatasets()
  await loadChunks()
})
</script>

<style scoped>
.page-head { margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; color: var(--text-1); margin-bottom: 4px; }
.page-sub { font-size: 13px; color: var(--text-3); }

.stat-grid {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 16px; margin-bottom: 20px;
}
.stat-card { border: 1px solid var(--border-2); }
.stat-label { font-size: 13px; color: var(--text-3); margin-bottom: 8px; }
.stat-value { font-size: 30px; font-weight: 600; line-height: 1.2; }

.content-row {
  display: grid; grid-template-columns: 340px 1fr; gap: 16px;
}
.col-left, .col-right { display: flex; flex-direction: column; }

.filter-card { padding: 0; }
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-tabs { margin: 12px 0 8px; }

.chunk-list-info { font-size: 12px; color: var(--text-3); margin-bottom: 8px; }

.chunk-list { max-height: 560px; overflow-y: auto; }
.chunk-list::-webkit-scrollbar { width: 6px; }
.chunk-list::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }

.chunk-item {
  padding: 12px; border: 1px solid var(--border-2);
  border-radius: 6px; margin-bottom: 8px;
  cursor: pointer; transition: all 0.15s;
}
.chunk-item:hover { border-color: var(--primary-border); background: var(--primary-bg); }
.chunk-item.active {
  border-color: var(--primary); background: var(--primary-bg);
  box-shadow: 0 0 0 2px rgba(199, 0, 11, 0.12);
}
.chunk-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
}
.chunk-id { font-size: 12px; color: var(--text-3); }
.chunk-preview {
  font-size: 12px; color: var(--text-2); line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical;
  overflow: hidden;
}
.chunk-status-row { display: flex; gap: 4px; margin-top: 8px; flex-wrap: wrap; }

.annotation-header {
  display: flex; justify-content: space-between; align-items: center;
}
.annotation-title { font-size: 16px; font-weight: 600; }

.annotation-section { margin-bottom: 20px; }
.section-label {
  font-size: 13px; font-weight: 600; margin-bottom: 8px;
  display: flex; align-items: center; gap: 8px;
}

.chunk-content-box {
  background: var(--fill-1); border: 1px solid var(--border-2);
  border-radius: 6px; padding: 16px;
  font-size: 13px; line-height: 1.8; color: var(--text-1);
  max-height: 200px; overflow-y: auto;
}

.keyword-cloud { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.keyword-chip { border-style: dashed; }

.form-grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-row { margin-bottom: 12px; }
.form-label { font-size: 12px; color: var(--text-2); margin-bottom: 4px; }

.importance-slider { display: flex; align-items: center; gap: 12px; }
.importance-value { font-size: 13px; font-weight: 600; color: var(--primary-active); }

.qa-item {
  background: var(--primary-bg); border-left: 3px solid var(--primary);
  padding: 8px 12px; border-radius: 0 4px 4px 0;
  margin-bottom: 8px; font-size: 12px;
}
.qa-q {
  color: var(--primary-active); font-weight: 500; margin-bottom: 4px;
  display: flex; justify-content: space-between; align-items: center; gap: 8px;
}
.qa-a { color: var(--text-2); }

.action-bar {
  display: flex; gap: 8px; justify-content: flex-end;
  padding-top: 12px; border-top: 1px solid var(--border-2);
  margin-top: 16px;
}
</style>
