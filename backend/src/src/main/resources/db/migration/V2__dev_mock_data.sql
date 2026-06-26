-- =============================================================================
-- V2 开发测试数据（组织/人员/角色映射/审核模板/示例文档）
-- 适用于本地开发环境，生产环境 Flyway 也会执行但数据无害（可清理）
-- =============================================================================

-- ========== Mock 组织 ==========
INSERT INTO sys_org (org_index_code, org_name, org_path, parent_org_index_code, is_leaf, sort, available, status) VALUES
('root000000',        '集团总部',     '@root000000@',                       '0',     false, 1, true, 0),
('org-group-hq',      '集团生产部',   '@root000000@org-group-hq@',          'root000000', true,  1, true, 0),
('org-region-east',   '华东区域中心', '@root000000@org-region-east@',       'root000000', false, 2, true, 0),
('org-plant-sh',      '上海电厂',     '@root000000@org-region-east@org-plant-sh@', 'org-region-east', false, 1, true, 0),
('org-plant-sh-prod', '生产技术部',   '@root000000@org-region-east@org-plant-sh@org-plant-sh-prod@', 'org-plant-sh', true, 1, true, 0),
('org-plant-sh-eqp',  '设备维护部',   '@root000000@org-region-east@org-plant-sh@org-plant-sh-eqp@',  'org-plant-sh', true, 2, true, 0)
ON CONFLICT (org_index_code) DO NOTHING;

-- ========== Mock 人员 ==========
INSERT INTO sys_person (person_id, person_name, org_index_code, org_path, org_path_name, job_no, phone, email, company, post, post_type, status) VALUES
('dev-user-001',  '开发测试员',   'org-group-hq',      '@root000000@org-group-hq@',                    '@集团总部@集团生产部@',     'DEV001', '13800000001', 'dev@knowledge.local',   '集团总部',     '系统管理员', 'ADMIN',     0),
('person-zhang',  '张明远',       'org-group-hq',      '@root000000@org-group-hq@',                    '@集团总部@集团生产部@',     'EMP001', '13900000001', 'zhangmy@company.com',   '集团总部',     '生产主管',   'MANAGER',   0),
('person-li',     '李建国',       'org-region-east',   '@root000000@org-region-east@',                '@集团总部@华东区域中心@',   'EMP002', '13900000002', 'lijg@company.com',      '华东区域中心', '区域审核员', 'AUDITOR',   0),
('person-wang',   '王志强',       'org-plant-sh-prod', '@root000000@org-region-east@org-plant-sh@org-plant-sh-prod@', '@集团总部@华东区域中心@上海电厂@生产技术部@', 'EMP003', '13900000003', 'wangzq@company.com', '上海电厂', '值长', 'SHIFT_LEAD', 0),
('person-zhao',   '赵敏',         'org-plant-sh-eqp',  '@root000000@org-region-east@org-plant-sh@org-plant-sh-eqp@',  '@集团总部@华东区域中心@上海电厂@设备维护部@', 'EMP004', '13900000004', 'zhaom@company.com',  '上海电厂', '设备工程师', 'ENGINEER',  0)
ON CONFLICT (person_id) DO NOTHING;

-- ========== 人-角色映射 ==========
INSERT INTO sys_person_role (person_id, role_id) VALUES
('dev-user-001',  (SELECT role_id FROM sys_role WHERE role_code = 'ADMIN')),
('person-zhang',  (SELECT role_id FROM sys_role WHERE role_code = 'ADMIN')),
('person-li',     (SELECT role_id FROM sys_role WHERE role_code = 'AUDITOR_REGION')),
('person-wang',   (SELECT role_id FROM sys_role WHERE role_code = 'AUDITOR_ENTERPRISE')),
('person-zhao',   (SELECT role_id FROM sys_role WHERE role_code = 'EDITOR'))
ON CONFLICT DO NOTHING;

-- ========== 权限索引（dev-user-001 管理员，全可见）==========
INSERT INTO permission_index (person_id, visible_depts, visible_categories, visible_tags, max_security_level) VALUES
('dev-user-001',  ARRAY['*'], ARRAY['*'], ARRAY['*'], 4),
('person-zhang',  ARRAY['*'], ARRAY['*'], ARRAY['*'], 4),
('person-li',     ARRAY['org-region-east','org-plant-sh'], ARRAY['REGULATION','STANDARD','CASE'], ARRAY['锅炉','汽轮机'], 3),
('person-wang',   ARRAY['org-plant-sh','org-plant-sh-prod'], ARRAY['REGULATION','ALARM'], ARRAY['锅炉','环保'], 2),
('person-zhao',   ARRAY['org-plant-sh','org-plant-sh-eqp'], ARRAY['LEDGER','CASE'], ARRAY['汽轮机','发电机'], 2)
ON CONFLICT (person_id) DO UPDATE SET
    visible_depts = EXCLUDED.visible_depts,
    visible_categories = EXCLUDED.visible_categories,
    visible_tags = EXCLUDED.visible_tags,
    max_security_level = EXCLUDED.max_security_level,
    updated_at = CURRENT_TIMESTAMP;

