# C+D 审核与运营管理子系统 设计文档

> 基于 RAGFlow 二开，覆盖《RAGFlow对比与定制方案.md》中 C+D 共 8 项负偏离。
> 版本：v0.1（原型评审版）  日期：2026-06-15

---

## 一、概述

### 1.1 背景

RAGFlow 原生覆盖约 40% 需求（解析/切分/向量化/图谱/检索/标注/质量调试），剩余 60% 通过外挂定制开发。本设计聚焦其中 C+D 两个层级：

- **C. 审核与治理层**：人工审核、AI 审核、审批流程定制、知识分析
- **D. 运营管理层**：细粒度权限、版本管理、门户定制、Neo4j 图谱评估

### 1.2 目标

为「知识驱动的发电运行多智能体」平台提供可上线的知识管理运营骨架，满足以下强约束：

| 强约束 | 原始出处 |
|--------|---------|
| 集团-区域-企业 三级运营机制 | output.md 第89行 |
| 文档级权限配置 | output.md 第200行 |
| 操作审计覆盖率 100% | output.md 第201行 |
| 等保三级合规 | output.md 第199行 |
| 检索响应 ≤1s、问答 ≤5s、并发 ≥300 | output.md 第192-194行 |

### 1.3 关键决策（v0.2 修订）

| 决策项 | v0.1 | v0.2（当前）| 修订原因 |
|--------|------|------------|---------|
| 集成形态 | 双门户并存 | **单门户 SPA（fork RAGFlow Vue 二开）** | 双门户割裂，上传→审核流程无闭环引导 |
| 前端策略 | 自建独立 Vue3 应用 | **fork RAGFlow Vue，融合自建模块** | 复用 RAGFlow 切片/解析/模型配置等成熟页面，体验统一 |
| 核心新增 | 无 | **知识工作台 + 上传向导** | 串联知识生命周期，提供引导式操作 |
| RAGFlow 升级 | 原样保留，外挂 | **fork 维护，定期 merge 上游** | 接受 fork 成本换取体验统一 |

### 1.4 范围

| 负偏离项 | 是否本次范围 | 说明 |
|---------|------------|------|
| 9. 人工审核 | ✅ | 模板化多级状态机 |
| 10. AI 审核 | ✅ | 6 类检测维度，LLM 抽象接口 |
| 11. 审批流程定制 | ✅ | 模板配置（不引入 Flowable） |
| 12. 知识分析 | ✅ 部分 | 统计为主，覆盖率/准确率走人工抽样 + 检索测试 |
| 13. 细粒度权限 | ✅ | 四维 + metadata 预过滤 + 审计日志 |
| 14. 版本管理 | ✅ | 多 dataset 隔离 + 指针切换 |
| 15. 门户定制 | ✅ | 仅 Web 端 |
| 16. Neo4j 图谱 | ⚠️ 评估替代 | 原型用 RAGFlow 内置 LightRAG，Neo4j 专项评估 |

### 1.4 不在本期范围

- A 层数据接入（DB/API/消息队列/爬虫/日志）—— 另立子项目
- B 层知识加工增强（清洗管道/融合/补全）—— 另立子项目
- 移动端门户
- 7 大智能体内部实现（门户仅提供入口卡片）
- 内部平台已有能力（登录、菜单、人员、组织、角色管理）

---

## 二、整体架构

### 2.1 核心理念：单门户 + 生命周期闭环

**不再采用双门户割裂方案**。改为单一门户承载知识从「采集 → 加工 → 审核 → 发布 → 运营」的完整生命周期，所有角色（编辑员/审核员/管理员/浏览者）在同一个系统内完成所有操作，避免跨系统跳转导致流程断裂。

**生命周期闭环**：

```
┌─────────── 知识工作台（闭环中枢）─────────────┐
│  我的草稿 │ 解析中 │ 待我审核 │ 已发布 │ 被退回  │
└───────────────────┬──────────────────────────┘
                    │
    ┌─── 上传向导（引导式 4 步）───┐
    │ ①上传 → ②切片配置 → ③元数据 → ④提交审核 │
    └──────────────────────────────┘
                    │
    ┌─── 审核中心 ───┐    ┌─── 运营管理 ───┐
    │ 人工审核 / AI审核 │    │ 统计 / 版本 / 权限 │
    └────────────────┘    └─────────────────┘
                    │
              发布到线上知识库
```

### 2.2 三层架构

采用「**单门户 SPA + 模块化单体后端 + RAGFlow 内核**」三层架构。

```
┌──────────────────────────────────────────────────────┐
│  内部平台（已有）                                       │
│  提供登录 / 菜单 / 人员 / 组织 / 角色 / 资源鉴权         │
│  └─ 知识库（一级菜单）──SSO Token──┐                   │
└────────────────────────────────────┼──────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────┐
│  统一知识门户 SPA（基于 RAGFlow Vue 前端 fork 二开）     │
│                                                        │
│  ┌────────── 自建业务模块 ──────────┐ ┌── RAGFlow 原生模块 ──┐│
│  │ 工作台 / 上传向导 / 审核中心       │ │ 切片调优 / 模型配置   ││
│  │ 权限 / 统计 / 版本 / 图谱 / 检索   │ │ 解析调试 / QA 生成   ││
│  └────────────────────────────────┘ └────────────────────┘│
│  统一布局 / 统一鉴权 / 统一主题（Element Plus）          │
└────────────────────────┬─────────────────────────────────┘
                         ▼ HTTPS（API 网关统一路由）
┌──────────────────────────────────────────────────────┐
│  自建增强后端            │   RAGFlow 原生后端（保留）     │
│  Spring Boot 2.7 / JDK11 │   Python / Flask              │
│  portal-api / audit /    │   Dataset / Document / Chunk  │
│  permission / stats /    │   Retrieval / Conversation    │
│  version / integration   │   Agent / DeepDoc 解析        │
│  ├─ PostgreSQL 11        │   ├─ 向量库（Infinity/ES）     │
│  ├─ Redis                │   ├─ 对象存储（MinIO/S3）      │
│  └─ XXL-Job              │   └─ 任务队列                  │
└──────────────────────────┴───────────────────────────────┘
```

### 2.3 前端集成：fork RAGFlow Vue 二开

**不再保留独立 RAGFlow 前端入口**，而是 fork RAGFlow Vue 前端源码进行二次开发，与自建模块融合为单一 SPA：

| 改造点 | 做法 |
|--------|------|
| 布局 | 复用 RAGFlow 的 `Layout` 组件（顶栏 + 侧边栏 + 内容区），统一替换 Logo/主题色为「发电知识中心」|
| 鉴权 | 海康 OAuth2 SSO（见 §12.2），无独立登录页；本地 Redis 会话 2 小时 |
| 路由 | 在 RAGFlow 路由表基础上注册自建模块路由（`/workbench` `/audit` `/permission` 等）|
| 菜单 | 按「生命周期」重组侧边栏菜单（见 §10.2），RAGFlow 原生页面收纳到「系统设置」分组 |
| API 网关 | 前端统一调 `/api/*`，由 Nginx/网关路由：自建模块请求转发到 Spring Boot，RAGFlow 模块请求转发到 Flask |
| 主题 | 统一 Element Plus 主题色（`--primary: #1890ff`），保持视觉一致 |

**RAGFlow 升级策略**：
- fork 后建立独立分支，记录所有改动点
- RAGFlow 发版时，通过 `git merge` 合并上游，人工解决冲突
- 改动尽量集中在布局层、路由层、鉴权层，避免深入业务组件
- 每次升级前在测试环境验证，回归切片/解析/检索核心功能

