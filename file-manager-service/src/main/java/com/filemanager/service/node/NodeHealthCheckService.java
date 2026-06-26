package com.filemanager.service.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.filemanager.dao.mapper.StorageNodeMapper;
import com.filemanager.model.entity.StorageNode;
import com.filemanager.service.lock.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 节点健康检测服务
 * 1. 自己的心跳更新（每10秒）
 * 2. 全局存活检测（分布式锁，每10秒）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeHealthCheckService {

    private static final String NODES_PATH = "/nodes";
    private static final String HEALTH_CHECK_LOCK = "health-check";
    private static final String LAST_CHECK_TIME_KEY = "file-manager:health-check:last-time";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NodeAutoRegisterService nodeAutoRegisterService;
    private final StorageNodeMapper storageNodeMapper;
    private final CuratorFramework curatorFramework;
    private final DistributedLockService distributedLockService;
    private final StringRedisTemplate redisTemplate;
    private final ConsistentHash consistentHash;

    @Value("${node.heartbeat-interval:10}")
    private int heartbeatInterval;

    @Value("${node.heartbeat-tolerance:20}")
    private int heartbeatTolerance;

    /**
     * 更新自己的心跳（每10秒）
     * 注意：心跳只更新 lastHeartbeat 字段，不修改 status 状态
     * 状态修改应该只在注册、停止、手动隔离/恢复、全局健康检测时发生
     */
    @Scheduled(fixedRateString = "${node.heartbeat-interval:10}000")
    public void updateOwnHeartbeat() {
        StorageNode currentNode = nodeAutoRegisterService.getCurrentNode();
        if (currentNode == null) {
            log.warn("当前节点未注册，跳过心跳更新");
            return;
        }

        // 1. 从数据库查询最新状态
        StorageNode dbNode = storageNodeMapper.selectById(currentNode.getId());
        if (dbNode == null) {
            log.warn("数据库中找不到节点，跳过心跳更新: nodeName={}", currentNode.getNodeName());
            return;
        }

        // 2. 如果数据库状态是隔离，跳过心跳更新，同步 currentNode 后返回
        if (dbNode.getStatus() == 2) {
            log.info("节点已隔离，跳过心跳更新: nodeName={}", dbNode.getNodeName());
            nodeAutoRegisterService.updateCurrentNode(dbNode);  // 同步整个 currentNode
            return;
        }

        // 3. 检查 currentNode 和数据库状态是否一致
        if (currentNode.getStatus() != null && !currentNode.getStatus().equals(dbNode.getStatus())) {
            log.warn("节点状态不一致，同步 currentNode: nodeName={}, currentNode.status={}, db.status={}",
                    currentNode.getNodeName(), currentNode.getStatus(), dbNode.getStatus());
        }

        try {
            // 4. 只更新 lastHeartbeat 字段（不修改 status）
            LocalDateTime now = LocalDateTime.now();
            storageNodeMapper.update(null,
                new LambdaUpdateWrapper<StorageNode>()
                    .eq(StorageNode::getId, dbNode.getId())
                    .set(StorageNode::getLastHeartbeat, now)
            );

            // 5. 更新 dbNode 的心跳时间，并同步到 currentNode
            dbNode.setLastHeartbeat(now);
            nodeAutoRegisterService.updateCurrentNode(dbNode);  // 用最新的 dbNode 替换 currentNode
            log.info("心跳更新成功: nodeName={}, lastHeartbeat={}", dbNode.getNodeName(), now);
        } catch (Exception e) {
            log.error("心跳更新失败: nodeName={}", dbNode.getNodeName(), e);
        }
    }

    /**
     * 全局存活检测（每10秒，分布式锁）
     */
    @Scheduled(fixedRateString = "${node.heartbeat-interval:10}000")
    public void globalHealthCheck() {
        if (!nodeAutoRegisterService.isRegistered()) {
            log.warn("当前节点未注册，跳过全局健康检测");
            return;
        }

        try {
            distributedLockService.executeWithLock(HEALTH_CHECK_LOCK, 0, 30, TimeUnit.SECONDS, () -> {
                doGlobalHealthCheck();
            });
        } catch (Exception e) {
            log.error("全局健康检测失败: {}", e.getMessage());
        }
    }

    /**
     * 执行全局健康检测
     */
    private void doGlobalHealthCheck() {
        log.info("开始全局健康检测...");

        // 1. 检查上次检测时间
        String lastCheckTimeStr = redisTemplate.opsForValue().get(LAST_CHECK_TIME_KEY);
        if (lastCheckTimeStr != null) {
            LocalDateTime lastCheckTime = LocalDateTime.parse(lastCheckTimeStr, DATE_FORMATTER);
            if (lastCheckTime.plusSeconds(heartbeatInterval).isAfter(LocalDateTime.now())) {
                log.info("上次检测在{}秒内，跳过本次检测: lastCheckTime={}", heartbeatInterval, lastCheckTime);
                return;
            }
        }

        // 2. 获取 Zookeeper 中的在线节点列表
        List<String> zkOnlineNodes = getZkOnlineNodes();
        log.info("Zookeeper在线节点列表: count={}, nodes={}", zkOnlineNodes.size(), zkOnlineNodes);

        // 3. 获取 MySQL 中的所有节点
        List<StorageNode> allDbNodes = storageNodeMapper.selectList(null);
        log.info("MySQL节点列表: count={}", allDbNodes.size());

        // 4. 双重判断存活状态
        List<StorageNode> aliveNodes = determineAliveNodes(allDbNodes, zkOnlineNodes);
        log.info("存活节点列表: count={}, nodes={}", aliveNodes.size(), 
                aliveNodes.stream().map(StorageNode::getNodeName).collect(Collectors.toList()));

        // 5. 更新全局检测时间
        redisTemplate.opsForValue().set(LAST_CHECK_TIME_KEY, LocalDateTime.now().format(DATE_FORMATTER));
        log.info("全局检测时间已更新: {}", LocalDateTime.now());

        // 6. 重建一致性哈希环
        consistentHash.rebuild(aliveNodes);
        log.info("一致性哈希环已重建: 节点数={}", aliveNodes.size());
        log.info("全局健康检测完成");
    }

    /**
     * 获取 Zookeeper 中的在线节点列表
     */
    private List<String> getZkOnlineNodes() {
        try {
            if (curatorFramework.checkExists().forPath(NODES_PATH) != null) {
                return curatorFramework.getChildren().forPath(NODES_PATH);
            }
        } catch (Exception e) {
            log.error("获取Zookeeper节点列表失败", e);
        }
        return Collections.emptyList();
    }

    /**
     * 判断节点存活状态（双重判断）
     */
    private List<StorageNode> determineAliveNodes(List<StorageNode> dbNodes, List<String> zkNodes) {
        List<StorageNode> aliveNodes = new java.util.ArrayList<>();

        for (StorageNode node : dbNodes) {
            // 隔离节点不检测，不加入存活列表
            if (node.getStatus() == 2) {
                log.info("节点已隔离，跳过检测: nodeName={}, status=2", node.getNodeName());
                continue;
            }

            // 判断 Zookeeper 临时节点是否存在
            boolean zkAlive = zkNodes.contains(node.getNodeName());

            // 判断心跳时间是否超时
            boolean heartbeatAlive = isHeartbeatAlive(node);

            // 双重判断存活
            boolean isAlive = zkAlive && heartbeatAlive;

            // 更新状态
            updateNodeStatus(node, isAlive);

            // 存活节点加入列表
            if (isAlive) {
                aliveNodes.add(node);
            }
        }

        return aliveNodes;
    }

    /**
     * 判断心跳时间是否在容忍范围内
     */
    private boolean isHeartbeatAlive(StorageNode node) {
        if (node.getLastHeartbeat() == null) {
            return false;
        }
        LocalDateTime deadline = node.getLastHeartbeat().plusSeconds(heartbeatTolerance);
        return deadline.isAfter(LocalDateTime.now());
    }

    /**
     * 更新节点状态
     */
    private void updateNodeStatus(StorageNode node, boolean isAlive) {
        int newStatus = isAlive ? 1 : 0;
        Integer currentStatus = node.getStatus();

        // 状态变更时更新
        if (currentStatus == null || !currentStatus.equals(newStatus)) {
            node.setStatus(newStatus);
            node.setLastHeartbeat(LocalDateTime.now());
            storageNodeMapper.updateById(node);
            log.info("节点状态变更: nodeName={}, oldStatus={}, newStatus={}", 
                    node.getNodeName(), currentStatus, newStatus == 1 ? "在线" : "离线");
        }
    }
}