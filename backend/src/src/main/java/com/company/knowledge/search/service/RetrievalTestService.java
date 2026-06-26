package com.company.knowledge.search.service;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.common.result.PageResult;
import com.company.knowledge.search.dto.RetrievalTestRequest;
import com.company.knowledge.search.dto.SearchQuery;
import com.company.knowledge.search.dto.SearchResult;
import com.company.knowledge.search.entity.SearchLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 检索测试 Service（管理员调参专用）。
 *
 * <p>核心场景：运营/管理员在不带权限过滤的前提下，反复调整 top_k / similarity_threshold /
 * rerank_id 等参数，观察检索效果。每次测试结果都记入 {@code search_log}（type=TEST），
 * 用于回看调参历史。
 *
 * <p>与 {@link SearchService} 的关系：本服务复用 {@link SearchService#doSearch} 的
 * 「拼请求 + 解析响应 + 埋点」逻辑，仅以 {@code type=TEST} 调用（跳过 metadata_condition）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalTestService {

    private final SearchService searchService;
    private final SearchLogService searchLogService;

    /**
     * 执行一次检索测试（不带权限过滤）。
     *
     * @param req 测试请求（datasetId/question/topK/...）
     * @return 检索结果
     */
    public SearchResult test(RetrievalTestRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (req.getDatasetId() == null || req.getDatasetId().isEmpty()) {
            throw new IllegalArgumentException("datasetId is required");
        }
        if (req.getQuestion() == null || req.getQuestion().isEmpty()) {
            throw new IllegalArgumentException("question is required");
        }

        // 复用 SearchService.doSearch（type=TEST 跳过 metadata_condition 注入）
        SearchQuery query = toSearchQuery(req);
        UserContext.CurrentUser user = UserContext.require();
        String personId = user != null ? user.getPersonId() : null;
        return searchService.doSearch(query, personId, "TEST");
    }

    /**
     * 检索测试历史列表（type=TEST 的 search_log）。
     *
     * <p>当前 {@link SearchLogService#listHistory} 返回所有类型的记录，
     * 这里在 Service 层做内存过滤。若后续数据量大，可在 Mapper 增加 type 条件。
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     */
    public PageResult<SearchLog> listHistory(int pageNo, int pageSize) {
        PageResult<SearchLog> all = searchLogService.listHistory(pageNo, pageSize);
        // 仅保留 type=TEST
        java.util.List<SearchLog> filtered = new java.util.ArrayList<>();
        if (all.getList() != null) {
            for (SearchLog log : all.getList()) {
                if ("TEST".equals(log.getType())) {
                    filtered.add(log);
                }
            }
        }
        // 注意：filtered 的 size 可能小于 pageSize，但 total 仍返回全量；
        // 调用方需理解这是「过滤后的页内数据」。
        return PageResult.of(all.getTotal(), all.getPageNo(), all.getPageSize(), filtered);
    }

    /**
     * 把 {@link RetrievalTestRequest} 转为 {@link SearchQuery}（供 {@link SearchService#doSearch} 使用）。
     */
    private SearchQuery toSearchQuery(RetrievalTestRequest req) {
        SearchQuery q = new SearchQuery();
        q.setQuestion(req.getQuestion());
        q.setDatasetIds(Collections.singletonList(req.getDatasetId()));
        q.setTopK(req.getTopK());
        q.setSimilarityThreshold(req.getSimilarityThreshold());
        q.setVectorSimilarityWeight(req.getVectorSimilarityWeight());
        q.setRerankId(req.getRerankId());
        q.setUseKg(req.getUseKg());
        q.setKeyword(req.getKeyword());
        q.setHighlight(req.getHighlight());
        // 测试场景默认不启用跨语言
        q.setCrossLanguages(null);
        return q;
    }
}
