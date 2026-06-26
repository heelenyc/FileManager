package com.filemanager.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filemanager.model.entity.FileMetadata;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    @Select("""
        <script>
        SELECT * FROM file_metadata
        WHERE deleted = '1'
        <if test="keyword != null and keyword != ''">
          AND file_name LIKE CONCAT('%', #{keyword}, '%')
        </if>
        ORDER BY updated_at DESC
        LIMIT #{offset}, #{size}
        </script>
    """)
    java.util.List<FileMetadata> selectRecycledFiles(@Param("keyword") String keyword,
                                                     @Param("offset") int offset,
                                                     @Param("size") int size);

    @Select("""
        <script>
        SELECT COUNT(*) FROM file_metadata
        WHERE deleted = '1'
        <if test="keyword != null and keyword != ''">
          AND file_name LIKE CONCAT('%', #{keyword}, '%')
        </if>
        </script>
    """)
    long countRecycledFiles(@Param("keyword") String keyword);

    @Select("SELECT * FROM file_metadata WHERE file_key = #{fileKey} AND deleted = '1' LIMIT 1")
    FileMetadata selectRecycledByKey(@Param("fileKey") String fileKey);

    @Update("UPDATE file_metadata SET deleted = '0', updated_at = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM file_metadata WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
