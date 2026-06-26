<template>
  <div class="page-container">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:20px">
      <div>
        <div class="page-title">我的工作台</div>
        <div class="page-sub">管理您知识的完整生命周期：草稿 → 解析 → 审核 → 发布</div>
      </div>
      <el-button type="primary" @click="$router.push('/upload-wizard')">+ 上传新知识</el-button>
    </div>

    <!-- 生命周期流程图 -->
    <el-row :gutter="12" style="margin-bottom:20px">
      <el-col :span="5" v-for="(stage, i) in lifecycleStages" :key="stage.key">
        <el-card shadow="hover" :class="{ active: activeStage === stage.key }" body-style="padding:20px;text-align:center;cursor:pointer" @click="activeStage = stage.key">
          <div style="font-size:30px;font-weight:600" :style="{ color: stage.color }">{{ stage.count }}</div>
          <div style="font-size:13px;color:var(--text-2);margin-top:4px">{{ stage.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 待审列表 -->
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;gap:16px">
          <span v-for="tab in tabs" :key="tab.key" style="cursor:pointer;padding-bottom:4px;border-bottom:2px solid" :style="{ borderColor: activeTab === tab.key ? 'var(--primary)' : 'transparent', color: activeTab === tab.key ? 'var(--primary)' : 'var(--text-2)', fontWeight: activeTab === tab.key ? 600 : 400 }" @click="activeTab = tab.key">
            {{ tab.label }} <el-badge :value="tab.count" :max="99" style="margin-left:4px" />
          </span>
        </div>
      </template>
      <div v-for="doc in docs" :key="doc.id" class="doc-row" @click="onDocClick(doc)">
        <div style="font-size:14px;font-weight:500;margin-bottom:6px">{{ doc.title }}</div>
        <div style="display:flex;gap:12px;font-size:12px;color:var(--text-3);align-items:center">
          <el-tag size="small" type="primary">{{ doc.category }}</el-tag>
          <el-tag size="small" :type="doc.stage === '集团终审' ? 'danger' : 'warning'">{{ doc.stage }}</el-tag>
          <span>{{ doc.submitter }}</span>
          <span>{{ doc.time }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const activeStage = ref('pending')
const activeTab = ref('pending')

const lifecycleStages = ref([
  { key: 'draft', label: '草稿', count: 5, color: 'var(--text-1)' },
  { key: 'parsing', label: '解析中', count: 2, color: 'var(--warning)' },
  { key: 'pending', label: '待审核', count: 12, color: 'var(--danger)' },
  { key: 'rejected', label: '被退回', count: 3, color: 'var(--warning)' },
  { key: 'published', label: '已发布', count: 86, color: 'var(--success)' },
])

const tabs = [
  { key: 'submitted', label: '我提交的', count: 34 },
  { key: 'pending', label: '待我审核', count: 12 },
  { key: 'draft', label: '我的草稿', count: 5 },
  { key: 'rejected', label: '被退回', count: 3 },
  { key: 'published', label: '我发布的', count: 86 },
]

const docs = ref([
  { id: 1, title: '磨煤机防爆门动作应急操作规程（修订）', category: '运行规程', stage: '企业初审', submitter: '李工', time: '2小时前' },
  { id: 2, title: '#3 机组汽轮机轴向位移异常故障分析报告', category: '故障案例', stage: '区域复审', submitter: '王工', time: '4小时前' },
  { id: 3, title: '脱硝喷氨优化调整试验方法（新版）', category: '技术标准', stage: '集团终审', submitter: '赵工', time: '今日 09:20' },
  { id: 4, title: '环保岛超低排放改造运行维护手册', category: '运行规程', stage: '企业初审', submitter: '孙工', time: '今日 08:45' },
])

function onDocClick(doc: any) {
  router.push('/audit-workbench')
}
</script>

<style scoped>
.doc-row { padding: 12px 16px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; transition: all 0.15s; margin-bottom: 8px; }
.doc-row:hover { background: var(--fill-1); border-color: var(--border-2); }
</style>
