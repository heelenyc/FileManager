package com.filemanager.web.controller;

import com.filemanager.common.annotation.SkipAuth;
import com.filemanager.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/config")
@SkipAuth
public class SystemConfigController {

    @Value("${file.storage.chunk-size}")
    private long chunkSize;

    @Value("${file.storage.max-file-size}")
    private long maxFileSize;

    @GetMapping
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("chunkSize", chunkSize);
        config.put("chunkSizeMB", chunkSize / (1024 * 1024));
        config.put("maxFileSizeMB", maxFileSize / (1024 * 1024));
        return Result.success(config);
    }
}
