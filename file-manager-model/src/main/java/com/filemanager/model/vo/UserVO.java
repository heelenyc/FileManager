package com.filemanager.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private List<String> roles;
    private List<String> permissions;
    private String createdAt;
}
