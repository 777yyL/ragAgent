<template>
  <div class="page-container">
    <div class="page-title">AI 审核报告</div>
    <div class="page-sub">
      基于规则引擎 + 大模型双引擎自动扫描知识库，检测矛盾、错误、时效性、完整性、一致性、规范性 6 类问题
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6" v-for="stat in statsCards" :key="stat.label">
        <el-card shadow="hover" body-style="padding:20px">
          <div style="font-size:13px;color:var(--text-3);margin-bottom:8px">{{ stat.label }}</div>
          <div style="font-size:30px;font-weight:600;line-height:1.2" :style="{ color: stat.color }">{{ stat.value }}</div>
          <div v-if="stat.trend" style="font-size:12px;margin-top:8px;color:var(--text-3)" v-html="stat.trend"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选条 -->
    <el-card shadow="never" class="filter-card" body-style="padding:12px 16px">
      <div class="filter-line">
        <span class="filter-label">问题类型</span>
        <el-select v-model="filter.type" multiple collapse-tags collapse-tags-tooltip placeholder="全部类型"
          style="width:320px" size="small" @change="loadList">
          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </div>
      <div class="filter-line">
        <span class="filter-label">严重度</span>
        <el-select v-model="filter.severity" placeholder="全部" style="width:120px" size="small" @change="loadList">
          <el-option label="全部" value="" />
          <el-option label="高" value="high" />
          <el-option label="中" value="medium" />
          <el-option label="低" value="low" />
        </el-select>
        <span class="filter-label" style="margin-left:16px">状态</span>
        <el-select v-model="filter.status" placeholder="全部状态" style="width:140px" size="small" @change="loadList">
          <el-option label="全部状态" value="" />
          <el-option label="待处理" value="pending" />
          <el-option label="已采纳" value="accepted" />
          <el-option label="已忽略" value="ignored" />
        </el-select>
        <el-button size="small" style="margin-left:auto" @click="resetFilter">重置</el-button>
      </div>
    </el-card>

    <!-- 批量操作条 -->
    <transition name="el-fade-in">
      <div v-if="selectedRows.length" class="batch-bar">
        <span class="batch-count">已选择 <strong>{{ selectedRows.length }}</strong> 条</span>
        <el-button size="small" type="success" @click="batchAction('accept')">批量采纳</el-button>
        <el-button size="small" @click="batchAction('ignore')">批量忽略</el-button>
        <el-button size="small" type="primary" @click="exportReport">导出报告</el-button>
        <el-button size="small" style="margin-left:auto" @click="clearSelection">取消选择</el-button>
      </div>
    </transition>

    <!-- 问题表格 -->
    <el-card shadow="never" style="margin-top:12px" body-style="padding:0">
      <el-table :data="issues" v-loading="loading" @selection-change="onSelectionChange" ref="tableRef" row-key="id">
        <el-table-column type="selection" width="42" :selectable="(row: Issue) => row.status === 'pending'" />
        <el-table-column label="类型" width="88">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.type)" effect="light" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="严重度" width="80">
          <template #default="{ row }">
            <el-tag :type="severityTagType(row.severity)" effect="dark" size="small">
              {{ severityLabel(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="问题摘要" min-width="320">
          <template #default="{ row }">
            <div class="issue-summary" v-html="row.summary"></div>
          </template>
        </el-table-column>
        <el-table-column label="涉及文档" width="200">
          <template #default="{ row }">
            <div class="issue-doc">
              <div class="issue-doc-name">{{ row.docName }}</div>
              <el-link type="primary" :underline="false" style="font-size:12px">{{ row.docLocation }}</el-link>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="AI 建议" min-width="220">
          <template #default="{ row }">
            <div class="issue-suggest">{{ row.suggestion }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button size="small" type="primary" link @click="singleAction(row, 'accept')">采纳</el-button>
              <el-button size="small" link @click="singleAction(row, 'ignore')">忽略</el-button>
            </template>
            <span v-else style="font-size:12px;color:var(--text-4)">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <span>共 {{ total }} 条记录</span>
        <el-pagination v-model:current-page="page.page" v-model:page-size="page.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" background small
          @current-change="loadList" @size-change="loadList" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditApi } from '@/api'

type IssueType = 'conflict' | 'error' | 'timeliness' | 'completeness' | 'consistency' | 'standard'
type Severity = 'high' | 'medium' | 'low'
type IssueStatus = 'pending' | 'accepted' | 'ignored'

interface Issue {
  id: number
  type: IssueType
  severity: Severity
  summary: string
  docName: string
  docLocation: string
  suggestion: string
  status: IssueStatus
}

const typeOptions = [
  { label: '矛盾', value: 'conflict' },
  { label: '错误', value: 'error' },
  { label: '时效', value: 'timeliness' },
  { label: '完整', value: 'completeness' },
  { label: '一致', value: 'consistency' },
  { label: '规范', value: 'standard' },
] as const

const loading = ref(false)
const issues = ref<Issue[]>([])
const total = ref(0)
const selectedRows = ref<Issue[]>([])
const tableRef = ref()

const filter = reactive({
  type: [] as string[],
  severity: '',
  status: '',
})

const page = reactive({ page: 1, pageSize: 10 })

const statsCards = ref([
  { label: '待处理问题', value: 0, color: 'var(--warning)', trend: '加载中...' },
  { label: '高严重度', value: 0, color: 'var(--danger)', trend: '<span style="color:var(--danger)">需立即处置</span>' },
  { label: '本周新增', value: 0, color: 'var(--text-1)', trend: '<span style="color:var(--success)">AI 周一扫描</span>' },
  { label: '已采纳建议', value: 0, color: 'var(--success)', trend: '<span style="color:var(--success)">采纳率 -</span>' },
])

// ===== 类型映射 =====
function typeLabel(t: IssueType): string {
  return typeOptions.find(o => o.value === t)?.label || t
}
function typeTagType(t: IssueType): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<IssueType, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    conflict: 'danger',
    error: 'warning',
    timeliness: 'primary',
    completeness: 'success',
    consistency: 'info',
    standard: 'info',
  }
  return map[t]
}
function severityLabel(s: Severity): string {
  switch (s) {
    case 'high': return '高'
    case 'medium': return '中'
    case 'low': return '低'
  }
}
function severityTagType(s: Severity): 'danger' | 'warning' | 'info' {
  switch (s) {
    case 'high': return 'danger'
    case 'medium': return 'warning'
    case 'low': return 'info'
  }
}
function statusLabel(s: IssueStatus): string {
  switch (s) {
    case 'pending': return '待处理'
    case 'accepted': return '已采纳'
    case 'ignored': return '已忽略'
  }
}
function statusTagType(s: IssueStatus): 'warning' | 'success' | 'info' {
  switch (s) {
    case 'pending': return 'warning'
    case 'accepted': return 'success'
    case 'ignored': return 'info'
  }
}

// ===== 数据加载 =====
async function loadList() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.page,
      pageSize: page.pageSize,
    }
    if (filter.type.length) params.type = filter.type.join(',')
    if (filter.severity) params.severity = filter.severity
    if (filter.status) params.status = filter.status

    const res: any = await auditApi.aiIssues(params)
    // 兼容后端返回结构：{ list, total } 或数组
    if (Array.isArray(res)) {
      issues.value = res as Issue[]
      total.value = res.length
    } else if (res?.list) {
      issues.value = res.list as Issue[]
      total.value = res.total || res.list.length
    } else {
      issues.value = []
      total.value = 0
    }
    updateStats()
  } catch (e: any) {
    // 接口失败时使用示例数据展示（便于预览）
    if (e?.response?.status === 404 || !issues.value.length) {
      loadDemoData()
    } else {
      ElMessage.error('加载失败: ' + (e.message || '未知错误'))
    }
  } finally {
    loading.value = false
  }
}

