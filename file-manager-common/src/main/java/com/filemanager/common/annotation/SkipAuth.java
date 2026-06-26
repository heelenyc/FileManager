package com.filemanager.common.annotation;

import java.lang.annotation.*;

/**
 * 标记接口无需认证即可访问
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SkipAuth {
}
