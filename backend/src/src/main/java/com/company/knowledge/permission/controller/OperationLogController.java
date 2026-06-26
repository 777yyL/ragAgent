package com.company.knowledge.permission.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.company.knowledge.common.result.PageResult;
import com.company.knowledge.common.result.Result;
import com.company.knowledge.permission.entity.OperationLogEntry;
import com.company.knowledge.permission.mapper.OperationLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作审计日志 REST 端点（等保三级查询接口）。
 *
 * <ul>
 *   <li>{@code GET /api/operation-logs} - 分页列表，支持按 user/action/resourceType/timeRange 筛选</li>
 * </ul>
 *
 * <p>访问此接口本身建议挂 {@code @RequiresPermission(resourceType="AUDIT_LOG", action="VIEW")}
 * 或限定 ADMIN 角色（由调用方在 Controller 方法上加注解决，本类不内嵌）。
 *
 * <p>分页说明：项目当前未启用 MyBatis-Plus 分页插件，本接口采用「全量查询 + 内存分页」。
 * 等保审计接口数据量受时间窗口约束，且通常按最近时间排序，性能可接受。
 * 若后续接入 PaginationInnerInterceptor，可改回 {@code mapper.selectPage}。
 */
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogMapper mapper;

    /**
     * 分页查询操作日志。
     *
     * @param query 筛选条件（page/pageSize/userId/action/resourceType/startTime/endTime）
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<OperationLogEntry>> list(OperationLogQuery query) {
        int pageNo = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20 : Math.min(query.getPageSize(), 500);

        LambdaQueryWrapper<OperationLogEntry> wrapper = Wrappers.<OperationLogEntry>lambdaQuery()
                .eq(!StringUtils.isEmpty(query.getUserId()),
                        OperationLogEntry::getUserId, query.getUserId())
                .eq(!StringUtils.isEmpty(query.getAction()),
                        OperationLogEntry::getAction, query.getAction())
                .eq(!StringUtils.isEmpty(query.getResourceType()),
                        OperationLogEntry::getResourceType, query.getResourceType())
                .ge(query.getStartTime() != null,
                        OperationLogEntry::getCreatedAt, query.getStartTime())
                .le(query.getEndTime() != null,
                        OperationLogEntry::getCreatedAt, query.getEndTime())
                .orderByDesc(OperationLogEntry::getCreatedAt);

        List<OperationLogEntry> all = mapper.selectList(wrapper);
        int total = all.size();
        int from = Math.min((pageNo - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<OperationLogEntry> slice = all.subList(from, to);

        return Result.success(PageResult.of(total, pageNo, pageSize, slice));
    }

    /** 查询参数 */
    @Data
    public static class OperationLogQuery {
        /** 页码，默认 1 */
        private Integer page;
        /** 每页大小，默认 20，上限 500 */
        private Integer pageSize;
        /** 操作人 personId */
        private String userId;
        /** 动作编码 */
        private String action;
        /** 资源类型 */
        private String resourceType;
        /** 起始时间（包含） */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime startTime;
        /** 截止时间（包含） */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime endTime;
    }
}
