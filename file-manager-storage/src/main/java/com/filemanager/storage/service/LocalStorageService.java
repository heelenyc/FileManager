package com.filemanager.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
public class LocalStorageService {

    @Value("${file.storage.base-path}")
    private String basePath;

    public void store(String relativePath, InputStream inputStream) {
        Path fullPath = resolvePath(relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            try (InputStream is = inputStream;
                 OutputStream os = Files.newOutputStream(fullPath,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                is.transferTo(os);
            }
        } catch (IOException e) {
            log.error("文件写入失败: path={}", fullPath, e);
            throw new RuntimeException("文件写入失败: " + relativePath, e);
        }
    }

    public void read(String relativePath, OutputStream outputStream) {
        Path fullPath = resolvePath(relativePath);
        if (!Files.exists(fullPath)) {
            throw new RuntimeException("文件不存在: " + relativePath);
        }
        try (InputStream is = Files.newInputStream(fullPath)) {
            is.transferTo(outputStream);
        } catch (IOException e) {
            log.error("文件读取失败: path={}", fullPath, e);
            throw new RuntimeException("文件读取失败: " + relativePath, e);
        }
    }

    public void delete(String relativePath) {
        Path fullPath = resolvePath(relativePath);
        if (Files.exists(fullPath)) {
            try {
                Files.delete(fullPath);
                log.debug("文件已删除: {}", fullPath);
            } catch (IOException e) {
                log.error("文件删除失败: path={}", fullPath, e);
                throw new RuntimeException("文件删除失败: " + relativePath, e);
            }
        }
    }

    public boolean exists(String relativePath) {
        return Files.exists(resolvePath(relativePath));
    }

    private Path resolvePath(String relativePath) {
        return Paths.get(basePath, relativePath).toAbsolutePath().normalize();
    }
}
