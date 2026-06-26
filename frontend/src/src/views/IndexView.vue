<template>
  <div class="page-container">
    <div class="page-title">知识门户</div>
    <div class="page-sub">发电运行知识统一检索与智能应用入口</div>

    <!-- 待办提示 -->
    <el-alert v-if="pendingCount > 0" type="warning" :closable="false" style="margin-bottom: 16px;">
      有 <strong>{{ pendingCount }}</strong> 篇知识待您审核，
      <el-link type="primary" @click="$router.push('/audit-workbench')">前往审核 →</el-link>
    </el-alert>

    <!-- 检索区 -->
    <div class="search-banner">
      <div class="search-banner-title">发电运行知识统一检索</div>
      <div class="search-banner-sub">覆盖规程、标准、台账、案例、告警、日志等全量知识资产</div>
      <div class="search-box">
        <el-select v-model="searchCategory" style="width:140px" placeholder="全部分类">
          <el-option label="全部分类" value="" />
          <el-option label="运行规程" value="REGULATION" />
          <el-option label="行业标准" value="STANDARD" />
          <el-option label="故障案例" value="CASE" />
        </el-select>
        <el-input v-model="searchQuery" placeholder="输入关键词，如：锅炉过热器管壁温度超限的处理方法"
          style="flex:1; border:none;" @keyup.enter="doSearch" />
        <el-button type="primary" @click="doSearch" :loading="searching">检 索</el-button>
      </div>
    </div>

    <!-- 统计概览 -->
    <el-row :gutter="16" style="margin-bottom:20px">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" body-style="padding:20px">
          <div style="font-size:13px;color:var(--text-3);margin-bottom:8px">{{ stat.label }}</div>
          <div style="font-size:30px;font-weight:600">{{ stat.value }}</div>
          <div v-if="stat.trend" style="font-size:12px;margin-top:4px;color:var(--success)">{{ stat.trend }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 分类导航 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <template #header><span style="font-weight:600">知识分类</span></template>
      <el-row :gutter="12">
        <el-col :span="4" v-for="cat in categories" :key="cat.name">
          <div class="cat-item" @click="$router.push({ path: '/search', query: { category: cat.name } })">
            <div style="font-size:14px;font-weight:500">{{ cat.name }}</div>
            <div style="font-size:12px;color:var(--text-3)">{{ cat.count }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { searchApi, statsApi } from '@/api'

const router = useRouter()
const searchQuery = ref('')
const searchCategory = ref('')
const searching = ref(false)
const pendingCount = ref(12)

const stats = ref([
  { label: '知识总量', value: '12,486', trend: '↑ 本周新增 142' },
  { label: '今日检索', value: '873', trend: '↑ 较昨日 +12%' },
  { label: '平均检索响应', value: '0.62s', trend: '↓ 较上周 -0.08s' },
  { label: '待审核', value: '12', trend: '含 3 篇紧急' },
])

const categories = ref([
  { name: '运行规程', count: '3,254 篇' },
  { name: '行业标准', count: '1,872 篇' },
  { name: '设备台账', count: '2,156 条' },
  { name: '故障案例', count: '945 篇' },
  { name: '告警事件', count: '3,201 条' },
  { name: '两票日志', count: '1,058 条' },
])

async function doSearch() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  try {
    await searchApi.search({ question: searchQuery.value, topK: 10 })
    router.push({ path: '/search', query: { q: searchQuery.value } })
  } catch (e: any) {
    // 401 已由拦截器处理；其他错误跳检索页展示
    router.push({ path: '/search', query: { q: searchQuery.value } })
  } finally {
    searching.value = false
  }
}

onMounted(async () => {
  try {
    const dash = await statsApi.dashboard()
    if (dash) {
      // 实际数据覆盖
    }
  } catch { /* 静默 */ }
})
</script>

<style scoped>
.search-banner {
  background: linear-gradient(135deg, #C7000B 0%, #7A0007 100%);
  border-radius: 8px; padding: 32px 24px; margin-bottom: 20px;
}
.search-banner-title { color: #fff; font-size: 20px; font-weight: 600; margin-bottom: 8px; }
.search-banner-sub { color: rgba(255,255,255,0.75); font-size: 13px; margin-bottom: 20px; }
.search-box {
  display: flex; background: #fff; border-radius: 4px; padding: 4px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
}
.cat-item {
  background: #fff; border: 1px solid var(--border-2); border-radius: 6px;
  padding: 16px; text-align: center; cursor: pointer; transition: all 0.15s;
}
.cat-item:hover { border-color: var(--primary); box-shadow: var(--shadow-2); }
</style>