### 2.4 写入收敛点

所有知识入库操作（无论来自采集层、API 推送、还是门户上传向导）统一收敛到 RAGFlow `Dataset/Document Upload API`，把 RAGFlow 当作「知识存储 + 检索内核」，不触碰其向量库与图谱内部结构。

---

## 三、后端模块设计（模块化单体）

### 3.1 模块划分

```
rag-knowledge-platform/
├── integration/       # 对接层
│   ├── sso/           # OAuth2 客户端（见 §12.2）
│   ├── org-sync/      # 组织架构同步（XXL-Job 定时拉取）
│   └── ragflow-client/# RAGFlow API Client（Dataset/Doc/Chunk/Retrieval）
├── permission/        # 权限网关
│   ├── model/         # 四维权限模型 + 角色定义
│   ├── engine/        # 权限校验引擎 + 索引预计算
│   ├── metadata/      # metadata 同步到 RAGFlow document
│   └── audit/         # 操作审计日志（等保三级）
├── audit/             # 审核治理
│   ├── template/      # 审核模板配置
│   ├── fsm/           # 审核状态机
│   ├── workbench/     # 人工审核工作台 API
│   └── ai/            # AI 审核（6 类检测，LLM Gateway 抽象）
├── version/           # 版本管理
│   ├── meta/          # 版本元数据
│   └── switch/        # 线上/测试 dataset 指针切换 + 回滚
├── stats/             # 统计分析
│   ├── collector/     # 检索日志埋点
│   ├── aggregator/    # 聚合任务（热门词/高频 chunk/知识量）
│   └── report/        # 报表导出
└── portal-api/        # 门户 API 聚合层
    ├── search/        # 检索代理（带权限过滤）
    ├── catalog/       # 分类导航
    ├── detail/        # 知识详情
    └── recommend/     # 热门推荐
```

### 3.2 与海康平台对接

> 完整方案见 **§11·补二 跨系统身份与组织同步**。本节仅摘要。

**SSO 单点登录（OAuth2 Authorization Code Flow）**：
- 海康平台是 IdP，知识中心是 SP，**无独立登录页**
- 三步：`/oauth2/authorize`（拿 code）→ `/oauth2/token`（换 access_token）→ `/oauth2/userinfo`（拿 userId）
- 拿到 userId 后调 Artemis `person/advance/personList` 补全组织/岗位信息
- 签发知识中心本地会话（Redis，2 小时 TTL）

**组织同步（Artemis 签名 + timeRange 接口）**：
- 全量：每周日 02:00，调 `personList` + `org/advance/orgList`
- 增量：每小时，调 `personList/timeRange` + `org/timeRange`（含 `status<0` 删除标记，48 小时红线）
- 实时兜底：SSO 登录时单查 personList 校验用户状态
- 角色本地维护（海康无角色概念）

**菜单挂载**：
- 海康平台配置「知识中心」一级菜单，指向 `/sso/login?menu_uri=/index`
- 知识中心侧栏菜单按招标 8 大模块组织（见 §10.2）

---

## 四、数据模型

### 4.1 核心表（PostgreSQL）

```sql
-- 业务文档元数据（自建，与 RAGFlow document 一一对应）
CREATE TABLE knowledge_doc (
    id              BIGINT PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    ragflow_doc_id  VARCHAR(64) NOT NULL,
    dataset_id      VARCHAR(64) NOT NULL,
    business_type   VARCHAR(50),       -- 业务分类（规程/标准/台账/案例...）
    dept_id         BIGINT,            -- 所属部门
    tags            VARCHAR(500)[],    -- 标签数组
    security_level  SMALLINT DEFAULT 1,-- 1公开 2内部 3秘密 4机密
    version_id      BIGINT,
    audit_status    VARCHAR(20) NOT NULL, -- DRAFT/PENDING/APPROVED/PUBLISHED/REJECTED
    source_type     VARCHAR(20),       -- UPLOAD/API/MQ/DB/CRAWLER
    source_ref      VARCHAR(500),      -- 来源引用（URL/表名/Topic）
    created_by      BIGINT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP
);
CREATE INDEX idx_doc_dataset ON knowledge_doc(dataset_id);
CREATE INDEX idx_doc_audit ON knowledge_doc(audit_status);
CREATE INDEX idx_doc_dept ON knowledge_doc(dept_id);

-- 权限策略（四维）
CREATE TABLE permission_policy (
    id              BIGINT PRIMARY KEY,
    subject_type    VARCHAR(20) NOT NULL,  -- ROLE/DEPT/USER
    subject_id      BIGINT NOT NULL,
    object_type     VARCHAR(20) NOT NULL,  -- DATASET/CATEGORY/TAG/DOC
    object_value    VARCHAR(200) NOT NULL, -- 对应分类码/标签值/doc_id
    actions         VARCHAR(100)[],        -- VIEW/SEARCH/EDIT/DELETE/AUDIT/PUBLISH/EXPORT
    inherit         BOOLEAN DEFAULT TRUE,  -- 是否继承给子分类
    created_by      BIGINT,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- 权限索引（预计算，用于检索时快速生成 metadata_filter）
CREATE TABLE permission_index (
    user_id         BIGINT,
    dept_path       VARCHAR(500),       -- 部门路径（支持层级继承）
    visible_depts   BIGINT[],           -- 可见部门集合
    visible_categories VARCHAR(50)[],   -- 可见分类集合
    visible_tags    VARCHAR(100)[],     -- 可见标签集合
    max_security_level SMALLINT,        -- 最高可见密级
    updated_at      TIMESTAMP
);

-- 审核模板
CREATE TABLE audit_template (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,   -- 一级直审/两级审核/三级集团审核
    business_type   VARCHAR(50),             -- 适用的业务分类（NULL=通用）
    nodes           JSONB NOT NULL,          -- [{order, name, approver_role, multi_sign}]
    enabled         BOOLEAN DEFAULT TRUE
);

-- 审核实例
CREATE TABLE audit_instance (
    id              BIGINT PRIMARY KEY,
    doc_id          BIGINT NOT NULL,
    template_id     BIGINT NOT NULL,
    current_node    SMALLINT,                -- 当前节点序号
    status          VARCHAR(20) NOT NULL,    -- PENDING/APPROVED/REJECTED/WITHDRAWN
    submitted_by    BIGINT,
    submitted_at    TIMESTAMP,
    finished_at     TIMESTAMP
);

-- 审核节点记录
CREATE TABLE audit_node_record (
    id              BIGINT PRIMARY KEY,
    instance_id     BIGINT NOT NULL,
    node_order      SMALLINT NOT NULL,
    approver_id     BIGINT,
    approver_role   VARCHAR(50),
    action          VARCHAR(20),         -- APPROVE/REJECT/WITHDRAW
    comment         TEXT,
    chunk_edits     JSONB,               -- 审核时对 chunk 的修改
    acted_at        TIMESTAMP
);

-- AI 审核问题
CREATE TABLE ai_audit_issue (
    id              BIGINT PRIMARY KEY,
    doc_id          BIGINT,
    chunk_id        VARCHAR(64),
    issue_type      VARCHAR(30) NOT NULL,    -- CONFLICT/ERROR/TIMELINESS/INTEGRITY/CONSISTENCY/NORM
    severity        VARCHAR(10) NOT NULL,    -- HIGH/MEDIUM/LOW
    position        VARCHAR(200),            -- 问题位置（章节/页码）
    description     TEXT,
    suggestion      TEXT,
    status          VARCHAR(20) DEFAULT 'OPEN', -- OPEN/RESOLVED/IGNORED
    created_at      TIMESTAMP DEFAULT NOW()
);

-- 版本元数据
CREATE TABLE version_meta (
    id              BIGINT PRIMARY KEY,
    dataset_id      VARCHAR(64) NOT NULL,
    env             VARCHAR(20) NOT NULL,    -- ONLINE/TEST
    version_label   VARCHAR(50),
    parent_id       BIGINT,                  -- 上一版本
    change_log      TEXT,
    published_by    BIGINT,
    published_at    TIMESTAMP
);

-- 操作审计日志（等保三级）
CREATE TABLE operation_log (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    username        VARCHAR(100),
    action          VARCHAR(50) NOT NULL,    -- LOGIN/VIEW/SEARCH/EDIT/DELETE/GRANT/REVOKE...
    resource_type   VARCHAR(30),
    resource_id     VARCHAR(100),
    request_ip      VARCHAR(50),
    request_body    TEXT,
    before_snapshot TEXT,
    after_snapshot  TEXT,
    result          VARCHAR(20),             -- SUCCESS/FAIL
    error_msg       TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_log_user ON operation_log(user_id, created_at DESC);
CREATE INDEX idx_log_resource ON operation_log(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_log_action ON operation_log(action, created_at DESC);

-- 检索日志（埋点，用于统计）
CREATE TABLE search_log (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT,
    query           TEXT,
    result_count    INT,
    top_chunk_ids   VARCHAR(64)[],
    response_ms     INT,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### 4.2 与 RAGFlow 的数据映射

| 自建表 | RAGFlow 实体 | 关系 |
|--------|-------------|------|
| `knowledge_doc.ragflow_doc_id` | `document.id` | 1:1 |
| `knowledge_doc.dataset_id` | `dataset.id` | N:1 |
| `version_meta.dataset_id` | `dataset.id`（线上/测试各一个） | 1:1 |
| `permission_index` → metadata | `document.metadata` = `{depts, categories, tags, security_level}` | 同步写入 |

---

## 五、审核流程设计

### 5.1 审核模板

预设 3 个标准模板，按业务分类映射：

| 模板 | 节点 | 适用场景 |
|------|------|---------|
| 一级直审 | 编辑员提交 → 部门审核员 → 发布 | 日常规程修订 |
| 两级审核 | 编辑员提交 → 区域审核员 → 集团审核员 → 发布 | 区域共性知识 |
| 三级集团审核 | 编辑员提交 → 企业初审 → 区域复审 → 集团终审 → 发布 | 集团通用标准、行业标准引用 |

模板存储为 JSONB，支持后台配置（不引入 Flowable，避免过重）：

```json
{
  "nodes": [
    {"order": 1, "name": "企业初审", "approver_role": "AUDITOR_ENTERPRISE"},
    {"order": 2, "name": "区域复审", "approver_role": "AUDITOR_REGION"},
    {"order": 3, "name": "集团终审", "approver_role": "AUDITOR_GROUP", "multi_sign": true}
  ]
}
```

### 5.2 状态机

```
                              ┌──────────────┐
                              │   DRAFT 草稿  │
                              └──────┬───────┘
                                     │ 提交
                                     ▼
                              ┌──────────────┐
                ┌─────────────│ PENDING 待审 │─────────────┐
                │             └──────┬───────┘             │
                │ 退回（任意节点）    │ 当前节点通过         │ 拒绝
                ▼                    ▼                      ▼
        ┌──────────────┐     ┌──────────────┐       ┌──────────────┐
        │   DRAFT 草稿  │     │ 下一节点 PENDING│      │ REJECTED 退回 │
        └──────────────┘     └──────┬───────┘       └──────────────┘
                                    │ 最后节点通过
                                    ▼
                             ┌──────────────┐
                             │ PUBLISHED 已发布│
                             └──────────────┘
