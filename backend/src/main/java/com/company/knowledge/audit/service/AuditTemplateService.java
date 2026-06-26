package com.company.knowledge.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.knowledge.audit.entity.AuditNode;
import com.company.knowledge.audit.entity.AuditTemplate;
import com.company.knowledge.audit.mapper.AuditTemplateMapper;
import com.company.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 审核模板服务。管理「几级审核」的节点配置。
 *
 * <p>模板示例：
 * <ul>
 *   <li>一级直审：[集团终审]</li>
 *   <li>两级：[企业初审, 集团终审]</li>
 *   <li>三级：[企业, 区域, 集团]</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTemplateService {

    /** 内置角色：企业初审 */
    public static final String ROLE_AUDITOR_ENTERPRISE = "AUDITOR_ENTERPRISE";
    /** 内置角色：区域复审 */
    public static final String ROLE_AUDITOR_REGION = "AUDITOR_REGION";
    /** 内置角色：集团终审 */
    public static final String ROLE_AUDITOR_GROUP = "AUDITOR_GROUP";

    private final AuditTemplateMapper mapper;

    /**
     * 列出模板。
     *
     * @param businessType 业务分类，可为 null（查全部）
     * @param enabled      启用过滤，可为 null（不过滤）
     */
    public List<AuditTemplate> list(String businessType, Boolean enabled) {
        LambdaQueryWrapper<AuditTemplate> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(businessType)) {
            w.eq(AuditTemplate::getBusinessType, businessType);
        }
        if (enabled != null) {
            w.eq(AuditTemplate::getEnabled, enabled);
        }
        w.orderByDesc(AuditTemplate::getCreatedAt);
        return mapper.selectList(w);
    }

    /**
     * 获取单个模板，不存在抛 {@link BizException}。
     */
    public AuditTemplate get(Long id) {
        AuditTemplate t = mapper.selectById(id);
        if (t == null) {
            throw BizException.of(4041, "audit template not found: " + id);
        }
        return t;
    }

    /**
     * 创建模板。{@code nodes} 会按 order 校验并自动补齐 order 字段。
     */
    public AuditTemplate create(String name, String businessType, List<AuditNode> nodes) {
        validateNodes(nodes);
        normalizeOrder(nodes);

        AuditTemplate t = new AuditTemplate();
        t.setName(name);
        t.setBusinessType(businessType);
        t.setNodes(nodes);
        t.setEnabled(true);
        t.setCreatedAt(LocalDateTime.now());
        mapper.insert(t);
        log.info("audit template created: id={}, name={}, nodes={}",
                t.getId(), name, nodes.size());
        return t;
    }

    /**
     * 更新模板。{@code enabled} 传 null 表示不改。
     */
    public AuditTemplate update(Long id, String name, String businessType,
                                List<AuditNode> nodes, Boolean enabled) {
        AuditTemplate t = get(id);
        if (StringUtils.hasText(name)) {
            t.setName(name);
        }
        if (businessType != null) {
            t.setBusinessType(businessType);
        }
        if (nodes != null && !nodes.isEmpty()) {
            validateNodes(nodes);
            normalizeOrder(nodes);
            t.setNodes(nodes);
        }
        if (enabled != null) {
            t.setEnabled(enabled);
        }
        mapper.updateById(t);
        return t;
    }

    /**
     * 删除模板（物理删除）。
     */
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * 按业务分类推荐默认模板。未配置时回退到「一级直审」。
     *
     * <p>规则：
     * <ul>
     *   <li>查找该 businessType 下 enabled=true 的最新模板</li>
     *   <li>未命中则返回内置三级模板（企业→区域→集团）</li>
     * </ul>
     */
    public AuditTemplate recommendTemplate(String businessType) {
        LambdaQueryWrapper<AuditTemplate> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(businessType)) {
            w.eq(AuditTemplate::getBusinessType, businessType);
        }
        w.eq(AuditTemplate::getEnabled, true);
        w.orderByDesc(AuditTemplate::getCreatedAt);
        w.last("LIMIT 1");
        List<AuditTemplate> list = mapper.selectList(w);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        // 未配置：返回内存中的默认三级模板（不入库，调用方可决定是否持久化）
        return defaultThreeLevel(businessType);
    }

    /** 一级直审模板（仅集团）。 */
    public static AuditTemplate defaultOneLevel(String businessType) {
        AuditTemplate t = new AuditTemplate();
        t.setName("默认一级审核（集团终审）");
        t.setBusinessType(businessType);
        t.setNodes(new ArrayList<>(Arrays.asList(
                new AuditNode(1, "集团终审", ROLE_AUDITOR_GROUP, false)
        )));
        t.setEnabled(true);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    /** 两级模板（企业初审 + 集团终审）。 */
    public static AuditTemplate defaultTwoLevel(String businessType) {
        AuditTemplate t = new AuditTemplate();
        t.setName("默认两级审核（企业+集团）");
        t.setBusinessType(businessType);
        t.setNodes(new ArrayList<>(Arrays.asList(
                new AuditNode(1, "企业初审", ROLE_AUDITOR_ENTERPRISE, false),
                new AuditNode(2, "集团终审", ROLE_AUDITOR_GROUP, false)
        )));
        t.setEnabled(true);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    /** 三级模板（企业→区域→集团）。 */
    public static AuditTemplate defaultThreeLevel(String businessType) {
        AuditTemplate t = new AuditTemplate();
        t.setName("默认三级审核（企业+区域+集团）");
        t.setBusinessType(businessType);
        t.setNodes(new ArrayList<>(Arrays.asList(
                new AuditNode(1, "企业初审", ROLE_AUDITOR_ENTERPRISE, false),
                new AuditNode(2, "区域复审", ROLE_AUDITOR_REGION, false),
                new AuditNode(3, "集团终审", ROLE_AUDITOR_GROUP, false)
        )));
        t.setEnabled(true);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    // ===== 内部校验 =====

    private void validateNodes(List<AuditNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw BizException.of(4001, "audit template nodes cannot be empty");
        }
        for (AuditNode n : nodes) {
            if (n.getApproverRole() == null || n.getApproverRole().isEmpty()) {
                throw BizException.of(4001, "audit node approverRole is required");
            }
        }
    }

    private void normalizeOrder(List<AuditNode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).setOrder(i + 1);
        }
    }
}
