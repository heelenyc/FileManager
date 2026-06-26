package com.filemanager.common.annotation;

import java.lang.annotation.*;

/**
 * 标记接口需要指定权限才能访问
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 所需权限编码（支持多个）
     */
    String[] value();

    /**
     * 是否需要满足所有权限（默认false，满足其一即可）
     */
    boolean requireAll() default false;
}
