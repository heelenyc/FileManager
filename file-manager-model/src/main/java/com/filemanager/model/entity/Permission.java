package com.filemanager.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class Permission extends BaseEntity {

    private String permissionCode;
    private String permissionName;
    private Integer resourceType;
    private Long parentId;
    private Integer sortOrder;
}
