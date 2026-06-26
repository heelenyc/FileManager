package com.filemanager.service.node;

import com.filemanager.model.entity.StorageNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

@Slf4j
@Component
public class ConsistentHash {

    private static final int VIRTUAL_NODE_COUNT = 150;

    // 哈希环: hash -> nodeId
    private final ConcurrentSkipListMap<Long, Long> hashRing = new ConcurrentSkipListMap<>();
    // nodeId -> 节点信息
    private final Map<Long, StorageNode> nodeMap = new HashMap<>();

    /**
     * 添加节点到哈希环
     */
    public synchronized void addNode(StorageNode node) {
        nodeMap.put(node.getId(), node);
        for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
            long hash = hash(node.getId() + "-VN" + i);
            hashRing.put(hash, node.getId());
        }
        log.info("一致性哈希: 添加节点 id={}, 虚拟节点数={}", node.getId(), VIRTUAL_NODE_COUNT);
    }

    /**
     * 从哈希环移除节点
     */
    public synchronized void removeNode(Long nodeId) {
        nodeMap.remove(nodeId);
        for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
            long hash = hash(nodeId + "-VN" + i);
            hashRing.remove(hash);
        }
        log.info("一致性哈希: 移除节点 id={}", nodeId);
    }

    /**
     * 根据 key 选择节点（顺时针第一个）
     */
    public StorageNode selectNode(String key) {
        if (hashRing.isEmpty()) {
            return null;
        }
        long hash = hash(key);
        Map.Entry<Long, Long> entry = hashRing.ceilingEntry(hash);
        if (entry == null) {
            entry = hashRing.firstEntry();
        }
        return nodeMap.get(entry.getValue());
    }

    /**
     * 选择多个不同节点（用于副本放置）
     */
    public List<StorageNode> selectNodes(String key, int count) {
        if (hashRing.isEmpty()) {
            return Collections.emptyList();
        }
        List<StorageNode> result = new ArrayList<>();
        Set<Long> selectedNodeIds = new HashSet<>();

        long hash = hash(key);
        // 从 hash 位置开始顺时针遍历
        for (int i = 0; i < count; i++) {
            Map.Entry<Long, Long> entry = hashRing.ceilingEntry(hash);
            if (entry == null) {
                entry = hashRing.firstEntry();
            }
            if (entry == null) break;

            Long nodeId = entry.getValue();
            if (!selectedNodeIds.contains(nodeId)) {
                result.add(nodeMap.get(nodeId));
                selectedNodeIds.add(nodeId);
            }
            // 移动到下一个位置继续查找
            hash = entry.getKey() + 1;
        }
        return result;
    }

    /**
     * 重建哈希环（全量刷新）
     */
    public synchronized void rebuild(List<StorageNode> nodes) {
        hashRing.clear();
        nodeMap.clear();
        for (StorageNode node : nodes) {
            addNode(node);
        }
        log.info("一致性哈希环已重建, 节点数={}", nodes.size());
    }

    /**
     * 获取当前节点数量
     */
    public int getNodeCount() {
        return nodeMap.size();
    }

    /**
     * 根据节点ID获取节点信息
     */
    public StorageNode getNodeById(Long nodeId) {
        return nodeMap.get(nodeId);
    }

    /**
     * FNV1_32 哈希算法
     */
    private long hash(String key) {
        final long FNV_32_PRIME = 16777619L;
        long hash = 2166136261L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= FNV_32_PRIME;
        }
        return hash & 0xffffffffL;
    }
}
