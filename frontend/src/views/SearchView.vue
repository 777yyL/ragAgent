<template>
  <div class="page-container">
    <div class="page-title">知识检索</div>
    <div class="page-sub">语义 + 关键词 + 图谱混合检索，自动按您的部门/密级权限预过滤</div>

    <!-- 检索栏 -->
    <div class="search-bar">
      <el-input v-model="query" placeholder="输入关键词或问题" style="flex:1" size="large" @keyup.enter="doSearch" clearable />
      <el-switch v-model="useKg" active-text="图谱" inactive-text="" style="margin:0 12px" />
      <el-button type="primary" size="large" @click="doSearch" :loading="loading">检 索</el-button>
    </div>

    <!-- 结果统计 -->
    <div v-if="searched" style="margin:16px 0 8px;font-size:13px;color:var(--text-3)">
      找到 <strong>{{ result?.total || 0 }}</strong> 条结果，耗时 {{ result?.responseMs || 0 }}ms
    </div>

    <!-- 结果列表 -->
    <div v-loading="loading">
      <el-card v-for="chunk in result?.chunks || []" :key="chunk.chunkId" shadow="never" style="margin-bottom:12px" body-style="padding:16px 20px">
        <div style="display:flex;justify-content:space-between;align-items:start">
          <div style="flex:1">
            <div style="font-size:14px;font-weight:500;margin-bottom:6px">{{ chunk.documentName }}</div>
            <div style="font-size:13px;color:var(--text-2);line-height:1.7" v-html="highlightContent(chunk.content)"></div>
          </div>
          <div style="text-align:right;margin-left:16px">
            <el-tag size="small" :type="similarityType(chunk.similarity)">{{ (chunk.similarity * 100).toFixed(1) }}%</el-tag>
          </div>
        </div>
      </el-card>
      <el-empty v-if="searched && !result?.chunks?.length" description="无匹配结果" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { searchApi, type SearchResult } from '@/api'

const route = useRoute()
const query = ref((route.query.q as string) || '')
const useKg = ref(false)
const loading = ref(false)
const searched = ref(false)
const result = ref<SearchResult | null>(null)

async function doSearch() {
  if (!query.value.trim()) return
  loading.value = true
  searched.value = true
  try {
    result.value = await searchApi.search({ question: query.value, topK: 10, useKg: useKg.value })
  } catch {
    result.value = { chunks: [], total: 0, responseMs: 0 }
  } finally { loading.value = false }
}

function highlightContent(content: string): string {
  if (!content) return ''
  const q = query.value.trim()
  if (!q) return content.substring(0, 300) + (content.length > 300 ? '...' : '')
  const reg = new RegExp(`(${q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return content.substring(0, 300).replace(reg, '<mark style="background:#FFF1F0;color:#C7000B;padding:0 2px;">$1</mark>') + (content.length > 300 ? '...' : '')
}

function similarityType(sim: number): 'success' | 'primary' | 'info' {
  if (sim >= 0.8) return 'success'
  if (sim >= 0.6) return 'primary'
  return 'info'
}

if (query.value) doSearch()
</script>

<style scoped>
.search-bar { display: flex; align-items: center; margin-bottom: 16px; }
</style>
