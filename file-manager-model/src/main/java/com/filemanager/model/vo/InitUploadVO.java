package com.filemanager.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitUploadVO {

    private String fileKey;
    private Long chunkSize;
    private Integer chunkCount;
    private Integer uploadedChunks;
}
