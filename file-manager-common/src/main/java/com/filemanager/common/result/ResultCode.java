package com.filemanager.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    // 参数校验 4xx
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // 用户相关 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "密码错误"),
    USER_ALREADY_EXISTS(1003, "用户已存在"),
    USER_DISABLED(1004, "用户已被禁用"),

    // 文件相关 2xxx
    FILE_NOT_FOUND(2001, "文件不存在"),
    FILE_UPLOAD_FAILED(2002, "文件上传失败"),
    FILE_DOWNLOAD_FAILED(2003, "文件下载失败"),
    FILE_DELETE_FAILED(2004, "文件删除失败"),
    FILE_SIZE_EXCEEDED(2005, "文件大小超出限制"),

    // 节点相关 3xxx
    NODE_NOT_AVAILABLE(3001, "无可用存储节点"),
    NODE_OFFLINE(3002, "存储节点离线");

    private final int code;
    private final String message;
}
