<template>
  <div class="app-layout">
    <!-- 顶栏 -->
    <header class="app-header">
      <div class="header-logo">
        <div class="logo-mark"></div>
        <span class="logo-text">发电知识中心</span>
      </div>

      <!-- 导航菜单 -->
      <nav class="nav">
        <router-link v-for="item in standaloneItems" :key="item.path" :to="item.path" class="nav-item"
          :class="{ active: currentNav === item.nav }">
          {{ item.label }}
          <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
        </router-link>

        <div v-for="module in navModules" :key="module.label" class="nav-item nav-dropdown-trigger"
          :class="{ active: module.children.some(c => c.nav === currentNav) }">
          {{ module.label }}
          <span class="nav-arrow">▾</span>
          <div class="nav-dropdown">
            <router-link v-for="child in module.children" :key="child.path" :to="child.path" class="nav-sub-item"
              :class="{ active: currentNav === child.nav, disabled: child.disabled }"
              @click.prevent="child.disabled ? null : undefined">
              {{ child.label }}
              <span v-if="child.badge" class="nav-sub-badge">{{ child.badge }}</span>
              <span v-if="child.tag" class="nav-sub-tag">{{ child.tag }}</span>
            </router-link>
          </div>
        </div>
      </nav>

      <!-- 用户区 -->
      <div class="header-actions">
        <el-badge :value="5" :max="99">
          <el-icon size="18" color="rgba(255,255,255,0.65)"><Bell /></el-icon>
        </el-badge>
        <el-dropdown trigger="click" @command="onUserCommand">
          <div class="header-user">
            <div class="avatar">{{ userInitial }}</div>
            <span class="user-name">{{ user?.personName || '加载中' }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>{{ user?.orgPath || '未知部门' }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '@/store/session'

const route = useRoute()
const store = useSessionStore()

const currentNav = computed(() => route.meta.nav as string)
const user = computed(() => store.user)
const userInitial = computed(() => store.user?.personName?.charAt(0) || '?')

interface NavChild {
  path: string; label: string; nav: string;
  badge?: string; tag?: string; disabled?: boolean;
}

const standaloneItems = [
  { path: '/index', label: '知识门户首页', nav: 'index' },
  { path: '/workbench', label: '我的工作台', nav: 'workbench', badge: '12' },
  { path: '/dataset', label: '知识库管理', nav: 'dataset' },
]

const navModules: { label: string; children: NavChild[] }[] = [
  {
    label: '知识采集与抽取',
    children: [
      { path: '/dataset', label: '上传入口（知识库管理）', nav: 'upload' },
      { path: '/documents', label: '文档管理', nav: 'documents' },
      { path: '/data-source', label: '数据源管理', nav: 'data-source' },
    ],
  },
  {
    label: '知识处理与加工',
    children: [
      { path: '/detail', label: '解析与切片', nav: 'detail' },
      { path: '#', label: '清洗·融合·补全', nav: '', disabled: true, tag: '二期' },
    ],
  },
  {
    label: '知识审核',
    children: [
      { path: '/audit-workbench', label: '人工审核', nav: 'audit', badge: '12' },
      { path: '/ai-audit-report', label: 'AI 审核', nav: 'ai-audit', badge: '38' },
    ],
  },
  {
    label: '知识入库',
    children: [
      { path: '/knowledge-graph', label: '知识图谱', nav: 'graph' },
      { path: '/annotation', label: '知识标注', nav: 'annotation' },
    ],
  },
  {
    label: '知识检索',
    children: [
      { path: '/search', label: '知识检索', nav: 'search' },
      { path: '/retrieval-test', label: '检索测试', nav: 'retrieval-test' },
    ],
  },
  {
    label: '智能应用',
    children: [
      { path: '/agents', label: '智能体管理', nav: 'agents' },
      { path: '/chat', label: '智能对话', nav: 'chat' },
    ],
  },
  {
    label: '知识统计与分析',
    children: [
      { path: '/statistics', label: '统计仪表盘', nav: 'statistics' },
    ],
  },
  {
    label: '运营管理',
    children: [
      { path: '/permission', label: '权限管理', nav: 'permission' },
      { path: '/version', label: '版本管理', nav: 'version' },
      { path: '/settings', label: '系统设置', nav: 'settings' },
    ],
  },
]

function onUserCommand(cmd: string) {
  if (cmd === 'logout') store.logout()
}
</script>

<style scoped>
.app-layout { min-height: 100vh; background: var(--fill-1); }

.app-header {
  height: var(--header-height); background: var(--header-bg); color: #fff;
  display: flex; align-items: center; padding: 0 24px;
  position: fixed; top: 0; left: 0; right: 0; z-index: 100;
}
.header-logo { display: flex; align-items: center; gap: 8px; margin-right: 16px; }
.logo-mark {
  width: 24px; height: 24px; background: var(--primary); border-radius: 4px; position: relative;
}
.logo-mark::before {
  content: ''; position: absolute; left: 11px; top: 4px; width: 2px; height: 16px; background: #fff;
}
.logo-mark::after {
  content: ''; position: absolute; left: 4px; top: 7px; width: 9px; height: 9px;
  border: 2px solid #fff; border-radius: 50%; border-top-color: transparent; border-right-color: transparent;
}
.logo-text { font-size: 16px; font-weight: 600; letter-spacing: 0.5px; }

.nav { flex: 1; display: flex; align-items: center; height: 100%; gap: 0; }
.nav-item {
  position: relative; height: 100%; display: flex; align-items: center;
  padding: 0 12px; font-size: 13px; color: rgba(255,255,255,0.65); cursor: pointer;
  white-space: nowrap; text-decoration: none; transition: color 0.15s;
}
.nav-item:hover { color: #fff; }
.nav-item.active { color: #fff; }
.nav-item.active::after {
  content: ''; position: absolute; left: 12px; right: 12px; bottom: 0; height: 2px; background: var(--primary-hover);
}
.nav-badge {
  margin-left: 6px; background: var(--danger); color: #fff; font-size: 11px;
  padding: 0 6px; height: 16px; border-radius: 8px; display: inline-flex;
  align-items: center; justify-content: center; min-width: 16px;
}
.nav-arrow { margin-left: 4px; font-size: 10px; opacity: 0.6; }
.nav-dropdown-trigger:hover .nav-arrow { transform: rotate(180deg); opacity: 1; }

.nav-dropdown {
  position: absolute; top: 100%; left: 0; min-width: 184px; background: #fff;
  border-radius: 0 0 6px 6px; box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  padding: 4px 0; display: none; z-index: 200;
}
.nav-dropdown-trigger:hover .nav-dropdown { display: block; }
.nav-sub-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px; font-size: 13px; color: var(--text-2); text-decoration: none; transition: all 0.15s;
}
.nav-sub-item:hover { background: var(--fill-1); color: var(--primary); }
.nav-sub-item.active {
  color: var(--primary); font-weight: 500; background: var(--primary-bg);
  border-left: 3px solid var(--primary); padding-left: 13px;
}
.nav-sub-item.disabled { color: var(--text-4); cursor: not-allowed; }
.nav-sub-item.disabled:hover { background: none; }
.nav-sub-tag {
  font-size: 11px; padding: 0 6px; height: 16px; border-radius: 4px;
  background: var(--border-2); color: var(--text-4); display: inline-flex; align-items: center;
}
.nav-sub-badge {
  background: var(--danger); color: #fff; font-size: 11px; padding: 0 6px;
  height: 16px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; min-width: 16px;
}

.header-actions { margin-left: auto; display: flex; align-items: center; gap: 16px; }
.header-user {
  display: flex; align-items: center; gap: 8px; cursor: pointer;
  padding: 0 8px; height: 32px; border-radius: 4px;
}
.header-user:hover { background: rgba(255,255,255,0.08); }
.avatar {
  width: 28px; height: 28px; border-radius: 50%; background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 500;
}
.user-name { font-size: 13px; color: #fff; }

.app-main { margin-top: var(--header-height); min-height: calc(100vh - var(--header-height)); }
</style>
