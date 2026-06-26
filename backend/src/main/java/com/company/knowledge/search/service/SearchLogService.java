package com.company.knowledge.search.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.knowledge.common.result.PageResult;
import com.company.knowledge.search.entity.SearchLog;
import com.company.knowledge.search.mapper.SearchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 检索埋点 Service。
 *
 * <p>能力：
 * <ul>
 *   <li>{@link #record} 异步写入单条检索日志</li>
 *   <li>{@link #listTrendingKeywords} 热门关键词聚合（最近 N 天 TopN）</li>
 *   <li>{@link #listRecentByUser} 用户最近检索记录</li>
 *   <li>{@link #countByDateRange} 时间段检索总量</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogMapper searchLogMapper;

    /**
     * 异步记录一次检索。
     *
     * <p>异步执行，失败仅记日志不抛异常（埋点失败不能影响主流程）。
     */
    @Async
    public void record(SearchLog logEntry) {
        if (logEntry == null) {
            return;
        }
        try {
            searchLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("record search log failed, query={}: {}",
                    logEntry.getQuery(), e.getMessage());
        }
    }

    /**
     * 热门关键词聚合。
     *
     * @param days 统计最近 N 天（如 7）
     * @param topN 取前 N 条
     * @return 元素 Map：{@code {keyword, cnt}}；异常返回空列表
     */
    public List<Map<String, Object>> listTrendingKeywords(int days, int topN) {
        if (days <= 0 || topN <= 0) {
            return Collections.emptyList();
        }
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        try {
            return searchLogMapper.selectTrendingKeywords(start, topN);
        } catch (Exception e) {
            log.warn("listTrendingKeywords failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询用户最近检索记录。
     *
     * @param userId 用户 personId
     * @param limit  返回条数（建议 &le; 50）
     */
    public List<SearchLog> listRecentByUser(String userId, int limit) {
        if (userId == null || userId.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        QueryWrapper<SearchLog> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("created_at").last("LIMIT " + limit);
        try {
            return searchLogMapper.selectList(qw);
        } catch (Exception e) {
            log.warn("listRecentByUser failed, userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 统计 {@code [start, end)} 时间段内的检索总量。
     */
    public long countByDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0L;
        }
        try {
            return searchLogMapper.countByDateRange(start, end);
        } catch (Exception e) {
            log.warn("countByDateRange failed: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 分页查询检索历史（管理端用，按时间倒序）。
     *
     * <p>项目当前未启用 MyBatis-Plus 分页插件，采用 {@code LIMIT + OFFSET} + {@code selectCount}
     * 的手写分页方式（与 {@code OperationLogController} 风格一致）。
     *
     * @param pageNo   页码（1-based）
     * @param pageSize 每页条数
     */
    public PageResult<SearchLog> listHistory(int pageNo, int pageSize) {
        if (pageNo <= 0) {
            pageNo = 1;
        }
        if (pageSize <= 0 || pageSize > 200) {
            pageSize = 20;
        }
        int offset = (pageNo - 1) * pageSize;
        QueryWrapper<SearchLog> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at");
        qw.last("LIMIT " + pageSize + " OFFSET " + offset);
        try {
            long total = searchLogMapper.selectCount(null);
            List<SearchLog> records = searchLogMapper.selectList(qw);
            return PageResult.of(total, pageNo, pageSize, records);
        } catch (Exception e) {
            log.warn("listHistory failed: {}", e.getMessage());
            return PageResult.of(0L, pageNo, pageSize, Collections.emptyList());
        }
    }
}
