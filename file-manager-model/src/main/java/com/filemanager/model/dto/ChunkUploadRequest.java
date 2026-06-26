package com.filemanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChunkUploadRequest {

    @NotBlank(message = "文件唯一标识不能为空")
    private String fileKey;

    @NotNull(message = "分片序号不能为空")
    private Integer chunkIndex;

    @NotNull(message = "总分片数不能为空")
    private Integer totalChunks;

    private String fileName;
    private String contentType;
}
