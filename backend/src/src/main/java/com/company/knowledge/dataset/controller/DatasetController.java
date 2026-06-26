package com.company.knowledge.dataset.controller;

import com.company.knowledge.common.result.Result;
import com.company.knowledge.dataset.DatasetService;
import com.company.knowledge.dataset.dto.CreateDatasetRequest;
import com.company.knowledge.dataset.dto.UpdateDatasetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
            @RequestParam(required = false) String keywords) {
        return Result.success(datasetService.list(page, pageSize, keywords));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateDatasetRequest req) {
        return Result.success(datasetService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @RequestBody UpdateDatasetRequest req) {
        return Result.success(datasetService.update(id, req));
    }

    @DeleteMapping
    public Result<Map<String, Object>> delete(@RequestParam List<String> ids) {
        return Result.success(datasetService.delete(ids));
    }
}
