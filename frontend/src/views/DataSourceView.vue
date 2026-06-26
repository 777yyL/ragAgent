<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-title">数据源管理</div>
    <div class="page-sub">
      配置多种异构数据源，实现 DCS 系统、两票系统、设备台账、规程文档、行业标准等异构数据源的统一接入与传输
    </div>

    <!-- 顶部说明条 -->
    <el-alert
      type="warning"
      :closable="false"
      style="margin-bottom: 16px"
      title="本页面展示「知识采集与抽取」模块的完整能力规划"
      description="手动上传已在本期实现；数据库 / API / 消息队列 / 爬虫 / 日志采集为二期规划（A 层增强），本期原型仅展示配置界面与采集流程。"
      show-icon
    />

    <!-- 顶部统计 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini">
          <div class="stat-mini-value">6</div>
          <div class="stat-mini-label">规划数据源类型</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini">
          <div class="stat-mini-value" style="color: var(--success)">1</div>
          <div class="stat-mini-label">本期已上线</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini">
          <div class="stat-mini-value">3,254</div>
          <div class="stat-mini-label">已入库文档</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini">
          <div class="stat-mini-value">5</div>
          <div class="stat-mini-label">二期规划</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据源卡片网格 2x3 -->
    <el-row :gutter="16" style="margin-bottom: 24px">
      <el-col
        v-for="item in dataSources"
        :key="item.key"
        :span="8"
        style="margin-bottom: 16px"
      >
        <el-card shadow="hover" class="ds-card" :body-style="{ padding: '20px', display: 'flex', flexDirection: 'column', height: '100%' }">
          <!-- 卡片头部 -->
          <div class="ds-card-head">
            <div class="ds-icon">
              <el-icon :size="20"><component :is="item.icon" /></el-icon>
            </div>
            <div class="ds-title">{{ item.name }}</div>
            <el-tag :type="item.tagType" size="small" effect="light">{{ item.tagText }}</el-tag>
          </div>

          <!-- 卡片描述 -->
          <div class="ds-body">
            <div v-for="row in item.rows" :key="row.label" class="ds-row">
              <span class="ds-row-label">{{ row.label }}</span>
              <span class="ds-row-value">{{ row.value }}</span>
            </div>

            <!-- 配置预览 -->
            <div v-if="item.preview" class="ds-preview-box">
              <div v-for="kv in item.preview" :key="kv.k" class="kv">
                <span class="k">{{ kv.k }}</span>
                <span class="v">{{ kv.v }}</span>
              </div>
            </div>

            <!-- 数据源实例列表 -->
            <div v-if="item.sources && item.sources.length" class="ds-source-list">
              <div v-for="src in item.sources" :key="src" class="ds-source-item">
                <span class="ds-source-dot" :class="item.online ? 'green' : 'gray'"></span>
                <span>{{ src }}</span>
              </div>
            </div>

            <!-- 已入库文档数 -->
            <div v-if="item.docCount !== undefined" class="ds-doc-count">
              已入库文档：<strong>{{ item.docCount.toLocaleString() }}</strong> 篇
            </div>
          </div>

          <!-- 卡片底部操作 -->
          <div class="ds-footer">
            <el-button
              v-if="item.key === 'file-upload'"
              type="primary"
              style="width: 100%"
              @click="$router.push('/upload-wizard')"
            >
              前往上传
            </el-button>
            <el-button v-else disabled style="width: 100%">配置{{ item.actionLabel }}（二期开放）</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部接入流程图 -->
    <el-card shadow="never" class="flow-section">
      <div class="flow-section-title">数据源接入流程</div>
      <div class="flow-section-desc">
        所有异构数据源经过统一 Connector 接入，格式转换后进入清洗管道，最终收敛写入 RAGFlow 知识库
      </div>

      <div class="flow-chart">
        <div v-for="(step, idx) in flowSteps" :key="step.title" style="display: flex; align-items: stretch; flex: 1; min-width: 0">
          <div class="flow-node-box" :class="{ highlight: step.highlight }">
            <div class="flow-node-icon">
              <el-icon :size="24"><component :is="step.icon" /></el-icon>
            </div>
            <div class="flow-node-title">{{ step.title }}</div>
            <div class="flow-node-sub" v-html="step.sub"></div>
          </div>
          <div v-if="idx < flowSteps.length - 1" class="flow-arrow">
            <el-icon :size="20"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="flow-note">
        <el-icon><InfoFilled /></el-icon>
        <span>
          所有数据源最终收敛到 <strong>RAGFlow Dataset Upload API</strong>，统一存储与索引。无论数据来自文件、数据库、消息队列还是爬虫，均通过同一写入入口完成知识入库。
        </span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import {
  Upload,
  Coin,
  Connection,
  Grid,
  Aim,
  Document,
  ArrowRight,
  InfoFilled,
  Files,
  DataLine,
  Brush,
  CopyDocument,
  DataAnalysis,
} from '@element-plus/icons-vue'

