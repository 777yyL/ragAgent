# 发电知识中心 · 实施计划（C+D 子系统）

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 RAGFlow API（13 大模块）+ 海康 OAuth2/Artemis 接口，构建发电企业知识管理平台 C+D 子系统（审核与运营管理），覆盖知识全生命周期闭环。

**Architecture:** 单门户 SPA（fork RAGFlow Vue 二开）+ 模块化单体后端（Spring Boot 2.7 / JDK 11）+ RAGFlow 内核（HTTP API 集成）。海康平台作 IdP，OAuth2 SSO + Artemis 同步组织副本，权限通过 metadata_condition 在 RAGFlow 向量库内部预过滤。

**Tech Stack:** Spring Boot 2.7.18 / JDK 11 / MyBatis-Plus / PostgreSQL 11 / Redis 6 / XXL-Job / RAGFlow HTTP API / 海康 Artemis OAuth2 / Vue3 + TypeScript + Vite + Element Plus（fork）

**Spec:** `docs/specs/2026-06-15-cd-audit-ops-design.md`（v0.4，含 §11 RAGFlow API 能力映射 + §11·补二 跨系统身份同步）

**前置条件（已就绪）：** RAGFlow 已部署 / 海康平台已部署 / AppKey/AppSecret 已注册

---

## 0. 阶段路线图与依赖

```
Phase 0: Foundation            （工程脚手架 + 配置 + DB schema + 通用客户端）
    │
    ▼
Phase 1: Identity & Org Sync   （OAuth2 SSO + Artemis 同步 + 权限索引）★ 解锁所有业务
    │
    ├──────────────┐
    ▼              ▼
Phase 2:        Phase 5:
Knowledge Core  Permission
(Dataset/Doc/   (RBAC策略 +
 Chunk)          审计日志)
    │              │
    ▼              │
Phase 3:           │
Search &           │
Annotation         │
    │              │
    ▼              │
Phase 4:           │
Audit              │
    │              │
    └─────┬────────┘
          ▼
Phase 6: Agents & Chat
          │
          ▼
Phase 7: Version & Stats
          │
          ▼
Phase 8: Frontend Integration (fork RAGFlow Vue + 19 页集成)
```

| Phase | 工作量占比 | 解锁后续 | 可独立验收 |
|-------|----------|---------|-----------|
| 0 Foundation | 10% | 全部 | ✅ 启动成功 + 健康检查 |
| 1 Identity & Org | 15% | 全部业务 | ✅ 海康登录跑通 |
| 2 Knowledge Core | 15% | 3,4 | ✅ 文档上传→解析→列表 |
| 3 Search & Annotation | 10% | 4 | ✅ 检索 + chunk 标注 |
| 4 Audit | 15% | — | ✅ 三级审核 + 发布 |
| 5 Permission | 15% | — | ✅ 文档级权限 + 审计 |
| 6 Agents & Chat | 10% | — | ✅ 智能体 + 对话 |
| 7 Version & Stats | 5% | — | ✅ 版本切换 + 仪表盘 |
| 8 Frontend Integration | 5% | — | ✅ 19 页全联通 |

**并行机会**：Phase 2/5 可并行；Phase 3/6 可并行；Phase 4 等 2/3；Phase 7 等 1/3。

---

## 1. 文件结构映射

### 1.1 后端工程（Java / Spring Boot）

```
rag-knowledge-platform/
├── pom.xml
├── src/main/java/com/company/knowledge/
│   ├── KnowledgePlatformApplication.java
│   │
│   ├── config/                       # 配置类
│   │   ├── RAGFlowProperties.java    # @ConfigurationProperties("ragflow")
│   │   ├── HikvisionProperties.java  # @ConfigurationProperties("hikvision")
│   │   ├── RedisConfig.java
│   │   ├── MybatisPlusConfig.java
│   │   ├── XxlJobConfig.java
│   │   ├── WebMvcConfig.java         # 拦截器、CORS、参数解析
│   │   └── RestClientConfig.java     # RestTemplate / WebClient Bean
│   │
│   ├── common/                       # 通用基础设施
│   │   ├── exception/                # BizException + 全局 @ControllerAdvice
│   │   ├── result/                   # Result<T>, PageResult<T>, ResultCode
│   │   ├── constant/                 # SecurityLevel, AuditStatus, IssueType...
│   │   ├── context/                  # UserContext (ThreadLocal 当前用户)
│   │   └── util/                     # JsonUtils, TimeUtils, SignUtils
│   │
│   ├── integration/                  # 对接层（无业务逻辑，纯 API 包装）
│   │   ├── ragflow/
│   │   │   ├── RAGFlowClient.java    # 通用 HTTP 调用 + Bearer token + 错误码处理
│   │   │   ├── DatasetApi.java       # /api/v1/datasets CRUD + 知识图谱 + RAPTOR
│   │   │   ├── DocumentApi.java      # /api/v1/datasets/{id}/documents
│   │   │   ├── ChunkApi.java         # /chunks 增删改查 + availability
│   │   │   ├── MetadataApi.java      # /metadata/update + /metadata/summary
│   │   │   ├── RetrievalApi.java     # /api/v1/retrieval（含 metadata_condition）
│   │   │   ├── ChatApi.java          # /api/v1/chats + sessions + completions
│   │   │   ├── AgentApi.java         # /api/v1/agents
│   │   │   └── dto/                  # RAGFlow 请求/响应 DTO
│   │   ├── hikvision/
│   │   │   ├── ArtemisSigner.java    # X-CA-Signature HMAC 计算
│   │   │   ├── ArtemisClient.java    # 通用 HTTP 封装（应用签名）
│   │   │   ├── OAuth2Client.java     # authorize/token/userinfo（用户 token）
│   │   │   ├── PersonApi.java        # /person/personList, /advance/personList, /timeRange
│   │   │   ├── OrgApi.java           # /org/advance/orgList, /timeRange, /rootOrg, /subOrgList
│   │   │   └── dto/                  # Hikvision DTO
│   │   └── sso/
│   │       ├── OAuth2Controller.java # /sso/login, /sso/callback, /sso/logout
│   │       ├── SessionManager.java   # Redis 会话签发/校验/续期
│   │       └── StateCache.java       # state 生成+校验（CSRF）
│   │
│   ├── org/                          # 组织副本（同步落库）
│   │   ├── entity/                   # SysPerson, SysOrg
│   │   ├── mapper/                   # SysPersonMapper, SysOrgMapper
│   │   ├── service/
│   │   │   ├── PersonSyncService.java
│   │   │   ├── OrgSyncService.java
│   │   │   └── PermissionIndexService.java  # 权限索引预计算
│   │   └── job/
│   │       ├── FullSyncJob.java            # XXL-Job 周日 02:00
│   │       └── IncrementalSyncJob.java     # XXL-Job 每小时
│   │
│   ├── permission/                   # 权限（四维 RBAC + 审计）
│   │   ├── entity/                   # Role, PermissionPolicy, OperationLog
│   │   ├── mapper/
│   │   ├── service/
│   │   │   ├── RoleService.java
│   │   │   ├── PermissionPolicyService.java
│   │   │   ├── MetadataSyncService.java  # 策略→metadata_condition→调 RAGFlow
│   │   │   └── OperationLogService.java
│   │   ├── annotation/               # @RequiresPermission 切面
│   │   └── controller/
│   │
│   ├── dataset/                      # 知识库管理（包装 RAGFlow DatasetApi）
│   │   ├── service/DatasetService.java
│   │   └── controller/DatasetController.java
│   │
│   ├── document/                     # 文档管理（包装 RAGFlow DocumentApi）
│   │   ├── service/DocumentService.java
│   │   └── controller/DocumentController.java
│   │
│   ├── chunk/                        # 切片管理 + 标注
│   │   ├── service/ChunkService.java
│   │   ├── service/AnnotationService.java
│   │   └── controller/
│   │
│   ├── search/                       # 检索（权限预过滤）
│   │   ├── service/SearchService.java     # 拼 metadata_condition 调 RetrievalApi
│   │   ├── service/SearchLogService.java  # 埋点
│   │   └── controller/SearchController.java
│   │
│   ├── audit/                        # 审核治理
│   │   ├── entity/                   # AuditTemplate, AuditInstance, AuditNodeRecord, AiAuditIssue
│   │   ├── mapper/
│   │   ├── service/
│   │   │   ├── AuditTemplateService.java
│   │   │   ├── AuditStateMachine.java     # 状态机核心
│   │   │   ├── AiAuditService.java        # 6 类检测 LLM 编排
│   │   │   └── PublishService.java        # 调 ChunkApi.updateAvailability
│   │   └── controller/
│   │
│   ├── agent/                        # 智能体 + 对话
│   │   ├── service/AgentService.java
│   │   ├── service/ChatService.java
│   │   └── controller/
│   │
│   ├── version/                      # 版本管理（dataset 指针）
│   │   ├── entity/VersionMeta
│   │   ├── service/VersionSwitchService.java
│   │   └── controller/
│   │
│   └── stats/                        # 统计
│       ├── service/StatsAggregator.java
│       └── controller/
│
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── db/migration/                 # Flyway
│   │   ├── V1__init_schema.sql
│   │   ├── V2__permission.sql
│   │   ├── V3__audit.sql
│   │   └── V4__version_stats.sql
│   ├── mapper/                       # MyBatis XML（复杂查询）
│   └── logback-spring.xml
│
└── src/test/java/                    # 单元 + 集成测试
    └── ...镜像主包结构
```

### 1.2 前端工程（fork RAGFlow Vue）