```

- 任一审核节点可执行 APPROVE / REJECT / WITHDRAW（撤回）
- 退回到草稿后，编辑员可修改后重新提交（创建新 instance）
- 审核通过后调用 RAGFlow API 启用对应 chunk（`enable_chunk`）

### 5.3 人工审核工作台

审核员界面提供：
- 待审列表（按提交时间、业务分类、紧急度排序）
- 审核详情：文档元信息 + chunk 列表（支持内联编辑 chunk 内容、补充关键词/问题）
- 原文溯源：跳转到原始文档对应页码
- AI 审核报告侧栏：展示 AI 自动检测出的问题（见 5.4）
- 操作：通过 / 退回 / 标记问题（带批注）

### 5.4 AI 审核

**触发时机**：文档解析入库后，由 XXL-Job 异步触发（不阻塞审核流程）。

**6 类检测维度**：

| 维度 | 实现方式 | 输出 |
|------|---------|------|
| 矛盾检测 | 同主题 chunk 向量近邻 + LLM 对比 | 矛盾对 + 建议取舍 |
| 错误检测 | 规则引擎（数值范围/单位/逻辑）+ 行业合理性规则库 | 超限项列表 |
| 时效性检测 | 内置行业标准版本库 + LLM 提取引用标准 | 过期标准告警 |
| 完整性检测 | 关键字段模板（设备/参数/故障/措施实体必须齐全）+ LLM | 缺失字段列表 |
| 一致性检测 | 同实体全库扫描，描述差异 > 阈值 | 不一致描述对 |
| 规范性检测 | 行业术语词典 + LLM | 非规范术语建议 |

**LLM Gateway 抽象接口**（暂不锁定具体模型）：

```java
public interface LlmGateway {
    String chat(String systemPrompt, String userPrompt);
    // 未来可对接公司 LLM 网关 / RAGFlow 已配模型 / OpenAI 兼容 API
}
```

**输出**：`ai_audit_issue` 表，每条含 `issue_type / severity / position / description / suggestion`，支持按类型/严重度筛选、批量处理（采纳/忽略/转人工）。

### 5.5 审批流程定制

后台配置页提供：
- 模板列表（增删改查）
- 节点编辑（顺序、名称、审批角色、是否会签）
- 业务分类与模板的映射关系

**不提供拖拽式流程设计器**（避免引入 Flowable/Camunda 的复杂度），如未来确有强需求再升级。

---

## 六、权限模型设计

### 6.1 四维权限

| 维度 | 取值 | 来源 |
|------|------|------|
| 角色 | 管理员/审核员（企业/区域/集团）/编辑员/浏览者/自定义 | 内部平台角色 + 本地扩展 |
| 部门 | 组织架构树（支持层级继承）| 内部平台组织同步 |
| 业务分类 | 规程/标准/台账/案例/告警/日志... | 本地维护分类码表 |
| 标签 | 文档标签（如「锅炉」「汽轮机」「环保」）| 文档打标时产生 |

### 6.2 权限落地：metadata 预过滤

**写入时机**：文档创建/修改时，自建后端把权限维度同步到 RAGFlow：

```json
// RAGFlow document.metadata
{
  "depts": [101, 102, 105],
  "categories": ["REGULATION", "STANDARD"],
  "tags": ["锅炉", "汽轮机"],
  "security_level": 2
}
```

**检索时机**：自建后端先根据当前用户计算可见条件，再调 RAGFlow retrieval：

```
用户检索请求
  → 后端从 permission_index 查出 {visible_depts, visible_categories, visible_tags, max_security_level}
  → 拼装 RAGFlow retrieval 请求，带 metadata_filter：
      {"depts": {"in": [101,102,105]},
       "categories": {"in": ["REGULATION","STANDARD"]},
       "security_level": {"lte": 2}}
  → RAGFlow 在向量库内部预过滤后检索
  → 返回可见 chunk（满足 ≤1s 性能要求）
