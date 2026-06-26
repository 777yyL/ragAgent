<template>
  <div class="page-container">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:20px">
      <div>
        <div class="page-title">知识库管理</div>
        <div class="page-sub">数据集（Dataset）的创建、配置与管理 · 对应 RAGFlow DATASET MANAGEMENT 接口</div>
      </div>
      <el-button type="primary" @click="showCreate = true">+ 新建知识库</el-button>
    </div>

    <!-- 搜索栏 -->
    <div style="display:flex;gap:12px;margin-bottom:16px">
      <el-input v-model="searchKeywords" placeholder="搜索知识库名称" clearable style="width:300px" @keyup.enter="loadList" @clear="loadList">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button @click="loadList">查询</el-button>
    </div>

    <!-- 知识库列表 -->
    <el-row :gutter="16" v-loading="loading">
      <el-col :span="12" v-for="ds in datasets" :key="ds.id" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <div>
                <div style="font-size:16px;font-weight:600">{{ ds.name }}</div>
                <div style="font-size:12px;color:var(--text-3)">{{ ds.description || '无描述' }}</div>
              </div>
              <el-tag :type="ds.chunkCount > 0 ? 'success' : 'warning'" size="small">
                {{ ds.chunkCount > 0 ? '已就绪' : '空' }}
              </el-tag>
            </div>
          </template>
          <el-row :gutter="16" style="margin-bottom:12px">
            <el-col :span="6"><div style="font-size:18px;font-weight:600">{{ ds.documentCount }}</div><div style="font-size:11px;color:var(--text-3)">文档</div></el-col>
            <el-col :span="6"><div style="font-size:18px;font-weight:600">{{ ds.chunkCount }}</div><div style="font-size:11px;color:var(--text-3)">切片</div></el-col>
            <el-col :span="12"><div style="font-size:12px;color:var(--text-3)">{{ ds.chunkMethod || 'naive' }}</div><div style="font-size:11px;color:var(--text-4)">{{ ds.embeddingModel }}</div></el-col>
          </el-row>
          <div style="display:flex;gap:8px">
            <el-button type="primary" size="small" @click="$router.push(`/documents?dataset=${ds.id}`)">进入文档管理 →</el-button>
            <el-button text size="small" @click="$router.push(`/retrieval-test?dataset=${ds.id}`)">检索测试</el-button>
            <el-button text size="small" @click="openEdit(ds)">配置</el-button>
            <el-button text size="small" type="danger" @click="onDelete(ds)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="showCreate" :title="editingId ? '编辑知识库' : '新建知识库'" width="720px" @open="onDialogOpen">
      <el-form :model="form" label-width="140px" size="default">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required><el-input v-model="form.name" placeholder="如：锅炉运行规程库" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="访问权限">
              <el-select v-model="form.permission" style="width:100%">
                <el-option label="仅创建者 (me)" value="me" />
                <el-option label="团队共享 (team)" value="team" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="Embedding模型" required>
          <el-select v-model="form.embeddingModel" style="width:100%" filterable>
            <el-option label="BAAI/bge-large-zh-v1.5@BAAI" value="BAAI/bge-large-zh-v1.5@BAAI" />
            <el-option label="BAAI/bge-m3@BAAI" value="BAAI/bge-m3@BAAI" />
            <el-option label="jina-embeddings-v2-base-zh@JinaAI" value="jina-embeddings-v2-base-zh@JinaAI" />
          </el-select>
        </el-form-item>

        <!-- 切分方法（分组选择） -->
        <el-form-item label="切分方法">
          <el-select v-model="form.chunkMethod" style="width:100%" @change="onChunkMethodChange">
            <el-option-group label="通用（完整配置）">
              <el-option value="naive" label="naive - 通用（推荐）" />
            </el-option-group>
            <el-option-group label="文档类型（支持 RAPTOR）">
              <el-option value="book" label="book - 书籍" />
              <el-option value="laws" label="laws - 法律/法规" />
              <el-option value="manual" label="manual - 手册" />
              <el-option value="paper" label="paper - 论文" />
              <el-option value="presentation" label="presentation - 演示文稿" />
              <el-option value="qa" label="qa - 问答" />
            </el-option-group>
            <el-option-group label="特殊格式（无配置项）">
              <el-option value="table" label="table - 表格" />
              <el-option value="picture" label="picture - 图片" />
              <el-option value="one" label="one - 单块（整文档一个切片）" />
              <el-option value="email" label="email - 邮件" />
            </el-option-group>
            <el-option-group label="标签系统">
              <el-option value="tag" label="tag - 标签" />
            </el-option-group>
          </el-select>
        </el-form-item>

        <!-- ===== naive 专属配置 ===== -->
        <template v-if="form.chunkMethod === 'naive'">
          <el-divider content-position="left">解析器配置</el-divider>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="分段Token数">
                <el-input-number v-model="form.chunkTokenNum" :min="1" :max="2048" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="PDF任务页数">
                <el-input-number v-model="form.taskPageSize" :min="1" :max="48" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="分隔符">
            <el-input v-model="form.delimiter" placeholder="\n!?;。；！？" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="自动关键词数">
                <el-slider v-model="form.autoKeywords" :min="0" :max="32" show-input :show-input-controls="false" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="自动问题数">
                <el-slider v-model="form.autoQuestions" :min="0" :max="10" show-input :show-input-controls="false" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="DeepDOC版面识别">
                <el-switch v-model="form.layoutRecognize" />
                <span style="margin-left:8px;font-size:12px;color:var(--text-3)">OCR + 表格结构识别</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Excel转HTML">
                <el-switch v-model="form.html4excel" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- ===== naive / qa / manual / paper / book / laws / presentation 共享 RAPTOR ===== -->
        <template v-if="['naive', 'qa', 'manual', 'paper', 'book', 'laws', 'presentation'].includes(form.chunkMethod)">
          <el-divider content-position="left">高级增强</el-divider>
          <el-row :gutter="16">
            <el-col :span="8"><el-form-item label="RAPTOR递归摘要"><el-switch v-model="form.useRaptor" /></el-form-item></el-col>
            <template v-if="form.chunkMethod === 'naive'">
              <el-col :span="8"><el-form-item label="GraphRAG知识图谱"><el-switch v-model="form.useGraphrag" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="父子分块"><el-switch v-model="form.useParentChild" /></el-form-item></el-col>
            </template>
          </el-row>
        </template>

        <!-- ===== table / picture / one / email：无配置项 ===== -->
        <template v-if="['table', 'picture', 'one', 'email'].includes(form.chunkMethod)">
          <el-alert type="info" :closable="false" style="margin-top:8px">
            此切分方法不支持额外配置，parser_config 为空。
          </el-alert>
        </template>

        <!-- ===== tag：标签集 ===== -->
        <template v-if="form.chunkMethod === 'tag'">
          <el-alert type="warning" :closable="false" style="margin-top:8px">
            标签切分需要先创建标签集。请先在 RAGFlow 管理台配置，然后关联 dataset ID。
          </el-alert>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { datasetApi, type Dataset } from '@/api'

