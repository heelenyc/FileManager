package com.filemanager.service.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filemanager.dao.mapper.StorageNodeMapper;
import com.filemanager.model.entity.StorageNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 节点查询服务
 * 提供节点信息的查询功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRegistryService {

    private final StorageNodeMapper storageNodeMapper;

    /**
     * 获取所有在线节点（status=1）
     */
    public List<StorageNode> getOnlineNodes() {
        return storageNodeMapper.selectList(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getStatus, 1)
        );
    }

    /**
     * 获取所有节点列表
     */
    public List<StorageNode> getAllNodes() {
        return storageNodeMapper.selectList(null);
    }

    /**
     * 根据节点名称获取节点信息
     */
    public StorageNode getNodeByName(String nodeName) {
        return storageNodeMapper.selectOne(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getNodeName, nodeName)
        );
    }
}