```

### 6.3 权限继承与覆盖

- 子分类默认继承父分类权限（`inherit=true`）
- 子分类可设置独立策略覆盖父分类（`inherit=false` 时只取自身策略）
- 权限变更实时生效：策略修改后立即更新 `permission_index`（异步刷新缓存）

### 6.4 审计日志（等保三级）

所有操作经自建后端转发 RAGFlow，全量记录到 `operation_log`：

| 记录项 | 说明 |
|--------|------|
| user_id / username | 操作人 |
| action | LOGIN/VIEW/SEARCH/EDIT/DELETE/GRANT/REVOKE/EXPORT... |
| resource_type / resource_id | 操作对象（DOC/CHUNK/DATASET/PERMISSION）|
| before_snapshot / after_snapshot | 修改前后快照（JSON）|
| request_ip / request_body | 请求来源 |
| result / error_msg | 成功/失败及错误信息 |

支持按用户、时间、操作类型、资源检索，满足等保三级「操作可追溯」要求。

---

## 七、版本管理

### 7.1 多版本并行

利用 RAGFlow 多 dataset 天然隔离：

- 每个知识库维护两个 dataset：`KB_prod_xxx`（线上）、`KB_test_xxx`（测试）
- 测试版本可独立解析、切分、调参，不影响线上
- 切换线上版本 = 更新 `version_meta` 中 `env=ONLINE` 指向的 dataset_id（配置中心管理）

### 7.2 回滚

- 版本切换不删除旧 dataset，仅修改指针
- 回滚 = 切回历史版本指针 + 记录 `change_log`
- 历史 chunk 全量保留在 RAGFlow，支持内容变更溯源

### 7.3 配置变更追溯

- 所有版本元数据修改写入 `operation_log`
- 文档操作日志与历史切片可通过 RAGFlow API 查询

---

## 八、统计分析

### 8.1 埋点

- 检索埋点：`search_log`（查询词、结果数、top chunk、响应时间）
- 命中埋点：复用 RAGFlow chunk 的 `hit_count` 字段
- 文档操作埋点：`operation_log`

### 8.2 统计指标

| 指标 | 计算 | 展示 |
|------|------|------|
| 知识量 | 文档数、chunk 数、实体/关系数（图谱）| 数字卡片 |
| 热门检索词 | search_log 按 query 聚合 TopN | 词云 / 柱状图 |
| 高频 chunk | hit_count 排序 | 列表 |
| 知识增长趋势 | 按日/周/月新增文档数 | 折线图 |
| 部门贡献 | 按 dept_id 聚合 | 饼图 |
| 检索响应时间 | search_log.response_ms 平均/P95 | 数字卡片 |
| 业务分类分布 | 按 business_type 聚合 | 饼图 |

### 8.3 报表导出

支持导出 Excel（按时间范围、部门、分类筛选）。

### 8.4 知识质量评估（复用 RAGFlow）

直接使用 RAGFlow 自带 Retrieval Testing：
- 选择知识库 + 输入测试关键词
- 展示召回 chunk
- 配置调试 topK / 相似度阈值
- 保存测试记录与历史匹配结果

覆盖率/准确率指标走业务专家抽样核查流程（不在本期算法实现范围内）。

---

## 九、知识图谱（Neo4j 评估替代）

### 9.1 原型阶段

使用 RAGFlow 内置 `knowledge_graph` chunk 方法：
- 跨文件统一图谱
- 随文档解析自动更新
- 支持实体检索、关系检索、社区报告、PageRank 排序

门户提供「知识图谱可视化」页面：
- 实体节点 + 关系边力导向图（前端用 ECharts 或 vis-network）
- 点击实体展示属性、关联实体、来源 chunk

### 9.2 Neo4j 专项评估（待立项）

原始方案明确指定 Neo4j/NebulaGraph。需评估：

| 评估项 | 通过则 | 不通过则 |
|--------|--------|---------|
| 实体关系抽取能力 | RAGFlow 抽取覆盖核心实体类型 | 旁路抽取写入 Neo4j |
| 多跳推理 | 内置图 PageRank 满足业务查询 | Neo4j Cypher 查询 |
| 可视化 | 力导向图满足展示需求 | Neo4j Bloom / 自研 |
| 社区发现 | 内置社区报告满足 | Neo4j GDS |

**建议**：原型阶段先用内置图验证业务接受度，若最终用户坚持 Neo4j 强约束，再启动旁路抽取 + 双写方案（独立子项目）。

---

## 九·补、招标模块覆盖矩阵

原型导航与上传流程严格对齐招标参数的 **8 大模块**，确保招标方评审时能直接看到每个模块在系统中的落地位置。

### 9.1 模块 × 页面 × 负偏离项 对应表

| 招标模块 | 子能力 | 对应页面 | RAGFlow覆盖 | 本期状态 | 负偏离项 |
|---------|--------|---------|------------|---------|---------|
| **① 知识采集与抽取** | 手动上传 | `upload-wizard` 步骤① | ✅ 文件上传 | ✅ 本期 | 批量导入 |
| | 数据库采集 | `data-source` | ❌ | 🔵 二期(A层) | 数据库链接 |
| | API 接入 | `data-source` | ⚠️ Push满足/Pull缺 | 🔵 二期(A层) | API接口 |
| | 消息队列 | `data-source` | ❌ | 🔵 二期(A层) | 消息队列 |
| | 网络爬虫 | `data-source` | ❌ | 🔵 二期(A层) | 网络爬虫 |
| | 日志采集 | `data-source` | ❌ | 🔵 二期(A层) | 日志采集 |
| **② 知识处理与加工** | 知识解析 | `upload-wizard` 步骤② | ✅ DeepDoc | ✅ 本期 | 知识解析 |
| | 知识切分 | `upload-wizard` 步骤② + `detail` | ✅ | ✅ 本期 | 知识切分 |
| | 知识清洗 | `upload-wizard` 步骤② | ⚠️ 固定规则 | 🟡 轻度增强 | 知识清洗 |
| | 知识融合 | — | ❌ | 🔵 二期(B层) | 知识融合 |
| | 知识补全 | — | ❌ | 🔵 二期(B层) | 知识补全 |
| | 知识关联 | `knowledge-graph` | ✅ LightRAG | ✅ 本期 | 知识关联 |
| **③ 知识审核** | 人工审核 | `audit-workbench` | ❌ | ✅ 本期 | 人工审核 |
| | AI 审核 | `ai-audit-report` | ❌ | ✅ 本期 | AI审核 |
| **④ 知识入库** | 向量化 | `settings`(模型配置) | ✅ | ✅ 本期 | 向量化 |
| | 知识增强(QA) | `upload-wizard` 步骤③ | ✅ QA chunk | ✅ 本期 | 知识增强 |
| | 图谱构建 | `knowledge-graph` | ⚠️ 内置图/Neo4j评估 | ✅ 本期 | 图谱构建 |
| | 知识标注 | `annotation` + `upload-wizard` 步骤④ | ⚠️ 关键词 | ✅ 本期 | 人工标注/AI标注 |
| **⑤ 知识检索** | 向量检索 | `search` | ✅ | ✅ 本期 | 向量检索 |
| | 图谱检索 | `knowledge-graph` | ✅ | ✅ 本期 | 图谱检索 |
| | 多模态检索 | `search` | ✅ 基本满足 | ✅ 本期 | 多模态检索 |
| **⑥ 知识统计与分析** | 知识分类 | `permission`(分类维度)+`search`(过滤) | ⚠️ | ✅ 本期 | 知识分类 |
| | 知识统计 | `statistics` | ⚠️ | ✅ 本期 | 知识统计 |
| | 知识分析 | `statistics`(质量评估) | ❌ | 🟡 统计为主 | 知识分析 |
| | 质量评估 | `retrieval-test`+`statistics` | ✅ 检索测试 | ✅ 本期 | 知识质量评估 |
| **⑦ 运营管理** | 知识门户 | `index` | ⚠️ | ✅ 本期 | 知识门户界面 |
| | 审批流程定制 | `audit-workbench`(模板配置) | ❌ | ✅ 本期 | 审批流程定制 |
| | 版本更新 | `version` | ⚠️ | ✅ 本期 | 知识版本更新 |
| | 权限管理 | `permission` | ❌ | ✅ 本期 | 知识权限管理 |

> 图例：✅ 满足 / ⚠️ 部分满足 / ❌ 不支持 ｜ ✅本期 / 🟡轻度增强 / 🔵二期

### 9.2 上传流程（5 步对齐招标模块）

```
┌─────────────────────────────────────────────────────────────┐
│                    知识生命周期上传向导                         │
├─────────────────────────────────────────────────────────────┤
│  ① 知识采集    →  ② 知识处理   →  ③ 知识入库  →  ④ 知识标注  →  ⑤ 知识审核  │
│    与抽取         与加工                                    │
│  ┌────────┐   ┌─────────┐   ┌────────┐   ┌────────┐   ┌────────┐│
│  │上传文件 │   │解析DeepDoc│   │向量化   │   │AI标注   │   │AI预审  ││
│  │选数据源 │   │清洗规则   │   │元数据   │   │人工补充 │   │选模板  ││
│  │         │   │切分配置   │   │图谱构建 │   │关键词   │   │提交审核││
│  │         │   │          │   │QA增强  │   │标签分类 │   │       ││
│  └────────┘   └─────────┘   └────────┘   └────────┘   └────────┘│
│     RAGFlow      RAGFlow        RAGFlow      自建+LLM       自建+LLM │
└─────────────────────────────────────────────────────────────┘
```

每步对应招标模块，且标注所复用的 RAGFlow 能力或自建能力，让招标方清楚看到技术落地点。

### 10.1 页面清单（15 个）

| # | 页面 | 文件 | 所属招标模块 | 主要内容 |
|---|------|------|------------|---------|
| 1 | 知识门户首页 | `index.html` | —（总览）| 检索 Hero、分类导航、热门知识、7 大智能体入口 |
| 2 | 我的工作台 | `workbench.html` | —（总览）| 生命周期看板：草稿/解析中/待审/已发布/退回 |
| 3 | 上传向导 | `upload-wizard.html` | ①②③④⑤ | **5 步对齐招标模块**：采集→加工→入库→标注→审核 |
| 4 | 数据源管理 | `data-source.html` | ① 采集抽取 | DB/API/MQ/爬虫/日志 五种数据源配置（二期规划展示）|
| 5 | 知识详情/切片管理 | `detail.html` | ② 处理加工 | chunk 列表、原文溯源、版本历史 |
| 6 | 审核工作台 | `audit-workbench.html` | ③ 审核 | 待审列表、三级流程、chunk 编辑、AI 报告侧栏 |
| 7 | AI 审核报告 | `ai-audit-report.html` | ③ 审核 | 6 类问题、严重度分级、批量处理 |
| 8 | 知识图谱 | `knowledge-graph.html` | ②关联 / ④入库 | 力导向图、实体详情、社区报告 |
| 9 | 知识标注工作台 | `annotation.html` | ④ 入库 | 人工标注 + AI 标注（关键词/标签/分类/摘要/预期问题）|
| 10 | 检索结果页 | `search.html` | ⑤ 检索 | 结果列表、溯源、高亮、多维过滤 |
| 11 | 检索测试 | `retrieval-test.html` | ⑤ 检索 | topK/阈值调试、召回结果、历史记录 |
| 12 | 统计仪表盘 | `statistics.html` | ⑥ 统计分析 | 知识量、趋势、词云、高频 chunk、质量评估 |
| 13 | 权限管理 | `permission.html` | ⑦ 运营 | 四维授权矩阵、角色/部门/分类/标签/审计 |
| 14 | 版本管理 | `version.html` | ⑦ 运营 | 线上/测试切换、回滚、操作日志 |
| 15 | 系统设置 | `settings.html` | ⑦ 运营 | RAGFlow 嵌入区：知识库/模型/解析/Agent |

### 10.2 侧边栏菜单（按招标 8 大模块分组）

```
🏠 知识门户首页            index.html
📋 我的工作台              workbench.html       [徽章: 12]

