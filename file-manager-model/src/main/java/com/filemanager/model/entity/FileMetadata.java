package com.filemanager.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_metadata")
public class FileMetadata extends BaseEntity {

    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String fileHash;
    private String aesKeyEncrypted;
    private Integer chunkCount;
    private Long chunkSize;
    private Long uploadUserId;
    private String storagePath;
    private String deleted;
}