function loadDemoData() {
  issues.value = [
    {
      id: 1, type: 'conflict', severity: 'high',
      summary: '#3 机组规程中过热器出口温度限值 <code style="color:var(--danger)">580°C</code> 与 DL/T 标准的 <code style="color:var(--success)">565°C</code> 不一致',
      docName: '#3 机组锅炉运行规程', docLocation: 'Chunk #14 / 第 28 页',
      suggestion: '建议以 DL/T 标准为准，修正为 565°C，并补充引用条款', status: 'pending',
    },
    {
      id: 2, type: 'error', severity: 'high',
      summary: '磨煤机防爆门规程中出口温度报警值 <code style="color:var(--danger)">150°C</code> 超过 DL/T 467 跳闸值 <code style="color:var(--success)">130°C</code>',
      docName: '磨煤机防爆门动作应急操作规程', docLocation: 'Chunk #2 / 第 8 页',
      suggestion: '依据 DL/T 467-2024 修正为「出口温度升高至 130°C 以上」', status: 'pending',
    },
    {
      id: 3, type: 'timeliness', severity: 'high',
      summary: '脱硫设计规程引用 DL/T 5145-2012，已被 <code style="color:var(--success)">2024 版</code>替代，涉及氨法脱硫章节重大修订',
      docName: '脱硫系统设计规范', docLocation: 'Chunk #5 / 第 12 页',
      suggestion: '标准已升级，建议更新引用版本并对涉及章节做差异比对', status: 'pending',
    },
    {
      id: 4, type: 'completeness', severity: 'medium',
      summary: '磨煤机防爆门规程缺少环境保护相关条款（消防废水、扬尘控制），不符合 GB 13223-2024',
      docName: '磨煤机防爆门动作应急操作规程', docLocation: 'Chunk #5 后 / 第 10 页',
      suggestion: '在 Chunk #5 后补充「环境保护注意事项」一节', status: 'pending',
    },
    {
      id: 5, type: 'consistency', severity: 'medium',
      summary: '磨煤机规程中「防爆门」出现 12 次、「防爆膜」出现 3 次，术语不统一',
      docName: '磨煤机防爆门动作应急操作规程', docLocation: 'Chunk #4 / 第 9 页',
      suggestion: '统一为 DL/T 5145 术语「防爆门（Explosion Door）」', status: 'accepted',
    },
    {
      id: 6, type: 'standard', severity: 'medium',
      summary: '凝汽器真空下降规程中编号格式不规范，「3.2.1」后直接跳到「3.2.3」，缺「3.2.2」',
      docName: '凝汽器真空下降应急处理流程', docLocation: 'Chunk #7 / 第 15 页',
      suggestion: '检查 3.2.2 内容是否遗漏，或重新顺延编号', status: 'pending',
    },
    {
      id: 7, type: 'conflict', severity: 'low',
      summary: '辅机循环水泵振动限值规程为 <code style="color:var(--danger)">7.1mm/s</code>，与同公司设备台账记录 <code style="color:var(--success)">4.5mm/s</code> 存在差异',
      docName: '辅机循环水泵运行规程', docLocation: 'Chunk #3 / 第 6 页',
      suggestion: '建议复核设备厂家说明书，确认是否为不同型号不同限值', status: 'pending',
    },
    {
      id: 8, type: 'standard', severity: 'low',
      summary: '过热器温度处置规程图表标题「图 3-2」与正文引用「图 3-3」不一致',
      docName: '过热器管壁温度超限处置规程', docLocation: 'Chunk #11 / 第 22 页',
      suggestion: '统一图表编号，或在正文引用处修正', status: 'accepted',
    },
  ]
  total.value = issues.value.length
  updateStats()
}

