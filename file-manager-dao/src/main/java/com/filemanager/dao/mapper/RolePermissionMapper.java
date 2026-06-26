package com.filemanager.dao.mapper;

import com.filemanager.model.entity.Permission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper {

    @Select("""
        SELECT DISTINCT p.id, p.permission_code, p.permission_name, p.resource_type,
               p.parent_id, p.sort_order, p.deleted, p.created_at, p.updated_at
        FROM sys_permission p
        INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
        INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
        WHERE ur.user_id = #{userId} AND p.deleted = '0'
        ORDER BY p.sort_order
    """)
    List<Permission> selectPermissionsByUserId(Long userId);

    @Select("""
        SELECT DISTINCT p.permission_code
        FROM sys_permission p
        INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
        INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
        WHERE ur.user_id = #{userId} AND p.deleted = '0'
    """)
    List<String> selectPermissionCodesByUserId(Long userId);

    @Select("""
        SELECT DISTINCT p.id, p.permission_code, p.permission_name, p.resource_type,
               p.parent_id, p.sort_order, p.deleted, p.created_at, p.updated_at
        FROM sys_permission p
        INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
        WHERE rp.role_id = #{roleId} AND p.deleted = '0'
        ORDER BY p.sort_order
    """)
    List<Permission> selectPermissionsByRoleId(Long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    void deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_permission (role_id, permission_id, created_at) VALUES (#{roleId}, #{permissionId}, NOW())")
    void insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
