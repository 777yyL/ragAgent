package com.company.knowledge.agent;

import com.company.knowledge.agent.dto.CreateAgentRequest;
import com.company.knowledge.agent.dto.UpdateAgentRequest;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.integration.ragflow.AgentApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentService 单元测试。
 *
 * <p>验证：参数校验、canvasConfig→dsl 字段映射、get 从 list 过滤。
 */
class AgentServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void list_delegatesToAgentApi() {
        AgentApi api = mock(AgentApi.class);
        JsonNode ok = mapper.valueToTree(Map.of("code", 0, "data", new Object[]{Map.of("id", "a1")}));
        when(api.listAgents()).thenReturn(ok);

        AgentService service = new AgentService(api);
        List<Map<String, Object>> result = service.list();

        // AgentService.list 把 JsonNode data 数组转成 List<Map>
        assertEquals(1, result.size());
        assertEquals("a1", result.get(0).get("id"));
        verify(api).listAgents();
    }

    @Test
    void get_returnsFirstFromArray() throws Exception {
        AgentApi api = mock(AgentApi.class);
        JsonNode listResp = mapper.readTree("{\"code\":0,\"data\":[{\"id\":\"a1\",\"title\":\"t1\"},{\"id\":\"a2\"}]}");
        when(api.listAgents(isNull(), isNull(), eq("a1"), isNull())).thenReturn(listResp);

        AgentService service = new AgentService(api);
        Map<String, Object> result = service.get("a1");

        assertEquals("a1", result.get("id"));
        assertEquals("t1", result.get("title"));
    }

    @Test
    void get_throwsNotFoundWhenEmpty() throws Exception {
        AgentApi api = mock(AgentApi.class);
        when(api.listAgents(isNull(), isNull(), eq("missing"), isNull()))
                .thenReturn(mapper.readTree("{\"code\":0,\"data\":[]}"));

        AgentService service = new AgentService(api);
        BizException ex = assertThrows(BizException.class, () -> service.get("missing"));
        assertEquals(4040, ex.getCode());
    }

    @Test
    void get_throwsWhenIdIsEmpty() {
        AgentApi api = mock(AgentApi.class);
        AgentService service = new AgentService(api);
        assertThrows(BizException.class, () -> service.get(""));
        assertThrows(BizException.class, () -> service.get(null));
    }

    @Test
    void create_mapsCanvasConfigToDsl() {
        AgentApi api = mock(AgentApi.class);
        JsonNode ok = mapper.valueToTree(Map.of("code", 0));
        when(api.createAgent(anyMap())).thenReturn(ok);

        CreateAgentRequest req = new CreateAgentRequest();
        req.setTitle("汽机专家");
        Map<String, Object> canvas = new HashMap<>();
        canvas.put("components", Map.of());
        req.setCanvasConfig(canvas);
        req.setDescription("汽机故障诊断");
        req.setAvatar("base64");

        AgentService service = new AgentService(api);
        service.create(req);

        verify(api).createAgent(argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return "汽机专家".equals(m.get("title"))
                    && m.get("dsl") == canvas
                    && "汽机故障诊断".equals(m.get("description"))
                    && "base64".equals(m.get("avatar"));
        }));
    }

    @Test
    void create_requiresTitle() {
        AgentApi api = mock(AgentApi.class);
        AgentService service = new AgentService(api);

        CreateAgentRequest req = new CreateAgentRequest();
        req.setCanvasConfig(new HashMap<>());
        BizException ex = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(4002, ex.getCode());

        verifyNoInteractions(api);
    }

    @Test
    void create_requiresCanvasConfig() {
        AgentApi api = mock(AgentApi.class);
        AgentService service = new AgentService(api);

        CreateAgentRequest req = new CreateAgentRequest();
        req.setTitle("t1");
        BizException ex = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(4002, ex.getCode());

        verifyNoInteractions(api);
    }

    @Test
    void update_partialMapsOnlyProvidedFields() {
        AgentApi api = mock(AgentApi.class);
        when(api.updateAgent(anyString(), anyMap())).thenReturn(mapper.valueToTree(Map.of("code", 0)));

        UpdateAgentRequest req = new UpdateAgentRequest();
        req.setTitle("new-title");

        AgentService service = new AgentService(api);
        service.update("a1", req);

        verify(api).updateAgent(eq("a1"), argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return "new-title".equals(m.get("title"))
                    && !m.containsKey("dsl")
                    && !m.containsKey("description");
        }));
    }

    @Test
    void update_requiresId() {
        AgentApi api = mock(AgentApi.class);
        AgentService service = new AgentService(api);
        UpdateAgentRequest req = new UpdateAgentRequest();
        assertThrows(BizException.class, () -> service.update(null, req));
        assertThrows(BizException.class, () -> service.update("", req));
    }

    @Test
    void delete_delegates() {
        AgentApi api = mock(AgentApi.class);
        when(api.deleteAgent(anyString())).thenReturn(mapper.valueToTree(Map.of("code", 0)));

        AgentService service = new AgentService(api);
        service.delete("a1");

        verify(api).deleteAgent("a1");
    }

    @Test
    void delete_requiresId() {
        AgentApi api = mock(AgentApi.class);
        AgentService service = new AgentService(api);
        assertThrows(BizException.class, () -> service.delete(null));
        assertThrows(BizException.class, () -> service.delete(""));
    }
}
