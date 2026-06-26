package com.company.knowledge.permission.service;

import com.company.knowledge.permission.entity.OperationLogEntry;
import com.company.knowledge.permission.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步写入器。
 *
 * <p>独立 Bean，方法标注 {@link Async}，由 {@code OperationLogAspect}
 * 通过 Spring 代理调用，确保走异步线程池，不阻塞业务方法返回。
 *
 * <p>失败仅记录 WARN，不影响主流程（审计不能压垮业务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogWriter {

    private final OperationLogMapper mapper;

    /**
     * 异步插入一条操作日志。
     *
     * @param logEntry 日志实体
     */
    @Async
    public void writeAsync(OperationLogEntry logEntry) {
        try {
            mapper.insert(logEntry);
        } catch (Exception e) {
            // 审计写入失败不能让业务回滚；只 WARN
            log.warn("operation log insert failed, action={}, user={}: {}",
                    logEntry.getAction(), logEntry.getUserId(), e.getMessage());
        }
    }
}
