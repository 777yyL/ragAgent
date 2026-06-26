<template>
  <div class="page-container">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:20px">
      <div>
        <div class="page-title">智能体管理</div>
        <div class="page-sub">基于 RAGFlow Agent 的智能体编排与管理 · 7 大智能体覆盖发电运行核心场景</div>
      </div>
      <el-button type="primary" @click="openDialog()">+ 新建智能体</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索智能体名称" clearable style="flex:1;max-width:320px" />
      <el-select v-model="statusFilter" placeholder="全部状态" style="width:140px">
        <el-option label="全部状态" value="" />
        <el-option label="已发布" value="published" />
        <el-option label="草稿" value="draft" />
      </el-select>
    </div>

    <!-- 智能体卡片网格 -->
    <div v-loading="loading">
      <el-row :gutter="16">
        <el-col :span="8" v-for="agent in filteredAgents" :key="agent.id" style="margin-bottom:16px">
          <el-card shadow="hover" class="agent-card" body-style="padding:20px">
            <div class="agent-head">
              <div class="agent-icon">{{ agent.key }}</div>
              <div class="agent-title">
                <div class="agent-name">{{ agent.name }}</div>
                <div class="agent-desc">{{ agent.description }}</div>
                <div style="margin-top:6px">
                  <el-tag :type="agent.status === 'published' ? 'success' : 'info'" size="small" effect="light">
                    {{ agent.status === 'published' ? '已发布' : '草稿' }}
                  </el-tag>
                </div>
              </div>
            </div>

            <div class="agent-meta">
              <div>
                <div class="meta-num">{{ agent.calls.toLocaleString() }}</div>
                <div class="meta-label">累计调用</div>
              </div>
              <div style="text-align:right">
                <div class="meta-num">{{ agent.datasets.length }}</div>
                <div class="meta-label">关联知识库</div>
              </div>
            </div>

            <div class="agent-kbs">
              <div class="kbs-label">关联知识库</div>
              <div class="kbs-list">
                <el-tag v-for="kb in agent.datasets" :key="kb" size="small" type="info" effect="plain">
                  {{ kb }}
                </el-tag>
              </div>
            </div>

            <div class="agent-foot">
              <span class="agent-time">更新于 {{ agent.updatedAt }}</span>
              <div class="agent-ops">
                <el-button link type="primary" size="small" @click="$router.push('/chat')">对话</el-button>
                <el-button link type="primary" size="small" @click="openDialog(agent)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onDelete(agent)">删除</el-button>
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 新建卡片 -->
        <el-col :span="8" v-if="!statusFilter && !keyword" style="margin-bottom:16px">
          <div class="agent-new" @click="openDialog()">
            <el-icon :size="32" color="var(--text-3)"><Plus /></el-icon>
            <div class="new-text">新建 Agent</div>
            <div class="new-hint">基于 RAGFlow 可视化编排</div>
          </div>
        </el-col>
      </el-row>

      <el-empty v-if="!filteredAgents.length && !loading" description="暂无匹配智能体" />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing?.id ? '编辑智能体' : '新建智能体'" width="600px">
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：运行指导智能体" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="智能体功能描述" />
        </el-form-item>
        <el-form-item label="关联知识库" prop="datasetIds">
          <el-select v-model="form.datasetIds" multiple filterable placeholder="选择知识库" style="width:100%">
            <el-option v-for="ds in datasetOptions" :key="ds.id" :label="ds.name" :value="ds.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="DSL 配置">
          <el-input v-model="form.dsl" type="textarea" :rows="6"
            placeholder='{"llm":{"model":"qwen2.5-14b","temperature":0.3,"max_tokens":2048}}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保 存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { agentApi, datasetApi, type Dataset } from '@/api'

type AgentStatus = 'published' | 'draft'

interface Agent {
  id: string
  key: string
  name: string
  description: string
  datasets: string[]
  status: AgentStatus
  calls: number
  updatedAt: string
  dsl?: string
}

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const agents = ref<Agent[]>([])
const datasetOptions = ref<Dataset[]>([])

const dialogVisible = ref(false)
const editing = ref<Agent | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
  datasetIds: [] as string[],
  dsl: '',
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
  datasetIds: [{ required: true, message: '请至少选择一个知识库', trigger: 'change' }],
}

