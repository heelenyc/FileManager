package com.filemanager.web.config;

import com.filemanager.web.interceptor.AuthInterceptor;
import com.filemanager.web.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流拦截器（优先级最高）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // 认证拦截器
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/auth/refresh",
                        "/file/download/presigned/*",
                        "/internal/**",        // 内部存储接口（节点间通信）
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/knife4j/**",
                        "/doc.html",
                        "/webjars/**",
                        "/error"
                )
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
