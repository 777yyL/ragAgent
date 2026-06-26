package com.company.knowledge.health;

import com.company.knowledge.integration.ragflow.DatasetApi;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查端点。
 *
 * <p>返回 app 与 ragflow 的连通性状态：
 * <ul>
 *   <li>{@code app}: 本应用存活状态（始终 UP，能响应即说明存活）</li>
 *   <li>{@code ragflow}: 调 {@link DatasetApi#list(int, int)} 探测，UP/DOWN + 失败原因</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DatasetApi datasetApi;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("app", "UP");

        Map<String, Object> ragflow = new LinkedHashMap<>();
        try {
            JsonNode rsp = datasetApi.list(1, 1);
            ragflow.put("status", "UP");
        } catch (Exception e) {
            log.warn("ragflow probe failed: {}", e.getMessage());
            ragflow.put("status", "DOWN");
            ragflow.put("error", e.getMessage());
        }
        status.put("ragflow", ragflow);
        return status;
    }
}
