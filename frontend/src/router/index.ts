import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useSessionStore } from '@/store/session'

const MainLayout = () => import('@/layouts/MainLayout.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/index',
    children: [
      // 总览
      { path: 'index', name: 'Index', component: () => import('@/views/IndexView.vue'), meta: { title: '知识门户首页', nav: 'index' } },
      { path: 'workbench', name: 'Workbench', component: () => import('@/views/WorkbenchView.vue'), meta: { title: '我的工作台', nav: 'workbench' } },
      { path: 'dataset', name: 'Dataset', component: () => import('@/views/DatasetView.vue'), meta: { title: '知识库管理', nav: 'dataset' } },

      // 知识采集与抽取
      { path: 'upload-wizard', name: 'UploadWizard', component: () => import('@/views/UploadWizardView.vue'), meta: { title: '手动上传', nav: 'upload' } },
      { path: 'documents', name: 'Documents', component: () => import('@/views/DocumentsView.vue'), meta: { title: '文档管理', nav: 'documents' } },
      { path: 'data-source', name: 'DataSource', component: () => import('@/views/DataSourceView.vue'), meta: { title: '数据源管理', nav: 'data-source' } },

      // 知识处理与加工
      { path: 'detail', name: 'Detail', component: () => import('@/views/DetailView.vue'), meta: { title: '解析与切片', nav: 'detail' } },

      // 知识审核
      { path: 'audit-workbench', name: 'AuditWorkbench', component: () => import('@/views/AuditWorkbenchView.vue'), meta: { title: '人工审核', nav: 'audit' } },
      { path: 'ai-audit-report', name: 'AiAuditReport', component: () => import('@/views/AiAuditReportView.vue'), meta: { title: 'AI 审核', nav: 'ai-audit' } },

      // 知识入库
      { path: 'knowledge-graph', name: 'KnowledgeGraph', component: () => import('@/views/KnowledgeGraphView.vue'), meta: { title: '知识图谱', nav: 'graph' } },
      { path: 'annotation', name: 'Annotation', component: () => import('@/views/AnnotationView.vue'), meta: { title: '知识标注', nav: 'annotation' } },

      // 知识检索
      { path: 'search', name: 'Search', component: () => import('@/views/SearchView.vue'), meta: { title: '知识检索', nav: 'search' } },
      { path: 'retrieval-test', name: 'RetrievalTest', component: () => import('@/views/RetrievalTestView.vue'), meta: { title: '检索测试', nav: 'retrieval-test' } },

      // 智能应用
      { path: 'agents', name: 'Agents', component: () => import('@/views/AgentsView.vue'), meta: { title: '智能体管理', nav: 'agents' } },
      { path: 'chat', name: 'Chat', component: () => import('@/views/ChatView.vue'), meta: { title: '智能对话', nav: 'chat' } },

      // 知识统计与分析
      { path: 'statistics', name: 'Statistics', component: () => import('@/views/StatisticsView.vue'), meta: { title: '统计仪表盘', nav: 'statistics' } },

      // 运营管理
      { path: 'permission', name: 'Permission', component: () => import('@/views/PermissionView.vue'), meta: { title: '权限管理', nav: 'permission' } },
      { path: 'version', name: 'Version', component: () => import('@/views/VersionView.vue'), meta: { title: '版本管理', nav: 'version' } },
      { path: 'settings', name: 'Settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '系统设置', nav: 'settings' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局守卫：加载用户信息（SSO 401 由 axios 拦截器处理）
router.beforeEach(async (to) => {
  const store = useSessionStore()
  if (!store.user && !store.loading) {
    await store.fetchUser()
  }
  document.title = `${to.meta.title || '首页'} - 发电知识中心`
})

export default router
