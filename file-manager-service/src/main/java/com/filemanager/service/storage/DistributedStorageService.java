package com.filemanager.service.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filemanager.dao.mapper.StorageNodeMapper;
import com.filemanager.service.node.ConsistentHash;
import com.filemanager.model.entity.StorageNode;
import com.filemanager.storage.service.LocalStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 分布式存储服务
 * 根据节点信息决定本地存储还是远程存储
 */
@Slf4j
@Service
public class DistributedStorageService {

    @Autowired
    private LocalStorageService localStorageService;

    @Autowired
    private RemoteStorageService remoteStorageService;

    @Autowired
    private ConsistentHash consistentHash;

    @Autowired
    private StorageNodeMapper storageNodeMapper;

    @Value("${server.port:8080}")
    private String currentPort;

    /**
     * 存储分片（自动判断本地或远程）
     * @param node 目标节点
     * @param storagePath 存储路径
     * @param data 分片数据
     */
    public void store(StorageNode node, String storagePath, byte[] data) {
        if (isCurrentNode(node)) {
            // 本节点，直接存储
            log.info("本地存储: path={}, size={}", storagePath, data.length);
            localStorageService.store(storagePath, new ByteArrayInputStream(data));
        } else {
            // 远程节点，HTTP调用
            log.info("远程存储: node={}, path={}, size={}", node.getNodeName(), storagePath, data.length);
            remoteStorageService.store(node, storagePath, data);
        }
    }

    /**
     * 读取分片（自动判断本地或远程）
     * @param node 目标节点
     * @param storagePath 存储路径
     * @param baos 输出流
     */
    public void read(StorageNode node, String storagePath, ByteArrayOutputStream baos) {
        if (isCurrentNode(node)) {
            // 本节点，直接读取
            log.info("本地读取: path={}", storagePath);
            localStorageService.read(storagePath, baos);
        } else {
            // 远程节点，HTTP调用
            log.info("远程读取: node={}, path={}", node.getNodeName(), storagePath);
            remoteStorageService.read(node, storagePath, baos);
        }
    }

    /**
     * 删除分片（自动判断本地或远程）
     * @param node 目标节点
     * @param storagePath 存储路径
     */
    public void delete(StorageNode node, String storagePath) {
        if (isCurrentNode(node)) {
            // 本节点，直接删除
            log.info("本地删除: path={}", storagePath);
            localStorageService.delete(storagePath);
        } else {
            // 远程节点，HTTP调用
            log.info("远程删除: node={}, path={}", node.getNodeName(), storagePath);
            remoteStorageService.delete(node, storagePath);
        }
    }

    /**
     * 判断是否是当前节点
     * 根据端口判断（假设所有节点在同一主机上）
     */
    private boolean isCurrentNode(StorageNode node) {
        return String.valueOf(node.getNodePort()).equals(currentPort);
    }

    /**
     * 根据节点ID获取节点信息（检查节点状态）
     * 优先从哈希环查询，如果不存在则从数据库查询
     * @param nodeId 节点ID
     * @return 节点信息（如果节点离线或不存在则返回 null）
     */
    public StorageNode getNodeById(Long nodeId) {
        // 1. 先从哈希环查询（活跃节点）
        StorageNode hashNode = consistentHash.getNodeById(nodeId);
        if (hashNode != null) {
            // 在哈希环中，检查数据库状态
            StorageNode dbNode = storageNodeMapper.selectById(nodeId);
            if (dbNode == null || dbNode.getStatus() == null || dbNode.getStatus() != 1) {
                log.warn("节点在哈希环中但数据库状态离线: nodeId={}, nodeName={}", nodeId, hashNode.getNodeName());
                return null;
            }
            return hashNode;
        }
        
        // 2. 哈希环中没有，从数据库查询（可能是旧节点）
        StorageNode dbNode = storageNodeMapper.selectById(nodeId);
        if (dbNode == null) {
            log.warn("节点不存在: nodeId={}", nodeId);
            return null;
        }
        
        // 检查节点状态
        if (dbNode.getStatus() == null || dbNode.getStatus() != 1) {
            log.warn("节点离线: nodeId={}, nodeName={}, status={}", nodeId, dbNode.getNodeName(), dbNode.getStatus());
            return null;
        }
        
        log.info("节点不在哈希环中但数据库状态在线，尝试添加: nodeId={}, nodeName={}", nodeId, dbNode.getNodeName());
        // 节点在线但不在哈希环中，可能是数据不一致，添加到哈希环
        consistentHash.addNode(dbNode);
        return dbNode;
    }

    /**
     * 根据节点名称获取节点信息（检查节点状态）
     * 用于副本节点查找，因为副本节点记录的是节点名称而非ID
     * @param nodeName 节点名称
     * @return 节点信息（如果节点离线或不存在则返回 null）
     */
    public StorageNode getNodeByName(String nodeName) {
        // 1. 从数据库查询
        StorageNode dbNode = storageNodeMapper.selectOne(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getNodeName, nodeName)
        );

        if (dbNode == null) {
            log.warn("节点不存在: nodeName={}", nodeName);
            return null;
        }

        // 2. 检查节点状态
        if (dbNode.getStatus() == null || dbNode.getStatus() != 1) {
            log.warn("节点离线: nodeId={}, nodeName={}, status={}", dbNode.getId(), nodeName, dbNode.getStatus());
            return null;
        }

        // 3. 确保节点在哈希环中
        StorageNode hashNode = consistentHash.getNodeById(dbNode.getId());
        if (hashNode == null) {
            log.info("节点不在哈希环中，尝试添加: nodeId={}, nodeName={}", dbNode.getId(), nodeName);
            consistentHash.addNode(dbNode);
        }

        return dbNode;
    }
}