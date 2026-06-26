package com.company.knowledge.version.controller;

import com.company.knowledge.common.result.Result;
import com.company.knowledge.version.entity.VersionMeta;
import com.company.knowledge.version.service.VersionSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 版本管理 REST API。
 *
 * <p>路径：{@code /api/versions}
 */
@RestController
@RequestMapping("/api/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionSwitchService service;

    /**
     * 版本列表。
     *
     * @param datasetId 可选，按 dataset 过滤
     */
    @GetMapping
    public Result<List<VersionMeta>> list(@RequestParam(required = false) String datasetId) {
        return Result.success(service.list(datasetId));
    }

    /**
     * 当前线上版本。
     */
    @GetMapping("/online")
    public Result<VersionMeta> getCurrentOnline(@RequestParam String datasetId) {
        return Result.success(service.getCurrentOnline(datasetId));
    }

    /**
     * 创建新版本。
     */
    @PostMapping
    public Result<VersionMeta> create(@RequestBody CreateRequest req) {
        return Result.success(service.create(req.getDatasetId(), req.getEnv(),
                req.getVersionLabel(), req.getChangeLog()));
    }

    /**
     * 切到线上（旧 ONLINE → TEST，目标 → ONLINE）。
     */
    @PostMapping("/{id}/switch-online")
    public Result<VersionMeta> switchOnline(@PathVariable Long id) {
        return Result.success(service.switchToOnline(id));
    }

    /**
     * 切到测试环境。
     */
    @PostMapping("/{id}/switch-test")
    public Result<VersionMeta> switchTest(@PathVariable Long id) {
        return Result.success(service.switchToTest(id));
    }

    /**
     * 回滚到指定版本。
     */
    @PostMapping("/{id}/rollback")
    public Result<VersionMeta> rollback(@PathVariable Long id) {
        return Result.success(service.rollback(id));
    }

    // ===== 请求 DTO =====

    public static class CreateRequest {
        private String datasetId;
        private String env;
        private String versionLabel;
        private String changeLog;

        public String getDatasetId() { return datasetId; }
        public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
        public String getEnv() { return env; }
        public void setEnv(String env) { this.env = env; }
        public String getVersionLabel() { return versionLabel; }
        public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }
        public String getChangeLog() { return changeLog; }
        public void setChangeLog(String changeLog) { this.changeLog = changeLog; }
    }
}