-- ========== 审核模板 ==========
INSERT INTO audit_template (name, business_type, nodes, enabled) VALUES
('一级直审',   'REGULATION', '[{"order":1,"name":"部门审核","approverRole":"AUDITOR_ENTERPRISE","multiSign":false}]', true),
('两级审核',   'STANDARD',   '[{"order":1,"name":"企业初审","approverRole":"AUDITOR_ENTERPRISE","multiSign":false},{"order":2,"name":"区域复审","approverRole":"AUDITOR_REGION","multiSign":false}]', true),
('三级集团审核', 'REGULATION', '[{"order":1,"name":"企业初审","approverRole":"AUDITOR_ENTERPRISE","multiSign":false},{"order":2,"name":"区域复审","approverRole":"AUDITOR_REGION","multiSign":false},{"order":3,"name":"集团终审","approverRole":"AUDITOR_GROUP","multiSign":true}]', true)
ON CONFLICT DO NOTHING;

-- ========== 示例业务文档 ==========
INSERT INTO knowledge_doc (title, ragflow_doc_id, dataset_id, business_type, dept_id, tags, security_level, audit_status, source_type, created_by) VALUES
('600MW 机组锅炉过热器管壁温度超限处置规程', 'rf-doc-001', 'ds-boiler',    'REGULATION', 'org-plant-sh-prod', ARRAY['锅炉','过热器','温度'],    2, 'PUBLISHED',  'UPLOAD', 'person-wang'),
('DL/T 5145-2024 火力发电厂烟气脱硫设计规范', 'rf-doc-002', 'ds-standard',  'STANDARD',   'org-group-hq',      ARRAY['脱硫','环保','标准'],      1, 'PUBLISHED',  'UPLOAD', 'person-zhang'),
('#3 机组汽轮机轴向位移异常故障分析报告',     'rf-doc-003', 'ds-case',      'CASE',       'org-plant-sh-eqp',  ARRAY['汽轮机','轴向位移','故障'], 2, 'PUBLISHED',  'UPLOAD', 'person-zhao'),
('凝汽器真空下降应急处理标准化流程',           'rf-doc-004', 'ds-boiler',    'REGULATION', 'org-plant-sh-prod', ARRAY['凝汽器','真空','应急'],     2, 'PENDING',    'UPLOAD', 'person-wang'),
('磨煤机防爆门动作应急操作规程（修订）',       'rf-doc-005', 'ds-boiler',    'REGULATION', 'org-plant-sh-prod', ARRAY['磨煤机','防爆门','规程'],   2, 'PENDING',    'UPLOAD', 'person-wang'),
('环保岛超低排放改造运行维护手册（第二版）',   'rf-doc-006', 'ds-standard',  'STANDARD',   'org-group-hq',      ARRAY['环保','超低排放','手册'],   1, 'DRAFT',      'UPLOAD', 'person-zhang')
ON CONFLICT DO NOTHING;

-- ========== 示例 AI 审核问题 ==========
INSERT INTO ai_audit_issue (doc_id, chunk_id, issue_type, severity, position, description, suggestion, status) VALUES
(1, 'chunk-003', 'CONFLICT',    'HIGH',   '第8页 第3段', '过热器出口温度限值 580°C 与 DL/T 标准的 565°C 不一致', '建议以 DL/T 标准为准，修正为 565°C', 'OPEN'),
(1, 'chunk-003', 'NORM',        'MEDIUM', '第8页 第5段', '术语"防爆门"与"防爆膜"在同一文档中混用',              '统一使用"防爆门"',                  'OPEN'),
(2, 'chunk-001', 'TIMELINESS',  'MEDIUM', '引用标准',    '引用的 GB 13223-2011 已被 GB 13223-2024 替代',        '更新引用版本为 GB 13223-2024',      'OPEN'),
(3, 'chunk-007', 'INTEGRITY',   'LOW',    '第12页',      '故障分析缺少振动值的具体数据记录',                     '补充振动值趋势图和数据',            'OPEN'),
(5, 'chunk-002', 'ERROR',       'HIGH',   '第3页 表格',  '减温水流量单位标注为 t/h，应为 m³/h',                   '修正单位为 m³/h',                   'RESOLVED')
ON CONFLICT DO NOTHING;
