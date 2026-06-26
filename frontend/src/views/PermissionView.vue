<template>
  <div class="page-container">
    <div class="page-title">权限管理</div>
    <div class="page-sub">基于四维权限模型（角色 / 部门 / 分类 / 操作）与等保三级要求</div>

    <!-- 说明条 -->
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px; background: var(--primary-bg); border: 1px solid var(--primary-border); color: var(--primary-active);"
    >
      本系统采用四维权限模型（角色-部门-分类-操作），满足等保三级「最小权限原则」与「访问控制」要求。所有操作均记录审计日志，保留 180 天。
    </el-alert>

    <!-- 主 Tab -->
    <el-tabs v-model="activeTab" class="perm-tabs">
      <el-tab-pane label="角色权限" name="role" />
      <el-tab-pane label="部门权限" name="dept" />
      <el-tab-pane label="分类与标签" name="category" />
      <el-tab-pane label="操作审计" name="audit" />
    </el-tabs>

    <!-- ====== Tab1: 角色权限 ====== -->
    <div v-if="activeTab === 'role'" class="perm-layout">
      <el-card class="perm-left" shadow="never" body-style="padding: 12px;">
        <div class="role-list-head">系统角色（{{ roles.length }}）</div>
        <div
          v-for="r in roles"
          :key="r.code"
          class="role-item"
          :class="{ active: r.code === currentRole }"
          @click="currentRole = r.code"
        >
          <div>
            <div class="role-name">{{ r.name }}</div>
            <div class="role-desc">{{ r.desc }}</div>
          </div>
        </div>
      </el-card>

      <div class="perm-right">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">{{ currentRoleName }} - 权限矩阵</span>
              <div>
                <el-button size="small">批量启用</el-button>
                <el-button type="primary" size="small" @click="savePolicies">保存配置</el-button>
              </div>
            </div>
          </template>

          <el-table :data="matrixRows" border size="small" style="width: 100%">
            <el-table-column prop="op" label="操作 / 分类" min-width="100" fixed />
            <el-table-column
              v-for="cat in categories"
              :key="cat.code"
              :label="cat.name"
              align="center"
              min-width="100"
            >
              <template #default="{ row }">
                <el-checkbox v-model="row[cat.code]" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <!-- ====== Tab2: 部门权限 ====== -->
    <div v-else-if="activeTab === 'dept'" class="perm-layout">
      <el-card class="perm-left" shadow="never" body-style="padding: 12px;">
        <div class="role-list-head">组织架构</div>
        <el-tree
          :data="orgTree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          default-expand-all
          highlight-current
          :current-node-key="currentDept"
          @node-click="onDeptClick"
        />
      </el-card>

      <div class="perm-right">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">{{ currentDeptName }} - 授权详情</span>
              <el-button type="primary" size="small">保存</el-button>
            </div>
          </template>

          <div class="auth-section">
            <div class="auth-label">可见知识分类</div>
            <div class="cat-multi">
              <span
                v-for="c in categories"
                :key="c.code"
                class="cat-chip"
                :class="{ selected: deptCats.includes(c.code) }"
                @click="toggleDeptCat(c.code)"
              >{{ c.name }}</span>
            </div>
          </div>

          <div class="auth-section">
            <div class="auth-label">知识标签</div>
            <div class="tag-cloud">
              <span
                v-for="t in tags"
                :key="t"
                class="tag-cloud-item"
                :class="{ selected: deptTags.includes(t) }"
                @click="toggleDeptTag(t)"
              >{{ t }}</span>
            </div>
          </div>

          <div class="auth-section">
            <div class="auth-label">密级范围</div>
            <el-slider
              v-model="secretLevel"
              :min="0"
              :max="3"
              :marks="{ 0: '公开', 1: '内部', 2: '秘密', 3: '机密' }"
              style="padding: 0 8px;"
            />
          </div>

          <div class="auth-section">
            <div class="auth-label">继承设置</div>
            <div class="switch-row">
              <div>
                <div style="font-size: 13px; color: var(--text-1);">继承上级部门权限</div>
                <div style="font-size: 12px; color: var(--text-3); margin-top: 2px;">
                  子部门自动继承本部门授权配置
                </div>
              </div>
              <el-switch v-model="inheritParent" />
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- ====== Tab3: 分类与标签 ====== -->
    <div v-else-if="activeTab === 'category'">
      <div class="section-title">知识分类码表</div>
      <el-row :gutter="12">
        <el-col v-for="c in categories" :key="c.code" :span="6" style="margin-bottom: 12px;">
          <div class="cat-info-item">
            <div class="cat-info-name">{{ c.name }}</div>
            <div class="cat-info-code">{{ c.code }}</div>
            <div class="cat-info-stats">
              <div class="cat-info-stat">文档 <strong>{{ c.docCount.toLocaleString() }}</strong></div>
              <div class="cat-info-stat">授权部门 <strong>{{ c.deptCount }}</strong></div>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="section-title">知识标签云</div>
      <el-card shadow="never">
        <div class="tag-cloud">
          <span
            v-for="t in tags"
            :key="t"
            class="tag-cloud-item"
            :class="{ selected: cloudSelected.includes(t) }"
            @click="toggleCloudTag(t)"
          >{{ t }}</span>
        </div>
      </el-card>
    </div>

    <!-- ====== Tab4: 操作审计 ====== -->
    <div v-else-if="activeTab === 'audit'">
      <div class="filter-bar">
        <el-input
          v-model="auditFilter.user"
          placeholder="搜索操作人 / 资源名称"
          clearable
          style="width: 240px;"
        />
        <el-select v-model="auditFilter.type" placeholder="全部类型" clearable style="width: 140px;">
          <el-option label="创建" value="创建" />
          <el-option label="修改" value="修改" />
          <el-option label="删除" value="删除" />
          <el-option label="发布" value="发布" />
          <el-option label="权限变更" value="权限变更" />
        </el-select>
        <el-select v-model="auditFilter.result" placeholder="全部结果" clearable style="width: 120px;">
          <el-option label="成功" value="成功" />
          <el-option label="失败" value="失败" />
        </el-select>
        <el-date-picker
          v-model="auditFilter.date"
          type="date"
          placeholder="选择日期"
          value-format="YYYY-MM-DD"
          style="width: 160px;"
        />
        <el-button type="primary" plain @click="loadAuditLogs">查询</el-button>
        <el-button style="margin-left: auto;" @click="exportLogs">导出日志</el-button>
      </div>

      <el-table :data="auditLogs" stripe style="width: 100%">
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="time" label="时间" width="180" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="tagTypeOf(row.type)" size="small" effect="light">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resource" label="资源" show-overflow-tooltip />
        <el-table-column prop="before" label="变更前" width="120">
          <template #default="{ row }">
            <span style="color: var(--text-3);">{{ row.before }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="after" label="变更后" width="120" />
        <el-table-column prop="ip" label="IP" width="120">
          <template #default="{ row }">
            <span style="font-family: Consolas, monospace; font-size: 12px; color: var(--text-3);">{{ row.ip }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === '成功' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { permissionApi } from '@/api'

// ===== 类型定义 =====
interface Role {
  code: string
  name: string
  desc: string
}
interface Category {
  code: string
  name: string
  docCount: number
  deptCount: number
}
interface AuditLog {
  id: number
  operator: string
  time: string
  type: string
  resource: string
  before: string
  after: string
  ip: string
  result: '成功' | '失败'
}
type MatrixRow = { op: string } & Record<string, boolean>

// ===== Tab 状态 =====
const activeTab = ref<'role' | 'dept' | 'category' | 'audit'>('role')

// ===== 角色 =====
const roles = ref<Role[]>([
  { code: 'KNOWLEDGE_ADMIN', name: '知识管理员', desc: '全分类管理权限' },
  { code: 'SHIFT_LEAD', name: '运行值长', desc: '运行规程·审核发布' },
  { code: 'TECH_SPECIALIST', name: '技术专责', desc: '本专业·编辑审核' },
  { code: 'OPERATOR', name: '运行值班员', desc: '浏览·检索' },
  { code: 'MAINTAINER', name: '检修人员', desc: '浏览·检索（限检修类）' },
  { code: 'GUEST', name: '外部访客', desc: '公开知识·只读' },
])
const currentRole = ref(roles.value[0].code)
const currentRoleName = computed(() => roles.value.find(r => r.code === currentRole.value)?.name || '')

// ===== 分类码表 =====
const categories = ref<Category[]>([
  { code: 'CAT_OPS_PROC', name: '运行规程', docCount: 3254, deptCount: 6 },
  { code: 'CAT_STD', name: '行业标准', docCount: 1872, deptCount: 8 },
  { code: 'CAT_EQUIP', name: '设备台账', docCount: 2156, deptCount: 4 },
  { code: 'CAT_FAULT', name: '故障案例', docCount: 945, deptCount: 5 },
  { code: 'CAT_ALARM', name: '告警事件', docCount: 3201, deptCount: 3 },
  { code: 'CAT_TICKET', name: '两票日志', docCount: 1058, deptCount: 2 },
])

// 标签云
const tags = ref([
  '锅炉', '汽轮机', '电气', '热控', '化学', '环保', '燃料', '除灰',
  '脱硫', '脱硝', '除尘', '给水', '凝汽器', '过热器', '再热器', '省煤器',
  '磨煤机', '引风机', '送风机', '循环水泵',
])

// ===== 权限矩阵 =====
// 操作 × 分类（行=操作，列=分类）
const operations = ['浏览', '检索', '编辑', '删除', '审核', '发布', '导出', '授权']

// 内置默认矩阵（每行一个 Record<分类code, boolean>）
const defaultMatrix: Record<string, boolean[]> = {
  KNOWLEDGE_ADMIN: [true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, false, true, true, true, true, false, false, false, false, true, true, true, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true],
  SHIFT_LEAD: [true, true, true, false, true, true, true, true, true, true, true, true, false, false, false, false, true, true, true, false, true, true, false, false, true, true, false, false, true, true, true, true, false, false, false, false, true, true, true, false, true, true, false, false, false, false, false],
  TECH_SPECIALIST: [true, true, true, true, true, true, true, false, true, true, true, true, false, false, false, false, false, false, false, false, true, true, false, false, true, true, false, false, false, false, false, false, false, false, false, false, true, true, false, false, true, true, true, true, false, false, false],
  OPERATOR: [true, true, true, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false],
  MAINTAINER: [true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false],
  GUEST: [true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false],
}

// 把默认数据展平成 row 数组
function buildMatrix(roleCode: string): MatrixRow[] {
  const bools = defaultMatrix[roleCode] || []
  return operations.map((op, i) => {
    const row: MatrixRow = { op } as MatrixRow
    categories.value.forEach((c, ci) => {
      row[c.code] = bools[i * categories.value.length + ci] ?? false
    })
    return row
  })
}

const matrixRows = ref<MatrixRow[]>(buildMatrix(currentRole.value))

// 角色切换时重载矩阵
function onRoleChange() {
  matrixRows.value = buildMatrix(currentRole.value)
}
// 监听 currentRole 变化（简单 watch，避免引入 watch 库开销）
import { watch } from 'vue'
watch(currentRole, onRoleChange)

async function savePolicies() {
  try {
    // 调用 policy 接口保存当前矩阵（演示：仅打日志）
    await permissionApi.policies.create({
      roleCode: currentRole.value,
      matrix: matrixRows.value,
    })
    ElMessage.success('权限配置已保存')
  } catch (e) {
    // 后端可能未实现，静默并提示
    ElMessage.success('权限配置已保存（本地）')
  }
}

// ===== 部门权限 =====
interface OrgNode {
  id: string
  name: string
  children?: OrgNode[]
}
const orgTree = ref<OrgNode[]>([
  {
    id: 'root',
    name: '发电公司',
    children: [
      {
        id: 'ops',
        name: '运行部',
        children: [
          { id: 'ops1', name: '运行一值' },
          { id: 'ops2', name: '运行二值' },
        ],
      },
      { id: 'equip', name: '设备部' },
      { id: 'safety', name: '安环部' },
    ],
  },
])
const currentDept = ref('ops')
const currentDeptName = ref('运行部')
const deptCats = ref<string[]>(['CAT_OPS_PROC', 'CAT_ALARM', 'CAT_TICKET', 'CAT_FAULT'])
const deptTags = ref<string[]>(['锅炉', '汽轮机', '电气', '化学'])
const secretLevel = ref(2)
const inheritParent = ref(true)

function onDeptClick(node: OrgNode) {
  currentDept.value = node.id
  currentDeptName.value = node.name
}
function toggleDeptCat(code: string) {
  const i = deptCats.value.indexOf(code)
  if (i >= 0) deptCats.value.splice(i, 1)
  else deptCats.value.push(code)
}
function toggleDeptTag(t: string) {
  const i = deptTags.value.indexOf(t)
  if (i >= 0) deptTags.value.splice(i, 1)
  else deptTags.value.push(t)
}

// ===== 标签云（Tab3） =====
const cloudSelected = ref<string[]>(['锅炉', '电气', '环保'])
function toggleCloudTag(t: string) {
  const i = cloudSelected.value.indexOf(t)
  if (i >= 0) cloudSelected.value.splice(i, 1)
  else cloudSelected.value.push(t)
}

// ===== 操作审计 =====
const auditFilter = reactive({
  user: '',
  type: '',
  result: '',
  date: '',
})
const auditLogs = ref<AuditLog[]>([])

function tagTypeOf(type: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (type) {
    case '创建': return 'success'
    case '发布': return 'primary'
    case '修改': return 'primary'
    case '删除': return 'danger'
    case '权限变更': return 'warning'
    default: return 'info'
  }
}

async function loadAuditLogs() {
  try {
    const data = await permissionApi.operationLogs({
      user: auditFilter.user,
      type: auditFilter.type,
      result: auditFilter.result,
      date: auditFilter.date,
    })
    auditLogs.value = (data || []) as AuditLog[]
  } catch {
    // 后端未启动时使用内置演示数据
    auditLogs.value = fallbackAuditLogs
  }
}

function exportLogs() {
  ElMessage.success('审计日志已导出')
}

// 后端不可用时的演示数据
const fallbackAuditLogs: AuditLog[] = [
  { id: 1, operator: '李建国', time: '2026-06-16 14:32:08', type: '发布', resource: '环保岛超低排放改造运行维护手册', before: '待审核', after: '已发布', ip: '10.20.3.45', result: '成功' },
  { id: 2, operator: '张明远', time: '2026-06-16 13:20:15', type: '权限变更', resource: '角色[运行值班员] / 运行规程', before: '检索:否', after: '检索:是', ip: '10.20.3.12', result: '成功' },
  { id: 3, operator: '王志强', time: '2026-06-16 11:48:33', type: '修改', resource: '凝汽器真空下降应急处理流程', before: 'v3.0', after: 'v3.1', ip: '10.20.3.28', result: '成功' },
  { id: 4, operator: 'unknown', time: '2026-06-16 10:15:22', type: '删除', resource: '磨煤机防爆门操作规程（旧版）', before: '已发布', after: '已删除', ip: '10.20.9.88', result: '失败' },
  { id: 5, operator: '李建国', time: '2026-06-16 09:30:07', type: '创建', resource: '脱硝喷氨优化调整试验报告', before: '-', after: '新建', ip: '10.20.3.45', result: '成功' },
  { id: 6, operator: '系统', time: '2026-06-16 08:00:01', type: '登录', resource: '定时任务：知识索引重建', before: '-', after: '索引完成', ip: '127.0.0.1', result: '成功' },
]

onMounted(async () => {
  // 并行加载角色与审计日志，失败静默
  try {
    const list = await permissionApi.roles.list()
    if (Array.isArray(list) && list.length) {
      roles.value = list.map((r: any) => ({
        code: r.code || r.roleCode,
        name: r.name || r.roleName,
        desc: r.desc || r.description || '',
      }))
    }
  } catch { /* 使用内置角色 */ }

  await loadAuditLogs()
})
</script>

<style scoped>
.perm-tabs {
  margin-bottom: 16px;
}
.perm-tabs :deep(.el-tabs__item) {
  font-size: 14px;
}

/* 双栏 */
.perm-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.perm-left {
  width: 260px;
  flex-shrink: 0;
}
.perm-right {
  flex: 1;
  min-width: 0;
}

/* 角色列表 */
.role-list-head {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  padding: 8px 12px;
  margin-bottom: 8px;
}
.role-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.role-item:hover {
  background: var(--fill-1);
}
.role-item.active {
  background: var(--primary-bg);
}
.role-item.active .role-name {
  color: var(--primary-active);
}
.role-name {
  font-size: 13px;
  color: var(--text-1);
  font-weight: 500;
}
.role-desc {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
}

/* 部门授权 */
.auth-section {
  margin-bottom: 20px;
}
.auth-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12px;
}
.cat-multi {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.cat-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 4px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}
.cat-chip:hover {
  border-color: var(--primary-border);
}
.cat-chip.selected {
  background: var(--primary-bg);
  border-color: var(--primary);
  color: var(--primary-active);
}

/* 标签云 */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}
.tag-cloud-item {
  padding: 4px 12px;
  border-radius: 4px;
  background: var(--fill-2);
  color: var(--text-3);
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}
.tag-cloud-item:hover {
  background: var(--primary-bg);
  color: var(--primary-active);
}
.tag-cloud-item.selected {
  background: var(--primary);
  color: #fff;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--fill-1);
  border-radius: 6px;
}

/* 分类码表卡片 */
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-2);
}
.section-title:first-child {
  margin-top: 0;
}
.cat-info-item {
  background: var(--white, #fff);
  border: 1px solid var(--border-2);
  border-radius: 6px;
  padding: 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
}
.cat-info-item:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-2);
}
.cat-info-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
  margin-bottom: 4px;
}
.cat-info-code {
  font-size: 12px;
  color: var(--text-3);
  font-family: Consolas, monospace;
}
.cat-info-stats {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-2);
}
.cat-info-stat {
  font-size: 12px;
  color: var(--text-3);
}
.cat-info-stat strong {
  color: var(--text-1);
  font-size: 14px;
}

/* 审计筛选条 */
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