📥 知识采集与抽取
  ├─ 📤 手动上传           upload-wizard.html
  └─ 🔌 数据源管理         data-source.html（DB/API/MQ/爬虫/日志，二期）

⚙️ 知识处理与加工
  ├─ 📄 解析与切片         detail.html（RAGFlow DeepDoc）
  └— 🧹 清洗·融合·补全     （二期 B 层，灰色禁用）

✅ 知识审核
  ├─ 📋 人工审核           audit-workbench.html  [徽章: 12]
  └─ 🤖 AI 审核            ai-audit-report.html  [徽章: 38]

🗄️ 知识入库
  ├─ 🕸️ 知识图谱           knowledge-graph.html
  └─ 🏷️ 知识标注           annotation.html

🔍 知识检索
  ├─ 🔍 知识检索           search.html
  └─ 🧪 检索测试           retrieval-test.html

📊 知识统计与分析
  └─ 📊 统计仪表盘         statistics.html

🔐 运营管理
  ├─ 🔐 权限管理           permission.html
  ├─ 📦 版本管理           version.html
  └─ ⚙️ 系统设置           settings.html
```

原型技术：纯 HTML + 内嵌 CSS/JS，零外部依赖，浏览器直接打开预览。最终实现时由 fork 的 RAGFlow Vue 工程承载。

---

## 十一、关键约束应对

| 强约束 | 应对方案 |
|--------|---------|
| 文档级权限 | metadata 预过滤（向量库内部过滤，性能无损）|
| 操作审计 100% | 所有操作经自建后端，`operation_log` 全量记录 |
| 等保三级 | 审计日志 + 权限继承 + 数据分类分级（security_level）+ 操作快照 |
| 检索 ≤1s | metadata 预过滤避免后置过滤、Redis 缓存权限索引、RAGFlow 向量库原生性能 |
| 三级运营机制 | 审核模板三级（企业-区域-集团）+ 权限按部门层级继承 |
| 并发 ≥300 | 模块化单体无分布式开销、Redis 缓存、必要时 RAGFlow 横向扩展 |

---

## 十一·补、RAGFlow API 能力映射（v0.3 修订）

> 基于 `E:\claudecode_workspace\rag\ragApi\docs\http_api_reference.md`（RAGFlow 官方 HTTP API 完整清单，13 大模块、80+ 接口）逐条核对后，发现 RAGFlow 原生能力远强于先前判断。本章节为设计的**真实实现依据**，每个页面/操作均落到具体 API。

### 11.1 RAGFlow API 模块 × 页面 × 原生/自建 对照表

| RAGFlow API 模块 | 关键接口 | 承载页面 | 原生/自建 |
|------------------|---------|---------|----------|
| **DATASET MANAGEMENT** | Create/Update/Delete/List dataset；`chunk_method`(12种)、`parser_config`(auto_keywords/auto_questions/raptor/graphrag/parent_child)；Construct/Get/Delete knowledge graph；Construct RAPTOR | `dataset.html`（**新增**） | ✅ 原生 |
| **FILE MANAGEMENT WITHIN DATASET** | Upload/Download/Update/List/Delete documents；Parse/Stop parsing | `documents.html`（**新增**）+ `upload-wizard.html`（引导式上传） | ✅ 原生 |
| **CHUNK MANAGEMENT** | Add/List/Get/Delete/Update chunk；**Update chunk availability**（批量）；Update or delete metadata（含 metadata_condition）；Retrieve metadata summary | `detail.html` + `annotation.html` | ✅ 原生 |
| **CHUNK MANAGEMENT · Retrieve chunks** | `/api/v1/retrieval` 含 `metadata_condition`/`use_kg`/`toc_enhance`/`keyword`/`highlight`/`rerank_id`/`similarity_threshold`/`vector_similarity_weight`/`top_k`/`cross_languages` | `search.html` + `retrieval-test.html` | ✅ 原生 |
| **CHAT ASSISTANT + SESSION** | 助手 CRUD + 会话 CRUD + Converse + TTS/STT/思维导图/相关问题 | `chat.html`（**新增**） | ✅ 原生 |
| **AGENT MANAGEMENT** | List/Create/Update/Delete agent | `agents.html`（**新增**） | ✅ 原生 |
| **OpenAI-Compatible API** | chat completion / agent completion | `chat.html` + 外部 LLM 网关 | ✅ 原生 |
| **SEARCH APP MANAGEMENT** | Create/List/Get/Update/Delete search app + Search completion | 后续扩展 | ✅ 原生（本期仅预留） |
| **MEMORY MANAGEMENT** | 长期记忆 CRUD + 消息搜索 | 后续扩展 | ✅ 原生（本期仅预留） |
| **System** | Check system health | `settings.html` | ✅ 原生 |
| **FILE MANAGEMENT**（用户文件） | 文件/文件夹 CRUD + Links files to datasets | 后续扩展 | ✅ 原生（本期仅预留） |
| **自建增强（RAGFlow 无对应 API）** | 三级审核流程引擎；四维权限策略模型；AI 审核 6 类检测 LLM 编排；采集 Connector（DB/API/MQ/爬虫/日志）；统计埋点；SSO/组织同步 | 自建后端 | ❌ 自建 |

### 11.2 负偏离矩阵修正（v0.3）

| 负偏离项 | v0.2 判断 | v0.3 修正 | 依据 API |
|---------|----------|----------|---------|
| 知识图谱（Neo4j 强约束）| ⚠️ 评估替代 | ✅ **原生 GraphRAG 已满足，无需 Neo4j** | `parser_config.graphrag` + `Construct knowledge graph` + `Retrieve chunks?use_kg=true` |
| 知识增强 QA / 多跳 | ⚠️ 轻度补充 | ✅ **原生 RAPTOR + QA chunk** | `parser_config.raptor` + `Construct RAPTOR` + `chunk_method:"qa"` |
| 父子分块 / 精准检索 | 未识别 | ✅ **原生 parent_child** | `parser_config.parent_child` |
| 人工标注 | ⚠️ 部分满足 | ✅ **原生** | `Update chunk` 的 `important_keywords`/`questions`/`tag_kwd` |
| AI 标注 | ⚠️ 轻度偏离 | ✅ **原生自动生成** | `parser_config.auto_keywords`/`auto_questions`（解析时生成）|
| 标签 / 分类体系 | ⚠️ 部分支持 | ✅ **原生 tag chunk + 标签集** | `chunk_method:"tag"` + `tag_kb_ids` + `metadata summary` |
| 多模态检索 | ⚠️ 轻度偏离 | ✅ **原生** | `chunk_method:"picture"` + `image_base64` + `Update chunk.image_base64` |
| 检索质量评估 | ⚠️ 基本满足 | ✅ **原生完整** | `Retrieve chunks` 全参数 + `metadata summary` |
| 人工审核流程 | ❌ 完全负偏离 | ⚠️ **流程自建 + 发布原生** | 自建状态机 + `Update chunk availability(available=true)` 控制发布 |
| 细粒度权限 | ❌ 完全负偏离（强约束）| ⚠️ **策略自建 + 过滤原生** | 自建权限模型 → `Update or delete metadata` 写入文档元数据 → `Retrieve chunks?metadata_condition` 预过滤 |
| AI 审核 6 类 | ❌ 完全负偏离 | ❌ 自建（LLM 编排）| 复用 `Retrieve chunks` 检索相似 + LLM 调用 |
| 采集层（DB/API/MQ/爬虫/日志）| ❌ 完全负偏离 | ❌ 自建（二期 A 层）| 收敛到 `Upload documents` API |
| 知识融合 / 补全 | ❌ 完全负偏离 | ❌ 自建（二期 B 层）| LLM + `Update chunk` |
| 审批流程定制 | ❌ 完全负偏离 | ❌ 自建 | 模板配置（不引入 Flowable） |
| 知识分析 | ❌ 完全负偏离 | 🟡 自建（统计为主）| 埋点 + 聚合 |
| 版本管理 | ⚠️ 部分支持 | ⚠️ 自建（多 dataset 指针）| 利用 dataset 隔离 |

**结论**：v0.2 的 16 项负偏离中，**7 项上调为"原生满足"**，自建工作量大幅收敛。核心自建只剩：**审核流程引擎、权限策略模型、AI 审核 LLM 编排、采集层（二期）、统计埋点**。

### 11.3 上传向导 5 步 × RAGFlow API 落点

| 步骤 | 操作 | RAGFlow API |
|------|------|------------|
| ① 知识采集与抽取 | 上传文件到指定 dataset | `POST /api/v1/datasets/{dataset_id}/documents`（Upload documents）|
| ② 知识处理与加工 | 触发解析（chunk_method 与 parser_config 在 dataset 创建时配置）| `POST /api/v1/datasets/{dataset_id}/chunks`（Parse documents）；DeepDOC 解析、auto_keywords/auto_questions 自动生成 |
| ③ 知识入库 | 构建知识图谱 / RAPTOR / 配置父子分块 | dataset 创建时 `parser_config.graphrag/raptor/parent_child=true`；`Construct knowledge graph`；`Construct RAPTOR` |
| ④ 知识标注 | 人工补充关键词/问题/标签 | `PATCH .../chunks/{chunk_id}`（Update chunk 的 `important_keywords`/`questions`/`tag_kwd`）；文档级标签用 `Update or delete metadata` |
| ⑤ 知识审核 | 审核通过 → 启用 chunk 参与检索 | 自建审核流程引擎；通过后调 `PATCH .../chunks`（Update chunk availability，`available=true`）|

### 11.4 权限模型 API 闭环（强约束落地的关键）

```
[权限策略配置]（自建 RBAC 网关，permission 页）
       │
       ▼ 写入文档元数据
