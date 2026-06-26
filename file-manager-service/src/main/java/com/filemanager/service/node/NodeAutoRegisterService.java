package com.filemanager.service.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filemanager.dao.mapper.StorageNodeMapper;
import com.filemanager.model.entity.StorageNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 节点自动注册服务
 * 服务启动时自动注册到 Zookeeper 和 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeAutoRegisterService {

    private static final String NODES_PATH = "/nodes";

    private final CuratorFramework curatorFramework;
    private final StorageNodeMapper storageNodeMapper;
    private final ConsistentHash consistentHash;

    @Value("${node.name}")
    private String nodeName;

    @Value("${node.host}")
    private String nodeHost;

    @Value("${node.port}")
    private int nodePort;

    private StorageNode currentNode;
    private boolean registered = false;

    /**
     * 服务启动完成后自动注册节点
     */
    @EventListener(ApplicationReadyEvent.class)
    public void autoRegister() {
     log.info("=========================================");
            log.info("节点自动注册开始: nodeName={}, host={}, port={}", nodeName, nodeHost, nodePort);
            log.info("=========================================");

        try {
            // 1. 确保父路径存在
            ensureParentPathExists();

            // 2. 检查 Zookeeper 中是否已存在同名节点
            String nodePath = NODES_PATH + "/" + nodeName;
            if (curatorFramework.checkExists().forPath(nodePath) != null) {
                log.error("节点名称已存在，服务启动失败: nodeName={}", nodeName);
                throw new RuntimeException("节点名称已存在: " + nodeName + "，请检查配置或等待旧节点完全下线");
            }

            // 3. 创建 Zookeeper 临时节点
            String nodeData = nodeHost + ":" + nodePort;
            curatorFramework.create()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(nodePath, nodeData.getBytes(StandardCharsets.UTF_8));
            log.info("Zookeeper临时节点创建成功: path={}, data={}", nodePath, nodeData);

            // 4. 同步到 MySQL
            currentNode = syncNodeToDb();
            log.info("MySQL节点记录同步成功: nodeName={}, status=在线", nodeName);

            // 5. 添加到一致性哈希环
            consistentHash.addNode(currentNode);
            log.info("一致性哈希环添加节点成功: nodeName={}, 虚拟节点数=150", nodeName);

            registered = true;
            log.info("========================================");
            log.info("节点自动注册完成: nodeName={}", nodeName);
            log.info("========================================");

        } catch (Exception e) {
            log.error("节点自动注册失败: nodeName={}", nodeName, e);
            throw new RuntimeException("节点自动注册失败: " + e.getMessage(), e);
        }
    }

    /**
     * 服务停止时清理
     */
    @PreDestroy
    public void cleanup() {
        if (!registered) {
            return;
        }
        log.info("节点停止，开始清理: nodeName={}", nodeName);
        try {
            // Zookeeper 临时节点会自动删除，无需手动处理
            
            // 更新 MySQL 状态为离线
            if (currentNode != null) {
                currentNode.setStatus(0);
                currentNode.setLastHeartbeat(LocalDateTime.now());
                storageNodeMapper.updateById(currentNode);
                log.info("MySQL节点状态更新为离线: nodeName={}", nodeName);
            }
            
            log.info("节点清理完成: nodeName={}", nodeName);
        } catch (Exception e) {
            log.error("节点清理失败: nodeName={}", nodeName, e);
        }
    }

    /**
     * 确保父路径存在
     */
    private void ensureParentPathExists() throws Exception {
        if (curatorFramework.checkExists().forPath(NODES_PATH) == null) {
            curatorFramework.create().creatingParentsIfNeeded().forPath(NODES_PATH);
            log.info("Zookeeper父路径创建成功: path={}", NODES_PATH);
        }
    }

    /**
     * 同步节点到数据库
     * 方案1：恢复逻辑删除的记录（name + host + port 匹配）
     */
    private StorageNode syncNodeToDb() {
        // 1. 查询逻辑删除的同名节点（绕过MyBatis-Plus逻辑删除机制）
        StorageNode deletedNode = storageNodeMapper.selectDeletedByName(nodeName);

        if (deletedNode != null) {
            // 2. 检查 host + port 是否匹配
            if (deletedNode.getNodeHost().equals(nodeHost) && deletedNode.getNodePort().equals(nodePort)) {
                // 匹配：恢复逻辑删除的记录
                deletedNode.setStatus(1);  // 在线
                deletedNode.setLastHeartbeat(LocalDateTime.now());
                deletedNode.setZkPath(NODES_PATH + "/" + nodeName);
                storageNodeMapper.restoreById(deletedNode.getId());
                storageNodeMapper.updateById(deletedNode);
                log.info("恢复逻辑删除的节点记录: nodeName={}", nodeName);
                return deletedNode;
            } else {
                // 不匹配：报错
                log.error("节点地址已变更: nodeName={}, oldHost={}, oldPort={}, newHost={}, newPort={}",
                        nodeName, deletedNode.getNodeHost(), deletedNode.getNodePort(), nodeHost, nodePort);
                throw new RuntimeException("节点名称已存在但地址已变更，请使用新名称或先彻底删除旧节点");
            }
        }

        // 3. 查询正常记录（deleted='0'）
        StorageNode existing = storageNodeMapper.selectOne(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getNodeName, nodeName)
        );

        if (existing != null) {
            // 更新现有记录
            existing.setNodeHost(nodeHost);
            existing.setNodePort(nodePort);
            existing.setStatus(1);  // 在线
            existing.setLastHeartbeat(LocalDateTime.now());
            existing.setZkPath(NODES_PATH + "/" + nodeName);
            storageNodeMapper.updateById(existing);
            log.info("更新已有节点记录: nodeName={}", nodeName);
            return existing;
        } else {
            // 创建新记录
            StorageNode node = new StorageNode();
            node.setNodeName(nodeName);
            node.setNodeHost(nodeHost);
            node.setNodePort(nodePort);
            node.setStatus(1);  // 在线
            node.setAvailableSpace(0L);
            node.setTotalSpace(0L);
            node.setZkPath(NODES_PATH + "/" + nodeName);
            node.setLastHeartbeat(LocalDateTime.now());
            storageNodeMapper.insert(node);
            log.info("创建新节点记录: nodeName={}", nodeName);
            return node;
        }
    }

    /**
     * 获取当前节点信息
     */
    public StorageNode getCurrentNode() {
        return currentNode;
    }

    /**
     * 更新当前节点信息（从数据库同步）
     */
    public void updateCurrentNode(StorageNode newNode) {
        this.currentNode = newNode;
    }

    /**
     * 获取当前节点ID
     */
    public Long getCurrentNodeId() {
        return currentNode != null ? currentNode.getId() : null;
    }

    /**
     * 是否已注册
     */
    public boolean isRegistered() {
        return registered;
    }
}