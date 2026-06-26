<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-title">知识图谱</div>
    <div class="page-sub">实体关系可视化探索，支持溯源检索与社区发现</div>

    <!-- 顶部说明条 -->
    <el-alert
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
      title="当前使用 RAGFlow 内置 GraphRAG 引擎自动构建图谱"
      description="基于文档解析切片，由 LLM 抽取实体关系并构建知识图谱，支持实体类型/关系类型多维度筛选与可视化探索。"
      show-icon
    />

    <!-- 三栏布局 -->
    <div class="graph-layout">
      <!-- ============ 左侧筛选栏 ============ -->
      <div class="graph-left">
        <el-card shadow="never">
          <!-- 实体类型 -->
          <div class="filter-group">
            <div class="filter-group-title">实体类型</div>
            <el-checkbox-group v-model="entityTypesChecked">
              <div
                v-for="t in entityTypes"
                :key="t.value"
                class="check-row"
              >
                <el-checkbox :label="t.value" :value="t.value">
                  <span class="entity-dot" :style="{ background: t.color }"></span>
                  <span class="check-label">{{ t.label }}</span>
                  <span class="check-count">{{ t.count.toLocaleString() }}</span>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>

          <!-- 关系类型 -->
          <div class="filter-group">
            <div class="filter-group-title">关系类型</div>
            <el-checkbox-group v-model="relationTypesChecked">
              <div
                v-for="r in relationTypes"
                :key="r.value"
                class="check-row"
              >
                <el-checkbox :label="r.value" :value="r.value">
                  <span class="check-label">{{ r.label }}</span>
                  <span class="check-count">{{ r.count.toLocaleString() }}</span>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>

          <!-- 搜索 -->
          <div class="filter-group">
            <div class="filter-group-title">搜索实体</div>
            <el-input
              v-model="searchKeyword"
              placeholder="输入实体名称..."
              clearable
              size="default"
              style="margin-bottom: 12px"
            />
            <div class="entity-list">
              <div
                v-for="e in filteredEntities"
                :key="e.id"
                class="entity-row"
                :class="{ active: e.id === selectedEntity?.id }"
                @click="onSelectEntity(e)"
              >
                <span class="entity-dot" :style="{ background: getTypeColor(e.type) }"></span>
                <span class="entity-name">{{ e.name }}</span>
                <span class="entity-type-label">{{ getTypeLabel(e.type) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <!-- ============ 中央图谱画布 ============ -->
      <div class="graph-center">
        <div class="graph-canvas" ref="canvasRef">
          <!-- 信息条 -->
          <div class="canvas-info">
            <span>实体 <strong>{{ visibleNodes.length }}</strong></span>
            <span style="color: var(--text-4)">|</span>
            <span>关系 <strong>{{ visibleEdges.length }}</strong></span>
            <span style="color: var(--text-4)">|</span>
            <span>选中：<strong>{{ selectedEntity?.name || '-' }}</strong></span>
          </div>

          <!-- 操作工具栏 -->
          <div class="canvas-toolbar">
            <div class="toolbar-btn" title="重置视图" @click="resetSelection">
              <el-icon><Refresh /></el-icon>
            </div>
          </div>

          <!-- SVG 连线层 -->
          <svg class="edge-layer">
            <line
              v-for="(edge, idx) in visibleEdges"
              :key="`edge-${idx}`"
              :x1="`${edge.fromX}%`"
              :y1="`${edge.fromY}%`"
              :x2="`${edge.toX}%`"
              :y2="`${edge.toY}%`"
              :stroke="edge.highlight ? '#C7000B' : '#C9CDD4'"
              :stroke-width="edge.highlight ? 2 : 1.5"
              :opacity="edge.highlight ? 0.6 : 1"
            />
            <!-- 关系标签 -->
            <text
              v-for="(edge, idx) in visibleEdges.filter(e => e.label)"
              :key="`label-${idx}`"
              :x="`${(edge.fromX + edge.toX) / 2}%`"
              :y="`${(edge.fromY + edge.toY) / 2}%`"
              class="edge-label"
              :fill="edge.highlight ? '#C7000B' : '#86909C'"
            >
              {{ edge.label }}
            </text>
          </svg>

          <!-- 节点层 -->
          <div
            v-for="node in visibleNodes"
            :key="node.id"
            class="node"
            :class="[`node-${node.type}`, { selected: node.id === selectedEntity?.id, dimmed: selectedEntity && !isRelatedToSelected(node.id) }]"
            :style="{ left: `${node.x}%`, top: `${node.y}%` }"
            @click="onSelectNode(node)"
          >
            <div class="node-circle">{{ node.shortName }}</div>
            <div class="node-label">{{ node.name }}</div>
          </div>
        </div>

        <!-- 底部图例 -->
        <div class="legend-bar">
          <span class="legend-title">图例</span>
          <div v-for="t in entityTypes" :key="t.value" class="legend-item">
            <span class="legend-dot" :style="{ background: t.color }"></span>
            <span>{{ t.label }}</span>
          </div>
          <div class="legend-item" style="margin-left: auto">
            <svg width="20" height="8"><line x1="0" y1="4" x2="20" y2="4" stroke="#C7000B" stroke-width="2" /></svg>
            <span>高亮关联</span>
          </div>
          <div class="legend-item">
            <svg width="20" height="8"><line x1="0" y1="4" x2="20" y2="4" stroke="#C9CDD4" stroke-width="1.5" /></svg>
            <span>一般关系</span>
          </div>
        </div>
      </div>

      <!-- ============ 右侧实体详情 ============ -->
      <div class="graph-right">
        <el-card shadow="never">
          <template v-if="selectedEntity">
            <!-- 详情头部 -->
            <div class="detail-head">
              <div class="detail-type">
                {{ getTypeLabel(selectedEntity.type) }}
                <span v-if="selectedEntity.isCenter"> · 中心实体</span>
              </div>
              <div class="detail-name">{{ selectedEntity.name }}</div>
            </div>

            <!-- 属性 -->
            <div class="detail-section">
              <div class="detail-section-title">属性</div>
              <div
                v-for="prop in selectedEntity.props"
                :key="prop.key"
                class="detail-prop"
              >
                <span class="key">{{ prop.key }}</span>
                <span class="val" :style="prop.danger ? { color: 'var(--danger)' } : {}">{{ prop.value }}</span>
              </div>
            </div>

            <!-- 关联实体 -->
            <div class="detail-section">
              <div class="detail-section-title">关联实体</div>
              <div class="relation-list">
                <div
                  v-for="rel in selectedEntity.relations"
                  :key="rel.name"
                  class="relation-item"
                  @click="jumpToEntity(rel.name)"
                >
                  <span class="entity-dot" :style="{ background: getTypeColor(rel.type), width: '8px', height: '8px' }"></span>
                  <span class="rel-name">{{ rel.name }}</span>
                  <span class="rel-type">{{ getTypeLabel(rel.type) }} · {{ rel.relation }}</span>
                </div>
              </div>
            </div>

            <!-- 来源 chunk -->
            <div class="detail-section">
              <div class="detail-section-title">来源 Chunk</div>
              <div
                v-for="(chunk, idx) in selectedEntity.chunks"
                :key="idx"
                class="chunk-box"
              >
                {{ chunk.content }}
                <div class="chunk-source">{{ chunk.source }}</div>
              </div>
            </div>
          </template>

          <template v-else>
            <el-empty description="点击左侧或图谱节点查看详情" :image-size="80" />
          </template>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

/** 实体类型枚举 */
type EntityType = 'equipment' | 'parameter' | 'fault' | 'measure' | 'person' | 'department' | 'standard'

/** 实体属性 */
interface EntityProp {
  key: string
  value: string
  danger?: boolean
}

/** 关联实体 */
interface RelationItem {
  name: string
  type: EntityType
  relation: string
}

/** 来源 Chunk */
interface ChunkItem {
  content: string
  source: string
}

/** 图谱节点 */
interface GraphNode {
  id: string
  name: string
  shortName: string
  type: EntityType
  x: number
  y: number
  isCenter?: boolean
  props: EntityProp[]
  relations: RelationItem[]
  chunks: ChunkItem[]
}

/** 图谱边 */
interface GraphEdge {
  fromId: string
  toId: string
  fromX: number
  fromY: number
  toX: number
  toY: number
  label?: string
  highlight?: boolean
}

/** 实体类型定义 */
interface TypeMeta {
  value: EntityType
  label: string
  color: string
  count: number
}

const entityTypes: TypeMeta[] = [
  { value: 'equipment', label: '设备', color: '#A6000A', count: 1284 },
  { value: 'parameter', label: '参数', color: '#D9001B', count: 2156 },
  { value: 'fault', label: '故障', color: '#F53F3F', count: 945 },
  { value: 'measure', label: '措施', color: '#00B42A', count: 1632 },
  { value: 'standard', label: '标准', color: '#4E5969', count: 412 },
  { value: 'person', label: '人员', color: '#86909C', count: 328 },
  { value: 'department', label: '部门', color: '#C9CDD4', count: 56 },
]

const relationTypes = [
  { value: 'contain', label: '包含', count: 3824 },
  { value: 'cause', label: '引发', count: 1205 },
  { value: 'handle', label: '处理', count: 1632 },
  { value: 'supervise', label: '监管', count: 412 },
  { value: 'reference', label: '引用', count: 684 },
]

const entityTypesChecked = ref<EntityType[]>(['equipment', 'parameter', 'fault', 'measure', 'standard'])
const relationTypesChecked = ref<string[]>(['contain', 'cause', 'handle'])
const searchKeyword = ref('')

// ============ 图谱假数据 ============
const allNodes: GraphNode[] = [
  {
    id: 'n1',
    name: '过热器',
    shortName: '过热器',
    type: 'equipment',
    x: 50,
    y: 50,
    isCenter: true,
    props: [
      { key: '实体 ID', value: 'EQ-2024-0312' },
      { key: '设备类型', value: '锅炉受热面' },
      { key: '材质', value: 'T91 / TP347H' },
      { key: '设计壁温', value: '595℃' },
      { key: '报警阈值', value: '605℃', danger: true },
      { key: '关联文档', value: '28 篇' },
    ],
    relations: [
      { name: '管壁温度', type: 'parameter', relation: '包含' },
      { name: '超温蠕变', type: 'fault', relation: '引发' },
      { name: '投减温水', type: 'measure', relation: '处理' },
      { name: '降负荷运行', type: 'measure', relation: '处理' },
      { name: '减温水调节阀', type: 'equipment', relation: '关联' },
      { name: 'DL/T 438', type: 'standard', relation: '引用' },
    ],
    chunks: [
      {
        content: '当过热器管壁温度超限达到报警值时，值班员应立即检查给煤量与风煤比，必要时投入一级减温水...',
        source: '600MW 机组运行规程.pdf · 第 142 页',
      },
      {
        content: 'T91 钢在过热器管壁温度超限工况下，Larson-Miller 参数预测蠕变寿命...',
        source: 'DL/T 1317-2024 · 第 8 页',
      },
    ],
  },
  {
    id: 'n2',
    name: '管壁温度',
    shortName: '参数',
    type: 'parameter',
    x: 22,
    y: 22,
    props: [
      { key: '实体 ID', value: 'PR-2024-0821' },
      { key: '参数类型', value: '温度测点' },
      { key: '测点位置', value: '过热器出口' },
      { key: '正常范围', value: '560 ~ 595℃' },
      { key: '报警值', value: '605℃', danger: true },
    ],
    relations: [
      { name: '过热器', type: 'equipment', relation: '属于' },
    ],
    chunks: [
      { content: '过热器管壁温度测点布设在管屏出口段，每屏布置 4 个热电偶...', source: '热工仪表手册.pdf · 第 56 页' },
    ],
  },
  {
    id: 'n3',
    name: '减温水流量',
    shortName: '参数',
    type: 'parameter',
    x: 78,
    y: 22,
    props: [
      { key: '实体 ID', value: 'PR-2024-0825' },
      { key: '参数类型', value: '流量测点' },
      { key: '设计流量', value: '120 t/h' },
    ],
    relations: [
      { name: '过热器', type: 'equipment', relation: '冷却' },
      { name: '减温水调节阀', type: 'equipment', relation: '关联' },
    ],
    chunks: [
      { content: '一级减温水流量设计值 120 t/h，机组满负荷工况下应保持在 80 ~ 110 t/h...', source: '运行规程.pdf · 第 95 页' },
    ],
  },
  {
    id: 'n4',
    name: '超温蠕变',
    shortName: '故障',
    type: 'fault',
    x: 85,
    y: 50,
    props: [
      { key: '实体 ID', value: 'FT-2024-0102' },
      { key: '故障类型', value: '材料老化' },
      { key: '严重等级', value: '高', danger: true },
      { key: '处置时限', value: '立即处置' },
    ],
    relations: [
      { name: '过热器', type: 'equipment', relation: '发生于' },
      { name: '投减温水', type: 'measure', relation: '处理' },
      { name: '降负荷运行', type: 'measure', relation: '处理' },
      { name: 'DL/T 438', type: 'standard', relation: '依据' },
    ],
    chunks: [
      { content: 'T91 钢长期超温运行将加速蠕变空洞形核与聚集，最终导致管材失效...', source: '金属监督规程.pdf · 第 23 页' },
    ],
  },
  {
    id: 'n5',
    name: '投减温水',
    shortName: '措施',
    type: 'measure',
    x: 78,
    y: 78,
    props: [
      { key: '实体 ID', value: 'MS-2024-0201' },
      { key: '措施类别', value: '运行调整' },
      { key: '响应时间', value: '< 30 秒' },
    ],
    relations: [
      { name: '超温蠕变', type: 'fault', relation: '处置' },
      { name: '减温水调节阀', type: 'equipment', relation: '执行' },
    ],
    chunks: [
      { content: '发现过热器壁温升高时，应先投入一级减温水，无效后再投入二级减温水...', source: '运行规程.pdf · 第 143 页' },
    ],
  },
  {
    id: 'n6',
    name: '降负荷运行',
    shortName: '措施',
    type: 'measure',
    x: 50,
    y: 88,
    props: [
      { key: '实体 ID', value: 'MS-2024-0202' },
      { key: '措施类别', value: '运行调整' },
      { key: '负荷区间', value: '降至 80% 以下' },
    ],
    relations: [
      { name: '超温蠕变', type: 'fault', relation: '处置' },
    ],
    chunks: [
      { content: '若减温水投运后壁温仍超限，应立即降负荷至 80% 以下...', source: '应急预案.pdf · 第 12 页' },
    ],
  },
  {
    id: 'n7',
    name: 'DL/T 438',
    shortName: '标准',
    type: 'standard',
    x: 22,
    y: 78,
    props: [
      { key: '编号', value: 'DL/T 438-2022' },
      { key: '名称', value: '火力发电厂金属技术监督规程' },
      { key: '发布单位', value: '国家能源局' },
    ],
    relations: [
      { name: '过热器', type: 'equipment', relation: '适用于' },
    ],
    chunks: [
      { content: 'DL/T 438-2022 规定了受压部件金属监督的检测周期与方法...', source: 'DL/T 438-2022 · 第 5 页' },
    ],
  },
  {
    id: 'n8',
    name: '减温水调节阀',
    shortName: '设备',
    type: 'equipment',
    x: 12,
    y: 50,
    props: [
      { key: '实体 ID', value: 'EQ-2024-0401' },
      { key: '设备类型', value: '调节阀门' },
      { key: '口径', value: 'DN200' },
    ],
    relations: [
      { name: '过热器', type: 'equipment', relation: '配套' },
      { name: '投减温水', type: 'measure', relation: '执行于' },
    ],
    chunks: [
      { content: '一级减温水调节阀布置在省煤器出口至屏式过热器入口的连接管路上...', source: '设备清册.xlsx · 阀门类' },
    ],
  },
]

// ============ 计算属性 ============
const selectedEntity = ref<GraphNode | null>(allNodes[0])

const visibleNodes = computed(() =>
  allNodes.filter(n => entityTypesChecked.value.includes(n.type))
)

const visibleEdges = computed<GraphEdge[]>(() => {
  if (!selectedEntity.value) return []
  const center = selectedEntity.value
  const visibleIds = new Set(visibleNodes.value.map(n => n.id))
  const edges: GraphEdge[] = []

  // 中心节点辐射到所有可见节点
  for (const node of visibleNodes.value) {
    if (node.id === center.id) continue
    // 仅对中心节点直接关联的实体绘制 highlight 边
    const isRelated = center.relations.some(r => r.name === node.name)
    edges.push({
      fromId: center.id,
      toId: node.id,
      fromX: center.x,
      fromY: center.y,
      toX: node.x,
      toY: node.y,
      highlight: isRelated,
      label: isRelated ? center.relations.find(r => r.name === node.name)?.relation : undefined,
    })
  }

  // 周围节点的间接关联（虚线）
  const indirectPairs: [string, string][] = [
    ['n2', 'n3'], // 管壁温度 ↔ 减温水流量
    ['n5', 'n6'], // 投减温水 ↔ 降负荷运行
    ['n7', 'n8'], // 标准 ↔ 调节阀
  ]
  for (const [a, b] of indirectPairs) {
    const na = allNodes.find(n => n.id === a)
    const nb = allNodes.find(n => n.id === b)
    if (!na || !nb) continue
    if (!visibleIds.has(a) || !visibleIds.has(b)) continue
    edges.push({
      fromId: a,
      toId: b,
      fromX: na.x,
      fromY: na.y,
      toX: nb.x,
      toY: nb.y,
    })
  }

  return edges
})

const filteredEntities = computed(() => {
  const kw = searchKeyword.value.trim()
  if (!kw) return allNodes
  return allNodes.filter(n => n.name.includes(kw))
})

// ============ 方法 ============
function getTypeColor(type: EntityType): string {
  return entityTypes.find(t => t.value === type)?.color || '#C9CDD4'
}

function getTypeLabel(type: EntityType): string {
  return entityTypes.find(t => t.value === type)?.label || type
}

function isRelatedToSelected(nodeId: string): boolean {
  if (!selectedEntity.value) return true
  const node = allNodes.find(n => n.id === nodeId)
  if (!node) return false
  if (node.id === selectedEntity.value.id) return true
  return selectedEntity.value.relations.some(r => r.name === node.name)
}

function onSelectNode(node: GraphNode) {
  selectedEntity.value = node
}

function onSelectEntity(entity: GraphNode) {
  selectedEntity.value = entity
  searchKeyword.value = ''
}

function jumpToEntity(name: string) {
  const target = allNodes.find(n => n.name === name)
  if (target) selectedEntity.value = target
}

function resetSelection() {
  selectedEntity.value = allNodes[0]
  entityTypesChecked.value = ['equipment', 'parameter', 'fault', 'measure', 'standard']
  relationTypesChecked.value = ['contain', 'cause', 'handle']
  searchKeyword.value = ''
}
</script>

<style scoped>
.graph-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* ============ 左侧筛选栏 ============ */
.graph-left {
  width: 260px;
  flex-shrink: 0;
  position: sticky;
  top: 16px;
}
.filter-group {
  margin-bottom: 20px;
}
.filter-group:last-child {
  margin-bottom: 0;
}
.filter-group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12px;
}
.check-row {
  padding: 2px 0;
  font-size: 13px;
}
.check-row :deep(.el-checkbox) {
  width: 100%;
  height: auto;
}
.check-row :deep(.el-checkbox__label) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  color: var(--text-2);
  font-size: 13px;
}
.check-label {
  flex: 1;
}
.check-count {
  margin-left: auto;
  color: var(--text-3);
  font-size: 12px;
}
.entity-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.entity-list {
  max-height: 280px;
  overflow-y: auto;
  border-top: 1px solid var(--border-2);
  padding-top: 8px;
}
.entity-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-2);
}
.entity-row:hover {
  background: var(--fill-1);
  color: var(--primary);
}
.entity-row.active {
  background: var(--primary-bg);
  color: var(--primary-active);
  font-weight: 500;
}
.entity-name {
  flex: 1;
}
.entity-type-label {
  font-size: 12px;
  color: var(--text-3);
}