[Update or delete metadata] POST /api/v1/datasets/{dataset_id}/metadata/update
       │ body: { selector:{document_ids:[...]}, updates:[{key:"dept_ids",value:"101,102"}, {key:"security_level",value:"2"}, ...] }
       ▼
[文档参与检索时]（search 页）
       │
       ▼ 拼装 metadata_condition
[Retrieve chunks] POST /api/v1/retrieval
       │ body: { question, dataset_ids, metadata_condition:{
       │         logic:"and",
       │         conditions:[
       │           {name:"dept_ids", comparison_operator:"contains", value:"当前用户部门"},
       │           {name:"security_level", comparison_operator:"≤", value:"用户最高密级"}
       │         ]
       │       }, top_k, similarity_threshold, use_kg, highlight }
       ▼
[向量库内部预过滤] → 仅返回可见 chunk（满足 ≤1s 性能 + 文档级权限 + 等保三级审计）
```

**关键收益**：权限过滤完全在 RAGFlow 向量库内部完成，无需后置过滤、无性能损耗、无需双写。

### 11.5 审核流程 API 闭环

```
[文档上传+解析] → chunk 默认 available=false（待审）
       │
       ▼ 审核员在审核工作台操作
[自建审核状态机] → 三级模板流转
       │
       ▼ 终审通过