function updateStats() {
  const all = issues.value
  const pending = all.filter(i => i.status === 'pending')
  const high = all.filter(i => i.severity === 'high' && i.status === 'pending')
  const accepted = all.filter(i => i.status === 'accepted')
  const total = all.length || 1
  statsCards.value[0].value = pending.length
  statsCards.value[1].value = high.length
  statsCards.value[2].value = pending.length
  statsCards.value[3].value = accepted.length
  const rate = ((accepted.length / total) * 100).toFixed(1)
  statsCards.value[3].trend = `<span style="color:var(--success)">采纳率 ${rate}%</span>`
}

// ===== 交互 =====
function onSelectionChange(rows: Issue[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

function resetFilter() {
  filter.type = []
  filter.severity = ''
  filter.status = ''
  page.page = 1
  loadList()
}

async function singleAction(row: Issue, action: 'accept' | 'ignore') {
  try {
    // 乐观更新（API 未提供单条端点时回退到本地状态）
    row.status = action === 'accept' ? 'accepted' : 'ignored'
    ElMessage.success(action === 'accept' ? '已采纳' : '已忽略')
    updateStats()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

async function batchAction(action: 'accept' | 'ignore') {
  const count = selectedRows.value.length
  if (!count) return
  try {
    await ElMessageBox.confirm(
      `确认对选中的 ${count} 条问题进行${action === 'accept' ? '采纳' : '忽略'}操作？`,
      '批量操作', { type: 'warning' },
    )
  } catch {
    return
  }
  selectedRows.value.forEach(row => {
    row.status = action === 'accept' ? 'accepted' : 'ignored'
  })
  ElMessage.success(`已${action === 'accept' ? '采纳' : '忽略'} ${count} 条`)
  clearSelection()
  updateStats()
}

function exportReport() {
  ElMessage.info('导出功能开发中')
}

onMounted(loadList)
</script>

<style scoped>
.filter-card { margin-bottom: 12px; }
.filter-line {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 8px; flex-wrap: wrap;
}
.filter-line:last-child { margin-bottom: 0; }
.filter-label {
  font-size: 12px; color: var(--text-3); flex-shrink: 0;
  min-width: 56px;
}
.batch-bar {
  background: var(--primary-bg, #FFF1F0); border: 1px solid var(--primary-border, #FFA39E);
  border-radius: 6px; padding: 8px 16px; margin: 12px 0;
  display: flex; align-items: center; gap: 12px;
}
.batch-count { font-size: 13px; color: var(--primary-active, #A6000A); font-weight: 500; }
.issue-summary {
  line-height: 1.6; font-size: 13px; max-width: 360px;
}
.issue-summary :deep(code) {
  background: rgba(245, 63, 63, 0.08); color: var(--danger);
  padding: 1px 4px; border-radius: 3px; font-size: 12px;
  font-family: Consolas, Monaco, monospace;
}
.issue-doc-name { color: var(--text-1); font-weight: 500; font-size: 13px; margin-bottom: 2px; }
.issue-suggest {
  background: var(--primary-bg, #FFF1F0); padding: 8px 10px;
  border-radius: 4px; font-size: 12px;
  color: var(--primary-active, #A6000A); line-height: 1.5; max-width: 260px;
}
.pagination {
  padding: 12px 16px; display: flex;
  justify-content: space-between; align-items: center;
  border-top: 1px solid var(--border-2); font-size: 12px; color: var(--text-3);
}
</style>
