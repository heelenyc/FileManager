package com.filemanager.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filemanager.model.entity.StorageNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StorageNodeMapper extends BaseMapper<StorageNode> {

    /**
     * 查询逻辑删除的同名节点（绕过MyBatis-Plus逻辑删除机制）
     */
    @Select("SELECT * FROM storage_node WHERE node_name = #{nodeName} AND deleted = '1' LIMIT 1")
    StorageNode selectDeletedByName(@Param("nodeName") String nodeName);

    /**
     * 物理删除节点（绕过MyBatis-Plus逻辑删除机制）
     */
    @Delete("DELETE FROM storage_node WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 恢复逻辑删除的节点
     */
    @Update("UPDATE storage_node SET deleted = '0' WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