[Update chunk availability] PATCH /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks
       │ body: { chunk_ids:[...], available:true }
       ▼
[chunk 参与检索] → 知识正式发布
```

退回 / 删除则保持 `available=false` 或调 `Delete chunks`。

---

## 十一·补二、跨系统身份与组织同步（v0.4 增补）

> 基于 `E:\claudecode_workspace\rag\ragAgent\api\oauth2对接协议.txt`（海康 OAuth2 服务接口说明书）与 `主平台组织以及人员信息接口.md`（Artemis 资源目录接口）落地。海康平台是身份与组织的唯一来源（Source of Truth），知识中心只保留只读副本，不维护账号、不增改组织。

### 12.1 双系统定位与认证分工

| 系统 | 角色 | 职责 |
|------|------|------|
| **海康平台** | 身份提供方 IdP + 组织数据源 | 账号密码认证、组织/人员/岗位 CRUD、颁发 OAuth2 令牌 |
| **知识中心** | 服务提供方 SP + 只读副本消费者 | 业务逻辑（审核/权限/标注/统计）、本地会话、组织副本同步 |

**两套认证并行，互不混淆**：

| 认证方式 | 用途 | 触发时机 | 凭证生命周期 |
|---------|------|---------|------------|
| **OAuth2 Authorization Code Flow** | 用户单点登录（SSO）| 用户从海康跳转知识中心 | access_token 30 分钟；本地会话 2 小时 |
| **Artemis 签名**（X-CA-Key + X-CA-Signature）| 后端业务调用 | 组织/人员同步定时任务、登录时人员详情查询 | AppKey/AppSecret 长期有效 |

### 12.2 OAuth2 单点登录流程

基于海康 `/artemis/api/application/auth/v2/app/oauth2/*` 三个标准接口实现，知识中心**无登录页**。

```
用户点击海康平台「知识中心」菜单
   │
   ▼ ① 海康 302 跳转 authorize
   https://海康/artemis/.../oauth2/authorize?
     response_type=code&client_id=AppKey
     &redirect_uri=https://knowledge.company.com/sso/callback
     &state=<随机防CSRF>&menu_uri=<目标页>
   │
   ▼ ② 海康校验用户登录态（未登录则跳海康登录页）
   │
   ▼ ③ 用户授权后 302 回调知识中心
   https://knowledge.company.com/sso/callback?code=XXX&state=YYY&menu_uri=/index
   │
   ▼ ④ 后端 /sso/callback 处理
   a. 校验 state（防 CSRF，与发往 Redis 的 state 比对后删除）
   b. POST /oauth2/token  → 用 code 换 access_token（code 一次性，5分钟过期）
   c. POST /oauth2/userinfo → 用 access_token 取 userId
   d. 用 userId 调 Artemis person/advance/personList（Artemis 签名）拿完整人员信息
   e. upsert sys_person + 触发该用户权限索引重算
   f. 签发本地会话（Redis，2 小时 TTL）→ Set-Cookie
   g. 302 重定向到 menu_uri
   │
   ▼ ⑤ 用户进入知识中心，全程无登录页
```

**接口清单**：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/artemis/.../oauth2/authorize` | GET 302 | 获取授权码 code（5 分钟有效）|
| `/artemis/.../oauth2/token` | POST form | code 换 access_token + refresh_token（30 分钟有效）|
| `/artemis/.../oauth2/userinfo` | POST（Header: access_token）| 返回 userId |

### 12.3 登出闭环

海康平台登出时，会以 `redirect_uri?logoutOauth2={access_token}` 回调知识中心：

```
/sso/callback?logoutOauth2=f78d3082...
   │
   ▼ 后端检测到 logoutOauth2 参数
   ① 按 access_token 找到本地会话 → 清除 Redis
   ② 返回成功
   ③ 用户在海康侧看到已登出
```

知识中心顶栏「退出」按钮 → 调 `/sso/logout` → 清本地会话 → 302 跳海康登出 URL（让海康侧也登出）。

### 12.4 组织与人员同步（Artemis 签名）

**同步原则**：单向只读 + 最小字段集 + 三层同步。知识中心**不创建/修改/删除**人员组织（那是海康的事），所有变更回海康操作。

#### 同步策略

| 层级 | 频率 | 接口 | 用途 |
|------|------|------|------|
| **全量同步** | 每周日 02:00 | `/api/resource/v2/person/personList` + `/api/resource/v2/org/advance/orgList` | 纠偏、初始化 |
| **增量同步** ★ | 每小时 | `/api/resource/v1/person/personList/timeRange` + `/api/resource/v1/org/timeRange` | 拉取 1 小时内变更（含删除）|
| **实时兜底** | SSO 登录时 + 关键操作 | `/api/resource/v2/person/advance/personList?personIds=...` | 防"离职人员仍可访问"|

★ **48 小时红线**：增量接口要求时间窗在 1-48 小时内。XXL-Job 监控同步失败，若 `last_sync_time` 距今 > 40 小时，**自动降级为全量同步**。

#### 删除处理

人员/组织接口均返回 `status` 字段：**`status < 0` 代表已被删除**。知识中心对这类记录做**软删除**（保留行，标记 `status=-1`），保证审计可追溯，不物理删除。

#### 字段映射

**人员表 sys_person**（来自 personList）：

| 海康字段 | 本地字段 | 用途 |
|---------|---------|------|
| `personId` | `person_id` | 主键 |
| `personName` | `person_name` | 显示名 |
| `orgIndexCode` | `org_index_code` | 所属部门（外键）|
| `orgPath` / `orgPathName` | `org_path` / `org_path_name` | 部门层级路径 |
| `jobNo` | `job_no` | 工号 |
| `phoneNo` / `email` | `phone` / `email` | 通知 |
| `company` / `employeePost` / `postType` | `company` / `post` / `post_type` | 审批人匹配 |
| `updateTime` | `update_time` | 增量游标 |
| `status` | `status` | <0 软删除 |

**组织表 sys_org**（来自 org/timeRange）：

| 海康字段 | 本地字段 | 用途 |
|---------|---------|------|
| `orgIndexCode` | `org_index_code` | 主键 |
| `orgName` | `org_name` | 名称 |
| `orgPath` | `org_path` | 层级路径 |
| `parentOrgIndexCode` | `parent_org_index_code` | 父节点（建树）|
| `leaf` | `is_leaf` | 叶子标识（权限继承终止）|
| `available` | `available` | 是否有权限操作 |
| `status` | `status` | <0 软删除 |

### 12.5 角色与权限映射（角色本地维护）

**海康人员模型无角色概念**，只有组织/岗位类别（`postType`）。角色是知识中心的业务概念（管理员/集团审核员/区域审核员/企业审核员/编辑员/浏览者），由知识中心本地维护：

```
sys_role_local        角色定义（管理员/审核员/编辑员/浏览者/自定义）
sys_person_role_local 人员-角色映射（person_id ↔ role_id）
```

**初始化建议**：按 `postType` 自动推荐默认角色（如「值长」→ 审核员、「专工」→ 编辑员），管理员在 `permission.html` 微调。

**海康字段到知识中心权限的映射**：

| 海康字段 | 权限用途 |
|---------|---------|
| `orgPath`（如 `@root@集团@华东@某公司@`）| 部门层级 → 可见数据范围（权限继承）|
| `available` | 组织节点是否有权限操作 |
| `postType` | 角色推荐 |
| `status` | 用户有效性（<0 不可登录）|

### 12.6 同步完成后联动：权限索引预计算

增量同步任务尾部触发权限索引重算（这是同步的核心价值——本地有副本，才能离线预计算）：

```
[增量同步完成]
   │
   ▼ 遍历受影响 person_id
[权限索引重算]（per user）
   ① 查 sys_person_role_local → 角色
   ② 查 sys_org 递归 → visible_depts（部门 + 子部门 + 横向授权）
   ③ 角色映射 → visible_categories
   ④ 角色映射 → max_security_level
   ⑤ 写 permission_index 表 + Redis 缓存（5 分钟 TTL）
   │
   ▼ 检索时
   读索引 → 拼装 RAGFlow metadata_condition → 调 Retrieve chunks
   无需实时查海康，满足 ≤1s 性能
```

### 12.7 后端模块改造（integration）

```
integration/
├── oauth/                    # OAuth2 客户端（新增）
│   ├── OAuth2Controller      # /sso/login, /sso/callback, /sso/logout
│   ├── OAuth2Client          # 调海康 authorize/token/userinfo
│   ├── StateCache            # state 生成+校验（Redis，5min TTL）
│   └── SessionManager        # 本地会话签发（Redis，2h TTL）
├── artemis/                  # Artemis 签名 SDK（新增）
│   ├── ArtemisSigner         # X-CA-Signature 计算
│   ├── ArtemisConfig         # host/AppKey/AppSecret（配置中心注入）
│   └── ArtemisHttpClient     # 通用 HTTP 调用封装
├── org-sync/                 # 组织同步（增强）
│   ├── FullSyncJob           # 全量同步（XXL-Job 周日 02:00）
│   ├── IncrementalSyncJob    # 增量同步（XXL-Job 每小时，<48h 红线）
│   ├── PersonFallback        # 登录时单点查询兜底
│   └── PermissionIndexRebuild # 权限索引重算
```

### 12.8 前端改造

- **无登录页**：访问 `/login` 或 401 → 302 跳 `/sso/login`
- **顶栏用户信息**：从本地会话取 `person_name` + `org_path_name` + 本地角色
- **退出按钮**：调 `/sso/logout`，不操作前端状态
- **菜单来源**：海康侧配置「知识中心」一级菜单指向 `/sso/login?menu_uri=/index`

### 12.9 安全要点

| 风险 | 对策 |
|------|------|
| CSRF（伪造回调）| `state` 生成存 Redis，回调时比对后立即删除 |
| code 被劫持 | `redirect_uri` 精确匹配注册值；code 一次性使用，5 分钟过期 |
| 离职人员本地会话未失效 | 登录时实时调 personList 校验 `status`；本地会话 TTL ≤ 2h；每小时同步自动软删除 |
| access_token 泄露 | 仅后端持有，不下发前端，用完即弃 |
| AppSecret 泄露 | 存配置中心/Vault，不进代码仓库 |
| 同步延迟导致权限误判 | 增量每小时 + SSO 登录时实时校验兜底 |

### 12.10 对接清单（前置依赖）

| # | 事项 | 责任方 | 阻塞性 |
|---|------|--------|--------|
| 1 | 海康 API 网关注册知识中心应用，获取 `AppKey` / `AppSecret` | 业务方 + 海康运维 | ⭐⭐⭐ |
| 2 | 注册 OAuth2 `redirect_uri`（如 `https://knowledge.company.com/sso/callback`）| 海康运维 | ⭐⭐⭐ |
| 3 | 海康平台配置「知识中心」一级菜单（指向 `/sso/login?menu_uri=/index`）| 海康运维 | ⭐⭐⭐ |
| 4 | 知识中心后端实现 OAuth2 Client + Artemis 签名 SDK | 开发（我）| — |
| 5 | 配置 AppKey/AppSecret 到配置中心 | 运维 | ⭐⭐ |
| 6 | 确认 `sysFlag`（附录 A.77）能否用于"启用/禁用"判断（可选优化）| 海康接口支持 | ⭐ |

---

## 十二、技术栈

| 层 | 技术 |
|----|------|
| 前端 | **fork RAGFlow Vue 前端**（Vue3 + TypeScript + Vite + Element Plus），扩展自建模块路由，统一布局与主题 |
| 后端 | Spring Boot 2.7.18 + JDK 11 + MyBatis-Plus + Spring Security |
| 数据库 | PostgreSQL 11 |
| 缓存 | Redis 6 |
| 调度 | XXL-Job |
| RAGFlow | 原样部署，通过 RESTful API 集成 |
| LLM | 抽象 Gateway 接口，暂不锁定（候选：公司 LLM 网关 / RAGFlow 已配模型）|

---

## 十三、风险与待确认项

| # | 风险/待确认 | 影响 | 缓解 |
|---|-----------|------|------|
| 1 | Neo4j 强约束是否可被 RAGFlow 内置图替代 | 图谱方案 | 原型阶段先用内置图，最终用户确认后再决策 |
| 2 | 海康 OAuth2 SSO（方案已明确，见 §12）| 集成层 | 待海康运维注册 AppKey/AppSecret + redirect_uri |
| 3 | 海康 Artemis 组织/人员同步接口（方案已明确，见 §12.4）| 权限索引预计算 | 同上，复用 AppKey/AppSecret 调 timeRange 接口 |
| 4 | LLM 选型未定 | AI 审核/统计成本 | 抽象接口，后期切换 |
| 5 | 中文电力行业 Embedding/Rerank 模型选型 | 向量化质量 | 由 RAGFlow 模型工厂配置，本期不锁定 |
| 6 | 信创适配（麒麟/鲲鹏/达梦）| 部署环境 | 需专项验证 RAGFlow + Spring Boot 兼容性 |
| 7 | 性能压测（≤1s / ≥300 并发）| 上线指标 | 需搭建压测环境 |
| 8 | 三级审核中「集团/区域/企业」与内部平台组织架构的映射 | 审核流程配置 | 需业务方确认组织代码映射 |

---

## 十四、工作量预估（粗估）

| 模块 | 工作量占比 | 说明 |
|------|-----------|------|
| integration（SSO + RAGFlow Client + 组织同步）| 15% | 基础对接 |
| permission（四维 + metadata + 审计）| 25% | 强约束最多 |
| audit（模板 + 状态机 + 工作台 + AI 审核）| 30% | 业务最复杂 |
| version | 5% | 利用 dataset 隔离，较轻 |
| stats | 10% | 埋点 + 聚合 + 图表 |
| portal-api + 前端门户 | 15% | 10 个页面 |

> 实际工期取决于团队规模与并行度，本文档不做时间预估。

---

## 十五、下一步

1. **本文档 + 原型图** → 提交最终用户确认
2. 最终用户确认后 → 启动 A 层（数据接入）与 B 层（知识加工增强）的子项目设计
3. Neo4j 专项评估立项
4. 海康 API 网关注册应用、获取 AppKey/AppSecret、注册 redirect_uri（见 §12.10 对接清单）
