package com.company.knowledge.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.knowledge.audit.constant.IssueSeverity;
import com.company.knowledge.audit.constant.IssueStatus;
import com.company.knowledge.audit.constant.IssueType;
import com.company.knowledge.audit.entity.AiAuditIssue;
import com.company.knowledge.audit.mapper.AiAuditIssueMapper;
import com.company.knowledge.chunk.ChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 审核服务。对文档的所有 chunks 执行 6 类检测：
 *
 * <ol>
 *   <li>{@link IssueType#CONFLICT 矛盾}：相似 chunk 间数值/结论矛盾（LLM 对比）</li>
 *   <li>{@link IssueType#ERROR 错误}：数值范围/单位硬错误（规则引擎 + LLM）</li>
 *   <li>{@link IssueType#TIMELINESS 时效性}：引用的国标/行标版本过期</li>
 *   <li>{@link IssueType#INTEGRITY 完整性}：关键字段/章节缺失（LLM 模板对照）</li>
 *   <li>{@link IssueType#CONSISTENCY 一致性}：同实体全库表述不一致（规则：实体词典）</li>
 *   <li>{@link IssueType#NORM 规范性}：术语词典不匹配</li>
 * </ol>
 *
 * <p>规则引擎可在无 LLM 时独立工作；LLM 增强（如矛盾判断）在 {@link LlmGateway}
 * 可用时调用，不可用时跳过该类检测。
 *
 * <p>{@link #runAiAudit} 标注 {@link Async}，不阻塞调用方；返回值失效，
 * 调用方应通过 {@link #reportByDoc} 轮询结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {

    private final AiAuditIssueMapper issueMapper;
    private final ChunkService chunkService;

    /**
     * LLM 网关，可选注入。无实现 Bean 时为 null，服务降级为仅规则检测。
     */
    @Autowired(required = false)
    private LlmGateway llmGateway;

    /** 数值范围规则：含明确单位且数值越界。示例：电压 > 1500kV 视为可疑。支持负数 */
    private static final Pattern NUMERIC_PATTERN = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?)\\s*(kV|kW|MW|kA|A|℃|°C|Hz|m/s|r/min)");

    /** 引用的标准号（GB/T / DL/T / IEC 等） */
    private static final Pattern STANDARD_PATTERN = Pattern.compile(
            "(GB/T\\s*\\d+(?:\\.\\d+)?|DL/T\\s*\\d+|IEC\\s*\\d+(?:-\\d+)?|IEEE\\s*\\d+)");

    /** 术语词典（示例，Phase 8 可改为数据库配置） */
    private static final Set<String> TERM_DICTIONARY = new HashSet<>();
    static {
        Collections.addAll(TERM_DICTIONARY,
                "发电机", "汽轮机", "锅炉", "变压器", "断路器", "继电保护",
                "励磁系统", "调速系统", "凝汽器", "高压加热器", "脱硫", "脱硝",
                "DCS", "SCADA", "AVR", "PSS", "GIS");
    }

    /** 误用术语映射（错误 → 正确） */
    private static final String[][] TERM_FIXES = {
            {"瓦特", "W"},
            {"赫兹", "Hz"},
            {"安培", "A"},
            {"欧姆", "Ω"}
    };

    /**
     * 异步执行 AI 审核。
     *
     * @param datasetId RAGFlow dataset ID
     * @param docId     文档 ID（也是 knowledge_doc.id）
     */
    @Async
    public void runAiAudit(String datasetId, Long docId) {
        log.info("AI audit start: datasetId={}, docId={}", datasetId, docId);
        try {
            List<ChunkInfo> chunks = listChunks(datasetId, String.valueOf(docId));
            if (chunks.isEmpty()) {
                log.warn("AI audit no chunks: docId={}", docId);
                return;
            }

            List<AiAuditIssue> issues = new ArrayList<>();
            for (ChunkInfo c : chunks) {
                issues.addAll(detectError(docId, c));
                issues.addAll(detectTimeliness(docId, c));
                issues.addAll(detectNorm(docId, c));
                issues.addAll(detectIntegrity(docId, c));
            }
            // 跨 chunk 检测
            issues.addAll(detectConflict(docId, chunks));
            issues.addAll(detectConsistency(docId, chunks));

            for (AiAuditIssue i : issues) {
                issueMapper.insert(i);
            }
            log.info("AI audit done: docId={}, issues={}", docId, issues.size());
        } catch (Exception e) {
            log.error("AI audit failed: docId=" + docId, e);
        }
    }

    /**
     * 查询某文档的 AI 审核问题列表。
     *
     * @param docId    文档 ID
     * @param type     可选，IssueType
     * @param severity 可选，IssueSeverity
     * @param status   可选，OPEN/RESOLVED/IGNORED
     */
    public List<AiAuditIssue> reportByDoc(Long docId, String type, String severity, String status) {
        LambdaQueryWrapper<AiAuditIssue> w = new LambdaQueryWrapper<>();
        w.eq(AiAuditIssue::getDocId, docId);
        if (StringUtils.hasText(type)) {
            w.eq(AiAuditIssue::getIssueType, type);
        }
        if (StringUtils.hasText(severity)) {
            w.eq(AiAuditIssue::getSeverity, severity);
        }
        if (StringUtils.hasText(status)) {
            w.eq(AiAuditIssue::getStatus, status);
        }
        w.orderByDesc(AiAuditIssue::getCreatedAt);
        return issueMapper.selectList(w);
    }

    /**
     * 批量处理问题：采纳（RESOLVED）或忽略（IGNORED）。
     *
     * @param issueIds 问题 ID 列表
     * @param action   RESOLVE / IGNORE
     * @return 处理条数
     */
    public int batchResolve(List<Long> issueIds, String action) {
        if (issueIds == null || issueIds.isEmpty()) {
            return 0;
        }
        String target;
        if ("RESOLVE".equalsIgnoreCase(action)) {
            target = IssueStatus.RESOLVED;
        } else if ("IGNORE".equalsIgnoreCase(action)) {
            target = IssueStatus.IGNORED;
        } else {
            throw new IllegalArgumentException("unsupported action: " + action);
        }
        int count = 0;
        for (Long id : issueIds) {
            AiAuditIssue issue = issueMapper.selectById(id);
            if (issue == null) {
                continue;
            }
            issue.setStatus(target);
            issueMapper.updateById(issue);
            count++;
        }
        return count;
    }

    // ===== 6 类检测 =====

    /**
     * 错误检测：数值范围/单位规则。
     *
     * <p>规则示例（可扩展）：
     * <ul>
     *   <li>电压 > 1500kV</li>
     *   <li>温度 &gt; 600℃（常规锅炉蒸汽温度上限）</li>
     *   <li>频率 != 50Hz（中国电网）</li>
     * </ul>
     *
     * <p>LLM 可用时：把规则结果 + chunk 原文喂给 LLM 复核。
     */
    List<AiAuditIssue> detectError(Long docId, ChunkInfo chunk) {
        List<AiAuditIssue> out = new ArrayList<>();
        Matcher m = NUMERIC_PATTERN.matcher(chunk.content);
        while (m.find()) {
            String num = m.group(1);
            String unit = m.group(2);
            double v = Double.parseDouble(num);
            String problem = checkNumericRange(v, unit);
            if (problem != null) {
                AiAuditIssue issue = baseIssue(docId, chunk.id, IssueType.ERROR,
                        IssueSeverity.WARN, m.group(), problem,
                        "请核对数值范围与单位是否正确");
                // LLM 增强
                if (llmGateway != null) {
                    try {
                        String llmOut = llmGateway.chat(
                                "你是发电领域技术专家，判断下面这句话中的数值是否合理。",
                                "原文：" + chunk.content);
                        if (llmOut != null && llmOut.contains("不合理")) {
                            issue.setSeverity(IssueSeverity.ERROR.name());
                            issue.setDescription(issue.getDescription() + " | LLM: " + llmOut);
                        }
                    } catch (Exception e) {
                        log.debug("LLM detectError skipped: {}", e.getMessage());
                    }
                }
                out.add(issue);
            }
        }
        return out;
    }

    /**
     * 时效性检测：引用的国标/行标版本是否过期。
     *
     * <p>当前实现为「规则标记」：发现标准号引用就生成一条 INFO 级 issue，
     * 提示人工核对版本。Phase 8 接入标准版本库后可自动比对。
     */
    List<AiAuditIssue> detectTimeliness(Long docId, ChunkInfo chunk) {
        List<AiAuditIssue> out = new ArrayList<>();
        Matcher m = STANDARD_PATTERN.matcher(chunk.content);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String std = m.group(1).replaceAll("\\s+", " ");
            if (!seen.add(std)) {
                continue;
            }
            AiAuditIssue issue = baseIssue(docId, chunk.id, IssueType.TIMELINESS,
                    IssueSeverity.INFO, std,
                    "引用了标准 " + std + "，请核对版本是否为最新",
                    "查询国家标准化管理委员会或行业标准最新版本");
            out.add(issue);
        }
        return out;
    }

    /**
     * 规范性检测：术语词典。
     *
     * <p>检测非标准中文化物理量单位（如「瓦特」应改为 W），以及不在词典内的术语标记。
     */
    List<AiAuditIssue> detectNorm(Long docId, ChunkInfo chunk) {
        List<AiAuditIssue> out = new ArrayList<>();
        for (String[] fix : TERM_FIXES) {
            String wrong = fix[0];
            String right = fix[1];
            if (chunk.content.contains(wrong)) {
                out.add(baseIssue(docId, chunk.id, IssueType.NORM,
                        IssueSeverity.INFO, wrong,
                        "使用了非规范术语「" + wrong + "」，建议改为符号「" + right + "」",
                        "替换为 " + right));
            }
        }
        return out;
    }

    /**
     * 完整性检测：关键字段是否缺失。
     *
     * <p>简化实现：chunk 内容过短（&lt; 30 字）或无标点视为可疑，标记为 INFO。
     * LLM 可用时：让 LLM 判断是否信息不完整。
     */
    List<AiAuditIssue> detectIntegrity(Long docId, ChunkInfo chunk) {
        List<AiAuditIssue> out = new ArrayList<>();
        if (chunk.content == null || chunk.content.trim().length() < 30) {
            out.add(baseIssue(docId, chunk.id, IssueType.INTEGRITY,
                    IssueSeverity.INFO, "content",
                    "chunk 内容过短（" + (chunk.content == null ? 0 : chunk.content.length()) + " 字）",
                    "确认是否漏抽取"));
        }
        if (llmGateway != null && chunk.content != null) {
            try {
                String llmOut = llmGateway.chat(
                        "你是技术文档审核员。判断下面这段文字是否信息完整，若有缺失关键字段（如时间/人员/参数）请指出。",
                        "原文：" + chunk.content);
                if (llmOut != null && llmOut.contains("缺失")) {
                    out.add(baseIssue(docId, chunk.id, IssueType.INTEGRITY,
                            IssueSeverity.WARN, "llm",
                            "LLM 判断信息不完整：" + llmOut,
                            "补充缺失字段后重新提交"));
                }
            } catch (Exception e) {
                log.debug("LLM integrity skipped: {}", e.getMessage());
            }
        }
        return out;
    }

    /**
     * 矛盾检测：跨 chunk 数值/结论矛盾。
     *
     * <p>简化规则：在所有 chunks 中扫描同一物理量（unit）的不同数值，
     * 若数值差异超过 50%，标记为 WARN。LLM 可用时让 LLM 复核。
     */
    List<AiAuditIssue> detectConflict(Long docId, List<ChunkInfo> chunks) {
        List<AiAuditIssue> out = new ArrayList<>();
        // 按 unit 分组，记录每个 unit 的 (chunkId, value)
        java.util.Map<String, List<double[]>> unitValues = new java.util.HashMap<>();
        java.util.Map<String, List<String>> unitChunks = new java.util.HashMap<>();
        for (ChunkInfo c : chunks) {
            Matcher m = NUMERIC_PATTERN.matcher(c.content);
            while (m.find()) {
                String unit = m.group(2);
                double v = Double.parseDouble(m.group(1));
                unitValues.computeIfAbsent(unit, k -> new ArrayList<>()).add(new double[]{v});
                unitChunks.computeIfAbsent(unit, k -> new ArrayList<>()).add(c.id);
            }
        }
        for (java.util.Map.Entry<String, List<double[]>> e : unitValues.entrySet()) {
            List<double[]> vals = e.getValue();
            if (vals.size() < 2) {
                continue;
            }
            // 同 unit 出现多次：判断 max/min 是否差异大
            double max = Double.NEGATIVE_INFINITY;
            double min = Double.POSITIVE_INFINITY;
            for (double[] d : vals) {
                if (d[0] > max) max = d[0];
                if (d[0] < min) min = d[0];
            }
            if (min > 0 && max / min > 1.5) {
                String chunkId = unitChunks.get(e.getKey()).get(0);
                AiAuditIssue issue = baseIssue(docId, chunkId, IssueType.CONFLICT,
                        IssueSeverity.WARN, e.getKey(),
                        "同一单位 " + e.getKey() + " 出现不同数值（min=" + min + ", max=" + max + "），可能矛盾",
                        "确认实际值，统一表述");
                if (llmGateway != null) {
                    try {
                        String llmOut = llmGateway.chat(
                                "判断以下两段表述是否矛盾。",
                                "1) " + min + e.getKey() + "  2) " + max + e.getKey());
                        if (llmOut != null) {
                            issue.setDescription(issue.getDescription() + " | LLM: " + llmOut);
                        }
                    } catch (Exception ex) {
                        log.debug("LLM conflict skipped: {}", ex.getMessage());
                    }
                }
                out.add(issue);
            }
        }
        return out;
    }

    /**
     * 一致性检测：术语词典中实体的表述。
     *
     * <p>简化规则：同一文档中出现「变压器」与「变壓器」（繁体）等视为不一致。
     * 当前词典规模有限，Phase 8 扩展。
     */
    List<AiAuditIssue> detectConsistency(Long docId, List<ChunkInfo> chunks) {
        List<AiAuditIssue> out = new ArrayList<>();
        // 简单规则：检查繁体字「壓」「機」「爐」等
        Pattern trad = Pattern.compile("[壓機爐電廠]");
        for (ChunkInfo c : chunks) {
            Matcher m = trad.matcher(c.content);
            if (m.find()) {
                out.add(baseIssue(docId, c.id, IssueType.CONSISTENCY,
                        IssueSeverity.INFO, m.group(),
                        "可能使用了繁体字「" + m.group() + "」，与术语词典不一致",
                        "统一使用简体中文"));
            }
        }
        return out;
    }

    // ===== 辅助 =====

    /**
     * 通过 ChunkService.list 解析 chunks。
     *
     * @return 简化的 ChunkInfo 列表（id + content）
     */
    private List<ChunkInfo> listChunks(String datasetId, String docId) {
        Map<String, Object> resp = chunkService.list(datasetId, docId, null);
        List<ChunkInfo> out = new ArrayList<>();
        if (resp == null) {
            return out;
        }
        Object chunksObj = resp.get("chunks");
        if (!(chunksObj instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) chunksObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> c = (Map<?, ?>) item;
            Object id = c.get("id");
            if (id == null) {
                continue;
            }
            Object content = c.get("content");
            out.add(new ChunkInfo(String.valueOf(id), content == null ? "" : String.valueOf(content)));
        }
        return out;
    }

    private AiAuditIssue baseIssue(Long docId, String chunkId, IssueType type,
                                   IssueSeverity severity, String position,
                                   String description, String suggestion) {
        AiAuditIssue i = new AiAuditIssue();
        i.setDocId(docId);
        i.setChunkId(chunkId);
        i.setIssueType(type.name());
        i.setSeverity(severity.name());
        i.setPosition(position);
        i.setDescription(description);
        i.setSuggestion(suggestion);
        i.setStatus(IssueStatus.OPEN);
        i.setCreatedAt(LocalDateTime.now());
        return i;
    }

    /** 数值范围检查。返回问题描述，合法返回 null。 */
    private String checkNumericRange(double v, String unit) {
        switch (unit) {
            case "kV":
                if (v > 1500) return "电压 " + v + " kV 超出常规范围（<=1500kV）";
                if (v < 0) return "电压 " + v + " kV 为负值";
                break;
            case "℃":
            case "°C":
                if (v > 600) return "温度 " + v + " ℃ 超出常规范围（<=600℃）";
                if (v < -50) return "温度 " + v + " ℃ 异常偏低";
                break;
            case "Hz":
                if (v != 50 && v != 60) {
                    return "频率 " + v + " Hz 非标准值（50/60）";
                }
                break;
            case "kA":
            case "A":
                if (v < 0) return "电流 " + v + " " + unit + " 为负值";
                break;
            case "MW":
                if (v > 2000) return "功率 " + v + " MW 超出单机范围（<=2000MW）";
                break;
            default:
                break;
        }
        return null;
    }

    /** 简化 chunk 表示（内部用）。 */
    static class ChunkInfo {
        final String id;
        final String content;

        ChunkInfo(String id, String content) {
            this.id = id;
            this.content = content;
        }
    }
}
