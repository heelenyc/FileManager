package com.filemanager.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filemanager.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
