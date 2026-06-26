<template>
  <div class="page-container">
    <div class="page-title">系统设置</div>
    <div class="page-sub">知识库配置、模型供应商、解析组件与系统集成管理</div>

    <!-- 说明条 -->
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px; background: var(--primary-bg); border: 1px solid var(--primary-border); color: var(--primary-active);"
    >
      本页承载 RAGFlow 原生能力配置。标记为「RAGFlow 原生」的模块由 RAGFlow Vue 应用直接渲染嵌入，自建模块通过门户 SPA 统一调度。
    </el-alert>

    <!-- 二级菜单 + 内容区 -->
    <div class="settings-layout">
      <!-- 左侧二级菜单 -->
      <div class="sub-menu">
        <div
          v-for="m in menus"
          :key="m.key"
          class="sub-menu-item"
          :class="{ active: activeMenu === m.key }"
          @click="activeMenu = m.key"
        >
          {{ m.name }}
        </div>
      </div>

      <!-- 右侧内容 -->
      <div class="settings-content">
        <!-- 系统监控 -->
        <template v-if="activeMenu === 'monitor'">
          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <span class="card-title">系统健康检查</span>
                <el-button size="small" :loading="loading" @click="loadHealth">刷新</el-button>
              </div>
            </template>

            <el-row :gutter="12" style="margin-bottom: 16px;">
              <el-col :span="12">
                <div class="health-item" :class="{ ok: health.app === 'UP', fail: health.app && health.app !== 'UP' }">
                  <div class="health-icon">
                    <span v-if="health.app === 'UP'" style="color: var(--success);">●</span>
                    <span v-else-if="health.app" style="color: var(--danger);">●</span>
                    <span v-else style="color: var(--text-3);">○</span>
                  </div>
                  <div>
                    <div class="health-name">门户应用（app）</div>
                    <div class="health-status">{{ health.app || '检测中...' }}</div>
                  </div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="health-item" :class="{ ok: health.ragflow === 'UP', fail: health.ragflow && health.ragflow !== 'UP' }">
                  <div class="health-icon">
                    <span v-if="health.ragflow === 'UP'" style="color: var(--success);">●</span>
                    <span v-else-if="health.ragflow" style="color: var(--danger);">●</span>
                    <span v-else style="color: var(--text-3);">○</span>
                  </div>
                  <div>
                    <div class="health-name">RAGFlow 引擎（ragflow）</div>
                    <div class="health-status">{{ health.ragflow || '检测中...' }}</div>
                  </div>
                </div>
              </el-col>
            </el-row>

            <!-- RAGFlow 连接信息 -->
            <div class="section-title">RAGFlow 连接信息</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="服务地址">
                {{ ragflowInfo.address || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="版本">
                {{ ragflowInfo.version || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag v-if="ragflowInfo.status === '在线'" type="success" size="small">{{ ragflowInfo.status }}</el-tag>
                <el-tag v-else-if="ragflowInfo.status" type="danger" size="small">{{ ragflowInfo.status }}</el-tag>
                <span v-else style="color: var(--text-3);">-</span>
              </el-descriptions-item>
              <el-descriptions-item label="API Key">
                <code style="font-family: Consolas, monospace;">{{ ragflowInfo.apiKey || '-' }}</code>
              </el-descriptions-item>
            </el-descriptions>

            <!-- 模型配置概览 -->
            <div class="section-title">模型配置概览</div>
            <el-table :data="models" stripe size="small">
              <el-table-column prop="type" label="类型" width="120" />
              <el-table-column prop="name" label="模型名称" />
              <el-table-column prop="provider" label="供应商" width="160" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === '可用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>

        <!-- 其他菜单 -->
        <template v-else>
          <el-card shadow="never">
            <el-empty description="该模块为 RAGFlow 原生能力，后续集成">
              <template #image>
                <el-icon style="font-size: 64px; color: var(--text-4);">
                  <component :is="placeholderIcon" />
                </el-icon>
              </template>
              <div style="color: var(--text-3); font-size: 13px;">
                {{ activeMenuName }} 模块由 RAGFlow Vue 应用直接渲染嵌入，
                <br />
                将通过 iframe / micro-app 方式接入本门户。
              </div>
            </el-empty>
          </el-card>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Cpu } from '@element-plus/icons-vue'

interface HealthResponse {
  app?: string
  ragflow?: string
  [k: string]: unknown
}

interface Menu {
  key: string
  name: string
}

const menus = ref<Menu[]>([
  { key: 'kb', name: '知识库管理' },
  { key: 'model', name: '模型供应商' },
  { key: 'parser', name: '解析组件配置' },
  { key: 'agent', name: 'Agent编排' },
  { key: 'monitor', name: '系统监控' },
])
const activeMenu = ref('monitor')
const activeMenuName = computed(() => menus.value.find(m => m.key === activeMenu.value)?.name || '')

// 占位图标
const placeholderIcon = Cpu

// ===== 健康检查 =====
const health = ref<HealthResponse>({})
const loading = ref(false)

const ragflowInfo = ref({
  address: 'http://ragflow.internal:9380',
  version: 'v0.15.0',
  status: '在线',
  apiKey: 'ragflow-*******************',
})

const models = ref([
  { type: 'Embedding', name: 'bge-large-zh-v1.5', provider: 'BAAI', status: '可用' },
  { type: 'Embedding', name: 'bge-base-zh-v1.5', provider: 'BAAI', status: '可用' },
  { type: 'Rerank', name: 'bge-reranker-v2-m3', provider: 'BAAI', status: '可用' },
  { type: 'LLM', name: 'qwen2.5-32b-instruct', provider: '阿里云', status: '可用' },
  { type: 'LLM', name: 'deepseek-v3', provider: 'DeepSeek', status: '维护中' },
])

async function loadHealth() {
  loading.value = true
  try {
    const res = await fetch('/health')
    const data: unknown = await res.json()
    if (data && typeof data === 'object') {
      const obj = data as Record<string, unknown>
      // 兼容 Spring Boot Actuator 的 { "status": "UP" } 或自定义 { app, ragflow }
      const status = (obj.status as string) || ''
      if (status) {
        health.value = { app: status, ragflow: status }
      } else {
        health.value = {
          app: (obj.app as string) || '',
          ragflow: (obj.ragflow as string) || '',
        }
      }
    }
  } catch {
    // 后端未启动时使用占位
    health.value = { app: 'DOWN', ragflow: 'DOWN' }
  } finally {
    loading.value = false
  }
}

onMounted(loadHealth)
</script>

<style scoped>
.settings-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.sub-menu {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: var(--shadow-1);
  padding: 12px;
}
.sub-menu-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--text-2);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.sub-menu-item:hover {
  background: var(--fill-1);
  color: var(--primary);
}
.sub-menu-item.active {
  background: var(--primary-bg);
  color: var(--primary-active);
  font-weight: 500;
}

.settings-content {
  flex: 1;
  min-width: 0;
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

/* 健康检查卡片 */
.health-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 6px;
  background: var(--fill-1);
  border: 1px solid var(--border-2);
}
.health-item.ok {
  background: #E8FFEA;
  border-color: #C4E8C9;
}
.health-item.fail {
  background: #FFECE8;
  border-color: #FFCCC7;
}
.health-icon {
  font-size: 24px;
  line-height: 1;
}
.health-name {
  font-size: 13px;
  color: var(--text-1);
  font-weight: 500;
}
.health-status {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
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
</style>
