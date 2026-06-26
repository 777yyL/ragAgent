package com.company.knowledge.search.controller;

import com.company.knowledge.common.result.Result;
import com.company.knowledge.search.dto.SearchQuery;
import com.company.knowledge.search.dto.SearchResult;
import com.company.knowledge.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索 REST API。
 *
 * <p>路径前缀：{@code /api/search}
 *
 * <ul>
 *   <li>POST /api/search — 用户检索（权限预过滤）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 用户检索。
     *
     * <p>权限元数据条件由 {@code PermissionIndexService} 自动注入，
     * 调用方只需传 question / datasetIds / topK 等业务参数。
     */
    @PostMapping
    public Result<SearchResult> search(@RequestBody SearchQuery query) {
        return Result.success(searchService.search(query));
    }
}
