package com.company.knowledge.integration.hikvision;

import com.company.knowledge.integration.hikvision.dto.HikPerson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 海康人员接口包装。对应《主平台组织以及人员信息接口.md》人员相关章节。
 *
 * <ul>
 *   <li>{@code POST /api/resource/v2/person/personList}                全量分页</li>
 *   <li>{@code POST /api/resource/v2/person/advance/personList}        高级查询（按 ID/姓名/组织）</li>
 *   <li>{@code POST /api/resource/v1/person/personList/timeRange}      增量（按时间窗，含已删除）</li>
 * </ul>
 */
@Component
public class PersonApi {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final ArtemisClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public PersonApi(ArtemisClient client) {
        this.client = client;
    }

    /** 全量人员列表（分页）。 */
    public List<HikPerson> listAll(int pageNo, int pageSize) {
        Map<String, Object> resp = client.post("/api/resource/v2/person/personList",
                Map.of("pageNo", pageNo, "pageSize", pageSize), null);
        return extractList(resp);
    }

    /** 按 personId 精确查询（SSO 登录时兜底用）。 */
    public HikPerson getById(String personId) {
        Map<String, Object> resp = client.post("/api/resource/v2/person/advance/personList",
                Map.of("personIds", personId, "pageNo", 1, "pageSize", 1), null);
        List<HikPerson> list = extractList(resp);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 增量人员查询（含已删除）。
     *
     * <p>海康约束：start/end 时间差必须在 1-48 小时内。调用方需控制时间窗，
     * {@code PersonSyncService} 在 since > 40h 时降级为全量。
     */
    public List<HikPerson> listByTimeRange(LocalDateTime start, LocalDateTime end, int pageNo, int pageSize) {
        Map<String, Object> resp = client.post("/api/resource/v1/person/personList/timeRange",
                Map.of(
                        "startTime", start.format(ISO),
                        "endTime", end.format(ISO),
                        "pageNo", pageNo,
                        "pageSize", pageSize
                ), null);
        return extractList(resp);
    }

    @SuppressWarnings("unchecked")
    private List<HikPerson> extractList(Map<String, Object> resp) {
        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map)) return Collections.emptyList();
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object listObj = data.get("list");
        if (!(listObj instanceof List)) return Collections.emptyList();
        List<Map<String, Object>> raw = (List<Map<String, Object>>) listObj;
        return mapper.convertValue(raw, new TypeReference<List<HikPerson>>() {});
    }
}