/** 数据源配置项 key */
type SourceKey =
  | 'file-upload'
  | 'database'
  | 'api'
  | 'mq'
  | 'crawler'
  | 'log'

/** 单条键值对 */
interface Kv {
  k: string
  v: string
}

/** 数据源卡片配置 */
interface DataSourceItem {
  key: SourceKey
  name: string
  icon: Component
  tagType: 'success' | 'info'
  tagText: string
  online: boolean
  actionLabel: string
  rows: { label: string; value: string }[]
  preview?: Kv[]
  sources?: string[]
  docCount?: number
}

/** 流程图节点 */
interface FlowStep {
  title: string
  icon: Component
  sub: string
  highlight?: boolean
}

const dataSources: DataSourceItem[] = [
  {
    key: 'file-upload',
    name: '文件上传',
    icon: Upload,
    tagType: 'success',
    tagText: '已上线',
    online: true,
    actionLabel: '上传',
    rows: [
      { label: '本期接入', value: 'PDF / Word / Excel / PPT / 图片 / CSV / HTML' },
      { label: '上传方式', value: '单文件 / 批量拖拽 / 文件夹整传' },
      { label: '单文件上限', value: '200 MB（PDF 可分段上传）' },
    ],
    docCount: 3254,
  },
  {
    key: 'database',
    name: '数据库采集',
    icon: Coin,
    tagType: 'info',
    tagText: '二期',
    online: false,
    actionLabel: '数据源',
    rows: [
      { label: '支持协议', value: 'JDBC / ODBC' },
      { label: '同步模式', value: '全量 / 增量(时间戳) / 实时(CDC)' },
    ],
    preview: [
      { k: '数据源名称', v: 'DCS历史库' },
      { k: '连接串', v: 'jdbc:sqlserver://192.168.1.10' },
      { k: 'Schema', v: 'dbo - 38 张表' },
      { k: '字段映射', v: 'tag_id - 测点编号' },
    ],
    sources: ['DCS历史库 - SQLServer', '设备台账库 - Oracle 19c', '两票系统库 - MySQL 8.0'],
  },
  {
    key: 'api',
    name: 'API 接入',
    icon: Connection,
    tagType: 'info',
    tagText: '二期',
    online: false,
    actionLabel: '接口',
    rows: [
      { label: 'Push 模式', value: '被动接收外部推送' },
      { label: 'Pull 模式', value: '主动拉取（Cron 调度）' },
      { label: '鉴权方式', value: 'Bearer Token / API Key / OAuth2' },
    ],
    preview: [
      { k: 'endpoint', v: 'http://api/two-ticket/list' },
      { k: 'method', v: 'GET' },
      { k: 'headers', v: 'Authorization: Bearer ***' },
      { k: '分页', v: '$.data.list[*] (JSONPath)' },
      { k: '调度', v: '0 */2 * * * (每 2 小时)' },
    ],
    sources: ['两票系统API', '设备健康API'],
  },
  {
    key: 'mq',
    name: '消息队列',
    icon: Grid,
    tagType: 'info',
    tagText: '二期',
    online: false,
    actionLabel: '订阅',
    rows: [
      { label: '中间件', value: 'Kafka / RabbitMQ / RocketMQ' },
      { label: '消费模式', value: '集群消费 / 广播消费' },
    ],
    preview: [
      { k: 'Topic', v: 'dcs-alarm-topic' },
      { k: '消费组', v: 'rag-knowledge-consumer' },
      { k: '过滤规则', v: 'level >= WARN' },
      { k: '死信队列', v: 'dlq-dcs-alarm（3 次重试）' },
    ],
    sources: ['DCS告警Topic - Kafka', '设备状态Queue - RabbitMQ'],
  },
  {
    key: 'crawler',
    name: '网络爬虫',
    icon: Aim,
    tagType: 'info',
    tagText: '二期',
    online: false,
    actionLabel: '爬虫',
    rows: [
      { label: '能力', value: '定向爬取 / URL 规则 / 抓取频率 / 内容清洗' },
      { label: '渲染引擎', value: '静态抓取 / Headless Chrome（JS 渲染）' },
    ],
    preview: [
      { k: '种子 URL', v: 'https://regulatory-docs.com/std' },
      { k: 'URL 规则', v: '/std/*/dl-power-*.html' },
      { k: '抓取频率', v: '每日 02:00 / 增量更新' },
      { k: '内容清洗', v: '去除导航/广告/页脚' },
    ],
    sources: ['行业标准网爬取', '能源局政策法规'],
  },
  {
    key: 'log',
    name: '日志采集',
    icon: Document,
    tagType: 'info',
    tagText: '二期',
    online: false,
    actionLabel: '采集',
    rows: [
      { label: '采集工具', value: 'Filebeat / Fluentd' },
      { label: '日志格式', value: '文本日志 / JSON 结构化 / Syslog' },
    ],
    preview: [
      { k: '日志路径', v: '/var/log/dcs/operation.log' },
      { k: '多行合并', v: '^\\d{4}-\\d{2}-\\d{2}' },
      { k: '过滤规则', v: 'level: ERROR|WARN' },
      { k: '输出', v: '→ RAGFlow 清洗管道' },
    ],
    sources: ['DCS操作日志', 'SOE事件日志'],
  },
]

