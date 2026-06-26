package com.company.knowledge.integration.hikvision;

import com.company.knowledge.integration.hikvision.dto.HikOrg;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 海康组织接口包装。对应《主平台组织以及人员信息接口.md》组织相关章节。
 *
 * <ul>
 *   <li>{@code POST /api/resource/v1/org/rootOrg}                              根组织</li>
 *   <li>{@code POST /api/resource/v1/org/parentOrgIndexCode/subOrgList}        下级组织</li>
 *   <li>{@code POST /api/resource/v1/org/timeRange}                            增量（按时间窗，含已删除）</li>
 * </ul>
 */
@Component
public class OrgApi {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final ArtemisClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrgApi(ArtemisClient client) {
        this.client = client;
    }

    /** 获取根组织列表（通常 1 条）。 */
    public List<HikOrg> listRoot() {
        Map<String, Object> resp = client.post("/api/resource/v1/org/rootOrg",
                Map.of("pageNo", 1, "pageSize", 1000), null);
        return extractList(resp);
    }

    /** 获取指定父组织的直接下级组织。 */
    public List<HikOrg> listSub(String parentOrgIndexCode) {
        Map<String, Object> resp = client.post("/api/resource/v1/org/parentOrgIndexCode/subOrgList",
                Map.of("parentOrgIndexCode", parentOrgIndexCode,
                        "pageNo", 1, "pageSize", 1000), null);
        return extractList(resp);
    }

    /**
     * 增量组织查询（含已删除）。
     * 同 {@link PersonApi#listByTimeRange} 的 1-48 小时约束。
     */
    public List<HikOrg> listByTimeRange(LocalDateTime start, LocalDateTime end, int pageNo, int pageSize) {
        Map<String, Object> resp = client.post("/api/resource/v1/org/timeRange",
                Map.of(
                        "startTime", start.format(ISO),
                        "endTime", end.format(ISO),
                        "pageNo", pageNo,
                        "pageSize", pageSize
                ), null);
        return extractList(resp);
    }

    @SuppressWarnings("unchecked")
    private List<HikOrg> extractList(Map<String, Object> resp) {
        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map)) return Collections.emptyList();
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object listObj = data.get("list");
        if (!(listObj instanceof List)) return Collections.emptyList();
        List<Map<String, Object>> raw = (List<Map<String, Object>>) listObj;
        return mapper.convertValue(raw, new TypeReference<List<HikOrg>>() {});
    }
}
