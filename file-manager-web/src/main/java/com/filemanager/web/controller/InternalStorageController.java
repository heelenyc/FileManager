package com.filemanager.web.controller;

import com.filemanager.common.result.Result;
import com.filemanager.storage.service.LocalStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 内部存储接口（节点间通信）
 * 用于分布式存储时，节点之间互相调用存储和读取文件
 */
@Slf4j
@RestController
@RequestMapping("/internal")
public class InternalStorageController {

    @Autowired
    private LocalStorageService localStorageService;

    @Value("${file.internal.token:internal-secret-token}")
    private String internalToken;

    /**
     * 存储分片（其他节点调用）
     * @param token 内部认证token
     * @param storagePath 存储路径
     * @param data 分片数据
     */
    @PostMapping("/store")
    public Result<Void> store(
            @RequestParam String token,
            @RequestParam String storagePath,
            @RequestBody byte[] data) {
        // 验证内部token
        if (!internalToken.equals(token)) {
            log.warn("内部存储接口认证失败");
            return Result.fail(401, "认证失败");
        }

        try {
            localStorageService.store(storagePath, new ByteArrayInputStream(data));
            log.info("内部存储成功: path={}, size={}", storagePath, data.length);
            return Result.success();
        } catch (Exception e) {
            log.error("内部存储失败: path={}", storagePath, e);
            return Result.fail(500, "存储失败: " + e.getMessage());
        }
    }

    /**
     * 读取分片（其他节点调用）
     * 直接返回二进制数据，不包装在Result中（避免JSON序列化问题）
     * @param token 内部认证token
     * @param storagePath 存储路径
     */
    @GetMapping("/read")
    public byte[] read(
            @RequestParam String token,
            @RequestParam String storagePath) {
        // 验证内部token
        if (!internalToken.equals(token)) {
            log.warn("内部读取接口认证失败");
            throw new RuntimeException("认证失败");
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            localStorageService.read(storagePath, baos);
            byte[] data = baos.toByteArray();
            log.info("内部读取成功: path={}, size={}", storagePath, data.length);
            return data;
        } catch (Exception e) {
            log.error("内部读取失败: path={}", storagePath, e);
            throw new RuntimeException("读取失败: " + e.getMessage());
        }
    }

    /**
     * 删除分片（其他节点调用）
     * @param token 内部认证token
     * @param storagePath 存储路径
     */
    @DeleteMapping("/delete")
    public Result<Void> delete(
            @RequestParam String token,
            @RequestParam String storagePath) {
        // 验证内部token
        if (!internalToken.equals(token)) {
            log.warn("内部删除接口认证失败");
            return Result.fail(401, "认证失败");
        }

        try {
            localStorageService.delete(storagePath);
            log.info("内部删除成功: path={}", storagePath);
            return Result.success();
        } catch (Exception e) {
            log.error("内部删除失败: path={}", storagePath, e);
            return Result.fail(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 检查节点健康状态
     */
    @GetMapping("/health")
    public Result<String> health(@RequestParam String token) {
        if (!internalToken.equals(token)) {
            return Result.fail(401, "认证失败");
        }
        return Result.success("healthy");
    }
}