package com.filemanager.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filemanager.common.annotation.OperationLog;
import com.filemanager.common.annotation.RequirePermission;
import com.filemanager.common.exception.BusinessException;
import com.filemanager.common.result.Result;
import com.filemanager.common.result.ResultCode;
import com.filemanager.dao.mapper.StorageNodeMapper;
import com.filemanager.model.entity.StorageNode;
import com.filemanager.service.node.ConsistentHash;
import com.filemanager.service.node.NodeAutoRegisterService;
import com.filemanager.service.node.NodeRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Tag(name = "节点管理")
@RestController
@RequestMapping("/node")
@RequiredArgsConstructor
public class NodeController {

    private final NodeAutoRegisterService nodeAutoRegisterService;
    private final NodeRegistryService nodeRegistryService;
    private final StorageNodeMapper storageNodeMapper;
    private final ConsistentHash consistentHash;

    @Operation(summary = "获取当前节点信息")
    @GetMapping("/current")
    public Result<StorageNode> getCurrentNode() {
        StorageNode node = nodeAutoRegisterService.getCurrentNode();
        if (node == null) {
            throw new BusinessException(3001, "当前节点未注册");
        }
        return Result.success(node);
    }

    @Operation(summary = "获取所有节点列表")
    @GetMapping("/list")
    @RequirePermission("node:view")
    public Result<List<StorageNode>> listNodes() {
        List<StorageNode> nodes = storageNodeMapper.selectList(
                new LambdaQueryWrapper<StorageNode>().orderByAsc(StorageNode::getId)
        );
        return Result.success(nodes);
    }

    @Operation(summary = "获取在线节点列表")
    @GetMapping("/online")
    @RequirePermission("node:view")
    public Result<List<StorageNode>> onlineNodes() {
        return Result.success(nodeRegistryService.getOnlineNodes());
    }

    @Operation(summary = "隔离节点（强制下线）")
    @PostMapping("/isolate/{nodeName}")
    @RequirePermission("node:manage")
    @OperationLog("隔离节点")
    public Result<Void> isolateNode(@PathVariable String nodeName) {
        StorageNode node = storageNodeMapper.selectOne(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getNodeName, nodeName)
        );
        if (node == null) {
            throw new BusinessException(404, "节点不存在");
        }
        if (node.getStatus() == 2) {
            throw new BusinessException(400, "节点已处于隔离状态");
        }

        // 更新状态为隔离
        node.setStatus(2);
        node.setLastHeartbeat(LocalDateTime.now());
        storageNodeMapper.updateById(node);

        // 从哈希环移除
        consistentHash.removeNode(nodeName);

        log.info("节点隔离成功: nodeName={}", nodeName);
        return Result.success();
    }

    @Operation(summary = "恢复节点（取消隔离）")
    @PostMapping("/recover/{nodeName}")
    @RequirePermission("node:manage")
    @OperationLog("恢复节点")
    public Result<Void> recoverNode(@PathVariable String nodeName) {
        StorageNode node = storageNodeMapper.selectOne(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getNodeName, nodeName)
        );
        if (node == null) {
            throw new BusinessException(404, "节点不存在");
        }
        if (node.getStatus() != 2) {
            throw new BusinessException(400, "节点未处于隔离状态");
        }

        // 恢复为在线状态（前提是节点实际在线）
        node.setStatus(1);
        node.setLastHeartbeat(LocalDateTime.now());
        storageNodeMapper.updateById(node);

        // 添加到哈希环
        consistentHash.addNode(node);

        log.info("节点恢复成功: nodeName={}", nodeName);
        return Result.success();
    }

    @Operation(summary = "删除节点记录")
    @DeleteMapping("/{nodeName}")
    @RequirePermission("node:manage")
    @OperationLog("删除节点")
    public Result<Void> deleteNode(@PathVariable String nodeName) {
        StorageNode node = storageNodeMapper.selectOne(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getNodeName, nodeName)
        );
        if (node == null) {
            throw new BusinessException(404, "节点不存在");
        }

        // 只能删除离线或隔离的节点
        if (node.getStatus() == 1) {
            throw new BusinessException(400, "在线节点不能删除，请先隔离");
        }

        storageNodeMapper.deleteById(node.getId());
        consistentHash.removeNode(nodeName);

        log.info("节点记录删除成功: nodeName={}", nodeName);
        return Result.success();
    }

    @Operation(summary = "彻底删除节点（物理删除）")
    @DeleteMapping("/physical/{nodeName}")
    @RequirePermission("node:manage")
    @OperationLog("彻底删除节点")
    public Result<Void> physicalDeleteNode(@PathVariable String nodeName) {
        StorageNode node = storageNodeMapper.selectOne(
                new LambdaQueryWrapper<StorageNode>()
                        .eq(StorageNode::getNodeName, nodeName)
        );
        if (node == null) {
            throw new BusinessException(ResultCode.NODE_NOT_AVAILABLE);
        }

        // 物理删除
        storageNodeMapper.physicalDeleteById(node.getId());
        consistentHash.removeNode(nodeName);

        log.info("节点彻底删除成功: nodeName={}", nodeName);
        return Result.success();
    }

    @Operation(summary = "获取哈希环状态")
    @GetMapping("/hash-ring")
    @RequirePermission("node:view")
    public Result<Integer> hashRingStatus() {
        return Result.success(consistentHash.getNodeCount());
    }
}