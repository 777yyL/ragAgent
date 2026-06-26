<template>
  <div class="page-container">
    <div class="page-title">统计仪表盘</div>
    <div class="page-sub">知识量、检索趋势、热门词、高频 chunk</div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom:20px">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" body-style="padding:20px">
          <div style="font-size:13px;color:var(--text-3);margin-bottom:8px">{{ card.label }}</div>
          <div style="font-size:30px;font-weight:600">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 热门检索词 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span style="font-weight:600">热门检索词</span></template>
          <div style="display:flex;flex-wrap:wrap;gap:8px">
            <el-tag v-for="(kw, i) in hotKeywords" :key="kw.keyword" :style="{ fontSize: (14 + (kw.count / maxCount) * 16) + 'px' }" size="large">
              {{ kw.keyword }} ({{ kw.count }})
            </el-tag>
          </div>
          <el-empty v-if="!hotKeywords.length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 高频 chunk -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span style="font-weight:600">高频命中切片 Top 10</span></template>
          <el-table :data="topChunks" size="small" stripe>
            <el-table-column type="index" width="50" label="#" />
            <el-table-column prop="documentName" label="文档" show-overflow-tooltip />
            <el-table-column prop="hitCount" label="命中次数" width="120">
              <template #default="{ row }">
                <el-progress :percentage="Math.min(100, (row.hitCount / maxHit) * 100)" :text-inside="true" :stroke-width="16" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!topChunks.length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { statsApi } from '@/api'

const cards = ref([
  { label: '知识总量', value: '12,486' },
  { label: '文档总数', value: '3,254' },
  { label: '切片总数', value: '86,542' },
  { label: '本周检索', value: '5,873' },
])

const hotKeywords = ref<{ keyword: string; count: number }[]>([])
const topChunks = ref<{ documentName: string; hitCount: number }[]>([])

const maxCount = computed(() => Math.max(1, ...hotKeywords.value.map(k => k.count)))
const maxHit = computed(() => Math.max(1, ...topChunks.value.map(c => c.hitCount)))

onMounted(async () => {
  try {
    const [kw, chunks] = await Promise.all([
      statsApi.hotKeywords(7, 20),
      statsApi.topChunks(undefined, 10),
    ])
    hotKeywords.value = kw || []
    topChunks.value = chunks || []
  } catch { /* 静默，后端未启动时用空数据 */ }
})
</script>
