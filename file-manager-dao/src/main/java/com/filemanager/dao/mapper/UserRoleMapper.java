package com.filemanager.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filemanager.model.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper {

    @Select("""
        SELECT r.role_code FROM sys_role r
        INNER JOIN sys_user_role ur ON r.id = ur.role_id
        WHERE ur.user_id = #{userId} AND r.deleted = '0'
    """)
    List<String> selectRoleCodesByUserId(Long userId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id, created_at) VALUES (#{userId}, #{roleId}, NOW())")
    void insert(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
