package com.company.knowledge.audit.controller;

import com.company.knowledge.audit.entity.AuditNode;
import com.company.knowledge.audit.entity.AuditTemplate;
import com.company.knowledge.audit.service.AuditTemplateService;
import com.company.knowledge.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核模板管理 REST API。
 *
 * <p>路径：{@code /api/audit/templates}
 */
@RestController
@RequestMapping("/api/audit/templates")
@RequiredArgsConstructor
public class AuditTemplateController {

    private final AuditTemplateService service;

    /**
     * 模板列表。
     *
     * @param businessType 可选，按业务分类过滤
     * @param enabled      可选，true/false
     */
    @GetMapping
    public Result<List<AuditTemplate>> list(@RequestParam(required = false) String businessType,
                                            @RequestParam(required = false) Boolean enabled) {
        return Result.success(service.list(businessType, enabled));
    }

    /**
     * 推荐模板（按业务分类）。
     */
    @GetMapping("/recommend")
    public Result<AuditTemplate> recommend(@RequestParam(required = false) String businessType) {
        return Result.success(service.recommendTemplate(businessType));
    }

    /**
     * 单个模板详情。
     */
    @GetMapping("/{id}")
    public Result<AuditTemplate> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    /**
     * 创建模板。
     */
    @PostMapping
    public Result<AuditTemplate> create(@RequestBody CreateRequest req) {
        return Result.success(service.create(req.getName(), req.getBusinessType(), req.getNodes()));
    }

    /**
     * 更新模板。
     */
    @PutMapping("/{id}")
    public Result<AuditTemplate> update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        return Result.success(service.update(id, req.getName(), req.getBusinessType(),
                req.getNodes(), req.getEnabled()));
    }

    /**
     * 删除模板。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    // ===== 请求 DTO =====

    public static class CreateRequest {
        private String name;
        private String businessType;
        private List<AuditNode> nodes;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public List<AuditNode> getNodes() { return nodes; }
        public void setNodes(List<AuditNode> nodes) { this.nodes = nodes; }
    }

    public static class UpdateRequest {
        private String name;
        private String businessType;
        private List<AuditNode> nodes;
        private Boolean enabled;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public List<AuditNode> getNodes() { return nodes; }
        public void setNodes(List<AuditNode> nodes) { this.nodes = nodes; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}
