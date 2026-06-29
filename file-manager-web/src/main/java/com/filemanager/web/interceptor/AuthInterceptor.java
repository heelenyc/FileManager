package com.filemanager.web.interceptor;

import com.filemanager.common.annotation.RequirePermission;
import com.filemanager.common.annotation.SkipAuth;
import com.filemanager.common.constants.CommonConstants;
import com.filemanager.common.exception.BusinessException;
import com.filemanager.common.result.ResultCode;
import com.filemanager.common.util.JwtUtil;
import com.filemanager.service.auth.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查是否跳过认证
        if (isSkipAuth(handlerMethod)) {
            return true;
        }

        // 解析 Token
        String token = extractToken(request);
        if (token == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = JwtUtil.parseToken(jwtSecret, token);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 校验 token 类型
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 检查 token 是否在黑名单中（已退出登录）
        if (authService.isTokenBlacklisted(token)) {
            log.warn("Token已失效（在黑名单中）: userId={}", claims.getSubject());
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 设置用户信息到 request
        Long userId = Long.parseLong(claims.getSubject());
        request.setAttribute("userId", userId);

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);
        request.setAttribute("roles", roles);
        request.setAttribute("permissions", permissions);

        // 校验权限注解
        RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (requirePermission != null) {
            String[] requiredPermissions = requirePermission.value();
            // permissions为空时视为无权限
            if (permissions == null || permissions.isEmpty()) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            if (requirePermission.requireAll()) {
                // 需要拥有所有指定权限
                for (String perm : requiredPermissions) {
                    if (!permissions.contains(perm)) {
                        throw new BusinessException(ResultCode.FORBIDDEN);
                    }
                }
            } else {
                // 拥有任一权限即可
                boolean hasAny = false;
                for (String perm : requiredPermissions) {
                    if (permissions.contains(perm)) {
                        hasAny = true;
                        break;
                    }
                }
                if (!hasAny) {
                    throw new BusinessException(ResultCode.FORBIDDEN);
                }
            }
        }

        return true;
    }

    private boolean isSkipAuth(HandlerMethod handlerMethod) {
        if (handlerMethod.getMethodAnnotation(SkipAuth.class) != null) {
            return true;
        }
        return handlerMethod.getBeanType().getAnnotation(SkipAuth.class) != null;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (header != null && header.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return header.substring(CommonConstants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
