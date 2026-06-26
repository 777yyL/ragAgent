<template>
  <div class="page-container">
    <div class="page-title">{{ title }}</div>
    <div class="page-sub">该页面正在开发中，敬请期待</div>
    <el-empty description="页面建设中">
      <template #image>
        <el-icon size="64" color="#C9CDD4"><Document /></el-icon>
      </template>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title || '页面')
</script>
