# 发电知识中心 · 前端

Vue 3 + TypeScript + Vite + Element Plus，对接后端 `rag-knowledge-platform`。

## 快速开始

```bash
pnpm install
pnpm dev          # 启动 dev server (localhost:3000，自动代理 /api → localhost:8081)
pnpm build        # 生产构建
```

## 架构

- **独立 Vue3 工程**（不 fork RAGFlow），后续按需嵌入 RAGFlow 原生页面
- **主题**：海康品牌红 `#C7000B`（覆盖 Element Plus `--el-color-primary`）
- **SSO**：无登录页，401 自动跳 `/sso/login`（由后端 OAuth2 流程处理）
- **导航**：顶部导航 + 9 模块下拉，对应 19 个页面路由

## 目录结构

```
src/
├── api/          axios 封装 + 全部 API 模块（对接后端 RESTful）
├── router/       路由表（19 页）+ SSO 守卫
├── store/        Pinia（session）
├── layouts/      MainLayout（顶部导航布局）
├── styles/       全局 CSS（设计系统变量）
├── views/        页面（8 个完整 + 11 个占位待补全）
└── main.ts       应用入口
```

## 已实现页面（8 个完整标杆）

| 页面 | 路由 | 功能 |
|------|------|------|
| 首页 | /index | 检索 Hero + 统计 + 分类导航 |
| 工作台 | /workbench | 生命周期看板 + Tab + 待审列表 |
| 知识库管理 | /dataset | Dataset 卡片 + 创建/编辑弹窗（12 种 chunk_method + parser_config）|
| 文档管理 | /documents | 上传/列表/重新解析/下载/删除 |
| 上传向导 | /upload-wizard | 4 步引导：采集→切片配置→元数据→提交审核 |
| 检索 | /search | 权限预过滤 + 高亮 + 相似度徽章 |
| 审核工作台 | /audit-workbench | 待审列表 + 通过/退回 + 审核记录时间线 |
| 统计仪表盘 | /statistics | 卡片 + 热门词云 + 高频 chunk |

## 待补全页面（11 个占位）

detail / annotation / ai-audit-report / data-source / knowledge-graph /
retrieval-test / agents / chat / permission / version / settings

参照 `prototype/*.html` 原型逐步实现。

## 后端对接

所有 API 请求经 vite proxy → `localhost:8081`（Spring Boot）。
Cookie 自动携带 `KNOWLEDGE_SID`（SSO 会话）。