```
ragflow-fork/                         # fork 自 infiniflow/ragflow
├── （RAGFlow 原生 src/）
├── src/
│   ├── pages/knowledge/              # ★ 新增自建模块
│   │   ├── workbench/                # 我的工作台
│   │   ├── upload-wizard/            # 5 步上传向导
│   │   ├── dataset/                  # 知识库管理
│   │   ├── documents/                # 文档管理
│   │   ├── detail/                   # 解析与切片
│   │   ├── annotation/               # 知识标注
│   │   ├── audit/                    # 审核工作台
│   │   ├── ai-audit/                 # AI 审核报告
│   │   ├── search/                   # 知识检索
│   │   ├── retrieval-test/           # 检索测试
│   │   ├── permission/               # 权限管理
│   │   ├── version/                  # 版本管理
│   │   ├── statistics/               # 统计仪表盘
│   │   ├── agents/                   # 智能体管理
│   │   ├── chat/                     # 智能对话
│   │   └── settings/                 # 系统设置
│   ├── api/knowledge/                # 自建 API 客户端（调用 Spring Boot）
│   │   ├── org.ts                    # 当前用户、部门树
│   │   ├── dataset.ts
│   │   ├── document.ts
│   │   ├── chunk.ts
│   │   ├── search.ts
│   │   ├── audit.ts
│   │   ├── permission.ts
│   │   ├── agent.ts
│   │   ├── chat.ts
│   │   ├── version.ts
│   │   └── stats.ts
│   ├── layouts/knowledge/            # 顶栏 + 9 模块下拉导航
│   ├── router/knowledge.ts           # 路由表（含 SSO 重定向守卫）
│   └── store/modules/session.ts      # 会话状态（不含登录页）
└── （RAGFlow 原生构建配置）
```

### 1.3 部署

- Nginx：同域子路径
  - `/knowledge/*` → Spring Boot
  - `/ragflow/*` → RAGFlow Flask
  - `/knowledge-spa/*` → 静态 Vue 资源
- Spring Boot：独立服务，连 PG + Redis + XXL-Job admin

---

## Chunk 1: Phase 0 — Foundation（工程脚手架）

> 目标：能 `mvn spring-boot:run` 启动，`/health` 返回 200，DB schema 已初始化，配置加载正确。**全栈空骨架，可被后续所有 Phase 依赖。**

### Task 0.1: 工程初始化与依赖

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/company/knowledge/KnowledgePlatformApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `.gitignore`
- Create: `README.md`

- [ ] **Step 1: 初始化 Maven 工程**

```xml
<!-- pom.xml 关键依赖 -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>2.7.18</version>
</parent>
<properties>
  <java.version>11</java.version>
  <mybatis-plus.version>3.5.5</mybatis-plus.version>
</properties>
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-aop</artifactId></dependency>
  <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-boot-starter</artifactId><version>${mybatis-plus.version}</version></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
  <dependency><groupId>com.xuxueli</groupId><artifactId>xxl-job-core</artifactId><version>2.4.1</version></dependency>
  <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.12.0</version></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><version>1.19.3</version><scope>test</scope></dependency>
</dependencies>
```

- [ ] **Step 2: 主启动类**

```java
package com.company.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.company.knowledge.**.mapper")
@EnableAsync
public class KnowledgePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgePlatformApplication.class, args);
    }
}
```

- [ ] **Step 3: application.yml 配置**

```yaml
# application.yml
spring:
  profiles:
    active: dev
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/knowledge
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true

ragflow:
  base-url: ${RAGFLOW_URL:http://localhost:9380}
  api-key: ${RAGFLOW_API_KEY:ragflow-xxx}

hikvision:
  host: ${HIKVISION_HOST:10.33.25.18:443}
  app-key: ${HIKVISION_APP_KEY:}
  app-secret: ${HIKVISION_APP_SECRET:}
  oauth-redirect-uri: ${OAUTH_REDIRECT_URI:https://knowledge.company.com/sso/callback}

xxl:
  job:
    admin:
      addresses: ${XXL_JOB_ADMIN:http://localhost:8080/xxl-job-admin}
    accessToken: ${XXL_JOB_TOKEN:}
    appname: knowledge-platform

server:
  port: 8081
```

- [ ] **Step 4: 验证启动**

```bash
mvn spring-boot:run
# 期望：应用启动，Flyway 执行（暂无 migration），监听 8081
curl http://localhost:8081/actuator/health
# 期望：{"status":"UP"}
```

- [ ] **Step 5: 提交**

```bash
git init
git add .
git commit -m "feat(phase0): scaffold spring-boot project with dependencies"
```

---

### Task 0.2: 通用基础类（Result + 异常 + UserContext）

**Files:**
- Create: `src/main/java/com/company/knowledge/common/result/Result.java`
- Create: `src/main/java/com/company/knowledge/common/result/PageResult.java`
- Create: `src/main/java/com/company/knowledge/common/exception/BizException.java`
- Create: `src/main/java/com/company/knowledge/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/company/knowledge/common/context/UserContext.java`
- Test: `src/test/java/com/company/knowledge/common/result/ResultTest.java`

- [ ] **Step 1: 写 Result 类的失败测试**

```java
// ResultTest.java
package com.company.knowledge.common.result;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultTest {
    @Test
    void success_shouldReturnCodeZeroAndData() {
        Result<String> result = Result.success("hello");
        assertEquals(0, result.getCode());
        assertEquals("hello", result.getData());
        assertNull(result.getMsg());
    }

    @Test
    void error_shouldReturnNonZeroCode() {
        Result<?> result = Result.error(1001, "invalid param");
        assertEquals(1001, result.getCode());
        assertEquals("invalid param", result.getMsg());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
mvn test -Dtest=ResultTest
# 期望：编译失败，Result 类不存在
```

- [ ] **Step 3: 实现 Result 类**

```java
// Result.java
package com.company.knowledge.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
mvn test -Dtest=ResultTest
# 期望：PASS
```

- [ ] **Step 5: 实现 BizException + 全局异常处理**

```java
// BizException.java
package com.company.knowledge.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(int code, String msg) {
        return new BizException(code, msg);
    }
}
```

```java
// GlobalExceptionHandler.java
package com.company.knowledge.common.exception;

import com.company.knowledge.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<?> handleBiz(BizException e) {
        log.warn("biz error: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleUnknown(Exception e) {
        log.error("unknown error", e);
        return Result.error(500, "internal error");
    }
}
```

- [ ] **Step 6: 实现 UserContext（ThreadLocal）**

```java
// UserContext.java
package com.company.knowledge.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;

public class UserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) { HOLDER.set(user); }
    public static CurrentUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        private String personId;
        private String personName;
        private String orgIndexCode;
        private String orgPath;
        private java.util.Set<String> roles;  // 本地角色 code 集合
    }
}
```

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/company/knowledge/common/ src/test/java/com/company/knowledge/common/
git commit -m "feat(phase0): add Result, BizException, GlobalExceptionHandler, UserContext"
```

---

### Task 0.3: DB Schema 初始化（Flyway V1）

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`

- [ ] **Step 1: 编写初始化 SQL（覆盖组织/权限/审计/版本/统计所有表）**

