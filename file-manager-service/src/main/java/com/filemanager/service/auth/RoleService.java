package com.filemanager.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filemanager.dao.mapper.PermissionMapper;
import com.filemanager.dao.mapper.RoleMapper;
import com.filemanager.dao.mapper.RolePermissionMapper;
import com.filemanager.model.entity.Permission;
import com.filemanager.model.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public Page<Role> listRoles(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(Role::getDeleted, "0")
                .orderByAsc(Role::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(Role::getRoleCode, keyword)
                    .or().like(Role::getRoleName, keyword)
            );
        }
        return roleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Role getRoleById(Long id) {
        return roleMapper.selectById(id);
    }

    @Transactional
    public Role createRole(Role role) {
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, role.getRoleCode())
        );
        if (count > 0) {
            throw new IllegalArgumentException("角色编码已存在: " + role.getRoleCode());
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        roleMapper.insert(role);
        log.info("创建角色: {}", role.getRoleName());
        return role;
    }

    @Transactional
    public void updateRole(Role role) {
        Role existing = roleMapper.selectById(role.getId());
        if (existing == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        // 不允许修改ADMIN角色的编码
        if ("ADMIN".equals(existing.getRoleCode()) && !existing.getRoleCode().equals(role.getRoleCode())) {
            throw new IllegalArgumentException("不允许修改管理员角色编码");
        }
        roleMapper.updateById(role);
        log.info("更新角色: {}", role.getRoleName());
    }

    @Transactional
    public void deleteRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if ("ADMIN".equals(role.getRoleCode())) {
            throw new IllegalArgumentException("不允许删除管理员角色");
        }
        role.setDeleted("1");
        roleMapper.updateById(role);
        // 删除关联的权限
        rolePermissionMapper.deleteByRoleId(roleId);
        log.info("删除角色: {}", role.getRoleName());
    }

    public List<Permission> getPermissionsByRoleId(Long roleId) {
        return rolePermissionMapper.selectPermissionsByRoleId(roleId);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        // 先删除原有权限关联
        rolePermissionMapper.deleteByRoleId(roleId);
        // 重新添加
        for (Long permId : permissionIds) {
            rolePermissionMapper.insert(roleId, permId);
        }
        log.info("角色[{}]分配了{}个权限", role.getRoleName(), permissionIds.size());
    }

    public List<Permission> listAllPermissions() {
        return permissionMapper.selectList(null);
    }
}
