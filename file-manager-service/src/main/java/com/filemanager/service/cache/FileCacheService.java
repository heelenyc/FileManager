package com.filemanager.service.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filemanager.dao.mapper.FileMetadataMapper;
import com.filemanager.model.entity.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileCacheService {

    private final StringRedisTemplate redisTemplate;
    private final FileMetadataMapper fileMetadataMapper;

    private static final String FILE_CACHE_KEY = "file:metadata:";
    private static final long CACHE_TTL_HOURS = 2;

    /**
     * 获取文件元数据（先查缓存，未命中查DB并回填）
     */
    public FileMetadata getFileMetadata(String fileKey) {
        String cacheKey = FILE_CACHE_KEY + fileKey;

        // 查缓存
        String cachedFileName = (String) redisTemplate.opsForHash().get(cacheKey, "fileName");
        if (cachedFileName != null) {
            return buildFromCache(cacheKey, fileKey);
        }

        // 查 DB
        FileMetadata metadata = fileMetadataMapper.selectOne(
                new LambdaQueryWrapper<FileMetadata>().eq(FileMetadata::getFileKey, fileKey)
        );
        if (metadata == null) {
            return null;
        }

        // 回填缓存
        cacheFileMetadata(metadata);
        return metadata;
    }

    /**
     * 缓存文件元数据
     */
    public void cacheFileMetadata(FileMetadata metadata) {
        String cacheKey = FILE_CACHE_KEY + metadata.getFileKey();
        redisTemplate.opsForHash().put(cacheKey, "id", String.valueOf(metadata.getId()));
        redisTemplate.opsForHash().put(cacheKey, "fileName", metadata.getFileName() != null ? metadata.getFileName() : "");
        redisTemplate.opsForHash().put(cacheKey, "fileSize", String.valueOf(metadata.getFileSize()));
        redisTemplate.opsForHash().put(cacheKey, "contentType", metadata.getContentType() != null ? metadata.getContentType() : "");
        redisTemplate.opsForHash().put(cacheKey, "chunkCount", String.valueOf(metadata.getChunkCount()));
        redisTemplate.opsForHash().put(cacheKey, "storagePath", metadata.getStoragePath() != null ? metadata.getStoragePath() : "");
        redisTemplate.opsForHash().put(cacheKey, "aesKeyEncrypted", metadata.getAesKeyEncrypted());
        redisTemplate.expire(cacheKey, CACHE_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 清除文件元数据缓存
     */
    public void evictFileMetadata(String fileKey) {
        redisTemplate.delete(FILE_CACHE_KEY + fileKey);
    }

    private FileMetadata buildFromCache(String cacheKey, String fileKey) {
        FileMetadata metadata = new FileMetadata();
        metadata.setId(Long.parseLong((String) redisTemplate.opsForHash().get(cacheKey, "id")));
        metadata.setFileKey(fileKey);
        metadata.setFileName((String) redisTemplate.opsForHash().get(cacheKey, "fileName"));
        metadata.setFileSize(Long.parseLong((String) redisTemplate.opsForHash().get(cacheKey, "fileSize")));
        String contentType = (String) redisTemplate.opsForHash().get(cacheKey, "contentType");
        metadata.setContentType(contentType != null && !contentType.isEmpty() ? contentType : null);
        metadata.setChunkCount(Integer.parseInt((String) redisTemplate.opsForHash().get(cacheKey, "chunkCount")));
        String storagePath = (String) redisTemplate.opsForHash().get(cacheKey, "storagePath");
        metadata.setStoragePath(storagePath != null && !storagePath.isEmpty() ? storagePath : null);
        metadata.setAesKeyEncrypted((String) redisTemplate.opsForHash().get(cacheKey, "aesKeyEncrypted"));
        return metadata;
    }
}