/* ============ 中央画布 ============ */
.graph-center {
  flex: 1;
  min-width: 0;
}
.graph-canvas {
  position: relative;
  width: 100%;
  height: 560px;
  background: var(--white);
  border-radius: 8px;
  box-shadow: var(--shadow-1);
  overflow: hidden;
  background-image: radial-gradient(circle, var(--border-2) 1px, transparent 1px);
  background-size: 24px 24px;
}
.edge-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
.edge-label {
  font-size: 11px;
  text-anchor: middle;
  dominant-baseline: middle;
}

/* 节点 */
.node {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s;
  z-index: 2;
}
.node-circle {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  border: 2px solid var(--white);
  transition: all 0.2s;
}
.node-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-1);
  background: var(--white);
  padding: 1px 6px;
  border-radius: 4px;
  box-shadow: var(--shadow-1);
  white-space: nowrap;
}
.node:hover .node-circle {
  transform: scale(1.15);
}

/* 节点配色 */
.node-equipment .node-circle {
  background: #A6000A;
}
.node-parameter .node-circle {
  background: #D9001B;
}
.node-fault .node-circle {
  background: #F53F3F;
}
.node-measure .node-circle {
  background: #00B42A;
}
.node-standard .node-circle {
  background: #4E5969;
}
.node-person .node-circle {
  background: #86909C;
}
.node-department .node-circle {
  background: #C9CDD4;
  color: var(--text-1);
}

