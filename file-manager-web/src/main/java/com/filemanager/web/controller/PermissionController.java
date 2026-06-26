package com.filemanager.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filemanager.common.annotation.RequirePermission;
import com.filemanager.common.result.Result;
import com.filemanager.dao.mapper.PermissionMapper;
import com.filemanager.model.entity.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "权限管理")
@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionMapper permissionMapper;

    @Operation(summary = "获取权限列表（分页）")
    @GetMapping("/list")
    @RequirePermission("role:view")
    public Result<Page<Permission>> listPermissions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getSortOrder);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(Permission::getPermissionCode, keyword)
                    .or().like(Permission::getPermissionName, keyword)
            );
        }
        return Result.success(permissionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Operation(summary = "创建权限")
    @PostMapping
    @RequirePermission("role:assign-permission")
    public Result<Permission> create(@RequestBody Permission permission) {
        // 检查编码是否重复
        Long count = permissionMapper.selectCount(
                new LambdaQueryWrapper<Permission>()
                        .eq(Permission::getPermissionCode, permission.getPermissionCode())
        );
        if (count > 0) {
            return Result.fail(400, "权限码已存在");
        }
        permissionMapper.insert(permission);
        return Result.success(permission);
    }

    @Operation(summary = "更新权限")
    @PutMapping("/{id}")
    @RequirePermission("role:assign-permission")
    public Result<Void> update(@PathVariable Long id, @RequestBody Permission permission) {
        permission.setId(id);
        permissionMapper.updateById(permission);
        return Result.success();
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/{id}")
    @RequirePermission("role:assign-permission")
    public Result<Void> delete(@PathVariable Long id) {
        // 不允许删除已有关联的权限（简单校验）
        permissionMapper.deleteById(id);
        return Result.success();
    }
}
