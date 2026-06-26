package com.filemanager.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filemanager.common.annotation.RequirePermission;
import com.filemanager.common.result.Result;
import com.filemanager.model.dto.LoginRequest;
import com.filemanager.model.dto.RegisterRequest;
import com.filemanager.model.vo.TokenVO;
import com.filemanager.model.vo.UserVO;
import com.filemanager.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestParam String refreshToken) {
        return Result.success(authService.refreshToken(refreshToken));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/current")
    public Result<UserVO> currentUser(@RequestAttribute("userId") Long userId) {
        return Result.success(authService.getCurrentUser(userId));
    }

    // ========== 用户管理（需管理员权限）==========

    @Operation(summary = "获取用户列表（分页）")
    @GetMapping("/users")
    @RequirePermission("user:view")
    public Result<Page<UserVO>> userList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(authService.listUsers(pageNum, pageSize, keyword));
    }

    @Operation(summary = "创建用户（管理员）")
    @PostMapping("/users")
    @RequirePermission("user:create")
    public Result<UserVO> createUser(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.createUser(request));
    }

    @Operation(summary = "编辑用户信息")
    @PutMapping("/users/{id}")
    @RequirePermission("user:edit")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody RegisterRequest request) {
        authService.updateUser(id, request);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/users/{id}")
    @RequirePermission("user:delete")
    public Result<Void> deleteUser(@PathVariable Long id) {
        authService.deleteUser(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/users/{id}/status")
    @RequirePermission("user:edit")
    public Result<String> toggleStatus(@PathVariable Long id) {
        String result = authService.toggleUserStatus(id);
        if ("SKIP".equals(result)) {
            return Result.fail(400, "不允许禁用管理员账号");
        }
        return Result.success(result);
    }

    @Operation(summary = "重置用户密码（管理员）")
    @PutMapping("/users/{id}/password")
    @RequirePermission("user:reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        authService.resetPassword(id, body.get("password"));
        return Result.success();
    }

    @Operation(summary = "修改自己的密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestAttribute("userId") Long userId,
                                        @RequestBody Map<String, String> body) {
        authService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.success();
    }

    @Operation(summary = "分配角色给用户")
    @PutMapping("/users/{id}/roles")
    @RequirePermission("user:assign-role")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        authService.assignRoles(id, body.get("roleIds"));
        return Result.success();
    }
}
