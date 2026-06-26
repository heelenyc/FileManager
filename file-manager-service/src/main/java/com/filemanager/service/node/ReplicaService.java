package com.filemanager.service.node;

import com.filemanager.model.entity.StorageNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicaService {

    private final ConsistentHash consistentHash;
    private final NodeRegistryService nodeRegistryService;

    /**
     * 为文件分片选择副本节点（排除主节点，选择不同物理节点）
     */
    public List<StorageNode> selectReplicaNodes(String hashKey, int replicaCount, Long primaryNodeId) {
        List<StorageNode> allOnline = nodeRegistryService.getOnlineNodes();
        if (allOnline.size() <= 1) {
            return List.of();
        }

        // 排除主节点
        List<StorageNode> candidates = allOnline.stream()
                .filter(n -> !n.getId().equals(primaryNodeId))
                .toList();

        int count = Math.min(replicaCount, candidates.size());
        return candidates.subList(0, count);
    }

    /**
     * 选择读取副本（随机负载均衡）
     */
    public StorageNode selectReadReplica(List<StorageNode> replicas) {
        if (replicas == null || replicas.isEmpty()) {
            return null;
        }
        return replicas.get(ThreadLocalRandom.current().nextInt(replicas.size()));
    }
}