const flowSteps: FlowStep[] = [
  { title: '异构数据源', icon: Files, sub: 'DCS / 台账<br>两票 / 标准' },
  { title: '采集 Connector', icon: DataLine, sub: 'DB / API / MQ<br>爬虫 / 日志' },
  { title: '格式转换', icon: Brush, sub: '统一 JSON<br>schema 对齐' },
  { title: '清洗管道', icon: CopyDocument, sub: '责任链模式<br>去噪 / 补全' },
  { title: 'RAGFlow Upload API', icon: Upload, sub: '写入收敛点<br>统一入口', highlight: true },
  { title: '知识库', icon: DataAnalysis, sub: '向量索引<br>+ 知识图谱' },
]
</script>

<style scoped>
.stat-mini {
  text-align: left;
}
.stat-mini-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.2;
}
.stat-mini-label {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 4px;
}

.ds-card {
  height: 100%;
}
.ds-card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.ds-icon {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  background: var(--fill-1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  flex-shrink: 0;
}
.ds-card:hover .ds-icon {
  background: var(--primary-bg);
  color: var(--primary);
}
.ds-title {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
  color: var(--text-1);
}
.ds-body {
  flex: 1;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.8;
}
.ds-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: flex-start;
}
.ds-row-label {
  color: var(--text-3);
  min-width: 72px;
  flex-shrink: 0;
  font-size: 12px;
}
.ds-row-value {
  color: var(--text-1);
  font-size: 12px;
  flex: 1;
}
.ds-preview-box {
  background: var(--fill-1);
  border: 1px solid var(--border-2);
  border-radius: 6px;
  padding: 8px 12px;
  margin-top: 8px;
  font-size: 12px;
  font-family: Consolas, 'Courier New', monospace;
  color: var(--text-2);
  line-height: 1.6;
  max-height: 120px;
  overflow-y: auto;
}
.ds-preview-box .kv {
  display: flex;
  gap: 4px;
}
.ds-preview-box .k {
  color: var(--primary-active);
  min-width: 80px;
}
.ds-preview-box .v {
  color: var(--text-1);
}
.ds-source-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ds-source-item {
  background: var(--fill-1);
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
}
.ds-source-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.ds-source-dot.green {
  background: var(--success);
}
.ds-source-dot.gray {
  background: var(--text-4);
}
.ds-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-2);
}
.ds-doc-count {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 4px;
}
.ds-doc-count strong {
  color: var(--success);
  font-size: 16px;
}

.flow-section {
  padding: 20px;
}
.flow-section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}
.flow-section-desc {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 16px;
}
.flow-chart {
  display: flex;
  align-items: stretch;
  gap: 0;
  overflow-x: auto;
  padding: 12px 0;
}
.flow-node-box {
  flex: 1;
  min-width: 140px;
  background: var(--white);
  border: 1px solid var(--border-2);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  box-shadow: var(--shadow-1);
  transition: all 0.15s;
}
.flow-node-icon {
  margin-bottom: 8px;
  color: var(--text-2);
  display: flex;
  justify-content: center;
}
.flow-node-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--text-1);
}
.flow-node-sub {
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
}
.flow-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  color: var(--primary);
  flex-shrink: 0;
}
.flow-node-box.highlight {
  border-color: var(--primary);
  background: var(--primary-bg);
}
.flow-node-box.highlight .flow-node-title {
  color: var(--primary-active);
}
.flow-note {
  margin-top: 16px;
  padding: 12px 16px;
  background: var(--primary-bg);
  border: 1px dashed var(--primary-border);
  border-radius: 6px;
  font-size: 12px;
  color: var(--primary-active);
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.6;
}
</style>
