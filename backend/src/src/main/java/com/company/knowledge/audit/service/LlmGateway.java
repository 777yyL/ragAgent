package com.company.knowledge.audit.service;

/**
 * LLM 网关抽象接口。Phase 4 仅定义接口，Phase 8 由具体实现对接
 * （如 RAGFlow chat completion、文心/通义/智谱等）。
 *
 * <p>无实现时，Spring 容器中不存在该类型 Bean，
 * {@link AiAuditService} 通过 {@code @Autowired(required = false)} 注入，
 * 为 null 时跳过 LLM 检测，仅走规则检测。
 */
public interface LlmGateway {

    /**
     * 通用 chat 接口。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return LLM 输出文本
     */
    String chat(String systemPrompt, String userPrompt);
}
