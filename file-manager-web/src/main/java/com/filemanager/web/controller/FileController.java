package com.filemanager.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filemanager.common.annotation.OperationLog;
import com.filemanager.common.annotation.RequirePermission;
import com.filemanager.common.result.Result;
import com.filemanager.model.entity.FileMetadata;
import com.filemanager.model.vo.FileVO;
import com.filemanager.model.vo.InitUploadVO;
import com.filemanager.service.file.FileService;
import java.util.List;
import java.io.IOException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件（统一走分片逻辑，小文件 chunkCount=1）")
    @PostMapping("/upload")
    @RequirePermission("file:upload")
    @OperationLog("上传文件")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestAttribute("userId") Long userId) throws IOException {
        return Result.success(fileService.upload(file, userId));
    }

    @Operation(summary = "初始化分片上传")
    @PostMapping("/chunk/init")
    @RequirePermission("file:upload")
    @OperationLog("初始化分片上传")
    public Result<InitUploadVO> initChunkUpload(@RequestParam String fileName,
                                                 @RequestParam Long fileSize,
                                                 @RequestParam(required = false) String contentType,
                                                 @RequestAttribute("userId") Long userId) {
        return Result.success(fileService.initChunkUpload(fileName, fileSize, contentType, userId));
    }

    @Operation(summary = "上传分片")
    @PostMapping("/chunk/upload")
    @RequirePermission("file:upload")
    @OperationLog("上传分片")
    public Result<Void> uploadChunk(@RequestParam String fileKey,
                                    @RequestParam Integer chunkIndex,
                                    @RequestParam Integer totalChunks,
                                    @RequestParam("file") MultipartFile chunkFile,
                                    @RequestAttribute("userId") Long userId) {
        fileService.uploadChunk(fileKey, chunkIndex, totalChunks, chunkFile);
        return Result.success();
    }

    @Operation(summary = "完成分片上传")
    @PostMapping("/chunk/complete")
    @RequirePermission("file:upload")
    @OperationLog("完成分片上传")
    public Result<FileVO> completeChunkUpload(@RequestParam String fileKey) {
        return Result.success(fileService.completeChunkUpload(fileKey));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/download/{fileKey}")
    @RequirePermission("file:download")
    @OperationLog("下载文件")
    public ResponseEntity<byte[]> download(@PathVariable String fileKey,
                                           @RequestAttribute("userId") Long userId) {
        FileMetadata metadata = fileService.getByFileKey(fileKey);
        byte[] data = fileService.download(fileKey, userId);

        String encodedFileName = URLEncoder.encode(metadata.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .header(HttpHeaders.CONTENT_TYPE,
                        metadata.getContentType() != null ? metadata.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .contentLength(data.length)
                .body(data);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileKey}")
    @RequirePermission("file:delete")
    @OperationLog("删除文件")
    public Result<Void> delete(@PathVariable String fileKey,
                               @RequestAttribute("userId") Long userId) {
        fileService.delete(fileKey, userId);
        return Result.success();
    }

    @Operation(summary = "获取文件信息")
    @GetMapping("/{fileKey}")
    @RequirePermission("file:download")
    public Result<FileVO> getInfo(@PathVariable String fileKey) {
        return Result.success(fileService.getFileVO(fileKey));
    }

    @Operation(summary = "文件列表（分页）")
    @GetMapping("/list")
    public Result<Page<FileVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "10") int pageSize,
                                     @RequestParam(required = false) String keyword,
                                     @RequestAttribute("userId") Long userId,
                                     @RequestAttribute("roles") List<String> roles) {
        return Result.success(fileService.listFiles(userId, roles, keyword, pageNum, pageSize));
    }

    @Operation(summary = "查询分片上传进度（断点续传）")
    @GetMapping("/chunk/progress/{fileKey}")
    @RequirePermission("file:upload")
    public Result<InitUploadVO> getUploadProgress(@PathVariable String fileKey) {
        InitUploadVO progress = fileService.getUploadProgress(fileKey);
        if (progress == null) {
            return Result.success(null);
        }
        return Result.success(progress);
    }

    @Operation(summary = "生成预签名下载链接")
    @PostMapping("/download/presign/{fileKey}")
    @RequirePermission("file:download")
    @OperationLog("生成预签名下载链接")
    public Result<String> generatePresignedUrl(@PathVariable String fileKey,
                                                @RequestAttribute("userId") Long userId,
                                                @RequestParam(defaultValue = "3600") long expireSeconds) {
        return Result.success(fileService.generatePresignedUrl(fileKey, userId, expireSeconds));
    }

    @Operation(summary = "预签名下载（无需登录）")
    @GetMapping("/download/presigned/{token}")
    public ResponseEntity<byte[]> downloadByPresignedToken(@PathVariable String token) {
        FileMetadata metadata = fileService.getByFileKeyForPresigned(token);
        byte[] data = fileService.downloadByPresignedToken(token);

        String encodedFileName = URLEncoder.encode(metadata.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .header(HttpHeaders.CONTENT_TYPE,
                        metadata.getContentType() != null ? metadata.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .contentLength(data.length)
                .body(data);
    }

    // ==================== 回收站接口 ====================

    @Operation(summary = "回收站文件列表")
    @GetMapping("/recycle/list")
    @RequirePermission("file:recycle")
    public Result<Page<FileVO>> recycleList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(fileService.listRecycledFiles(keyword, pageNum, pageSize));
    }

    @Operation(summary = "恢复文件（从回收站）")
    @PutMapping("/{fileKey}/restore")
    @RequirePermission("file:recycle")
    @OperationLog("恢复文件")
    public Result<Void> restoreFile(@PathVariable String fileKey) {
        fileService.restoreFile(fileKey);
        return Result.success();
    }

    @Operation(summary = "物理删除文件（彻底清除）")
    @DeleteMapping("/{fileKey}/purge")
    @RequirePermission("file:recycle")
    @OperationLog("物理删除文件")
    public Result<Void> purgeFile(@PathVariable String fileKey) {
        fileService.purgeFile(fileKey);
        return Result.success();
    }
}
