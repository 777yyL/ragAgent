-- =============================================================================
-- 发电知识中心 · V1 初始化 schema
-- 覆盖：组织副本 / 角色 / 权限策略 / 业务文档 / 审核 / 版本 / 审计日志 / 检索埋点
-- =============================================================================

-- ========== 组织副本（来源海康同步，只读）==========
CREATE TABLE sys_person (
    person_id       VARCHAR(64) PRIMARY KEY,
    person_name     VARCHAR(100) NOT NULL,
    org_index_code  VARCHAR(64),
    org_path        VARCHAR(1000),
    org_path_name   VARCHAR(1000),
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
    org_index_code        VARCHAR(64) PRIMARY KEY,
    org_name              VARCHAR(200) NOT NULL,
    org_path              VARCHAR(2000),
    parent_org_index_code VARCHAR(64),
    is_leaf               BOOLEAN DEFAULT FALSE,
    sort                  INTEGER,
    available             BOOLEAN DEFAULT TRUE,
    status                SMALLINT DEFAULT 0,
    create_time           TIMESTAMP,
    update_time           TIMESTAMP,
    sync_time             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_org_parent ON sys_org(parent_org_index_code);

-- ========== 角色（本地维护）==========
CREATE TABLE sys_role (
    role_id     BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50) UNIQUE NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_builtin  BOOLEAN DEFAULT FALSE,
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

-- ========== 权限索引（预计算，检索时直接读）==========
CREATE TABLE permission_index (
    person_id           VARCHAR(64) PRIMARY KEY,
    visible_depts       VARCHAR(64)[],
    visible_categories  VARCHAR(50)[],
    visible_tags        VARCHAR(100)[],
    max_security_level  SMALLINT DEFAULT 1,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 业务文档元数据（与 RAGFlow document 一一对应）==========
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
    nodes           JSONB NOT NULL,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_instance (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT NOT NULL,
    template_id     BIGINT NOT NULL,
    current_node    SMALLINT,
    status          VARCHAR(20) NOT NULL,
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
    action          VARCHAR(20),
    comment         TEXT,
    chunk_edits     JSONB,
    acted_at        TIMESTAMP
);

CREATE TABLE ai_audit_issue (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT,
    chunk_id        VARCHAR(64),
    issue_type      VARCHAR(30) NOT NULL,
    severity        VARCHAR(10) NOT NULL,
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
    env             VARCHAR(20) NOT NULL,
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
    result          VARCHAR(20),
    error_msg       TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_user ON operation_log(user_id, created_at DESC);
CREATE INDEX idx_log_resource ON operation_log(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_log_action ON operation_log(action, created_at DESC);

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
('ADMIN',               '系统管理员',   '全权限：浏览/编辑/删除/审批/配置', true),
('AUDITOR_GROUP',       '集团审核员',   '集团终审节点审批',                 true),
('AUDITOR_REGION',      '区域审核员',   '区域复审节点审批',                 true),
('AUDITOR_ENTERPRISE',  '企业审核员',   '企业初审节点审批',                 true),
('EDITOR',              '编辑员',       '浏览/编辑/提交审核',               true),
('VIEWER',              '浏览者',       '仅浏览和检索',                     true);