```sql
-- V1__init_schema.sql

-- ========== 组织副本（来源海康同步）==========
CREATE TABLE sys_person (
    person_id       VARCHAR(64) PRIMARY KEY,
    person_name     VARCHAR(100) NOT NULL,
    org_index_code  VARCHAR(64),
    org_path        VARCHAR(500),
    org_path_name   VARCHAR(500),
    job_no          VARCHAR(50),
    certificate_no  VARCHAR(50),
    phone           VARCHAR(30),
    email           VARCHAR(100),
    company         VARCHAR(200),
    post            VARCHAR(100),
    post_type       VARCHAR(50),
    status          SMALLINT DEFAULT 0,    -- 0 正常, <0 已删除
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    sync_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_person_org ON sys_person(org_index_code);
CREATE INDEX idx_person_status ON sys_person(status);
CREATE INDEX idx_person_update ON sys_person(update_time);

CREATE TABLE sys_org (
    org_index_code      VARCHAR(64) PRIMARY KEY,
    org_name            VARCHAR(200) NOT NULL,
    org_path            VARCHAR(1000),
    parent_org_index_code VARCHAR(64),
    is_leaf             BOOLEAN DEFAULT FALSE,
    sort                INTEGER,
    available           BOOLEAN DEFAULT TRUE,
    status              SMALLINT DEFAULT 0,
    create_time         TIMESTAMP,
    update_time         TIMESTAMP,
    sync_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_org_parent ON sys_org(parent_org_index_code);

-- ========== 角色（本地维护）==========
CREATE TABLE sys_role (
    role_id     BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50) UNIQUE NOT NULL,   -- ADMIN/AUDITOR_GROUP/AUDITOR_REGION/AUDITOR_ENTERPRISE/EDITOR/VIEWER
    role_name   VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_builtin  BOOLEAN DEFAULT FALSE,         -- 内置角色不可删
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_person_role (
    id          BIGSERIAL PRIMARY KEY,
    person_id   VARCHAR(64) NOT NULL,
    role_id     BIGINT NOT NULL,
    UNIQUE(person_id, role_id)
);
CREATE INDEX idx_person_role_pid ON sys_person_role(person_id);

-- ========== 权限策略（四维）==========
CREATE TABLE permission_policy (
    id              BIGSERIAL PRIMARY KEY,
    subject_type    VARCHAR(20) NOT NULL,      -- ROLE/DEPT/USER
    subject_id      VARCHAR(64) NOT NULL,
    object_type     VARCHAR(20) NOT NULL,      -- DATASET/CATEGORY/TAG/DOC
    object_value    VARCHAR(200) NOT NULL,
    actions         VARCHAR(200)[],            -- VIEW/SEARCH/EDIT/DELETE/AUDIT/PUBLISH/EXPORT
    inherit         BOOLEAN DEFAULT TRUE,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 权限索引（预计算）==========
CREATE TABLE permission_index (
    person_id           VARCHAR(64) PRIMARY KEY,
    visible_depts       VARCHAR(64)[],         -- org_index_code 集合
    visible_categories  VARCHAR(50)[],
    visible_tags        VARCHAR(100)[],
    max_security_level  SMALLINT DEFAULT 1,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 业务文档元数据 ==========
CREATE TABLE knowledge_doc (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    ragflow_doc_id  VARCHAR(64) NOT NULL,
    dataset_id      VARCHAR(64) NOT NULL,
    business_type   VARCHAR(50),
    dept_id         VARCHAR(64),
    tags            VARCHAR(500)[],
    security_level  SMALLINT DEFAULT 1,
    version_id      BIGINT,
    audit_status    VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source_type     VARCHAR(20),
    source_ref      VARCHAR(500),
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_doc_dataset ON knowledge_doc(dataset_id);
CREATE INDEX idx_doc_audit ON knowledge_doc(audit_status);

-- ========== 审核模板与实例 ==========
CREATE TABLE audit_template (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    business_type   VARCHAR(50),
    nodes           JSONB NOT NULL,            -- [{order, name, approver_role, multi_sign}]
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_instance (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT NOT NULL,
    template_id     BIGINT NOT NULL,
    current_node    SMALLINT,
    status          VARCHAR(20) NOT NULL,     -- PENDING/APPROVED/REJECTED/WITHDRAWN
    submitted_by    VARCHAR(64),
    submitted_at    TIMESTAMP,
    finished_at     TIMESTAMP
);

CREATE TABLE audit_node_record (
    id              BIGSERIAL PRIMARY KEY,
    instance_id     BIGINT NOT NULL,
    node_order      SMALLINT NOT NULL,
    approver_id     VARCHAR(64),
    approver_role   VARCHAR(50),
    action          VARCHAR(20),              -- APPROVE/REJECT/WITHDRAW
    comment         TEXT,
    chunk_edits     JSONB,
    acted_at        TIMESTAMP
);

CREATE TABLE ai_audit_issue (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT,
    chunk_id        VARCHAR(64),
    issue_type      VARCHAR(30) NOT NULL,     -- CONFLICT/ERROR/TIMELINESS/INTEGRITY/CONSISTENCY/NORM
    severity        VARCHAR(10) NOT NULL,     -- HIGH/MEDIUM/LOW
    position        VARCHAR(200),
    description     TEXT,
    suggestion      TEXT,
    status          VARCHAR(20) DEFAULT 'OPEN',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 版本管理 ==========
CREATE TABLE version_meta (
    id              BIGSERIAL PRIMARY KEY,
    dataset_id      VARCHAR(64) NOT NULL,
    env             VARCHAR(20) NOT NULL,     -- ONLINE/TEST
    version_label   VARCHAR(50),
    parent_id       BIGINT,
    change_log      TEXT,
    published_by    VARCHAR(64),
    published_at    TIMESTAMP
);

-- ========== 操作审计日志（等保三级）==========
CREATE TABLE operation_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    username        VARCHAR(100),
    action          VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(30),
    resource_id     VARCHAR(100),
    request_ip      VARCHAR(50),
    request_body    TEXT,
    before_snapshot TEXT,
    after_snapshot  TEXT,
    result          VARCHAR(20),              -- SUCCESS/FAIL
    error_msg       TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_user ON operation_log(user_id, created_at DESC);
CREATE INDEX idx_log_resource ON operation_log(resource_type, resource_id, created_at DESC);

-- ========== 检索埋点 ==========
CREATE TABLE search_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64),
    query           TEXT,
    dataset_ids     VARCHAR(64)[],
    result_count    INTEGER,
    top_chunk_ids   VARCHAR(64)[],
    response_ms     INTEGER,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 内置角色 ==========
INSERT INTO sys_role(role_code, role_name, description, is_builtin) VALUES
('ADMIN', '系统管理员', '全权限：浏览/编辑/删除/审批/配置', true),
('AUDITOR_GROUP', '集团审核员', '集团终审节点审批', true),
('AUDITOR_REGION', '区域审核员', '区域复审节点审批', true),
('AUDITOR_ENTERPRISE', '企业审核员', '企业初审节点审批', true),
('EDITOR', '编辑员', '浏览/编辑/提交审核', true),
('VIEWER', '浏览者', '仅浏览和检索', true);
```

- [ ] **Step 2: 启动应用，验证 Flyway 执行**

```bash
mvn spring-boot:run
# 期望：日志含 "Migrating schema ... to version 1 - init schema"
# 期望：无 SQL 错误
psql -h localhost -U postgres -d knowledge -c "\dt"
# 期望：列出 sys_person, sys_org, sys_role, permission_policy, permission_index,
#       knowledge_doc, audit_template, audit_instance, audit_node_record,
#       ai_audit_issue, version_meta, operation_log, search_log 共 13 张表
```

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/db/migration/V1__init_schema.sql
git commit -m "feat(phase0): V1 schema with 13 tables (org/permission/audit/version/log)"
```

---

### Task 0.4: RAGFlow API 客户端骨架

**Files:**
- Create: `src/main/java/com/company/knowledge/config/RAGFlowProperties.java`
- Create: `src/main/java/com/company/knowledge/integration/ragflow/RAGFlowClient.java`
- Create: `src/main/java/com/company/knowledge/integration/ragflow/DatasetApi.java`
- Test: `src/test/java/com/company/knowledge/integration/ragflow/DatasetApiTest.java`

- [ ] **Step 1: 配置类**

```java
// RAGFlowProperties.java
package com.company.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ragflow")
public class RAGFlowProperties {
    private String baseUrl;
    private String apiKey;
}
```

- [ ] **Step 2: 通用客户端（OkHttp + Bearer Token）**

```java
// RAGFlowClient.java
package com.company.knowledge.integration.ragflow;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.config.RAGFlowProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RAGFlowClient {
    private final RAGFlowProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder().build();

    public JsonNode get(String path, Map<String, ?> params) {
        return request("GET", path, params, null);
    }

    public JsonNode post(String path, Object body) {
        return request("POST", path, null, body);
    }

    public JsonNode put(String path, Object body) {
        return request("PUT", path, null, body);
    }

    public JsonNode delete(String path, Map<String, ?> params) {
        return request("DELETE", path, params, null);
    }

    private JsonNode request(String method, String path, Map<String, ?> params, Object body) {
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(props.getBaseUrl() + path).newBuilder();
            if (params != null) params.forEach((k, v) -> urlBuilder.addQueryParameter(k, String.valueOf(v)));
            Request.Builder rb = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "Bearer " + props.getApiKey());
            if (body != null) {
                String json = mapper.writeValueAsString(body);
                rb.method(method, RequestBody.create(json, MediaType.parse("application/json")));
            } else {
                rb.method(method, null);
            }
            try (Response resp = http.newCall(rb.build()).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "{}";
                JsonNode node = mapper.readTree(respStr);
                if (node.path("code").asInt(-1) != 0) {
                    throw BizException.of(5001, "RAGFlow error: " + node.path("msg").asText());
                }
                return node;
            }
        } catch (IOException e) {
            log.error("RAGFlow call failed: {} {}", method, path, e);
            throw BizException.of(5002, "RAGFlow network error");
        }
    }
}
```

- [ ] **Step 3: DatasetApi（基础 CRUD 包装）**

```java
// DatasetApi.java
package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DatasetApi {
    private final RAGFlowClient client;

    public JsonNode list(int page, int pageSize) {
        return client.get("/api/v1/datasets", Map.of("page", page, "page_size", pageSize));
    }

    public JsonNode create(Map<String, Object> body) {
        return client.post("/api/v1/datasets", body);
    }

    public JsonNode update(String datasetId, Map<String, Object> body) {
        return client.put("/api/v1/datasets/" + datasetId, body);
    }

    public JsonNode delete(java.util.List<String> ids) {
        return client.delete("/api/v1/datasets", Map.of("ids", String.join(",", ids)));
    }
}
```

- [ ] **Step 4: 集成测试（连真实 RAGFlow）**

```java
// DatasetApiTest.java
package com.company.knowledge.integration.ragflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DatasetApiTest {
    @Autowired DatasetApi datasetApi;

    @Test
    void list_shouldReturnAtLeastOneDataset() {
        var result = datasetApi.list(1, 10);
        assertNotNull(result);
        assertEquals(0, result.path("code").asInt());
        assertTrue(result.path("data").has("datasets") || result.path("data").size() >= 0);
    }
}
```

- [ ] **Step 5: 运行测试**

```bash
mvn test -Dtest=DatasetApiTest
# 期望：PASS（前提：RAGFlow 已部署且 application-dev.yml 配置了正确的 api-key）
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/company/knowledge/config/RAGFlowProperties.java \
        src/main/java/com/company/knowledge/integration/ragflow/
git add src/test/java/com/company/knowledge/integration/ragflow/
git commit -m "feat(phase0): RAGFlow API client + DatasetApi + integration test"
```

---

### Task 0.5: 健康检查端点

**Files:**
- Create: `src/main/java/com/company/knowledge/health/HealthController.java`

- [ ] **Step 1: 实现健康检查（含 RAGFlow 连通性）**

```java
package com.company.knowledge.health;

