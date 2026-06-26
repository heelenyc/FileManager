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
        log.info("========================================");
        log.info("节点自动注册开始: name={}, host={}, port={}", nodeName, nodeHost, nodePort);
        log.info("========================================");

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
            log.info("MySQL节点记录同步成功: nodeId={}, status=在线", currentNode.getId());

            // 5. 添加到一致性哈希环
            consistentHash.addNode(currentNode);
            log.info("一致性哈希环添加节点成功: nodeId={}, 虚拟节点数=150", currentNode.getId());

            registered = true;
            log.info("========================================");
            log.info("节点自动注册完成: nodeName={}, nodeId={}", nodeName, currentNode.getId());
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
                log.info("MySQL节点状态更新为离线: nodeId={}", currentNode.getId());
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
     */
    private StorageNode syncNodeToDb() {
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
            log.info("更新已有节点记录: nodeId={}, nodeName={}", existing.getId(), nodeName);
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
            log.info("创建新节点记录: nodeId={}, nodeName={}", node.getId(), nodeName);
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