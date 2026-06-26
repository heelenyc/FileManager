package com.filemanager.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filemanager.common.result.ResultCode;
import com.filemanager.common.util.JwtUtil;
import com.filemanager.dao.mapper.UserMapper;
import com.filemanager.dao.mapper.UserRoleMapper;
import com.filemanager.dao.mapper.RolePermissionMapper;
import com.filemanager.model.dto.LoginRequest;
import com.filemanager.model.dto.RegisterRequest;
import com.filemanager.model.entity.User;
import com.filemanager.model.vo.TokenVO;
import com.filemanager.model.vo.UserVO;
import com.filemanager.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public TokenVO login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissionCodes = rolePermissionMapper.selectPermissionCodesByUserId(user.getId());

        return buildTokenVO(user, roleCodes, permissionCodes);
    }

    public void register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        userMapper.insert(user);

        // 默认分配普通用户角色
        userRoleMapper.insert(user.getId(), 2L); // 2 = USER角色
        log.info("用户注册成功: username={}", request.getUsername());
    }

    public TokenVO refreshToken(String refreshToken) {
        try {
            Claims claims = JwtUtil.parseToken(jwtSecret, refreshToken);
            String type = claims.get("type", String.class);
            if (!"refresh".equals(type)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED);
            }
            Long userId = Long.parseLong(claims.getSubject());
            User user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == 0) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
            List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(user.getId());
            List<String> permissionCodes = rolePermissionMapper.selectPermissionCodesByUserId(user.getId());
            return buildTokenVO(user, roleCodes, permissionCodes);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toUserVO(user, userId);
    }

    public Page<UserVO> listUsers(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, "0")
                .orderByAsc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword)
            );
        }
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(u -> toUserVO(u, u.getId())).toList());
        return voPage;
    }

    // ========== 用户管理（管理员功能）==========

    public UserVO createUser(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        userMapper.insert(user);

        // 默认分配普通用户角色
        userRoleMapper.insert(user.getId(), 2L);

        log.info("管理员创建用户: username={}", request.getUsername());
        return toUserVO(user, user.getId());
    }

    public void updateUser(Long userId, RegisterRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // admin账号允许修改昵称/邮箱/手机号，但不允许修改密码
        boolean isAdmin = "admin".equals(user.getUsername());

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            if (isAdmin) {
                throw new IllegalArgumentException("不允许修改管理员密码");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userMapper.updateById(user);
        log.info("更新用户信息: username={}", user.getUsername());
    }

    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if ("admin".equals(user.getUsername())) {
            throw new IllegalArgumentException("不允许删除管理员账号");
        }
        user.setDeleted("1");
        userMapper.updateById(user);
        log.info("删除用户: username={}", user.getUsername());
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("用户修改密码: username={}", user.getUsername());
    }

    public String toggleUserStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if ("admin".equals(user.getUsername())) {
            return "SKIP"; // 管理员账号不允许禁用
        }
        int newStatus = user.getStatus() == 1 ? 0 : 1;
        user.setStatus(newStatus);
        userMapper.updateById(user);
        log.info("{}用户: username={}", newStatus == 1 ? "启用" : "禁用", user.getUsername());
        return newStatus == 1 ? "ENABLED" : "DISABLED";
    }

    public void resetPassword(Long userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("重置用户密码: username={}", user.getUsername());
    }

    public void assignRoles(Long userId, List<Long> roleIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 先删除原有角色关联
        userRoleMapper.deleteByUserId(userId);
        // 重新分配角色
        for (Long roleId : roleIds) {
            userRoleMapper.insert(userId, roleId);
        }
        log.info("用户[{}]重新分配了{}个角色", user.getUsername(), roleIds.size());
    }

    private UserVO toUserVO(User user, Long userId) {
        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(userId);
        List<String> permissionCodes = rolePermissionMapper.selectPermissionCodesByUserId(userId);

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setRoles(roleCodes);
        vo.setPermissions(permissionCodes);
        vo.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        return vo;
    }

    private TokenVO buildTokenVO(User user, List<String> roleCodes, List<String> permissionCodes) {
        Map<String, Object> accessClaims = new HashMap<>();
        accessClaims.put("roles", roleCodes);
        accessClaims.put("permissions", permissionCodes);
        accessClaims.put("type", "access");

        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("type", "refresh");

        String accessToken = JwtUtil.generateToken(jwtSecret, String.valueOf(user.getId()), accessClaims, accessTokenExpiration);
        String refreshToken = JwtUtil.generateToken(jwtSecret, String.valueOf(user.getId()), refreshClaims, refreshTokenExpiration);

        return TokenVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpiration / 1000)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
}
