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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${cache.user-info.expire-minutes:5}")
    private long cacheExpireMinutes;

    // 用户信息缓存 key 前缀
    private static final String USER_INFO_CACHE_KEY = "user:info:";
    // Token 黑名单 key 前缀
    private static final String TOKEN_BLACKLIST_KEY = "token:blacklist:";

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
        // 1. 先从 Redis 缓存读取
        String cacheKey = USER_INFO_CACHE_KEY + userId;
        String cachedData = redisTemplate.opsForValue().get(cacheKey);

        if (cachedData != null) {
            try {
                UserVO cachedUser = objectMapper.readValue(cachedData, UserVO.class);
                log.debug("从缓存获取用户信息: userId={}, username={}", userId, cachedUser.getUsername());
                return cachedUser;
            } catch (JsonProcessingException e) {
                log.warn("缓存数据解析失败，将从数据库查询: userId={}", userId);
            }
        }

        // 2. 缓存不存在，从数据库查询
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        UserVO userVO = toUserVO(user, userId);

        // 3. 缓存到 Redis（配置的分钟数过期）
        try {
            String userVOJson = objectMapper.writeValueAsString(userVO);
            redisTemplate.opsForValue().set(cacheKey, userVOJson, cacheExpireMinutes, TimeUnit.MINUTES);
            log.debug("用户信息已缓存: userId={}, username={}, expireMinutes={}", userId, userVO.getUsername(), cacheExpireMinutes);
        } catch (JsonProcessingException e) {
            log.warn("用户信息缓存失败: userId={}", userId);
        }

        return userVO;
    }

    /**
     * 用户登出（清除缓存 + Token 加入黑名单）
     */
    public void logout(Long userId, String token) {
        // 1. 清除用户信息缓存
        clearUserCache(userId);

        // 2. 将 Token 加入黑名单
        addToTokenBlacklist(token);

        log.info("用户登出，缓存已清除，Token已加入黑名单: userId={}", userId);
    }

    /**
     * 将 Token 加入黑名单
     */
    public void addToTokenBlacklist(String token) {
        try {
            String tokenHash = hashToken(token);
            String blacklistKey = TOKEN_BLACKLIST_KEY + tokenHash;

            // 计算 Token 的剩余有效时间
            Claims claims = JwtUtil.parseToken(jwtSecret, token);
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            long remainingTime = expirationTime - currentTime;

            if (remainingTime > 0) {
                // 将 Token 加入黑名单，过期时间为剩余有效时间
                redisTemplate.opsForValue().set(blacklistKey, "1", remainingTime, TimeUnit.MILLISECONDS);
                log.debug("Token已加入黑名单: hash={}, remainingTime={}ms", tokenHash, remainingTime);
            } else {
                log.debug("Token已过期，无需加入黑名单: hash={}", tokenHash);
            }
        } catch (Exception e) {
            log.warn("Token加入黑名单失败: {}", e.getMessage());
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String tokenHash = hashToken(token);
            String blacklistKey = TOKEN_BLACKLIST_KEY + tokenHash;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
        } catch (Exception e) {
            log.warn("检查Token黑名单失败: {}", e.getMessage());
            return false;  // 查询失败时默认不在黑名单，避免影响正常用户
        }
    }

    /**
     * 计算 Token 的 hash 值（用于黑名单 key）
     */
    private String hashToken(String token) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
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
        // 清除用户缓存
        clearUserCache(userId);
        log.info("删除用户: username={}, 缓存已清除", user.getUsername());
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
        // 清除用户缓存（状态变更）
        clearUserCache(userId);
        log.info("{}用户: username={}, 缓存已清除", newStatus == 1 ? "启用" : "禁用", user.getUsername());
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
        // 清除用户缓存（角色变更后权限也会变更）
        clearUserCache(userId);
        log.info("用户[{}]重新分配了{}个角色，缓存已清除", user.getUsername(), roleIds.size());
    }

    /**
     * 清除用户信息缓存（角色/权限变更时调用）
     */
    public void clearUserCache(Long userId) {
        String cacheKey = USER_INFO_CACHE_KEY + userId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("用户缓存已清除: userId={}", userId);
        }
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