/* 选中 / 弱化 */
.node.dimmed {
  opacity: 0.25;
}
.node.selected .node-circle {
  box-shadow: 0 0 0 4px var(--primary-bg), 0 2px 12px rgba(199, 0, 11, 0.4);
  transform: scale(1.2);
}
.node.selected .node-label {
  color: var(--primary-active);
  font-weight: 600;
}

/* 画布信息条 */
.canvas-info {
  position: absolute;
  top: 16px;
  left: 16px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 6px;
  padding: 8px 16px;
  box-shadow: var(--shadow-1);
  font-size: 13px;
  color: var(--text-2);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 12px;
}
.canvas-info strong {
  color: var(--primary-active);
}

/* 工具栏 */
.canvas-toolbar {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  z-index: 3;
}
.toolbar-btn {
  width: 32px;
  height: 32px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid var(--border);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-2);
  box-shadow: var(--shadow-1);
}
.toolbar-btn:hover {
  color: var(--primary);
  border-color: var(--primary);
}

/* ============ 右侧详情 ============ */
.graph-right {
  width: 300px;
  flex-shrink: 0;
  position: sticky;
  top: 16px;
}
.detail-head {
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border-2);
}
.detail-type {
  font-size: 12px;
  color: var(--primary-active);
  margin-bottom: 4px;
}
.detail-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}
.detail-section {
  margin-bottom: 20px;
}
.detail-section:last-child {
  margin-bottom: 0;
}
.detail-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12px;
}
.detail-prop {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--border-2);
}
.detail-prop:last-child {
  border-bottom: none;
}
.detail-prop .key {
  color: var(--text-3);
}
.detail-prop .val {
  color: var(--text-1);
  font-weight: 500;
}
.relation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.relation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: var(--fill-1);
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}
.relation-item:hover {
  background: var(--primary-bg);
}
.rel-name {
  color: var(--text-1);
}
.relation-item:hover .rel-name {
  color: var(--primary);
}
.rel-type {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}
.chunk-box {
  background: var(--fill-1);
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
  margin-bottom: 8px;
}
.chunk-source {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 4px;
}

/* ============ 图例 ============ */
.legend-bar {
  margin-top: 16px;
  background: var(--white);
  border-radius: 8px;
  box-shadow: var(--shadow-1);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}
.legend-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2);
}
.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--white);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
</style>
