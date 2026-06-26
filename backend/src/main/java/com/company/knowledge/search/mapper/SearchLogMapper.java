package com.company.knowledge.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.search.entity.SearchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 检索埋点 Mapper。
 *
 * <p>统计 SQL 在 {@code resources/mapper/search/SearchLogMapper.xml}。
 */
@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {

    /**
     * 按时间窗聚合 query 词频 TopN。
     *
     * @param start 起始时间（含）
     * @param topN  取前 N 条
     * @return 元素 Map：{@code {keyword, cnt}}
     */
    List<Map<String, Object>> selectTrendingKeywords(@Param("start") LocalDateTime start,
                                                     @Param("topN") int topN);

    /**
     * 统计 {@code [start, end)} 时间段内的检索量。
     */
    long countByDateRange(@Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);
}
