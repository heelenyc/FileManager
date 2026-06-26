package com.filemanager.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("storage_node")
public class StorageNode extends BaseEntity {

    private String nodeName;
    private String nodeHost;
    private Integer nodePort;
    private Integer status;
    private Long availableSpace;
    private Long totalSpace;
    private String zkPath;
    private LocalDateTime lastHeartbeat;
}