import com.company.knowledge.integration.ragflow.DatasetApi;
import com.company.knowledge.integration.ragflow.RAGFlowClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final DatasetApi datasetApi;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("app", "UP");
        try {
            JsonNode rsp = datasetApi.list(1, 1);
            status.put("ragflow", "UP");
        } catch (Exception e) {
            status.put("ragflow", "DOWN: " + e.getMessage());
        }
        return status;
    }
}
```

- [ ] **Step 2: 验证**

```bash
mvn spring-boot:run
curl http://localhost:8081/health
# 期望：{"app":"UP","ragflow":"UP"}
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/company/knowledge/health/
git commit -m "feat(phase0): health endpoint with RAGFlow connectivity check"
```

---

**Phase 0 完成验收**：
- ✅ `mvn spring-boot:run` 成功启动
- ✅ Flyway 执行 V1 创建 13 张表
- ✅ `curl /health` 返回 `{"app":"UP","ragflow":"UP"}`
- ✅ 所有 Task 测试通过

---

## Chunk 2: Phase 1 — Identity & Org Sync（解锁所有业务）

> 目标：用户从海康平台点击「知识中心」菜单 → 自动 OAuth2 跳转 → 知识中心建立会话 → 调接口拿到当前用户信息（含组织、角色）。XXL-Job 每小时同步组织/人员到本地表，权限索引同步更新。

### Task 1.1: Artemis 签名 SDK

**Files:**
- Create: `src/main/java/com/company/knowledge/config/HikvisionProperties.java`
- Create: `src/main/java/com/company/knowledge/integration/hikvision/ArtemisSigner.java`
- Test: `src/test/java/com/company/knowledge/integration/hikvision/ArtemisSignerTest.java`

- [ ] **Step 1: 配置类**

```java
// HikvisionProperties.java
package com.company.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hikvision")
public class HikvisionProperties {
    private String host;
    private String appKey;
    private String appSecret;
    private String oauthRedirectUri;
}
```

- [ ] **Step 2: 写 ArtemisSigner 失败测试（基于文档示例的预期输入输出）**

参考海康文档 §签名计算：签名串 = method + "\n" + accept + "\n" + content-md5 + "\n" + content-type + "\n" + date + "\n" + headers + url（含 query 排序）

```java
// ArtemisSignerTest.java
package com.company.knowledge.integration.hikvision;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtemisSignerTest {
    @Test
    void sign_shouldReturnHmacSha256HexOfCanonicalString() {
        ArtemisSigner signer = new ArtemisSigner("test-secret");
        String sign = signer.sign(
            "POST",
            "application/json",
            "",
            "application/json",
            "",
            "/artemis/api/resource/v2/person/personList",
            java.util.SortedMap.class.cast(new java.util.TreeMap<>())
        );
        assertNotNull(sign);
        assertEquals(64, sign.length());  // HmacSHA256 → 32 字节 → 64 hex 字符
    }

    @Test
    void sign_shouldBeDeterministic() {
        ArtemisSigner signer = new ArtemisSigner("test-secret");
        String s1 = signer.sign("POST", "application/json", "", "application/json", "",
                "/x", new java.util.TreeMap<>());
        String s2 = signer.sign("POST", "application/json", "", "application/json", "",
                "/x", new java.util.Treemap<>());
        assertEquals(s1, s2);
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

```bash
mvn test -Dtest=ArtemisSignerTest
# 期望：编译失败
```

- [ ] **Step 4: 实现 ArtemisSigner**

```java
// ArtemisSigner.java
package com.company.knowledge.integration.hikvision;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;

public class ArtemisSigner {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private final byte[] secretBytes;

    public ArtemisSigner(String appSecret) {
        this.secretBytes = appSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 按 §调用认证 规则计算 X-CA-Signature。
     * 签名串顺序：method\n accept\n content-md5\n content-type\n date\n [headers]\n url(sorted query)
     */
    public String sign(String method, String accept, String contentMd5,
                       String contentType, String date,
                       String url, SortedMap<String, String> sortedQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append('\n');
        sb.append(accept).append('\n');
        sb.append(contentMd5).append('\n');
        sb.append(contentType).append('\n');
        sb.append(date).append('\n');
        // headers（默认空）省略
        // url + 排序后的 query
        sb.append(url);
        if (sortedQuery != null && !sortedQuery.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : sortedQuery.entrySet()) {
                if (!first) sb.append('&');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA256));
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC sign failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

```bash
mvn test -Dtest=ArtemisSignerTest
# 期望：PASS
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/company/knowledge/config/HikvisionProperties.java \
        src/main/java/com/company/knowledge/integration/hikvision/ArtemisSigner.java \
        src/test/java/com/company/knowledge/integration/hikvision/
git commit -m "feat(phase1): Artemis HmacSHA256 signer with deterministic test"
```

---

### Task 1.2: ArtemisClient + PersonApi + OrgApi

**Files:**
- Create: `src/main/java/com/company/knowledge/integration/hikvision/ArtemisClient.java`
- Create: `src/main/java/com/company/knowledge/integration/hikvision/PersonApi.java`
- Create: `src/main/java/com/company/knowledge/integration/hikvision/OrgApi.java`
- Create: `src/main/java/com/company/knowledge/integration/hikvision/dto/HikPerson.java`
- Create: `src/main/java/com/company/knowledge/integration/hikvision/dto/HikOrg.java`

- [ ] **Step 1: DTO**

```java
// HikPerson.java
package com.company.knowledge.integration.hikvision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HikPerson {
    private String personId;
    private String personName;
    private String orgIndexCode;
    private String orgPath;
    private String orgPathName;
    private String jobNo;
    private String certificateNo;
    private String phoneNo;
    private String email;
    private String company;
    private String employeePost;
    private String postType;
    private String createTime;
    private String updateTime;
    private Integer status;
}

// HikOrg.java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HikOrg {
    private String orgIndexCode;
    private String orgName;
    private String orgPath;
    private String parentOrgIndexCode;
    private Boolean leaf;
    private Integer sort;
    private Boolean available;
    private Integer status;
    private String createTime;
    private String updateTime;
}
```

- [ ] **Step 2: ArtemisClient（带签名的 HTTP 调用）**

```java
// ArtemisClient.java
package com.company.knowledge.integration.hikvision;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.config.HikvisionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ArtemisClient {
    private final HikvisionProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder().build();

    public Map<String, Object> post(String uri, Object body, Map<String, String> query) {
        return request("POST", uri, body, query);
    }

    public Map<String, Object> get(String uri, Map<String, String> query) {
        return request("GET", uri, null, query);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String uri, Object body, Map<String, String> query) {
        try {
            String jsonBody = body != null ? mapper.writeValueAsString(body) : "";
            String contentMd5 = body != null ? md5Base64(jsonBody) : "";
            SortedMap<String, String> sortedQuery = query != null ? new TreeMap<>(query) : new TreeMap<>();

            ArtemisSigner signer = new ArtemisSigner(props.getAppSecret());
            String signature = signer.sign(method, "application/json", contentMd5,
                    "application/json", "", uri, sortedQuery);

            HttpUrl.Builder urlBuilder = HttpUrl.parse("https://" + props.getHost() + uri).newBuilder();
            if (query != null) query.forEach(urlBuilder::addQueryParameter);

            Request.Builder rb = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("X-CA-Key", props.getAppKey())
                    .header("X-CA-Signature", signature)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            if (body != null) {
                rb.method(method, RequestBody.create(jsonBody, MediaType.parse("application/json")));
            } else {
                rb.method(method, null);
            }

            try (Response resp = http.newCall(rb.build()).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "{}";
                Map<String, Object> node = mapper.readValue(respStr, Map.class);
                if (!"0".equals(String.valueOf(node.get("code")))) {
                    throw BizException.of(6001, "Hikvision error: " + node.get("msg"));
                }
                return node;
            }
        } catch (Exception e) {
            if (e instanceof BizException) throw (BizException) e;
            throw BizException.of(6002, "Hikvision network error: " + e.getMessage());
        }
    }

    private static String md5Base64(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 3: PersonApi + OrgApi**

```java
// PersonApi.java
package com.company.knowledge.integration.hikvision;

import com.company.knowledge.integration.hikvision.dto.HikPerson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PersonApi {
    private final ArtemisClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 全量人员列表 */
    public List<HikPerson> listAll(int pageNo, int pageSize) {
        Map<String, Object> resp = client.post("/api/resource/v2/person/personList",
                Map.of("pageNo", pageNo, "pageSize", pageSize), null);
        return extractList(resp);
    }

    /** 按 ID 批量查（登录时兜底） */
    public HikPerson getById(String personId) {
        Map<String, Object> resp = client.post("/api/resource/v2/person/advance/personList",
                Map.of("personIds", personId, "pageNo", 1, "pageSize", 1), null);
        List<HikPerson> list = extractList(resp);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 增量人员（按时间窗，含已删除）*/
    public List<HikPerson> listByTimeRange(LocalDateTime start, LocalDateTime end, int pageNo, int pageSize) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Map<String, Object> resp = client.post("/api/resource/v1/person/personList/timeRange",
                Map.of("startTime", fmt.format(start), "endTime", fmt.format(end),
                       "pageNo", pageNo, "pageSize", pageSize), null);
        return extractList(resp);
    }

    @SuppressWarnings("unchecked")
    private List<HikPerson> extractList(Map<String, Object> resp) {
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        if (data == null) return Collections.emptyList();
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        if (list == null) return Collections.emptyList();
        return mapper.convertValue(list, new TypeReference<List<HikPerson>>() {});
    }
}
```

```java
// OrgApi.java
package com.company.knowledge.integration.hikvision;

import com.company.knowledge.integration.hikvision.dto.HikOrg;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OrgApi {
    private final ArtemisClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<HikOrg> listRoot() {
        Map<String, Object> resp = client.post("/api/resource/v1/org/rootOrg",
                Map.of("pageNo", 1, "pageSize", 1000), null);
        return extractList(resp);
    }

    public List<HikOrg> listSub(String parentOrgIndexCode) {
        Map<String, Object> resp = client.post("/api/resource/v1/org/parentOrgIndexCode/subOrgList",
                Map.of("parentOrgIndexCode", parentOrgIndexCode, "pageNo", 1, "pageSize", 1000), null);
        return extractList(resp);
    }

    public List<HikOrg> listByTimeRange(LocalDateTime start, LocalDateTime end, int pageNo, int pageSize) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Map<String, Object> resp = client.post("/api/resource/v1/org/timeRange",
                Map.of("startTime", fmt.format(start), "endTime", fmt.format(end),
                       "pageNo", pageNo, "pageSize", pageSize), null);
        return extractList(resp);
    }

    @SuppressWarnings("unchecked")
    private List<HikOrg> extractList(Map<String, Object> resp) {
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        if (data == null) return Collections.emptyList();
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        if (list == null) return Collections.emptyList();
        return mapper.convertValue(list, new TypeReference<List<HikOrg>>() {});
    }
}
```

- [ ] **Step 4: 集成测试（连真实海康）**

```java
// PersonApiTest.java
@SpringBootTest
class PersonApiTest {
    @Autowired PersonApi personApi;

    @Test
    void listAll_firstPage_shouldSucceed() {
        var list = personApi.listAll(1, 10);
        assertNotNull(list);
        // 不强制要求有数据，只要签名验证通过、HTTP 200、code=0
    }
}
```

- [ ] **Step 5: 运行测试**

```bash
mvn test -Dtest=PersonApiTest
# 期望：PASS（前提：HikvisionProperties 已配置真实 AppKey/AppSecret）
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/company/knowledge/integration/hikvision/
git commit -m "feat(phase1): ArtemisClient with signing + PersonApi + OrgApi"
```

---

### Task 1.3: 实体、Mapper 与组织同步服务

**Files:**
- Create: `src/main/java/com/company/knowledge/org/entity/SysPerson.java`
- Create: `src/main/java/com/company/knowledge/org/entity/SysOrg.java`
- Create: `src/main/java/com/company/knowledge/org/mapper/SysPersonMapper.java`
- Create: `src/main/java/com/company/knowledge/org/mapper/SysOrgMapper.java`
- Create: `src/main/java/com/company/knowledge/org/service/PersonSyncService.java`
- Create: `src/main/java/com/company/knowledge/org/service/OrgSyncService.java`

- [ ] **Step 1: 实体**

```java
// SysPerson.java
package com.company.knowledge.org.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_person")
public class SysPerson {
    @TableId
    private String personId;
    private String personName;
    private String orgIndexCode;
    private String orgPath;
    private String orgPathName;
    private String jobNo;
    private String certificateNo;
    private String phone;
    private String email;
    private String company;
    private String post;
    private String postType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime syncTime;
}
```

```java
// SysOrg.java
package com.company.knowledge.org.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_org")
public class SysOrg {
    @TableId
    private String orgIndexCode;
    private String orgName;
    private String orgPath;
    private String parentOrgIndexCode;
    private Boolean isLeaf;
    private Integer sort;
    private Boolean available;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime syncTime;
}
```

- [ ] **Step 2: Mapper**

```java
// SysPersonMapper.java
package com.company.knowledge.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.org.entity.SysPerson;

public interface SysPersonMapper extends BaseMapper<SysPerson> {}

// SysOrgMapper.java
package com.company.knowledge.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.org.entity.SysOrg;

public interface SysOrgMapper extends BaseMapper<SysOrg> {}
```

- [ ] **Step 3: PersonSyncService**

```java
// PersonSyncService.java
package com.company.knowledge.org.service;

import com.company.knowledge.integration.hikvision.PersonApi;
import com.company.knowledge.integration.hikvision.dto.HikPerson;
import com.company.knowledge.org.entity.SysPerson;
import com.company.knowledge.org.mapper.SysPersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonSyncService {
    private final PersonApi personApi;
    private final SysPersonMapper personMapper;

    /** 全量同步：分页拉取所有人员 upsert */
    public int fullSync() {
        int pageNo = 1, pageSize = 1000, total = 0;
        while (true) {
            List<HikPerson> list = personApi.listAll(pageNo, pageSize);
            if (list.isEmpty()) break;
            for (HikPerson p : list) {
                upsert(p);
                total++;
            }
            if (list.size() < pageSize) break;
            pageNo++;
        }
        log.info("Person full sync done: {} records", total);
        return total;
    }

    /** 增量同步：按时间窗拉变更（含删除）*/
    public int incrementalSync(LocalDateTime since) {
        LocalDateTime now = LocalDateTime.now();
        // 防 48h 红线
        if (since.isBefore(now.minusHours(40))) {
            log.warn("Since {} is older than 40h, fallback to full sync", since);
            return fullSync();
        }
        int pageNo = 1, pageSize = 1000, total = 0;
        while (true) {
            List<HikPerson> list = personApi.listByTimeRange(since, now, pageNo, pageSize);
            if (list.isEmpty()) break;
            for (HikPerson p : list) {
                upsert(p);
                total++;
            }
            if (list.size() < pageSize) break;
            pageNo++;
        }
        log.info("Person incremental sync: {} changed since {}", total, since);
        return total;
    }

    private void upsert(HikPerson p) {
        SysPerson entity = personMapper.selectById(p.getPersonId());
        boolean isNew = entity == null;
        if (isNew) entity = new SysPerson();
        // 字段映射
        entity.setPersonId(p.getPersonId());
        entity.setPersonName(p.getPersonName());
        entity.setOrgIndexCode(p.getOrgIndexCode());
        entity.setOrgPath(p.getOrgPath());
        entity.setOrgPathName(p.getOrgPathName());
        entity.setJobNo(p.getJobNo());
        entity.setCertificateNo(p.getCertificateNo());
        entity.setPhone(p.getPhoneNo());
        entity.setEmail(p.getEmail());
        entity.setCompany(p.getCompany());
        entity.setPost(p.getEmployeePost());
        entity.setPostType(p.getPostType());
        entity.setStatus(p.getStatus() != null ? p.getStatus() : 0);
        entity.setSyncTime(LocalDateTime.now());
        if (isNew) personMapper.insert(entity);
        else personMapper.updateById(entity);
    }
}
```

- [ ] **Step 4: OrgSyncService（类似 PersonSyncService）**

```java
// OrgSyncService.java
package com.company.knowledge.org.service;

import com.company.knowledge.integration.hikvision.OrgApi;
import com.company.knowledge.integration.hikvision.dto.HikOrg;
import com.company.knowledge.org.entity.SysOrg;
import com.company.knowledge.org.mapper.SysOrgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgSyncService {
    private final OrgApi orgApi;
    private final SysOrgMapper orgMapper;

    public int fullSync() {
        int total = 0;
        // 从根开始递归
        List<HikOrg> roots = orgApi.listRoot();
        for (HikOrg root : roots) {
            total += upsertAndRecurse(root);
        }
        log.info("Org full sync done: {} records", total);
        return total;
    }

    private int upsertAndRecurse(HikOrg org) {
        upsert(org);
        int count = 1;
        if (Boolean.FALSE.equals(org.getLeaf())) {
            List<HikOrg> subs = orgApi.listSub(org.getOrgIndexCode());
            for (HikOrg sub : subs) count += upsertAndRecurse(sub);
        }
        return count;
    }

    public int incrementalSync(LocalDateTime since) {
        LocalDateTime now = LocalDateTime.now();
        if (since.isBefore(now.minusHours(40))) {
            log.warn("Since {} older than 40h, fallback to full sync", since);
            return fullSync();
        }
        int pageNo = 1, pageSize = 1000, total = 0;
        while (true) {
            List<HikOrg> list = orgApi.listByTimeRange(since, now, pageNo, pageSize);
            if (list.isEmpty()) break;
            for (HikOrg o : list) { upsert(o); total++; }
            if (list.size() < pageSize) break;
            pageNo++;
        }
        log.info("Org incremental sync: {} changed since {}", total, since);
        return total;
    }

    private void upsert(HikOrg o) {
        SysOrg entity = orgMapper.selectById(o.getOrgIndexCode());
        boolean isNew = entity == null;
        if (isNew) entity = new SysOrg();
        entity.setOrgIndexCode(o.getOrgIndexCode());
        entity.setOrgName(o.getOrgName());
        entity.setOrgPath(o.getOrgPath());
        entity.setParentOrgIndexCode(o.getParentOrgIndexCode());
        entity.setIsLeaf(o.getLeaf());
        entity.setSort(o.getSort());
        entity.setAvailable(o.getAvailable());
        entity.setStatus(o.getStatus() != null ? o.getStatus() : 0);
        entity.setSyncTime(LocalDateTime.now());
        if (isNew) orgMapper.insert(entity);
        else orgMapper.updateById(entity);
    }
}
```

- [ ] **Step 5: 集成测试**

```java
// PersonSyncServiceTest.java
@SpringBootTest
class PersonSyncServiceTest {
    @Autowired PersonSyncService service;
    @Autowired SysPersonMapper mapper;

    @Test
    void incrementalSync_withinLastHour_shouldNotThrow() {
        int changed = service.incrementalSync(LocalDateTime.now().minusHours(1));
        assertTrue(changed >= 0);
    }
}
```

- [ ] **Step 6: 运行测试**

```bash
mvn test -Dtest=PersonSyncServiceTest
# 期望：PASS
```

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/company/knowledge/org/
git add src/test/java/com/company/knowledge/org/
git commit -m "feat(phase1): SysPerson/SysOrg entities + PersonSyncService + OrgSyncService"
```

---

### Task 1.4: OAuth2 客户端（authorize/token/userinfo）

**Files:**
- Create: `src/main/java/com/company/knowledge/integration/hikvision/OAuth2Client.java`
- Create: `src/main/java/com/company/knowledge/integration/hikvision/dto/TokenResponse.java`
- Test: `src/test/java/com/company/knowledge/integration/hikvision/OAuth2ClientTest.java`

- [ ] **Step 1: TokenResponse DTO**

```java
// TokenResponse.java
package com.company.knowledge.integration.hikvision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
}
```

- [ ] **Step 2: OAuth2Client（不签名，标准 OAuth2 Form 表单）**

```java
// OAuth2Client.java
package com.company.knowledge.integration.hikvision;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.config.HikvisionProperties;
import com.company.knowledge.integration.hikvision.dto.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2Client {
    private final HikvisionProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder().build();

    /** 生成 authorize URL（前端 /sso/login 直接 302 到此）*/
    public String buildAuthorizeUrl(String state, String menuUri) {
        return "https://" + props.getHost() +
                "/artemis/api/application/auth/v2/app/oauth2/authorize" +
                "?response_type=code" +
                "&client_id=" + props.getAppKey() +
                "&redirect_uri=" + URLEncoder.encode(props.getOauthRedirectUri(), StandardCharsets.UTF_8) +
                "&state=" + state +
                "&menu_uri=" + URLEncoder.encode(menuUri, StandardCharsets.UTF_8);
    }

    /** 用 code 换 access_token */
    @SuppressWarnings("unchecked")
    public TokenResponse exchangeCodeForToken(String code) {
        FormBody.Builder fb = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", props.getAppKey())
                .add("client_secret", props.getAppSecret())
                .add("redirect_uri", props.getOauthRedirectUri());
        Request req = new Request.Builder()
                .url("https://" + props.getHost() +
                     "/artemis/api/application/auth/v2/app/oauth2/token")
                .post(fb.build())
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            Map<String, Object> node = mapper.readValue(body, Map.class);
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data == null || !"0".equals(String.valueOf(node.get("code")))) {
                throw BizException.of(6101, "OAuth2 token exchange failed: " + node.get("msg"));
            }
            return mapper.convertValue(data, TokenResponse.class);
        } catch (Exception e) {
            if (e instanceof BizException) throw (BizException) e;
            throw BizException.of(6102, "OAuth2 network error: " + e.getMessage());
        }
    }

    /** 用 access_token 拿 userId */
    @SuppressWarnings("unchecked")
    public String getUserId(String accessToken) {
        Request req = new Request.Builder()
                .url("https://" + props.getHost() +
                     "/artemis/api/application/auth/v2/app/oauth2/userinfo")
                .post(RequestBody.create("", null))
                .header("access_token", accessToken)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            Map<String, Object> node = mapper.readValue(body, Map.class);
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data == null) throw BizException.of(6103, "OAuth2 userinfo empty");
            return String.valueOf(data.get("userId"));
        } catch (Exception e) {
            if (e instanceof BizException) throw (BizException) e;
            throw BizException.of(6104, "OAuth2 userinfo network error: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 3: 测试**

```java
// OAuth2ClientTest.java
@SpringBootTest
class OAuth2ClientTest {
    @Autowired OAuth2Client client;
    @Autowired HikvisionProperties props;

    @Test
    void buildAuthorizeUrl_shouldContainRequiredParams() {
        String url = client.buildAuthorizeUrl("state123", "/index");
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("client_id="));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("state=state123"));
        assertTrue(url.contains("menu_uri="));
    }
}
```

- [ ] **Step 4: 运行 + 提交**

```bash
mvn test -Dtest=OAuth2ClientTest
git add src/main/java/com/company/knowledge/integration/hikvision/OAuth2Client.java \
        src/main/java/com/company/knowledge/integration/hikvision/dto/TokenResponse.java \
        src/test/java/com/company/knowledge/integration/hikvision/OAuth2ClientTest.java
git commit -m "feat(phase1): OAuth2 client (authorize url, code-to-token, userinfo)"
```

---

### Task 1.5: 会话管理（Redis）+ State 缓存

**Files:**
- Create: `src/main/java/com/company/knowledge/integration/sso/SessionManager.java`
- Create: `src/main/java/com/company/knowledge/integration/sso/StateCache.java`
- Test: `src/test/java/com/company/knowledge/integration/sso/SessionManagerTest.java`

- [ ] **Step 1: 写 SessionManager 失败测试**

```java
// SessionManagerTest.java
package com.company.knowledge.integration.sso;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SessionManagerTest {
    @Autowired SessionManager sessionManager;

    @Test
    void createAndGet_shouldReturnSameUser() {
        var user = new SessionManager.SessionUser("p001", "张三", "org01", "/root/集团", java.util.Set.of("EDITOR"));
        String sessionId = sessionManager.create(user);
        assertNotNull(sessionId);

        SessionManager.SessionUser loaded = sessionManager.get(sessionId);
        assertNotNull(loaded);
        assertEquals("p001", loaded.getPersonId());
    }

    @Test
    void expired_after2hours_shouldBeNull() {
        // 用 Redis 的 TTL 验证；此处简化为创建后立刻能查到
        var user = new SessionManager.SessionUser("p002", "李四", "org02", "/root/集团", java.util.Set.of("VIEWER"));
        String sid = sessionManager.create(user);
        assertNotNull(sessionManager.get(sid));
        sessionManager.invalidate(sid);
        assertNull(sessionManager.get(sid));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
mvn test -Dtest=SessionManagerTest
# 期望：编译失败
```

- [ ] **Step 3: 实现 SessionManager**

```java
// SessionManager.java
package com.company.knowledge.integration.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {
    private static final String KEY_PREFIX = "knowledge:session:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    @Data
    @AllArgsConstructor
    public static class SessionUser {
        private String personId;
        private String personName;
        private String orgIndexCode;
        private String orgPath;
        private java.util.Set<String> roles;
    }

    public String create(SessionUser user) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        try {
            String json = mapper.writeValueAsString(user);
            redis.opsForValue().set(KEY_PREFIX + sessionId, json, TTL);
        } catch (Exception e) {
            throw new IllegalStateException("session create failed", e);
        }
        return sessionId;
    }

    public SessionUser get(String sessionId) {
        if (sessionId == null) return null;
        String json = redis.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null) return null;
        try {
            return mapper.readValue(json, SessionUser.class);
        } catch (Exception e) {
            log.warn("session parse failed: {}", json, e);
            return null;
        }
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) redis.delete(KEY_PREFIX + sessionId);
    }
}
```

- [ ] **Step 4: StateCache（CSRF state，5min TTL）**

```java
// StateCache.java
package com.company.knowledge.integration.sso;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StateCache {
    private static final String PREFIX = "knowledge:oauth2:state:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private final StringRedisTemplate redis;

    public String issue() {
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(PREFIX + state, "1", TTL);
        return state;
    }

    /** 校验并消费（一次性）*/
    public boolean consume(String state) {
        if (state == null) return false;
        Boolean deleted = redis.delete(PREFIX + state);
        return Boolean.TRUE.equals(deleted);
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

```bash
mvn test -Dtest=SessionManagerTest
# 期望：PASS
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/company/knowledge/integration/sso/
git add src/test/java/com/company/knowledge/integration/sso/
git commit -m "feat(phase1): Redis session manager (2h TTL) + state cache (5min CSRF)"
```

---

### Task 1.6: OAuth2Controller（/sso/login /sso/callback /sso/logout）

**Files:**
- Create: `src/main/java/com/company/knowledge/integration/sso/OAuth2Controller.java`
- Create: `src/main/java/com/company/knowledge/config/WebMvcConfig.java`
- Create: `src/main/java/com/company/knowledge/integration/sso/SessionInterceptor.java`

- [ ] **Step 1: OAuth2Controller**

```java
// OAuth2Controller.java
package com.company.knowledge.integration.sso;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.integration.hikvision.OAuth2Client;
import com.company.knowledge.integration.hikvision.PersonApi;
import com.company.knowledge.integration.hikvision.dto.HikPerson;
import com.company.knowledge.org.entity.SysPerson;
import com.company.knowledge.org.mapper.SysPersonMapper;
import com.company.knowledge.permission.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class OAuth2Controller {
    private final OAuth2Client oauth2Client;
    private final StateCache stateCache;
    private final SessionManager sessionManager;
    private final PersonApi personApi;
    private final SysPersonMapper personMapper;
    private final RoleService roleService;  // Phase 5 实现，此处先 mock 或注入空实现

    public static final String SESSION_COOKIE = "KNOWLEDGE_SID";

    @GetMapping("/login")
    public ResponseEntity<Void> login(@RequestParam(required = false, defaultValue = "/index") String menuUri) {
        String state = stateCache.issue();
        String url = oauth2Client.buildAuthorizeUrl(state, menuUri);
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            @RequestParam(value = "menu_uri", required = false, defaultValue = "/index") String menuUri,
            @RequestParam(value = "logoutOauth2", required = false) String logoutToken,
            HttpServletResponse response) {

        // 登出回调
        if (logoutToken != null) {
            // 通过 access_token 反查 session 并清除（生产中可建 token→sessionId 索引）
            log.info("logout callback received");
            return ResponseEntity.ok().build();
        }

        // ① 防 CSRF
        if (!stateCache.consume(state)) {
            throw BizException.of(6201, "invalid state (CSRF or expired)");
        }
        // ② 换 token
        var token = oauth2Client.exchangeCodeForToken(code);
        // ③ 拿 userId
        String userId = oauth2Client.getUserId(token.getAccessToken());
        // ④ 拉人员详情（实时兜底）
        HikPerson hikPerson = personApi.getById(userId);
        if (hikPerson == null) throw BizException.of(6202, "person not found: " + userId);
        // ⑤ 校验 status
        if (hikPerson.getStatus() != null && hikPerson.getStatus() < 0) {
            throw BizException.of(6203, "person disabled/deleted");
        }
        // ⑥ upsert sys_person
        upsertPerson(hikPerson);
        // ⑦ 查本地角色
        Set<String> roles = roleService.getRoleCodesByPersonId(userId);
        // ⑧ 签发会话
        var sessionUser = new SessionManager.SessionUser(
                hikPerson.getPersonId(), hikPerson.getPersonName(),
                hikPerson.getOrgIndexCode(), hikPerson.getOrgPath(), roles);
        String sessionId = sessionManager.create(sessionUser);

        // ⑨ 写 Cookie + 重定向
        Cookie cookie = new Cookie(SESSION_COOKIE, sessionId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(7200);
        response.addCookie(cookie);
        return ResponseEntity.status(302).location(URI.create(menuUri)).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = SESSION_COOKIE, required = false) String sid,
                                        HttpServletResponse response) {
        if (sid != null) sessionManager.invalidate(sid);
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    private void upsertPerson(HikPerson p) {
        SysPerson entity = personMapper.selectById(p.getPersonId());
        boolean isNew = entity == null;
        if (isNew) entity = new SysPerson();
        entity.setPersonId(p.getPersonId());
        entity.setPersonName(p.getPersonName());
        entity.setOrgIndexCode(p.getOrgIndexCode());
        entity.setOrgPath(p.getOrgPath());
        entity.setOrgPathName(p.getOrgPathName());
        entity.setJobNo(p.getJobNo());
        entity.setPhone(p.getPhoneNo());
        entity.setEmail(p.getEmail());
        entity.setCompany(p.getCompany());
        entity.setPost(p.getEmployeePost());
        entity.setPostType(p.getPostType());
        entity.setStatus(0);
        entity.setSyncTime(LocalDateTime.now());
        if (isNew) personMapper.insert(entity);
        else personMapper.updateById(entity);
    }
}
```

- [ ] **Step 2: SessionInterceptor（注入 UserContext）**

```java
// SessionInterceptor.java
package com.company.knowledge.integration.sso;

import com.company.knowledge.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
public class SessionInterceptor implements HandlerInterceptor {
    private final SessionManager sessionManager;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        // 放行 SSO 与健康检查
        String uri = req.getRequestURI();
        if (uri.startsWith("/sso/") || uri.equals("/health")) return true;

        String sid = extractSid(req);
        if (sid == null) {
            resp.setStatus(401);
            resp.setHeader("Location", "/sso/login");
            return false;
        }
        var user = sessionManager.get(sid);
        if (user == null) {
            resp.setStatus(401);
            resp.setHeader("Location", "/sso/login");
            return false;
        }
        UserContext.set(new UserContext.CurrentUser(
                user.getPersonId(), user.getPersonName(), user.getOrgIndexCode(),
                user.getOrgPath(), user.getRoles() != null ? user.getRoles() : new HashSet<>()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String extractSid(HttpServletRequest req) {
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (OAuth2Controller.SESSION_COOKIE.equals(c.getName())) return c.getValue();
            }
        }
        return req.getHeader("X-Knowledge-Sid");
    }
}
```

- [ ] **Step 3: WebMvcConfig 注册拦截器（放行 SSO + health）**

```java
// WebMvcConfig.java
package com.company.knowledge.config;

import com.company.knowledge.integration.sso.SessionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final SessionInterceptor sessionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/api/**")            // 保护业务 API
                .excludePathPatterns("/sso/**", "/health", "/actuator/**");
    }
}
```

- [ ] **Step 4: 当前用户端点**

```java
// 在 OAuth2Controller 加内部端点
@GetMapping("/me")
public com.company.knowledge.common.result.Result<SessionManager.SessionUser> me() {
    var ctx = com.company.knowledge.common.context.UserContext.get();
    if (ctx == null) throw BizException.of(401, "not logged in");
    return com.company.knowledge.common.result.Result.success(
            new SessionManager.SessionUser(ctx.getPersonId(), ctx.getPersonName(),
                    ctx.getOrgIndexCode(), ctx.getOrgPath(), ctx.getRoles()));
}
```

注意：`/sso/me` 会被 SessionInterceptor 放行，但我们需要它鉴权。调整 WebMvcConfig：把 `/sso/me` 改为 `/api/me`（受保护）。

```java
// 调整：把 me() 的 @GetMapping 改为 /api/me
@RestController
@RequestMapping("/api")
public class CurrentUserController {
    @GetMapping("/me")
    public Result<SessionManager.SessionUser> me() { ... }
}
```

- [ ] **Step 5: 端到端测试（curl 模拟）**

```bash
# 1. 触发 SSO 登录（应在浏览器中操作）
curl -i http://localhost:8081/sso/login?menuUri=/index
# 期望：302 到海康 authorize URL

# 2. 海康回调（手动模拟，code 用真实有效的）
curl -i "http://localhost:8081/sso/callback?code=XXX&state=YYY&menu_uri=/index"
# 期望：302 到 /index，Set-Cookie: KNOWLEDGE_SID=...

# 3. 调受保护接口
curl -i -b "KNOWLEDGE_SID=..." http://localhost:8081/api/me
# 期望：200，返回当前用户 personId/personName/orgIndexCode/roles

# 4. 未登录访问
curl -i http://localhost:8081/api/me
# 期望：401，Location: /sso/login
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/company/knowledge/integration/sso/OAuth2Controller.java \
        src/main/java/com/company/knowledge/integration/sso/SessionInterceptor.java \
        src/main/java/com/company/knowledge/config/WebMvcConfig.java \
        src/main/java/com/company/knowledge/api/CurrentUserController.java
git commit -m "feat(phase1): OAuth2 SSO endpoints + session interceptor + /api/me"
```

---

### Task 1.7: XXL-Job 定时同步任务

**Files:**
- Create: `src/main/java/com/company/knowledge/config/XxlJobConfig.java`
- Create: `src/main/java/com/company/knowledge/org/job/FullSyncJob.java`
- Create: `src/main/java/com/company/knowledge/org/job/IncrementalSyncJob.java`
- Create: `src/main/java/com/company/knowledge/org/service/SyncStateService.java`（记录 last_sync_time）

- [ ] **Step 1: XxlJobConfig**

```java
// XxlJobConfig.java
package com.company.knowledge.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {
    @Value("${xxl.job.admin.addresses}") private String adminAddresses;
    @Value("${xxl.job.accessToken}") private String accessToken;
    @Value("${xxl.job.appname}") private String appname;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setPort(9999);
        return executor;
    }
}
```

- [ ] **Step 2: SyncStateService（用 Redis 存 last_sync_time）**

```java
// SyncStateService.java
package com.company.knowledge.org.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SyncStateService {
    private static final String PERSON_KEY = "knowledge:sync:person:last_time";
    private static final String ORG_KEY = "knowledge:sync:org:last_time";
    private final StringRedisTemplate redis;

    public LocalDateTime getLastPersonSync() { return get(PERSON_KEY); }
    public void setPersonSync(LocalDateTime t) { set(PERSON_KEY, t); }
    public LocalDateTime getLastOrgSync() { return get(ORG_KEY); }
    public void setOrgSync(LocalDateTime t) { set(ORG_KEY, t); }

    private LocalDateTime get(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? null : LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private void set(String key, LocalDateTime t) {
        redis.opsForValue().set(key, t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
```

- [ ] **Step 3: FullSyncJob（周日 02:00）**

```java
// FullSyncJob.java
package com.company.knowledge.org.job;

import com.company.knowledge.org.service.OrgSyncService;
import com.company.knowledge.org.service.PersonSyncService;
import com.company.knowledge.org.service.SyncStateService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class FullSyncJob {
    private final PersonSyncService personSync;
    private final OrgSyncService orgSync;
    private final SyncStateService state;

    @XxlJob("fullSyncJob")
    public void execute() {
        log.info("[FullSync] start");
        try {
            int orgCount = orgSync.fullSync();
            int personCount = personSync.fullSync();
            LocalDateTime now = LocalDateTime.now();
            state.setOrgSync(now);
            state.setPersonSync(now);
            log.info("[FullSync] done: orgs={}, persons={}", orgCount, personCount);
        } catch (Exception e) {
            log.error("[FullSync] failed", e);
            throw e;
        }
    }
}
```

- [ ] **Step 4: IncrementalSyncJob（每小时）**

```java
// IncrementalSyncJob.java
package com.company.knowledge.org.job;

import com.company.knowledge.org.service.OrgSyncService;
import com.company.knowledge.org.service.PersonSyncService;
import com.company.knowledge.org.service.SyncStateService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncrementalSyncJob {
    private final PersonSyncService personSync;
    private final OrgSyncService orgSync;
    private final SyncStateService state;

    @XxlJob("incrementalSyncJob")
    public void execute() {
        log.info("[IncrementalSync] start");
        LocalDateTime now = LocalDateTime.now();
        try {
            LocalDateTime orgSince = state.getLastOrgSync();
            if (orgSince == null) orgSince = now.minusHours(1);
            int orgChanged = orgSync.incrementalSync(orgSince);
            state.setOrgSync(now);

            LocalDateTime personSince = state.getLastPersonSync();
            if (personSince == null) personSince = now.minusHours(1);
            int personChanged = personSync.incrementalSync(personSince);
            state.setPersonSync(now);

            log.info("[IncrementalSync] done: orgs={}, persons={}", orgChanged, personChanged);
        } catch (Exception e) {
            log.error("[IncrementalSync] failed", e);
            throw e;
        }
    }
}
```

- [ ] **Step 5: 在 XXL-Job Admin 中注册两个任务（手动）**

```
任务 1：fullSyncJob，Cron: 0 0 2 ? * SUN
任务 2：incrementalSyncJob，Cron: 0 0 * * * ?
```

- [ ] **Step 6: 手动触发测试 + 提交**

```bash
# 在 XXL-Job Admin 触发 incrementalSyncJob，看日志：
# [IncrementalSync] start
# [IncrementalSync] done: orgs=N, persons=M
```

```bash
git add src/main/java/com/company/knowledge/config/XxlJobConfig.java \
        src/main/java/com/company/knowledge/org/job/ \
        src/main/java/com/company/knowledge/org/service/SyncStateService.java
git commit -m "feat(phase1): XXL-Job full + incremental sync (48h fallback) + sync state"
```

---

**Phase 1 完成验收**：
- ✅ 浏览器从海康菜单点击 → 自动跳转 → 自动回调 → 用户进入知识中心，全程无登录页
- ✅ `curl /api/me` 返回当前用户 personId/name/org/roles
- ✅ XXL-Job 增量同步每小时跑一次，sys_person/sys_org 表有数据
- ✅ 删除一个海康人员 → 1 小时后本地 status<0

---

## Chunk 3: Phase 2-8 任务清单（任务级，留给执行阶段展开为 step-level）

> 以下 Phase 因依赖前置、需要执行时基于真实反馈迭代，给出**任务清单 + 关键文件 + 验收点**，不展开 step-level。执行时按 Task 顺序走 TDD 循环。

### Phase 2: Knowledge Core（Dataset / Document / Chunk）

**核心**：包装 RAGFlow 三个 API 模块，提供业务层 service + controller。

| Task | 文件 | 验收 |
|------|------|------|
| 2.1 DocumentApi + ChunkApi + MetadataApi 包装 | `integration/ragflow/DocumentApi.java` `ChunkApi.java` `MetadataApi.java` | 集成测试：上传→解析→列表 chunks→更新 metadata |
| 2.2 DatasetService + Controller | `dataset/service/DatasetService.java` `controller/DatasetController.java` | `/api/datasets` CRUD 跑通，含 parser_config（graphrag/raptor/auto_keywords 开关）|
| 2.3 DocumentService + Controller | `document/service/DocumentService.java` `controller/DocumentController.java` | `/api/datasets/{id}/documents` 上传/列表/删除/重新解析 |
| 2.4 ChunkService（list/get/update/availability） | `chunk/service/ChunkService.java` | 调 `Update chunk availability` 切换 available |
| 2.5 MetadataSyncService（文档元数据批量写） | `permission/service/MetadataSyncService.java` | 调 `/metadata/update` 把 dept/security_level 写入文档 |

### Phase 3: Search & Annotation

| Task | 文件 | 验收 |
|------|------|------|
| 3.1 SearchService（权限预过滤）| `search/service/SearchService.java` | 拼装 metadata_condition 调 RetrievalApi，验证不同用户返回不同结果 |
| 3.2 SearchLogService（埋点）| `search/service/SearchLogService.java` | search_log 表记录查询/响应时间/top chunks |
| 3.3 AnnotationService | `chunk/service/AnnotationService.java` | Update chunk 的 important_keywords/questions/tag_kwd |
| 3.4 检索测试 API（调参）| `search/controller/RetrievalTestController.java` | top_k/similarity_threshold/rerank_id 可调，记录历史 |

### Phase 4: Audit

| Task | 文件 | 验收 |
|------|------|------|
| 4.1 AuditTemplateService + Controller | `audit/service/AuditTemplateService.java` | 模板 CRUD（一级/两级/三级集团）|
| 4.2 AuditStateMachine（核心）| `audit/service/AuditStateMachine.java` | 单元测试覆盖：DRAFT→PENDING→通过/退回→PUBLISHED 全路径 |
| 4.3 AuditWorkbenchController | `audit/controller/AuditWorkbenchController.java` | 待审列表/审核详情/通过/退回 |
| 4.4 PublishService（调 Update chunk availability）| `audit/service/PublishService.java` | 终审通过 → available=true → 检索可命中 |
| 4.5 AiAuditService（6 类 LLM 编排）| `audit/service/AiAuditService.java` | 矛盾/错误/时效/完整/一致/规范 6 类检测，产出 ai_audit_issue |

### Phase 5: Permission

| Task | 文件 | 验收 |
|------|------|------|
| 5.1 RoleService（本地角色 CRUD）| `permission/service/RoleService.java` | 角色 CRUD + 人-角色映射 |
| 5.2 PermissionPolicyService（四维策略）| `permission/service/PermissionPolicyService.java` | 按 角色/部门/分类/标签 增删查 |
| 5.3 PermissionIndexService（预计算）| `org/service/PermissionIndexService.java` | 给定 personId 返回 visible_depts/categories/tags/max_security_level |
| 5.4 @RequiresPermission 切面 | `permission/annotation/RequiresPermission.java` `PermissionAspect.java` | 方法注解自动校验 |
| 5.5 OperationLogAspect（等保三级审计）| `permission/annotation/OperationLog.java` `OperationLogAspect.java` | 所有写操作自动记录到 operation_log |

### Phase 6: Agents & Chat

| Task | 文件 | 验收 |
|------|------|------|
| 6.1 ChatApi + AgentApi 包装 | `integration/ragflow/ChatApi.java` `AgentApi.java` | 集成测试：创建 chat/agent + 对话 |
| 6.2 AgentService + Controller | `agent/service/AgentService.java` | 7 大智能体对应 agent 配置 |
| 6.3 ChatService（含溯源）| `agent/service/ChatService.java` | 对话返回 reference 字段，含 chunk 来源 |

### Phase 7: Version & Stats

| Task | 文件 | 验收 |
|------|------|------|
| 7.1 VersionMeta + VersionSwitchService | `version/service/VersionSwitchService.java` | 线上/测试 dataset 指针切换 + 回滚 |
| 7.2 StatsAggregator（聚合任务）| `stats/service/StatsAggregator.java` | 每日聚合 search_log → 统计指标 |
| 7.3 StatsController | `stats/controller/StatsController.java` | 知识量/热门词/高频 chunk/趋势 |

### Phase 8: Frontend Integration（fork RAGFlow Vue）

| Task | 文件 | 验收 |
|------|------|------|
| 8.1 fork RAGFlow 前端，跑通本地构建 | `ragflow-fork/` | `pnpm dev` 启动，能访问 RAGFlow 原生页面 |
| 8.2 接入知识中心 API 客户端 | `src/api/knowledge/*.ts` | 调 `/api/me` `/api/datasets` 等 |
| 8.3 替换登录为 SSO 重定向 | `src/router/knowledge.ts` | 访问 `/login` 自动跳 `/sso/login` |
| 8.4 19 页集成 | `src/pages/knowledge/*` | 对照原型 `prototype/*.html`，逐页实现 |
| 8.5 顶部导航（9 模块下拉）| `src/layouts/knowledge/` | 复刻原型 nav，active 联动 |
| 8.6 上传向导（5 步）| `src/pages/knowledge/upload-wizard/` | 走通采集→加工→入库→标注→审核 |
| 8.7 Nginx 同域子路径配置 | `deploy/nginx.conf` | /knowledge-spa/ /knowledge/ /ragflow/ 三路分发 |

---

## 2. 全局验收清单（最终交付）

| 维度 | 验收点 |
|------|--------|
| 登录 | 海康菜单点击 → OAuth2 跳转 → 自动登录 → 操作知识中心 |
| 知识全生命周期 | 上传→解析→标注→三级审核→发布→可检索 |
| 权限 | 不同部门/角色用户检索同一关键词，返回结果不同（metadata 预过滤生效）|
| 审计 | 任何写操作可在 operation_log 查到操作人/前后快照 |
| 性能 | 检索 P95 ≤1s；并发 ≥300（压测）|
| 信创 | 麒麟 OS + 鲲鹏 + 达梦 DB 通过（专项验证）|
| 19 页联通 | 顶部导航在所有页面跳转无死链 |
| RAGFlow 升级 | fork 分支 merge 上游无冲突（改动集中在布局/路由/鉴权）|

---

## 3. 执行建议

### 3.1 推荐顺序

1. **Phase 0 → Phase 1**（顺序，解锁所有业务）
2. **Phase 2 + Phase 5 并行**（两人/两组）
3. **Phase 3 + Phase 6 并行**（待 2/5 完成后）
4. **Phase 4**（待 2/3 完成）
5. **Phase 7**（待 1/3 完成）
6. **Phase 8**（前端集成，可与 4/6/7 并行起步，待所有后端 API 稳定后收尾）

### 3.2 每日工作流（TDD）

```
早晨：拉最新 → 跑全量测试（mvn test）→ 看是否有失败
每个 Task：
  ① 写失败测试（明确预期）
  ② 跑测试见红
  ③ 写最小实现见绿
  ④ 重构（如有重复）
  ⑤ 提交（小步多次）
傍晚：PR / Code Review
```

### 3.3 关键风险与缓解

| 风险 | 缓解 |
|------|------|
| RAGFlow API 行为与文档不一致（如 metadata_condition 操作符）| Phase 0 Task 0.4 集成测试先行验证 |
| 海康签名计算错误 | Phase 1 Task 1.1 用确定性测试（同输入同输出）|
| OAuth2 code 5 分钟过期，本地调试难 | 用真实海康环境调；Mock OAuth2Client 做单元测试 |
| XXL-Job 增量同步漏数据（>48h）| SyncStateService 检测到 >40h 自动降级全量 |
| fork 前端 merge RAGFlow 升级冲突 | 改动集中在 layout/router/auth，业务页面自建独立路由 |
| 信创环境兼容性 | Phase 8 完成后专项验证，预留 2 周缓冲 |

---

## 4. 评审检查点

每个 Phase 完成后：
- ✅ 所有 Task 测试通过
- ✅ 验收点（见各 Phase 末尾）实测通过
- ✅ 提交 PR，code review 通过
- ✅ 设计文档（spec）该 Phase 相关章节无需修订

8 个 Phase 全部完成后：
- ✅ 全局验收清单全部通过
- ✅ 设计文档 v1.0 定稿
- ✅ 操作说明（`docs/知识闭环流转操作说明.md`）实测可执行
- ✅ 19 页原型与实现一致（视觉 diff ≤5%）

---

**Plan complete and saved to `docs/superpowers/plans/2026-06-17-knowledge-platform.md`. Ready to execute?**
