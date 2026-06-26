package com.filemanager.web.aspect;

import com.filemanager.common.annotation.OperationLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final org.slf4j.Logger OPERATION_LOGGER = org.slf4j.LoggerFactory.getLogger("OPERATION_LOG");

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        String operation = operationLog.value();
        String method = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        String ip = getClientIp();
        Long userId = getUserId();

        Object result;
        boolean success = true;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            OPERATION_LOGGER.info("operation={}, method={}, userId={}, ip={}, success={}, duration={}ms",
                    operation, method, userId, ip, success, duration);
        }
    }

    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "unknown";
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private Long getUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;
        Object userId = attributes.getRequest().getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }
}
