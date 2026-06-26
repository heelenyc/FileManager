package com.filemanager.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileVO {

    private Long id;
    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Integer chunkCount;
    private Long uploadUserId;
    private String uploadUsername;
    private String createdAt;
}
