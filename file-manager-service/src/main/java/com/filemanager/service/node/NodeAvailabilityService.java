package com.filemanager.service.node;

import com.filemanager.common.exception.BusinessException;
import com.filemanager.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 节点可用性检查服务
 * 用于检查存储节点是否可用，确保上传/下载服务正常运行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeAvailabilityService {

    private final ConsistentHash consistentHash;
    private final NodeRegistryService nodeRegistryService;

    /**
     * 检查是否有可用节点
     * @return true-有可用节点，false-无可用节点
     */
    public boolean hasAvailableNodes() {
        return consistentHash.getNodeCount() > 0;
    }

    /**
     * 获取可用节点数量
     */
    public int getAvailableNodeCount() {
        return consistentHash.getNodeCount();
    }

    /**
     * 检查节点可用性，无节点时抛出异常
     * 用于上传/下载接口前置检查
     */
    public void checkAvailable() {
        if (!hasAvailableNodes()) {
            log.warn("无可用存储节点，文件上传/下载服务暂不可用");
            throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE.getCode(),
                    "服务暂不可用，请先注册存储节点");
        }
    }

    /**
     * 获取所有在线节点信息
     */
    public List<?> getOnlineNodes() {
        return nodeRegistryService.getOnlineNodes();
    }
}