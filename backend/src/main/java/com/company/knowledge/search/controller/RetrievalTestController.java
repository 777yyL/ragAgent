package com.company.knowledge.search.controller;

import com.company.knowledge.common.result.PageResult;
import com.company.knowledge.common.result.Result;
import com.company.knowledge.search.dto.RetrievalTestRequest;
import com.company.knowledge.search.dto.SearchResult;
import com.company.knowledge.search.entity.SearchLog;
import com.company.knowledge.search.service.RetrievalTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索测试 REST API（管理员调参）。
 *
 * <p>路径前缀：{@code /api/retrieval-test}
 *
 * <ul>
 *   <li>POST /api/retrieval-test             — 发起一次检索测试（不带权限过滤）</li>
 *   <li>GET  /api/retrieval-test/history      — 检索测试历史</li>
 * </ul>
 *
 * <p>访问建议：生产环境在方法上加 {@code @RequiresPermission(resourceType="RETRIEVAL_TEST",
 * action="EXECUTE")} 或限定 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/retrieval-test")
@RequiredArgsConstructor
public class RetrievalTestController {

    private final RetrievalTestService retrievalTestService;

    /**
     * 发起检索测试。
     */
    @PostMapping
    public Result<SearchResult> test(@RequestBody RetrievalTestRequest req) {
        return Result.success(retrievalTestService.test(req));
    }

    /**
     * 检索测试历史（type=TEST）。
     */
    @GetMapping("/history")
    public Result<PageResult<SearchLog>> listHistory(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        return Result.success(retrievalTestService.listHistory(p, ps));
    }
}
