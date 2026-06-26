package com.filemanager.service.file;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filemanager.common.exception.BusinessException;
import com.filemanager.common.result.ResultCode;
import com.filemanager.dao.mapper.FileChunkMapper;
import com.filemanager.dao.mapper.FileMetadataMapper;
import com.filemanager.dao.mapper.RolePermissionMapper;
import com.filemanager.dao.mapper.UserMapper;
import com.filemanager.model.entity.FileChunk;
import com.filemanager.model.entity.FileMetadata;
import com.filemanager.model.entity.StorageNode;
import com.filemanager.model.entity.User;
import com.filemanager.model.vo.FileVO;
import com.filemanager.model.vo.InitUploadVO;
import com.filemanager.service.cache.FileCacheService;
import com.filemanager.service.lock.DistributedLockService;
import com.filemanager.service.node.ConsistentHash;
import com.filemanager.service.node.NodeRegistryService;
import com.filemanager.service.node.ReplicaService;
import com.filemanager.service.storage.DistributedStorageService;
import com.filemanager.storage.service.AesEncryptionService;
import com.filemanager.storage.service.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileMetadataMapper fileMetadataMapper;
    private final FileChunkMapper fileChunkMapper;
    private final UserMapper userMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final LocalStorageService localStorageService;
    private final DistributedStorageService distributedStorageService;
    private final AesEncryptionService aesEncryptionService;
    private final ConsistentHash consistentHash;
    private final NodeRegistryService nodeRegistryService;
    private final ReplicaService replicaService;
    private final DistributedLockService distributedLockService;
    private final FileCacheService fileCacheService;
    private final StringRedisTemplate redisTemplate;

    @Value("${file.encryption.master-key}")
    private String masterKey;

    @Value("${file.storage.max-file-size}")
    private long maxFileSize;

    @Value("${file.storage.chunk-size}")
    private long chunkSize;

    private static final String UPLOAD_PROGRESS_KEY = "file:upload:progress:";

    /**
     * 上传文件（统一走分片逻辑，小文件 chunkCount=1）
     * 使用事务确保原子性：先创建metadata，再创建分片记录，最后存储文件
     */
    @Transactional(rollbackFor = Exception.class)
    public FileVO upload(MultipartFile file, Long userId) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED);
        }

        // 检查节点可用性
        if (consistentHash.getNodeCount() == 0) {
            // 根据用户权限返回不同的提示信息
            List<String> permissions = rolePermissionMapper.selectPermissionCodesByUserId(userId);
            boolean hasNodePermission = permissions.contains("node:view");
            String message = hasNodePermission 
                    ? "服务暂不可用，请先注册存储节点" 
                    : "服务暂不可用，请联系管理员注册存储节点";
            throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE.getCode(), message);
        }

        // 1. 先创建元数据（获得fileId）
        String fileKey = IdUtil.fastSimpleUUID();
        int chunkCount = (int) Math.ceil((double) file.getSize() / chunkSize);

        String aesKey = aesEncryptionService.generateKey();
        String encryptedAesKey = aesEncryptionService.encryptFileKey(aesKey, masterKey);

        FileMetadata metadata = new FileMetadata();
        metadata.setFileKey(fileKey);
        metadata.setFileName(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.setAesKeyEncrypted(encryptedAesKey);
        metadata.setChunkCount(chunkCount);
        metadata.setChunkSize(chunkSize);
        metadata.setUploadUserId(userId);
        fileMetadataMapper.insert(metadata);

        // 2. 上传所有分片（小文件只有1个分片）
        byte[] fileData = file.getBytes();
        int offset = 0;
        for (int i = 0; i < chunkCount; i++) {
            int chunkLength = (int) Math.min(chunkSize, file.getSize() - offset);
            byte[] chunkData = new byte[chunkLength];
            System.arraycopy(fileData, offset, chunkData, 0, chunkLength);
            offset += chunkLength;

            // 加密分片
            byte[] encryptedData = aesEncryptionService.encrypt(chunkData, aesKey);

            // 选择存储节点
            String hashKey = fileKey + "-chunk-" + i;
            List<StorageNode> nodes = consistentHash.selectNodes(hashKey, 1);
            if (nodes.isEmpty()) {
                throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE);
            }
            StorageNode targetNode = nodes.get(0);

            // 存储分片（分布式存储）
            String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String storagePath = datePath + "/" + fileKey + "/chunk_" + i;
            distributedStorageService.store(targetNode, storagePath, encryptedData);

            // 记录分片信息
            FileChunk fileChunk = new FileChunk();
            fileChunk.setFileId(metadata.getId());
            fileChunk.setChunkIndex(i);
            fileChunk.setChunkSize((long) chunkLength);
            fileChunk.setStorageNodeName(targetNode.getNodeName());
            // 选择副本节点并记录（节点名称，唯一不变）
            List<StorageNode> replicaNodes = replicaService.selectReplicaNodes(hashKey, 2, targetNode.getId());
            String replicaNames = replicaNodes.stream().map(StorageNode::getNodeName).collect(Collectors.joining(","));
            fileChunk.setReplicaNodeNames(replicaNames);
            fileChunk.setStoragePath(storagePath);
            fileChunkMapper.insert(fileChunk);

            // 写入副本（分布式存储）
            for (int j = 0; j < replicaNodes.size(); j++) {
                StorageNode replicaNode = replicaNodes.get(j);
                String replicaPath = datePath + "/" + fileKey + "/chunk_" + i + "_replica_" + j;
                distributedStorageService.store(replicaNode, replicaPath, encryptedData);
            }

            log.info("分片上传成功: fileKey={}, chunkIndex={}/{}", fileKey, i, chunkCount);
        }

        // 3. 返回结果
        log.info("文件上传完成: fileKey={}, chunkCount={}, fileSize={}", fileKey, chunkCount, file.getSize());
        return toFileVO(metadata);
    }

    /**
     * 初始化分片上传：返回 fileKey、分片大小、分片数量
     */
    public InitUploadVO initChunkUpload(String fileName, Long fileSize, String contentType, Long userId) {
        if (fileSize > maxFileSize) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED);
        }

        // 检查节点可用性
        if (consistentHash.getNodeCount() == 0) {
            // 根据用户权限返回不同的提示信息
            List<String> permissions = rolePermissionMapper.selectPermissionCodesByUserId(userId);
            boolean hasNodePermission = permissions.contains("node:view");
            String message = hasNodePermission 
                    ? "服务暂不可用，请先注册存储节点" 
                    : "服务暂不可用，请联系管理员注册存储节点";
            throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE.getCode(), message);
        }

        String fileKey = IdUtil.fastSimpleUUID();
        int chunkCount = (int) Math.ceil((double) fileSize / chunkSize);

        // 生成 AES 密钥并缓存到 Redis
        String aesKey = aesEncryptionService.generateKey();
        String encryptedAesKey = aesEncryptionService.encryptFileKey(aesKey, masterKey);

        // 缓存上传信息到 Redis，有效期 24 小时
        String redisKey = UPLOAD_PROGRESS_KEY + fileKey;
        redisTemplate.opsForHash().put(redisKey, "fileName", fileName);
        redisTemplate.opsForHash().put(redisKey, "fileSize", String.valueOf(fileSize));
        redisTemplate.opsForHash().put(redisKey, "contentType", contentType != null ? contentType : "");
        redisTemplate.opsForHash().put(redisKey, "aesKeyEncrypted", encryptedAesKey);
        redisTemplate.opsForHash().put(redisKey, "chunkCount", String.valueOf(chunkCount));
        redisTemplate.opsForHash().put(redisKey, "userId", String.valueOf(userId));
        redisTemplate.opsForHash().put(redisKey, "uploadedChunks", "0");
        redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);

        return InitUploadVO.builder()
                .fileKey(fileKey)
                .chunkSize(chunkSize)
                .chunkCount(chunkCount)
                .build();
    }

    /**
     * 上传单个分片
     */
    public void uploadChunk(String fileKey, Integer chunkIndex, Integer totalChunks, MultipartFile chunkFile) {
        String redisKey = UPLOAD_PROGRESS_KEY + fileKey;

        // 校验上传会话是否存在
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "上传会话不存在或已过期，请重新初始化上传");
        }

        try {
            // 读取分片数据并加密
            String aesKeyEncrypted = (String) redisTemplate.opsForHash().get(redisKey, "aesKeyEncrypted");
            String aesKey = aesEncryptionService.decryptFileKey(aesKeyEncrypted, masterKey);

            byte[] chunkData = chunkFile.getBytes();
            byte[] encryptedData = aesEncryptionService.encrypt(chunkData, aesKey);

            // 通过一致性哈希选择存储节点
            String hashKey = fileKey + "-chunk-" + chunkIndex;
            List<StorageNode> nodes = consistentHash.selectNodes(hashKey, 1);
            if (nodes.isEmpty()) {
                throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE);
            }
            StorageNode targetNode = nodes.get(0);

            // 存储分片（分布式存储）
            String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String storagePath = datePath + "/" + fileKey + "/chunk_" + chunkIndex;
            distributedStorageService.store(targetNode, storagePath, encryptedData);

            // 记录分片信息到数据库
            FileChunk fileChunk = new FileChunk();
            fileChunk.setFileId(0L); // 临时值，合并时按fileKey精确更新
            fileChunk.setChunkIndex(chunkIndex);
            fileChunk.setChunkSize((long) chunkData.length);
            fileChunk.setStorageNodeName(targetNode.getNodeName());
            // 选择副本节点并记录（节点名称，唯一不变）
            List<StorageNode> replicaNodes = replicaService.selectReplicaNodes(hashKey, 2, targetNode.getId());
            String replicaNames = replicaNodes.stream().map(StorageNode::getNodeName).collect(Collectors.joining(","));
            fileChunk.setReplicaNodeNames(replicaNames);
            fileChunk.setStoragePath(storagePath);
            fileChunkMapper.insert(fileChunk);

            // 写入副本（分布式存储）
            for (int i = 0; i < replicaNodes.size(); i++) {
                StorageNode replicaNode = replicaNodes.get(i);
                String replicaPath = datePath + "/" + fileKey + "/chunk_" + chunkIndex + "_replica_" + i;
                distributedStorageService.store(replicaNode, replicaPath, encryptedData);
            }

            // 记录本次插入的分片ID到Redis，用于complete时精确定位
            redisTemplate.opsForList().rightPush(redisKey + ":chunkIds", String.valueOf(fileChunk.getId()));

            // 更新 Redis 中的已上传分片数
            redisTemplate.opsForHash().increment(redisKey, "uploadedChunks", 1);

            log.info("分片上传成功: fileKey={}, chunkIndex={}/{}", fileKey, chunkIndex, totalChunks);
        } catch (IOException e) {
            log.error("分片上传失败: fileKey={}, chunkIndex={}", fileKey, chunkIndex, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 完成分片上传：合并元数据
     */
    public FileVO completeChunkUpload(String fileKey) {
        String redisKey = UPLOAD_PROGRESS_KEY + fileKey;

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "上传会话不存在或已过期");
        }

        String fileName = (String) redisTemplate.opsForHash().get(redisKey, "fileName");
        Long fileSize = Long.parseLong((String) redisTemplate.opsForHash().get(redisKey, "fileSize"));
        String contentType = (String) redisTemplate.opsForHash().get(redisKey, "contentType");
        String aesKeyEncrypted = (String) redisTemplate.opsForHash().get(redisKey, "aesKeyEncrypted");
        int chunkCount = Integer.parseInt((String) redisTemplate.opsForHash().get(redisKey, "chunkCount"));
        int uploadedChunks = Integer.parseInt((String) redisTemplate.opsForHash().get(redisKey, "uploadedChunks"));
        Long userId = Long.parseLong((String) redisTemplate.opsForHash().get(redisKey, "userId"));

        if (uploadedChunks < chunkCount) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "分片未全部上传完成: " + uploadedChunks + "/" + chunkCount);
        }

        // 创建文件元数据
        FileMetadata metadata = new FileMetadata();
        metadata.setFileKey(fileKey);
        metadata.setFileName(fileName);
        metadata.setFileSize(fileSize);
        metadata.setContentType(contentType.isEmpty() ? null : contentType);
        metadata.setAesKeyEncrypted(aesKeyEncrypted);
        metadata.setChunkCount(chunkCount);
        metadata.setChunkSize(chunkSize);
        metadata.setUploadUserId(userId);
        metadata.setStoragePath(null); // 分片文件无统一存储路径
        fileMetadataMapper.insert(metadata);

        // 精确更新本次上传的分片记录的fileId（从Redis获取分片ID列表）
        String chunkIdListKey = redisKey + ":chunkIds";
        Long chunkIdCount = redisTemplate.opsForList().size(chunkIdListKey);
        if (chunkIdCount != null && chunkIdCount > 0) {
            List<String> chunkIds = redisTemplate.opsForList().range(chunkIdListKey, 0, -1);
            for (String chunkId : chunkIds) {
                fileChunkMapper.update(null, new LambdaUpdateWrapper<FileChunk>()
                        .eq(FileChunk::getId, Long.parseLong(chunkId))
                        .set(FileChunk::getFileId, metadata.getId()));
            }
            // 清理分片ID列表
            redisTemplate.delete(chunkIdListKey);
        }

        // 清除 Redis 缓存
        redisTemplate.delete(redisKey);

        log.info("分片上传完成: fileKey={}, chunkCount={}", fileKey, chunkCount);
        return toFileVO(metadata);
    }

    /**
     * 下载文件（分布式读取，支持副本读取）
     * 1. 随机选择主节点或副本节点（负载均衡）
     * 2. 如果选择的节点失败，自动切换到其他节点（故障恢复）
     */
    public byte[] download(String fileKey, Long userId) {
        FileMetadata metadata = getByFileKey(fileKey);

        String aesKey = aesEncryptionService.decryptFileKey(metadata.getAesKeyEncrypted(), masterKey);

        // 统一逻辑：所有文件都从 file_chunk 表读取分片
        List<FileChunk> chunks = fileChunkMapper.selectList(
                new LambdaQueryWrapper<FileChunk>()
                        .eq(FileChunk::getFileId, metadata.getId())
                        .orderByAsc(FileChunk::getChunkIndex)
        );

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (FileChunk chunk : chunks) {
            // 获取所有可用节点（主节点 + 副本节点）
            List<StorageNode> availableNodes = getAvailableNodes(chunk);
            if (availableNodes.isEmpty()) {
                throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE.getCode(), 
                        "分片 " + chunk.getChunkIndex() + " 无可用存储节点");
            }

            // 尝试从可用节点读取（负载均衡 + 故障恢复）
            byte[] chunkData = readFromNodes(availableNodes, chunk, chunk.getChunkIndex());
            byte[] decrypted = aesEncryptionService.decrypt(chunkData, aesKey);
            result.write(decrypted, 0, decrypted.length);
        }

        return result.toByteArray();
    }

    /**
     * 获取分片的可用节点列表（主节点 + 副本节点）
     * 使用节点名称而非ID，节点名称唯一不变
     */
    private List<StorageNode> getAvailableNodes(FileChunk chunk) {
        List<StorageNode> nodes = new ArrayList<>();

        // 添加主节点
        StorageNode primaryNode = distributedStorageService.getNodeByName(chunk.getStorageNodeName());
        if (primaryNode != null) {
            nodes.add(primaryNode);
        }

        // 添加副本节点
        if (chunk.getReplicaNodeNames() != null && !chunk.getReplicaNodeNames().isEmpty()) {
            String[] replicaNames = chunk.getReplicaNodeNames().split(",");
            for (String replicaName : replicaNames) {
                StorageNode replicaNode = distributedStorageService.getNodeByName(replicaName.trim());
                if (replicaNode != null) {
                    nodes.add(replicaNode);
                }
            }
        }

        log.info("分片可用节点: chunkIndex={}, primaryNode={}, replicaNodes={}",
                chunk.getChunkIndex(),
                primaryNode != null ? primaryNode.getNodeName() : "null",
                nodes.stream().skip(1).map(StorageNode::getNodeName).collect(Collectors.toList()));

        return nodes;
    }

    /**
     * 从多个节点尝试读取分片（负载均衡 + 故障恢复）
     * 1. 随机选择一个节点（负载均衡）
     * 2. 根据节点角色决定读取路径：主节点读取原始路径，副本节点读取副本路径
     * 3. 如果失败，依次尝试其他节点
     */
    private byte[] readFromNodes(List<StorageNode> nodes, FileChunk chunk, int chunkIndex) {
        // 随机选择起始节点（负载均衡）
        int startIndex = ThreadLocalRandom.current().nextInt(nodes.size());

        // 尝试所有节点（故障恢复）
        for (int i = 0; i < nodes.size(); i++) {
            int nodeIndex = (startIndex + i) % nodes.size();
            StorageNode node = nodes.get(nodeIndex);

            // 判断节点角色：主节点还是副本节点
            boolean isPrimaryNode = node.getNodeName().equals(chunk.getStorageNodeName());

            // 构建存储路径
            String storagePath;
            if (isPrimaryNode) {
                // 主节点，使用原始路径
                storagePath = chunk.getStoragePath();
            } else {
                // 副本节点，需要找到该节点在副本列表中的索引
                int replicaIndex = getReplicaIndex(node.getNodeName(), chunk.getReplicaNodeNames());
                if (replicaIndex == -1) {
                    log.warn("节点不在副本列表中，跳过: nodeName={}", node.getNodeName());
                    continue;
                }
                storagePath = chunk.getStoragePath() + "_replica_" + replicaIndex;
            }

            try {
                ByteArrayOutputStream chunkBaos = new ByteArrayOutputStream();
                distributedStorageService.read(node, storagePath, chunkBaos);
                log.info("分片读取成功: chunkIndex={}, node={}, path={}, isPrimary={}",
                        chunkIndex, node.getNodeName(), storagePath, isPrimaryNode);
                return chunkBaos.toByteArray();
            } catch (Exception e) {
                log.warn("从节点读取失败: chunkIndex={}, node={}, path={}, error={}, 尝试下一个节点",
                        chunkIndex, node.getNodeName(), storagePath, e.getMessage());
            }
        }

        throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE.getCode(),
                "分片 " + chunkIndex + " 所有节点均不可用");
    }

    /**
     * 获取节点在副本列表中的索引
     * @param nodeName 节点名称
     * @param replicaNodeNames 副本节点名称列表（逗号分隔）
     * @return 副本索引（0开始），如果不在列表中返回 -1
     */
    private int getReplicaIndex(String nodeName, String replicaNodeNames) {
        if (replicaNodeNames == null || replicaNodeNames.isEmpty()) {
            return -1;
        }
        String[] replicas = replicaNodeNames.split(",");
        for (int i = 0; i < replicas.length; i++) {
            if (replicas[i].trim().equals(nodeName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取文件元数据（优先查缓存）
     */
    public FileMetadata getByFileKey(String fileKey) {
        FileMetadata cached = fileCacheService.getFileMetadata(fileKey);
        if (cached != null) {
            return cached;
        }
        FileMetadata metadata = fileMetadataMapper.selectOne(
                new LambdaQueryWrapper<FileMetadata>()
                        .eq(FileMetadata::getFileKey, fileKey)
        );
        if (metadata == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        return metadata;
    }

    /**
     * 获取文件信息VO
     */
    public FileVO getFileVO(String fileKey) {
        return toFileVO(getByFileKey(fileKey));
    }

    /**
     * 删除文件（逻辑删除，同时删除分片）
     */
    public void delete(String fileKey, Long userId) {
        distributedLockService.executeWithLock("delete:" + fileKey, 5, 10, TimeUnit.SECONDS, () -> {
            FileMetadata metadata = getByFileKey(fileKey);

            // 逻辑删除文件元数据
            fileMetadataMapper.update(null, new LambdaUpdateWrapper<FileMetadata>()
                    .eq(FileMetadata::getId, metadata.getId())
                    .set(FileMetadata::getDeleted, "1"));

            // 逻辑删除分片记录
            if (metadata.getChunkCount() > 1) {
                fileChunkMapper.update(null, new LambdaUpdateWrapper<FileChunk>()
                        .eq(FileChunk::getFileId, metadata.getId())
                        .set(FileChunk::getDeleted, "1"));
            }

            // 清除缓存
            fileCacheService.evictFileMetadata(fileKey);

            log.info("文件已逻辑删除: fileKey={}, userId={}", fileKey, userId);
        });
    }

    /**
     * 分页查询文件列表
     */
    public Page<FileVO> listFiles(Long userId, List<String> roles, String keyword, int pageNum, int pageSize) {
        Page<FileMetadata> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileMetadata> wrapper = new LambdaQueryWrapper<FileMetadata>()
                .like(keyword != null && !keyword.isEmpty(), FileMetadata::getFileName, keyword)
                .orderByDesc(FileMetadata::getCreatedAt);
        // 非管理员只能看到自己的文件
        if (roles == null || !roles.contains("ADMIN")) {
            wrapper.eq(FileMetadata::getUploadUserId, userId);
        }
        Page<FileMetadata> result = fileMetadataMapper.selectPage(page, wrapper);

        Page<FileVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toFileVO).toList());
        return voPage;
    }

    /**
     * 查询分片上传进度（断点续传）
     */
    public InitUploadVO getUploadProgress(String fileKey) {
        String redisKey = UPLOAD_PROGRESS_KEY + fileKey;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            return null;
        }
        int chunkCount = Integer.parseInt((String) redisTemplate.opsForHash().get(redisKey, "chunkCount"));
        int uploadedChunks = Integer.parseInt((String) redisTemplate.opsForHash().get(redisKey, "uploadedChunks"));
        return InitUploadVO.builder()
                .fileKey(fileKey)
                .chunkSize(chunkSize)
                .chunkCount(chunkCount)
                .uploadedChunks(uploadedChunks)
                .build();
    }

    /**
     * 生成预签名下载链接（简单实现：token + 过期时间）
     */
    public String generatePresignedUrl(String fileKey, Long userId, long expireSeconds) {
        String token = IdUtil.fastSimpleUUID();
        String redisKey = "file:presigned:" + token;
        redisTemplate.opsForHash().put(redisKey, "fileKey", fileKey);
        redisTemplate.opsForHash().put(redisKey, "userId", String.valueOf(userId));
        redisTemplate.expire(redisKey, expireSeconds, TimeUnit.SECONDS);
        return "/api/file/download/presigned/" + token;
    }

    /**
     * 通过预签名 token 下载文件
     */
    public byte[] downloadByPresignedToken(String token) {
        String redisKey = "file:presigned:" + token;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "下载链接已过期");
        }
        String fileKey = (String) redisTemplate.opsForHash().get(redisKey, "fileKey");
        Long userId = Long.parseLong((String) redisTemplate.opsForHash().get(redisKey, "userId"));
        return download(fileKey, userId);
    }

    /**
     * 预签名下载时获取文件元数据（仅用于响应头）
     */
    public FileMetadata getByFileKeyForPresigned(String token) {
        String redisKey = "file:presigned:" + token;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "下载链接已过期");
        }
        String fileKey = (String) redisTemplate.opsForHash().get(redisKey, "fileKey");
        return getByFileKey(fileKey);
    }

    /**
     * 回收站文件列表（分页）
     */
    public Page<FileVO> listRecycledFiles(String keyword, int pageNum, int pageSize) {
        long total = fileMetadataMapper.countRecycledFiles(keyword);
        int offset = (pageNum - 1) * pageSize;
        List<FileMetadata> records = fileMetadataMapper.selectRecycledFiles(keyword, offset, pageSize);

        Page<FileVO> voPage = new Page<>(pageNum, pageSize, total);
        voPage.setRecords(records.stream().map(this::toFileVO).toList());
        return voPage;
    }

    /**
     * 恢复文件（从回收站恢复）
     */
    public void restoreFile(String fileKey) {
        FileMetadata metadata = fileMetadataMapper.selectRecycledByKey(fileKey);
        if (metadata == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND.getCode(), "文件不在回收站中");
        }

        fileMetadataMapper.restoreById(metadata.getId());

        // 恢复分片记录
        fileChunkMapper.restoreByFileId(metadata.getId());

        log.info("文件已从回收站恢复: fileKey={}", fileKey);
    }

    /**
     * 物理删除文件（彻底清除，不可逆）
     */
    public void purgeFile(String fileKey) {
        FileMetadata metadata = fileMetadataMapper.selectRecycledByKey(fileKey);
        if (metadata == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND.getCode(), "文件不在回收站中");
        }

        // 删除磁盘上的实际文件
        try {
            if (metadata.getStoragePath() != null) {
                localStorageService.delete(metadata.getStoragePath());
            }
            // 删除分片文件
            List<FileChunk> chunks = fileChunkMapper.selectList(
                    new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getFileId, metadata.getId())
            );
            for (FileChunk chunk : chunks) {
                localStorageService.delete(chunk.getStoragePath());
            }
        } catch (Exception e) {
            log.error("物理删除磁盘文件失败: fileKey={}, error={}", fileKey, e.getMessage());
        }

        // 物理删除分片记录
        fileChunkMapper.physicalDeleteByFileId(metadata.getId());

        // 物理删除元数据
        fileMetadataMapper.physicalDeleteById(metadata.getId());

        log.info("文件已物理删除: fileKey={}", fileKey);
    }

    private FileVO toFileVO(FileMetadata metadata) {
        String uploadUsername = null;
        if (metadata.getUploadUserId() != null) {
            User user = userMapper.selectById(metadata.getUploadUserId());
            if (user != null) {
                uploadUsername = user.getUsername();
            }
        }
        return FileVO.builder()
                .id(metadata.getId())
                .fileKey(metadata.getFileKey())
                .fileName(metadata.getFileName())
                .fileSize(metadata.getFileSize())
                .contentType(metadata.getContentType())
                .chunkCount(metadata.getChunkCount())
                .uploadUserId(metadata.getUploadUserId())
                .uploadUsername(uploadUsername)
                .createdAt(metadata.getCreatedAt() != null
                        ? metadata.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : null)
                .build();
    }
}