const filteredAgents = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return agents.value.filter(a => {
    if (kw && !a.name.toLowerCase().includes(kw)) return false
    if (statusFilter.value && a.status !== statusFilter.value) return false
    return true
  })
})

// ===== 示例数据：7 大智能体 =====
const demoAgents: Agent[] = [
  {
    id: '1', key: 'YZ', name: '运行指导智能体', status: 'published', calls: 8452,
    description: '针对锅炉/汽机启停、参数调整、异常处置提供分步骤运行操作指导，融合规程条款与实时工况。',
    datasets: ['锅炉规程库', '汽机规程库'], updatedAt: '2026-06-15 16:42',
  },
  {
    id: '2', key: 'GZ', name: '故障诊断智能体', status: 'published', calls: 6231,
    description: '结合历史故障案例与设备台账，对 DCS 报警进行根因定位并输出处置建议与同类案例参考。',
    datasets: ['故障案例库', '设备台账库'], updatedAt: '2026-06-14 10:18',
  },
  {
    id: '3', key: 'GC', name: '规程问答智能体', status: 'published', calls: 12085,
    description: '面向运行/检修人员的规程条款精确问答，支持条款溯源、版本对比与跨标准引用。',
    datasets: ['锅炉规程库', '行业标准库'], updatedAt: '2026-06-16 09:05',
  },
  {
    id: '4', key: 'PX', name: '培训考核智能体', status: 'published', calls: 3420,
    description: '基于全库知识生成岗位练兵题库与情景演练，支持自动判分与薄弱知识点画像。',
    datasets: ['全库'], updatedAt: '2026-06-12 14:30',
  },
  {
    id: '5', key: 'JX', name: '检修辅助智能体', status: 'published', calls: 2156,
    description: '依据设备台账与检修规程生成标准化作业指导书，支持工单物料关联与安全措施核对。',
    datasets: ['设备台账库'], updatedAt: '2026-06-10 11:22',
  },
  {
    id: '6', key: 'AJ', name: '安监合规智能体', status: 'published', calls: 1872,
    description: '结合行业标准与两票日志，对作业流程进行合规性审查并输出反违章清单。',
    datasets: ['行业标准库', '两票日志库'], updatedAt: '2026-06-08 15:48',
  },
  {
    id: '7', key: 'FZ', name: '仿真推演智能体', status: 'draft', calls: 0,
    description: '基于锅炉规程库构建工况仿真，支持超温/超压等极端工况的推演与处置决策预演。',
    datasets: ['锅炉规程库'], updatedAt: '2026-06-16 17:20',
  },
]

async function loadList() {
  loading.value = true
  try {
    const res = await agentApi.list()
    if (Array.isArray(res) && res.length) {
      agents.value = (res as any[]).map((a, i) => normalizeAgent(a, i))
    } else {
      agents.value = [...demoAgents]
    }
  } catch {
    agents.value = [...demoAgents]
  } finally {
    loading.value = false
  }
}

function normalizeAgent(a: any, idx: number): Agent {
  return {
    id: a.id || String(idx + 1),
    key: a.key || (a.name ? a.name.substring(0, 2).toUpperCase() : 'AG'),
    name: a.name || '未命名智能体',
    description: a.description || a.desc || '',
    datasets: Array.isArray(a.datasets) ? a.datasets : (a.datasets ? String(a.datasets).split(',') : []),
    status: a.status === 'draft' ? 'draft' : 'published',
    calls: Number(a.calls || 0),
    updatedAt: a.updatedAt || a.updated_at || '-',
    dsl: typeof a.dsl === 'string' ? a.dsl : (a.dsl ? JSON.stringify(a.dsl, null, 2) : ''),
  }
}

async function loadDatasets() {
  try {
    const res = await datasetApi.list(1, 100)
    datasetOptions.value = res.datasets || []
  } catch { /* 静默 */ }
}

