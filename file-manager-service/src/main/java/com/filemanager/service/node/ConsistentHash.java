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

    // 哈希环: hash -> nodeName
    private final ConcurrentSkipListMap<Long, String> hashRing = new ConcurrentSkipListMap<>();
    // nodeName -> 节点信息
    private final Map<String, StorageNode> nodeMap = new HashMap<>();

    /**
     * 添加节点到哈希环
     */
    public synchronized void addNode(StorageNode node) {
        nodeMap.put(node.getNodeName(), node);
        for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
            long hash = hash(node.getNodeName() + "-VN" + i);
            hashRing.put(hash, node.getNodeName());
        }
        log.debug("一致性哈希: 添加节点 nodeName={}, 虚拟节点数={}", node.getNodeName(), VIRTUAL_NODE_COUNT);
    }

    /**
     * 从哈希环移除节点
     */
    public synchronized void removeNode(String nodeName) {
        nodeMap.remove(nodeName);
        for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
            long hash = hash(nodeName + "-VN" + i);
            hashRing.remove(hash);
        }
        log.debug("一致性哈希: 移除节点 nodeName={}", nodeName);
    }

    /**
     * 根据 key 选择节点（顺时针第一个）
     */
    public StorageNode selectNode(String key) {
        if (hashRing.isEmpty()) {
            return null;
        }
        long hash = hash(key);
        Map.Entry<Long, String> entry = hashRing.ceilingEntry(hash);
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
        Set<String> selectedNodeNames = new HashSet<>();

        long hash = hash(key);
        // 从 hash 位置开始顺时针遍历
        for (int i = 0; i < count; i++) {
            Map.Entry<Long, String> entry = hashRing.ceilingEntry(hash);
            if (entry == null) {
                entry = hashRing.firstEntry();
            }
            if (entry == null) break;

            String nodeName = entry.getValue();
            if (!selectedNodeNames.contains(nodeName)) {
                result.add(nodeMap.get(nodeName));
                selectedNodeNames.add(nodeName);
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
        log.debug("一致性哈希环已重建, 节点数={}", nodes.size());
    }

    /**
     * 获取当前节点数量
     */
    public int getNodeCount() {
        return nodeMap.size();
    }

    /**
     * 根据节点名称获取节点信息
     */
    public StorageNode getNodeByName(String nodeName) {
        return nodeMap.get(nodeName);
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