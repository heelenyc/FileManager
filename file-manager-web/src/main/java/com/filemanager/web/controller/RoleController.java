package com.filemanager.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filemanager.common.annotation.RequirePermission;
import com.filemanager.common.result.Result;
import com.filemanager.model.entity.Permission;
import com.filemanager.model.entity.Role;
import com.filemanager.service.auth.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "获取角色列表（分页）")
    @GetMapping("/list")
    @RequirePermission("role:view")
    public Result<Page<Role>> listRoles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(roleService.listRoles(pageNum, pageSize, keyword));
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    @RequirePermission("role:view")
    public Result<Role> getRole(@PathVariable Long id) {
        return Result.success(roleService.getRoleById(id));
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @RequirePermission("role:create")
    public Result<Role> createRole(@RequestBody Role role) {
        return Result.success(roleService.createRole(role));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    @RequirePermission("role:edit")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        roleService.updateRole(role);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @RequirePermission("role:delete")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @Operation(summary = "获取角色的权限列表")
    @GetMapping("/{id}/permissions")
    @RequirePermission("role:view")
    public Result<List<Permission>> getRolePermissions(@PathVariable Long id) {
        return Result.success(roleService.getPermissionsByRoleId(id));
    }

    @Operation(summary = "给角色分配权限")
    @PostMapping("/{id}/permissions")
    @RequirePermission("role:assign-permission")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.assignPermissions(id, body.get("permissionIds"));
        return Result.success();
    }

    @Operation(summary = "获取所有权限（用于分配）")
    @GetMapping("/permissions/all")
    @RequirePermission("role:view")
    public Result<List<Permission>> listAllPermissions() {
        return Result.success(roleService.listAllPermissions());
    }
}