// ===== 弹窗 =====
function openDialog(agent?: Agent) {
  editing.value = agent || null
  if (agent) {
    form.name = agent.name
    form.description = agent.description
    form.datasetIds = [...agent.datasets]
    form.dsl = agent.dsl || ''
  } else {
    form.name = ''
    form.description = ''
    form.datasetIds = []
    form.dsl = ''
  }
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    const payload = {
      name: form.name,
      description: form.description,
      datasets: form.datasetIds,
      dsl: form.dsl,
    }
    try {
      if (editing.value) {
        await agentApi.update(editing.value.id, payload)
        const idx = agents.value.findIndex(a => a.id === editing.value!.id)
        if (idx >= 0) {
          agents.value[idx] = {
            ...agents.value[idx],
            name: form.name,
            description: form.description,
            datasets: [...form.datasetIds],
            dsl: form.dsl,
          }
        }
        ElMessage.success('更新成功')
      } else {
        const res: any = await agentApi.create(payload)
        agents.value.unshift(normalizeAgent({ ...payload, id: res?.id || String(Date.now()), status: 'draft', calls: 0 }, 0))
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
    } catch (e: any) {
      // API 未就绪时本地添加
      if (editing.value) {
        const idx = agents.value.findIndex(a => a.id === editing.value!.id)
        if (idx >= 0) {
          agents.value[idx].name = form.name
          agents.value[idx].description = form.description
          agents.value[idx].datasets = [...form.datasetIds]
          agents.value[idx].dsl = form.dsl
        }
      } else {
        agents.value.unshift({
          id: String(Date.now()),
          key: form.name.substring(0, 2).toUpperCase(),
          name: form.name,
          description: form.description,
          datasets: [...form.datasetIds],
          status: 'draft',
          calls: 0,
          updatedAt: new Date().toISOString().substring(0, 16).replace('T', ' '),
          dsl: form.dsl,
        })
      }
      ElMessage.success(editing.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
    } finally {
      saving.value = false
    }
  })
}

async function onDelete(agent: Agent) {
  try {
    await ElMessageBox.confirm(`确认删除「${agent.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await agentApi.delete(agent.id)
  } catch { /* API 未就绪时仍本地删除 */ }
  agents.value = agents.value.filter(a => a.id !== agent.id)
  ElMessage.success('删除成功')
}

onMounted(() => {
  loadList()
  loadDatasets()
})
</script>

<style scoped>
.toolbar {
  display: flex; gap: 12px; align-items: center; margin-bottom: 16px;
}
.agent-card {
  height: 100%;
  display: flex; flex-direction: column;
  transition: all 0.15s;
}
.agent-card:hover {
  border-color: var(--primary);
}
.agent-head {
  display: flex; align-items: flex-start; gap: 12px; margin-bottom: 12px;
}
.agent-icon {
  width: 40px; height: 40px; border-radius: 6px;
  background: var(--primary-bg, #FFF1F0); color: var(--primary-active, #A6000A);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 600; flex-shrink: 0;
  border: 1px solid var(--primary-border, #FFA39E);
}
.agent-title { flex: 1; min-width: 0; }
.agent-name { font-size: 16px; font-weight: 600; margin-bottom: 2px; }
.agent-desc {
  font-size: 12px; color: var(--text-3); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.agent-meta {
  padding: 8px 0; border-top: 1px solid var(--border-2);
  border-bottom: 1px solid var(--border-2); margin: 12px 0;
  display: flex; justify-content: space-between; font-size: 12px;
}
.meta-num { font-size: 16px; font-weight: 600; color: var(--text-1); }
.meta-label { color: var(--text-3); font-size: 11px; margin-top: 2px; }
.agent-kbs { margin-bottom: 12px; }
.kbs-label { font-size: 11px; color: var(--text-3); margin-bottom: 8px; }
.kbs-list { display: flex; flex-wrap: wrap; gap: 4px; }
.agent-foot {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: auto; padding-top: 12px; border-top: 1px solid var(--border-2);
}
.agent-time { font-size: 11px; color: var(--text-4); }
.agent-ops { display: flex; gap: 2px; }

.agent-new {
  border: 2px dashed var(--border); background: transparent;
  border-radius: 8px; min-height: 220px;
  display: flex; align-items: center; justify-content: center; flex-direction: column;
  cursor: pointer; color: var(--text-3); transition: all 0.15s;
}
.agent-new:hover {
  border-color: var(--primary); color: var(--primary);
  background: var(--primary-bg, #FFF1F0);
}
.new-text { font-size: 14px; margin-top: 8px; }
.new-hint { font-size: 11px; color: var(--text-4); margin-top: 4px; }
</style>
