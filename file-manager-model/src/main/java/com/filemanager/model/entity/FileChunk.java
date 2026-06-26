package com.filemanager.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_chunk")
public class FileChunk extends BaseEntity {

    private Long fileId;
    private Integer chunkIndex;
    private Long chunkSize;
    private String chunkHash;
    private String storageNodeName;   // 主存储节点名称（节点名称唯一不变）
    private String replicaNodeNames;  // 副本节点名称列表（逗号分隔）
    private String storagePath;
}