const loading = ref(false)
const datasets = ref<Dataset[]>([])
const showCreate = ref(false)
const editingId = ref('')
const searchKeywords = ref('')

const chunkMethods = [
  { value: 'naive', label: '通用' }, { value: 'book', label: '书籍' },
  { value: 'laws', label: '法规' }, { value: 'manual', label: '手册' },
  { value: 'paper', label: '论文' }, { value: 'qa', label: '问答' },
  { value: 'table', label: '表格' }, { value: 'picture', label: '图片' },
  { value: 'presentation', label: '演示' }, { value: 'one', label: '单块' },
  { value: 'email', label: '邮件' }, { value: 'tag', label: '标签' },
]

const form = reactive({
  name: '', description: '', permission: 'me',
  embeddingModel: 'BAAI/bge-large-zh-v1.5@BAAI', chunkMethod: 'naive',
  // naive 专属
  chunkTokenNum: 512, taskPageSize: 12, delimiter: '\\n!?;。；！？',
  autoKeywords: 5, autoQuestions: 3,
  layoutRecognize: true, html4excel: false,
  // 高级增强
  useGraphrag: true, useRaptor: false, useParentChild: false,
})

function resetForm() {
  Object.assign(form, { name: '', description: '', permission: 'me', embeddingModel: 'BAAI/bge-large-zh-v1.5@BAAI', chunkMethod: 'naive', useGraphrag: true, useRaptor: false, useParentChild: false, autoKeywords: 5, autoQuestions: 3 })
  editingId.value = ''
}

