package com.company.knowledge.chunk.dto;

import lombok.Data;

import java.util.List;

/**
 * 人工标注更新请求 DTO。
 *
 * <p>对应 RAGFlow chunk 的：
 * <ul>
 *   <li>{@code important_keywords}（重要关键词，影响向量权重）</li>
 *   <li>{@code questions}（辅助问句，提升问答命中率）</li>
 *   <li>{@code tag_kwd}（业务标签，用于分类检索）</li>
 *   <li>{@code content}（chunk 正文，支持人工校对修正）</li>
 * </ul>
 *
 * <p>所有字段都是可选的，只有非 null 的字段会被更新（{@code AnnotationService} 按非空判断拼请求体）。
 */
@Data
public class AnnotationUpdateRequest {

    /** 重要关键词列表，{@code null} 表示不更新 */
    private List<String> importantKeywords;

    /** 辅助问句列表，{@code null} 表示不更新 */
    private List<String> questions;

    /** 业务标签列表，{@code null} 表示不更新 */
    private List<String> tagKwd;

    /** chunk 正文文本，{@code null} 表示不更新 */
    private String content;
}
