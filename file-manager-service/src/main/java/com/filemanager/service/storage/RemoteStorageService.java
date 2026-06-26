package com.filemanager.service.storage;

import com.filemanager.common.exception.BusinessException;
import com.filemanager.common.result.Result;
import com.filemanager.model.entity.StorageNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;

/**
 * 远程存储服务（分布式）
 * 用于调用其他节点的内部存储接口
 */
@Slf4j
@Service
public class RemoteStorageService {

    @Value("${file.internal.token:internal-secret-token}")
    private String internalToken;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 远程存储分片到目标节点
     * @param node 目标节点
     * @param storagePath 存储路径
     * @param data 分片数据
     */
    public void store(StorageNode node, String storagePath, byte[] data) {
        String url = buildUrl(node, "/internal/store");
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            // 构建请求URL（包含token和storagePath参数）
            String requestUrl = url + "?token=" + internalToken + "&storagePath=" + storagePath;
            
            HttpEntity<byte[]> request = new HttpEntity<>(data, headers);
            ResponseEntity<Result> response = restTemplate.exchange(requestUrl, HttpMethod.POST, request, Result.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result result = response.getBody();
                if (result.getCode() == 200) {
                    log.info("远程存储成功: node={}, path={}, size={}", node.getNodeName(), storagePath, data.length);
                    return;
                }
                throw new BusinessException(500, "远程存储失败: " + result.getMessage());
            }
            throw new BusinessException(500, "远程存储失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("远程存储失败: node={}, path={}", node.getNodeName(), storagePath, e);
            throw new BusinessException(500, "远程存储失败: " + e.getMessage());
        }
    }

    /**
     * 从远程节点读取分片
     * @param node 目标节点
     * @param storagePath 存储路径
     * @param baos 输出流
     */
    public void read(StorageNode node, String storagePath, ByteArrayOutputStream baos) {
        String url = buildUrl(node, "/internal/read");
        
        try {
            String requestUrl = url + "?token=" + internalToken + "&storagePath=" + storagePath;
            
            // 直接获取二进制数据
            ResponseEntity<byte[]> response = restTemplate.getForEntity(requestUrl, byte[].class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                byte[] data = response.getBody();
                baos.write(data, 0, data.length);
                log.info("远程读取成功: node={}, path={}, size={}", node.getNodeName(), storagePath, data.length);
                return;
            }
            throw new BusinessException(500, "远程读取失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("远程读取失败: node={}, path={}", node.getNodeName(), storagePath, e);
            throw new BusinessException(500, "远程读取失败: " + e.getMessage());
        }
    }

    /**
     * 从远程节点删除分片
     * @param node 目标节点
     * @param storagePath 存储路径
     */
    public void delete(StorageNode node, String storagePath) {
        String url = buildUrl(node, "/internal/delete");
        
        try {
            String requestUrl = url + "?token=" + internalToken + "&storagePath=" + storagePath;
            
            ResponseEntity<Result> response = restTemplate.exchange(requestUrl, HttpMethod.DELETE, null, Result.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result result = response.getBody();
                if (result.getCode() == 200) {
                    log.info("远程删除成功: node={}, path={}", node.getNodeName(), storagePath);
                    return;
                }
                throw new BusinessException(500, "远程删除失败: " + result.getMessage());
            }
            throw new BusinessException(500, "远程删除失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("远程删除失败: node={}, path={}", node.getNodeName(), storagePath, e);
            throw new BusinessException(500, "远程删除失败: " + e.getMessage());
        }
    }

    /**
     * 检查节点健康状态
     * @param node 目标节点
     * @return 是否健康
     */
    public boolean checkHealth(StorageNode node) {
        String url = buildUrl(node, "/internal/health");
        
        try {
            String requestUrl = url + "?token=" + internalToken;
            ResponseEntity<Result> response = restTemplate.getForEntity(requestUrl, Result.class);
            
            return response.getStatusCode() == HttpStatus.OK 
                && response.getBody() != null 
                && response.getBody().getCode() == 200;
        } catch (Exception e) {
            log.warn("节点健康检查失败: node={}", node.getNodeName(), e);
            return false;
        }
    }

    /**
     * 构建节点URL
     */
    private String buildUrl(StorageNode node, String path) {
        return "http://" + node.getNodeHost() + ":" + node.getNodePort() + "/api" + path;
    }
}