function onChunkMethodChange() {
  // 切换切分方法时重置不相关的配置项
  if (form.chunkMethod !== 'naive') {
    form.autoKeywords = 0
    form.autoQuestions = 0
    form.useGraphrag = false
    form.useParentChild = false
  }
}

function onDialogOpen() {
  // 打开弹窗时如果不是编辑，重置表单
  if (!editingId.value) {
    form.chunkMethod = 'naive'
    form.chunkTokenNum = 512
    form.autoKeywords = 5
    form.autoQuestions = 3
    form.layoutRecognize = true
    form.html4excel = false
    form.useGraphrag = true
    form.useRaptor = false
    form.useParentChild = false
  }
}

function openEdit(ds: Dataset) {
  editingId.value = ds.id
  form.name = ds.name
  form.description = ds.description || ''
  form.chunkMethod = ds.chunkMethod || 'naive'
  form.embeddingModel = ds.embeddingModel || 'BAAI/bge-large-zh-v1.5@BAAI'
  showCreate.value = true
}

async function loadList() {
  loading.value = true
  try {
    const resp = await datasetApi.list(1, 50, searchKeywords.value || undefined)
    datasets.value = resp?.datasets || []
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally { loading.value = false }
}

async function onSave() {
  // 根据 chunk_method 构建 parser_config（RAGFlow 文档要求不同方法支持不同配置）
  const parserConfig: any = {}

  if (form.chunkMethod === 'naive') {
    parserConfig.auto_keywords = form.autoKeywords
    parserConfig.auto_questions = form.autoQuestions
    parserConfig.chunk_token_num = form.chunkTokenNum
    parserConfig.delimiter = form.delimiter
    parserConfig.html4excel = form.html4excel
    parserConfig.layout_recognize = form.layoutRecognize ? 'DeepDOC' : ''
    parserConfig.task_page_size = form.taskPageSize
    parserConfig.raptor = { use_raptor: form.useRaptor }
    parserConfig.graphrag = { use_graphrag: form.useGraphrag }
    parserConfig.parent_child = { use_parent_child: form.useParentChild }
  } else if (['qa', 'manual', 'paper', 'book', 'laws', 'presentation'].includes(form.chunkMethod)) {
    // 这些方法只支持 raptor
    parserConfig.raptor = { use_raptor: form.useRaptor }
  }
  // table/picture/one/email: parserConfig 为空（不添加任何属性）

  const body: any = {
    name: form.name, description: form.description, permission: form.permission,
    embeddingModel: form.embeddingModel, chunkMethod: form.chunkMethod,
  }
  if (Object.keys(parserConfig).length > 0) {
    body.parserConfig = parserConfig
  }
  try {
    if (editingId.value) await datasetApi.update(editingId.value, body)
    else await datasetApi.create(body)
    ElMessage.success(editingId.value ? '更新成功' : '创建成功')
    showCreate.value = false
    resetForm()
    loadList()
  } catch (e: any) { ElMessage.error('保存失败: ' + e.message) }
}

async function onDelete(ds: Dataset) {
  await ElMessageBox.confirm(`确认删除知识库「${ds.name}」？`, '删除确认', { type: 'warning' })
  await datasetApi.delete([ds.id])
  ElMessage.success('删除成功')
  loadList()
}

onMounted(loadList)
</script>
