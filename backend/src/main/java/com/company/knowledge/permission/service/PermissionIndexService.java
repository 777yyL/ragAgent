package com.company.knowledge.permission.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.company.knowledge.common.constant.SecurityLevel;
import com.company.knowledge.org.entity.SysOrg;
import com.company.knowledge.org.entity.SysPerson;
import com.company.knowledge.org.mapper.SysOrgMapper;
import com.company.knowledge.org.mapper.SysPersonMapper;
import com.company.knowledge.permission.entity.PermissionIndex;
import com.company.knowledge.permission.mapper.PermissionIndexMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 权限索引 Service（预计算 + 缓存 + RAGFlow metadata_condition 构建）。
 *
 * <p>核心职责：把分散在 sys_role / sys_person_role / sys_org / permission_policy
 * 中的权限数据，预计算成一张「可见部门 / 可见分类 / 可见标签 / 最大密级」表，
 * 让 {@code SearchService} 在拼 RAGFlow metadata_condition 时 O(1) 读取。
 *
 * <p>缓存策略：DB 读到的索引写入 Redis（TTL 5 分钟）；{@link #rebuild} 后强制刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionIndexService {

    /** Redis key 前缀：{@code knowledge:perm:index:{personId}} */
    private static final String CACHE_KEY_PREFIX = "knowledge:perm:index:";

    /** 缓存 TTL：5 分钟 */
    private static final long CACHE_TTL_MINUTES = 5;

    private final PermissionIndexMapper indexMapper;
    private final RoleService roleService;
    private final SysPersonMapper personMapper;
    private final SysOrgMapper orgMapper;
    private final PermissionPolicyService policyService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 按 personId 重建权限索引（同步写 DB + Redis）。
     *
     * <p>算法：
     *
     * <ol>
     *   <li>查 personId 的角色 code 集合（RoleService）</li>
     *   <li>查 personId 的 orgIndexCode + 递归向上算「祖先部门」集合</li>
     *   <li>visible_depts = 祖先 + 自己（管理员 = 全部，跳过本步）</li>
     *   <li>visible_categories / visible_tags = 查 permission_policy subject=ROLE,subjectId=roleCode</li>
     *   <li>max_security_level = 角色映射最大值</li>
     * </ol>
     *
     * @param personId 海康 personId
     * @return 写入后的索引对象
     */
    public PermissionIndex rebuild(String personId) {
        Set<String> roleCodes = roleService.getRoleCodesByPersonId(personId);

        // visible_depts：管理员全部；否则本人 + 祖先
        String[] visibleDepts;
        if (roleCodes.contains("ADMIN")) {
            visibleDepts = new String[]{"*"};
        } else {
            visibleDepts = computeVisibleDepts(personId);
        }

        // visible_categories / visible_tags：从策略表 ROLE 维度查
        String[] visibleCategories = collectByPolicy(roleCodes, "CATEGORY");
        String[] visibleTags = collectByPolicy(roleCodes, "TAG");

        // max_security_level：角色映射
        short maxLevel = computeMaxSecurityLevel(roleCodes);

        PermissionIndex idx = new PermissionIndex();
        idx.setPersonId(personId);
        idx.setVisibleDepts(visibleDepts);
        idx.setVisibleCategories(visibleCategories);
        idx.setVisibleTags(visibleTags);
        idx.setMaxSecurityLevel(maxLevel);
        idx.setUpdatedAt(LocalDateTime.now());

        // UPSERT：先 delete by id，再 insert
        indexMapper.deleteById(personId);
        indexMapper.insert(idx);
        // 刷新缓存
        cacheSet(personId, idx);
        log.info("rebuild perm index ok, personId={}, depts={}, categories={}, tags={}, level={}",
                personId, Arrays.toString(visibleDepts),
                Arrays.toString(visibleCategories), Arrays.toString(visibleTags), maxLevel);
        return idx;
    }

    /**
     * 读取权限索引（Redis 优先，DB 兜底；未找到返回 null）。
     *
     * @param personId 海康 personId
     * @return 索引对象；无记录返回 null
     */
    public PermissionIndex get(String personId) {
        // 1. Redis
        PermissionIndex cached = cacheGet(personId);
        if (cached != null) {
            return cached;
        }
        // 2. DB
        PermissionIndex fromDb = indexMapper.selectById(personId);
        if (fromDb != null) {
            cacheSet(personId, fromDb);
        }
        return fromDb;
    }

    /**
     * 把权限索引转成 RAGFlow metadata_condition 的 Map 结构（供 SearchService 拼检索请求）。
     *
     * <p>输出示例：
     * <pre>{@code
     * {
     *   "logic": "and",
     *   "conditions": [
     *     {"field": "dept_ids", "comparison_operator": "contains", "value": ["d1","d2"]},
     *     {"field": "security_level", "comparison_operator": "le", "value": 3},
     *     {"field": "category", "comparison_operator": "in", "value": ["ops","safe"]}
     *   ]
     * }
     * }</pre>
     *
     * <p>若 {@code visible_depts} 含通配 {@code "*"}（管理员），dept 条件省略。
     *
     * @param personId 海康 personId
     * @return metadata_condition Map；无索引时返回空条件（and/[]）
     */
    public Map<String, Object> buildMetadataCondition(String personId) {
        PermissionIndex idx = get(personId);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("logic", "and");
        List<Map<String, Object>> conditions = new ArrayList<>();

        if (idx == null) {
            root.put("conditions", conditions);
            return root;
        }

        // 1. dept 条件
        if (idx.getVisibleDepts() != null && idx.getVisibleDepts().length > 0) {
            boolean wildcard = false;
            for (String d : idx.getVisibleDepts()) {
                if ("*".equals(d)) {
                    wildcard = true;
                    break;
                }
            }
            if (!wildcard) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("field", "dept_ids");
                c.put("comparison_operator", "contains");
                c.put("value", idx.getVisibleDepts());
                conditions.add(c);
            }
        }

        // 2. security_level 条件
        if (idx.getMaxSecurityLevel() != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("field", "security_level");
            c.put("comparison_operator", "le");
            c.put("value", idx.getMaxSecurityLevel().intValue());
            conditions.add(c);
        }

        // 3. category 条件
        if (idx.getVisibleCategories() != null && idx.getVisibleCategories().length > 0) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("field", "category");
            c.put("comparison_operator", "in");
            c.put("value", idx.getVisibleCategories());
            conditions.add(c);
        }

        root.put("conditions", conditions);
        return root;
    }

    // ======================================================================
    // private helpers
    // ======================================================================

    /**
     * 计算可见部门：本人 orgIndexCode + 所有祖先（沿 parentOrgIndexCode 上溯）。
     *
     * <p>管理员已在调用方跳过此方法。
     */
    private String[] computeVisibleDepts(String personId) {
        SysPerson person = personMapper.selectById(personId);
        if (person == null || person.getOrgIndexCode() == null) {
            return new String[0];
        }
        Set<String> acc = new HashSet<>();
        String current = person.getOrgIndexCode();
        int guard = 0;
        while (current != null && !current.isEmpty() && guard < 50) {
            acc.add(current);
            SysOrg org = orgMapper.selectById(current);
            if (org == null) {
                break;
            }
            String parent = org.getParentOrgIndexCode();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
            guard++;
        }
        return acc.toArray(new String[0]);
    }

    /**
     * 从 permission_policy 收集所有 ROLE 维度的 object_value。
     *
     * @param roleCodes  角色 code 集合
     * @param objectType DATASET / CATEGORY / TAG / DOC
     * @return object_value 数组，去重后返回
     */
    private String[] collectByPolicy(Set<String> roleCodes, String objectType) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return new String[0];
        }
        Set<String> acc = new HashSet<>();
        for (String code : roleCodes) {
            policyService.listBySubject("ROLE", code).forEach(p -> {
                if (objectType.equals(p.getObjectType())) {
                    if (p.getObjectValue() != null) {
                        acc.add(p.getObjectValue());
                    }
                }
            });
        }
        // 管理员通配
        if (roleCodes.contains("ADMIN")) {
            acc.add("*");
        }
        return acc.toArray(new String[0]);
    }

    /**
     * 角色映射到 max_security_level。
     *
     * <p>取该用户所有角色的最大值（最宽松）：
     *
     * <ul>
     *   <li>ADMIN → {@link SecurityLevel#MAX}</li>
     *   <li>AUDITOR_GROUP → 3</li>
     *   <li>AUDITOR_REGION / AUDITOR_ENTERPRISE → 2</li>
     *   <li>EDITOR → 2</li>
     *   <li>VIEWER / 其他 → 1</li>
     * </ul>
     */
    private short computeMaxSecurityLevel(Set<String> roleCodes) {
        short max = 1;
        for (String code : roleCodes) {
            short level;
            switch (code) {
                case "ADMIN":
                    return SecurityLevel.MAX;
                case "AUDITOR_GROUP":
                    level = 3;
                    break;
                case "AUDITOR_REGION":
                case "AUDITOR_ENTERPRISE":
                case "EDITOR":
                    level = 2;
                    break;
                case "VIEWER":
                default:
                    level = 1;
                    break;
            }
            if (level > max) {
                max = level;
            }
        }
        return max;
    }

    // --- Redis cache helpers ---

    private String cacheKey(String personId) {
        return CACHE_KEY_PREFIX + personId;
    }

    private void cacheSet(String personId, PermissionIndex idx) {
        try {
            redis.opsForValue().set(cacheKey(personId),
                    objectMapper.writeValueAsString(idx),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.warn("cacheSet serialize failed, personId={}: {}", personId, e.getMessage());
        }
    }

    private PermissionIndex cacheGet(String personId) {
        String json = redis.opsForValue().get(cacheKey(personId));
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PermissionIndex.class);
        } catch (JsonProcessingException e) {
            log.warn("cacheGet deserialize failed, personId={}: {}", personId, e.getMessage());
            return null;
        }
    }
}
