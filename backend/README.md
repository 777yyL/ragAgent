# 发电知识中心 · 知识管理平台后端

基于 RAGFlow HTTP API + 海康 OAuth2/Artemis 接口构建的企业级知识管理平台。

## 技术栈

- Spring Boot 2.7.18 / JDK 11
- MyBatis-Plus 3.5.5
- PostgreSQL 11 / Redis 6 / Flyway
- OkHttp 4.12（RAGFlow / 海康 API 客户端）
- XXL-Job 2.4.1（定时任务）

## 快速开始

```bash
# 1. 配置 PG / Redis / RAGFlow 地址（编辑 src/main/resources/application.yml 或通过环境变量）
# 2. 编译
mvn clean compile

# 3. 运行（Flyway 自动初始化 DB schema）
mvn spring-boot:run

# 4. 健康检查
curl http://localhost:8081/health
```

## 模块

- `integration/` 对接层：RAGFlow API Client + 海康 Artemis/OAuth2 Client
- `org/` 组织副本：人员/组织同步 + 权限索引预计算
- `permission/` 四维权限策略 + 操作审计
- `dataset/` `document/` `chunk/` 知识核心（包装 RAGFlow）
- `search/` 检索（metadata_condition 权限预过滤）
- `audit/` 审核状态机 + AI 审核
- `agent/` 智能体 + 对话

详见 `docs/specs/2026-06-15-cd-audit-ops-design.md`。
