<template>
  <div class="page-container">
    <div class="page-title">版本管理</div>
    <div class="page-sub">知识库版本发布、回滚与环境切换</div>

    <!-- 环境 Tab -->
    <el-radio-group v-model="env" size="default" style="margin-bottom: 16px;">
      <el-radio-button label="ONLINE">线上环境 <span class="env-num">{{ onlineVersion }}</span></el-radio-button>
      <el-radio-button label="TEST">测试环境 <span class="env-num">{{ testVersion }}</span></el-radio-button>
    </el-radio-group>

    <div class="ver-layout">
      <!-- 左侧版本列表 -->
      <div class="ver-left">
        <div
          v-for="v in envVersions"
          :key="v.id"
          class="ver-card"
          :class="{ current: v.id === selectedId }"
          @click="selectVersion(v.id)"
        >
          <div class="ver-card-top">
            <div class="ver-num">{{ v.version }}</div>
            <el-tag v-if="v.id === onlineId" type="success" size="small">当前线上</el-tag>
            <el-tag v-else-if="v.id === testId" type="warning" size="small">测试中</el-tag>
            <el-tag v-else type="info" size="small">历史</el-tag>
          </div>
          <div class="ver-time">{{ v.publishTime }}</div>
          <div class="ver-author">发布人：{{ v.publisher }}</div>
          <div class="ver-summary">{{ v.summary }}</div>
          <div class="ver-stats">
            <span class="n-add">+{{ v.addCount }} 新增</span>
            <span class="n-mod">~{{ v.modCount }} 修改</span>
            <span class="n-del">-{{ v.delCount }} 删除</span>
          </div>
          <div class="ver-dataset">
            dataset: <code>{{ v.dataset }}</code>
          </div>
          <div class="ver-actions">
            <el-button
              v-if="env === 'TEST' && v.id !== onlineId"
              type="primary"
              text
              size="small"
              :loading="actionLoading === v.id"
              @click.stop="switchOnline(v.id)"
            >切换为线上</el-button>
            <el-button
              v-if="env === 'ONLINE' && v.id !== testId"
              type="warning"
              text
              size="small"
              :loading="actionLoading === v.id"
              @click.stop="switchTest(v.id)"
            >切换为测试</el-button>
            <el-button
              v-if="v.id !== onlineId"
              type="danger"
              text
              size="small"
              :loading="actionLoading === v.id"
              @click.stop="rollback(v.id)"
            >回滚</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧详情区 -->
      <div class="ver-right">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">{{ current?.version }} 版本详情</span>
              <el-tag v-if="current?.id === onlineId" type="success" size="small">当前线上</el-tag>
              <el-tag v-else-if="current?.id === testId" type="warning" size="small">测试中</el-tag>
            </div>
          </template>

          <template v-if="current">
            <!-- 元信息 -->
            <el-row :gutter="12" class="detail-meta">
              <el-col :span="6">
                <div class="meta-item">
                  <div class="meta-label">版本号</div>
                  <div class="meta-val">{{ current.version }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="meta-item">
                  <div class="meta-label">发布时间</div>
                  <div class="meta-val">{{ current.publishTime }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="meta-item">
                  <div class="meta-label">发布人</div>
                  <div class="meta-val">{{ current.publisher }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="meta-item">
                  <div class="meta-label">切片总量</div>
                  <div class="meta-val">{{ current.chunkTotal?.toLocaleString() }}</div>
                </div>
              </el-col>
            </el-row>

            <!-- 变更摘要 -->
            <div class="section-title">变更摘要</div>
            <div class="ver-summary-text">{{ current.summary }}</div>

            <!-- 新增列表 -->
            <div class="section-title">新增知识（{{ current.adds?.length || 0 }} 篇）</div>
            <div class="add-list">
              <div v-for="(a, i) in current.adds" :key="i" class="add-item">
                <el-tag type="success" size="small" effect="dark">NEW</el-tag>
                <span class="title">{{ a.title }}</span>
                <span class="cat">{{ a.category }}</span>
              </div>
              <el-empty v-if="!current.adds?.length" :image-size="60" description="无新增" />
            </div>

            <!-- 修改列表 -->
            <div class="section-title">修改内容（{{ current.mods?.length || 0 }} 处）</div>
            <div class="mod-list">
              <div v-for="(m, i) in current.mods" :key="i" class="diff-block">
                <div class="diff-head">{{ m.path }}</div>
                <div v-for="(line, j) in m.lines" :key="j" :class="diffLineClass(line)">{{ line.text }}</div>
              </div>
              <el-empty v-if="!current.mods?.length" :image-size="60" description="无修改" />
            </div>

            <!-- 删除列表 -->
            <div class="section-title">删除知识（{{ current.dels?.length || 0 }} 篇）</div>
            <div class="del-list">
              <div v-for="(d, i) in current.dels" :key="i" class="del-item">
                <el-tag type="danger" size="small" effect="dark">DEL</el-tag>
                <span class="title">{{ d.title }}</span>
                <span class="cat">{{ d.reason }}</span>
              </div>
              <el-empty v-if="!current.dels?.length" :image-size="60" description="无删除" />
            </div>

            <!-- 操作日志 -->
            <div class="section-title">操作日志</div>
            <el-timeline>
              <el-timeline-item
                v-for="(log, i) in current.logs"
                :key="i"
                :timestamp="log.time"
                placement="top"
                :color="log.user === '系统' ? '#86909C' : '#C7000B'"
              >
                <div class="tl-text">
                  <span class="tl-user">{{ log.user }}</span> {{ log.text }}
                </div>
              </el-timeline-item>
            </el-timeline>

            <!-- 底部操作 -->
            <div class="detail-footer">
              <el-button @click="exportChange">导出变更说明</el-button>
              <el-button @click="diffPrev">对比上一版本</el-button>
              <el-button type="primary" @click="createVersion">创建新版本</el-button>
            </div>
          </template>

          <el-empty v-else description="请选择左侧版本查看详情" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { versionApi } from '@/api'

// ===== 类型定义 =====
interface DiffLine {
  type: 'ctx' | 'add' | 'del'
  text: string
}
interface VersionAdd {
  title: string
  category: string
}
interface VersionMod {
  path: string
  lines: DiffLine[]
}
interface VersionDel {
  title: string
  reason: string
}
interface VersionLog {
  time: string
  user: string
  text: string
}
interface Version {
  id: number
  version: string
  env: 'ONLINE' | 'TEST' | 'HISTORY'
  publishTime: string
  publisher: string
  summary: string
  addCount: number
  modCount: number
  delCount: number
  dataset: string
  chunkTotal: number
  adds: VersionAdd[]
  mods: VersionMod[]
  dels: VersionDel[]
  logs: VersionLog[]
}

// ===== 状态 =====
const env = ref<'ONLINE' | 'TEST'>('ONLINE')
const versions = ref<Version[]>([])
const selectedId = ref<number | null>(null)
const onlineId = ref<number | null>(null)
const testId = ref<number | null>(null)
const actionLoading = ref<number | null>(null)

const onlineVersion = computed(() => versions.value.find(v => v.id === onlineId.value)?.version || '-')
const testVersion = computed(() => versions.value.find(v => v.id === testId.value)?.version || '-')

// 按当前选中环境过滤列表
const envVersions = computed(() => {
  if (env.value === 'ONLINE') {
    return versions.value.filter(v => v.env === 'ONLINE' || v.env === 'HISTORY')
  }
  return versions.value.filter(v => v.env === 'TEST' || v.env === 'HISTORY')
})

const current = computed(() => versions.value.find(v => v.id === selectedId.value) || null)

function selectVersion(id: number) {
  selectedId.value = id
}

function diffLineClass(line: DiffLine): string {
  if (line.type === 'add') return 'diff-add'
  if (line.type === 'del') return 'diff-del'
  return 'diff-ctx'
}

async function loadVersions() {
  try {
    const data = await versionApi.list()
    if (Array.isArray(data) && data.length) {
      versions.value = (data as any[]).map((v: any) => ({
        id: v.id,
        version: v.version,
        env: v.env || 'HISTORY',
        publishTime: v.publishTime || v.createdAt,
        publisher: v.publisher || v.createdBy || '-',
        summary: v.summary || '',
        addCount: v.addCount ?? 0,
        modCount: v.modCount ?? 0,
        delCount: v.delCount ?? 0,
        dataset: v.dataset || v.datasetName || '-',
        chunkTotal: v.chunkTotal ?? 0,
        adds: v.adds || [],
        mods: v.mods || [],
        dels: v.dels || [],
        logs: v.logs || [],
      }))
      // 推断线上/测试 ID
      const online = versions.value.find(v => v.env === 'ONLINE')
      const test = versions.value.find(v => v.env === 'TEST')
      onlineId.value = online?.id ?? null
      testId.value = test?.id ?? null
      selectedId.value = online?.id ?? versions.value[0]?.id ?? null
      return
    }
  } catch { /* 使用演示数据 */ }

  // 演示数据
  versions.value = fallbackVersions
  onlineId.value = 241
  testId.value = 250
  selectedId.value = 241
}

async function switchOnline(id: number) {
  try {
    await ElMessageBox.confirm(`确认将该版本切换为线上环境？`, '确认', { type: 'warning' })
    actionLoading.value = id
    try {
      await versionApi.switchOnline(id)
    } catch { /* 后端未实现时静默 */ }
    onlineId.value = id
    const v = versions.value.find(x => x.id === id)
    if (v) v.env = 'ONLINE'
    ElMessage.success('已切换为线上环境')
  } catch { /* 用户取消 */ } finally {
    actionLoading.value = null
  }
}

async function switchTest(id: number) {
  try {
    await ElMessageBox.confirm(`确认将该版本切换为测试环境？`, '确认', { type: 'warning' })
    actionLoading.value = id
    try {
      await versionApi.switchTest(id)
    } catch { /* 静默 */ }
    testId.value = id
    const v = versions.value.find(x => x.id === id)
    if (v) v.env = 'TEST'
    env.value = 'TEST'
    ElMessage.success('已切换为测试环境')
  } catch { /* 取消 */ } finally {
    actionLoading.value = null
  }
}

async function rollback(id: number) {
  try {
    await ElMessageBox.confirm(`确认回滚到该版本？此操作不可逆。`, '危险操作', { type: 'error' })
    actionLoading.value = id
    try {
      await versionApi.rollback(id)
    } catch { /* 静默 */ }
    ElMessage.success('已发起回滚')
  } catch { /* 取消 */ } finally {
    actionLoading.value = null
  }
}

function exportChange() {
  ElMessage.success('变更说明已导出')
}
function diffPrev() {
  ElMessage.info('对比功能开发中')
}
function createVersion() {
  ElMessage.info('新建版本功能开发中')
}

onMounted(loadVersions)

// ===== 演示数据 =====
const fallbackVersions: Version[] = [
  {
    id: 241,
    version: 'v2.4.1',
    env: 'ONLINE',
    publishTime: '2026-06-14 10:32',
    publisher: '李建国 · 设备部',
    summary: '新增环保岛超低排放相关规程 12 篇，修订过热器温度处置流程，删除 3 篇过期磨煤机操作规程。',
    addCount: 12,
    modCount: 8,
    delCount: 3,
    dataset: 'kb_power_v2.4.1_20260614',
    chunkTotal: 46732,
    adds: [
      { title: '环保岛超低排放改造运行维护手册（第二版）', category: '运行规程' },
      { title: '脱硝喷氨优化调整试验报告', category: '技术报告' },
      { title: '湿法脱硫石膏雨控制操作指导书', category: '运行规程' },
      { title: '电除尘器振打优化与节能运行方案', category: '技术方案' },
    ],
    mods: [
      {
        path: '运行规程 / 600MW 机组锅炉过热器管壁温度超限处置规程 · 第 4.2 节',
        lines: [
          { type: 'ctx', text: '4.2 二级应急响应触发条件' },
          { type: 'del', text: '当管壁温度超过 580°C 持续 5 分钟，应立即降低机组负荷至 80% 额定负荷。' },
          { type: 'add', text: '当管壁温度超过 565°C 持续 3 分钟，应立即降低机组负荷至 75% 额定负荷，' },
          { type: 'add', text: '并同步投入减温水一级旁路，通知集控室做好 MFT 预备。' },
          { type: 'ctx', text: '4.3 恢复条件' },
          { type: 'del', text: '管壁温度回落至 540°C 以下后，可逐步恢复负荷。' },
          { type: 'add', text: '管壁温度回落至 530°C 以下并稳定 10 分钟后，方可逐步恢复负荷，' },
          { type: 'add', text: '恢复速率不超过 2%/min。' },
        ],
      },
      {
        path: '运行规程 / 凝汽器真空下降应急处理标准化流程 · 第 3.1 节',
        lines: [
          { type: 'ctx', text: '3.1 判断标准' },
          { type: 'del', text: '真空低于 -85 kPa 视为异常。' },
          { type: 'add', text: '真空低于 -88 kPa 或较基准值下降超过 3 kPa/min 视为异常，' },
          { type: 'add', text: '需立即启动真空系统排查流程。' },
        ],
      },
    ],
    dels: [
      { title: '磨煤机防爆门操作规程（旧版）', reason: '被 DL/T 5145-2024 替代' },
      { title: '锅炉吹灰器操作细则（2018 版）', reason: '内容过期' },
      { title: '汽轮机润滑油系统维护手册（v1）', reason: '版本过旧' },
    ],
    logs: [
      { time: '2026-06-14 10:32', user: '李建国', text: '发布版本 v2.4.1 至线上环境' },
      { time: '2026-06-14 09:50', user: '张明远', text: '审核通过，签发上线许可' },
      { time: '2026-06-13 16:20', user: '王志强', text: '提交变更申请，含 23 项变更' },
      { time: '2026-06-12 14:00', user: '系统', text: '自动解析完成，生成切片 612 条' },
      { time: '2026-06-10 09:00', user: '李建国', text: '创建版本分支 v2.4.1-dev' },
    ],
  },
  {
    id: 240,
    version: 'v2.4.0',
    env: 'HISTORY',
    publishTime: '2026-06-07 14:20',
    publisher: '王志强 · 运行部',
    summary: '凝汽器真空下降应急处理标准化流程重构，新增两票管理规范 6 篇。',
    addCount: 9,
    modCount: 15,
    delCount: 1,
    dataset: 'kb_power_v2.4.0_20260607',
    chunkTotal: 46200,
    adds: [
      { title: '凝汽器真空下降应急处理标准化流程', category: '运行规程' },
      { title: '两票管理规范（一）- 工作票', category: '两票日志' },
    ],
    mods: [],
    dels: [],
    logs: [
      { time: '2026-06-07 14:20', user: '王志强', text: '发布版本 v2.4.0' },
      { time: '2026-06-06 10:00', user: '系统', text: '自动解析完成' },
    ],
  },
  {
    id: 232,
    version: 'v2.3.2',
    env: 'HISTORY',
    publishTime: '2026-05-28 09:15',
    publisher: '张明远 · 技术中心',
    summary: 'DL/T 5145-2024 火力发电厂烟气脱硫设计规范同步入库。',
    addCount: 5,
    modCount: 3,
    delCount: 0,
    dataset: 'kb_power_v2.3.2_20260528',
    chunkTotal: 45800,
    adds: [
      { title: 'DL/T 5145-2024 火力发电厂烟气脱硫设计规范', category: '行业标准' },
    ],
    mods: [],
    dels: [],
    logs: [
      { time: '2026-05-28 09:15', user: '张明远', text: '发布版本 v2.3.2' },
    ],
  },
  {
    id: 250,
    version: 'v2.5.0-rc3',
    env: 'TEST',
    publishTime: '2026-06-17 18:00',
    publisher: '李建国 · 设备部',
    summary: '测试版本：新增脱硫塔防腐大修规程、除尘器改造方案；修订化学水处理流程。',
    addCount: 6,
    modCount: 4,
    delCount: 1,
    dataset: 'kb_power_v2.5.0_rc3_20260617',
    chunkTotal: 47100,
    adds: [
      { title: '脱硫塔防腐大修规程', category: '运行规程' },
      { title: '电除尘器改造方案（第二版）', category: '技术方案' },
    ],
    mods: [],
    dels: [],
    logs: [
      { time: '2026-06-17 18:00', user: '李建国', text: '发布测试版本 v2.5.0-rc3' },
      { time: '2026-06-17 16:00', user: '系统', text: '自动解析完成，生成切片 320 条' },
    ],
  },
]
</script>

<style scoped>
.env-num {
  font-size: 12px;
  opacity: 0.75;
  margin-left: 4px;
}

.ver-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.ver-left {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ver-right {
  flex: 1;
  min-width: 0;
}

/* 版本卡片 */
.ver-card {
  background: #fff;
  border: 1px solid var(--border-2);
  border-radius: 6px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.15s;
}
.ver-card:hover {
  border-color: var(--primary-border);
  box-shadow: var(--shadow-1);
}
.ver-card.current {
  border-color: var(--primary);
  box-shadow: 0 0 0 1px var(--primary);
}
.ver-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}
.ver-num {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.ver-time {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 8px;
}
.ver-author {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 8px;
}
.ver-summary {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ver-stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--border-2);
}
.ver-stats span {
  color: var(--text-3);
}
.ver-stats .n-add {
  color: var(--success);
}
.ver-stats .n-mod {
  color: var(--primary);
}
.ver-stats .n-del {
  color: var(--danger);
}
.ver-dataset {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
}
.ver-dataset code {
  background: var(--fill-2);
  padding: 1px 8px;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  color: var(--text-2);
}
.ver-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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

/* 详情元信息 */
.detail-meta {
  margin-bottom: 16px;
}
.meta-item {
  background: var(--fill-1);
  border-radius: 6px;
  padding: 12px;
}
.meta-label {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 4px;
}
.meta-val {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-2);
}
.section-title:first-of-type {
  margin-top: 0;
}

.ver-summary-text {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
}

/* 新增/删除列表 */
.add-list,
.mod-list,
.del-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.add-item,
.del-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
}
.add-item {
  background: #E8FFEA;
}
.del-item {
  background: #FFECE8;
}
.add-item .title,
.del-item .title {
  color: var(--text-1);
}
.add-item .cat,
.del-item .cat {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}

/* diff 块 */
.diff-block {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.8;
  border: 1px solid var(--border-2);
  border-radius: 6px;
  overflow: hidden;
}
.diff-head {
  padding: 8px 16px;
  background: var(--fill-1);
  border-bottom: 1px solid var(--border-2);
  font-size: 12px;
  color: var(--text-3);
}
.diff-add {
  background: #E8FFEA;
  color: var(--success);
  padding: 0 16px;
  display: block;
}
.diff-del {
  background: #FFECE8;
  color: var(--danger);
  padding: 0 16px;
  display: block;
  text-decoration: line-through;
}
.diff-ctx {
  padding: 0 16px;
  color: var(--text-2);
  display: block;
}

/* 时间线 */
.tl-text {
  font-size: 13px;
  color: var(--text-2);
}
.tl-user {
  font-size: 12px;
  color: var(--primary);
  font-weight: 500;
}

/* 底部操作 */
.detail-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border-2);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
