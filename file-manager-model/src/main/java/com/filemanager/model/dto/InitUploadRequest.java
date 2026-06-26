package com.filemanager.model.dto;

import lombok.Data;

@Data
public class InitUploadRequest {

    private String fileName;
    private Long fileSize;
    private String contentType;
}
