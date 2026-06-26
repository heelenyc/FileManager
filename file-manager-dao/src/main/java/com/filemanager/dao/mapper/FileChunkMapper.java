package com.filemanager.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filemanager.model.entity.FileChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunk> {

    @Update("UPDATE file_chunk SET deleted = '0', updated_at = NOW() WHERE file_id = #{fileId}")
    int restoreByFileId(@Param("fileId") Long fileId);

    @Delete("DELETE FROM file_chunk WHERE file_id = #{fileId}")
    int physicalDeleteByFileId(@Param("fileId") Long fileId);
}
