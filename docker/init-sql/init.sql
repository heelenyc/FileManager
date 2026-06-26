-- 设置客户端字符集为 utf8mb4，确保中文注释正确存储
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- =====================================================
-- 分布式文件管理系统 - 数据库初始化脚本
-- =====================================================

CREATE DATABASE IF NOT EXISTS file_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE file_manager;

-- -----------------------------------------------------
-- 用户表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `deleted` CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- -----------------------------------------------------
-- 角色表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `deleted` CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- -----------------------------------------------------
-- 权限表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `permission_code` VARCHAR(128) NOT NULL COMMENT '权限编码',
    `permission_name` VARCHAR(64) NOT NULL COMMENT '权限名称',
    `resource_type` TINYINT NOT NULL COMMENT '资源类型: 1-菜单, 2-按钮, 3-接口',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父权限ID',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted` CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- -----------------------------------------------------
-- 用户-角色关联表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- -----------------------------------------------------
-- 角色-权限关联表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

-- -----------------------------------------------------
-- 文件元数据表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_metadata` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `file_key` VARCHAR(128) NOT NULL COMMENT '文件唯一标识(UUID)',
    `file_name` VARCHAR(256) NOT NULL COMMENT '文件名',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `content_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME类型',
    `file_hash` VARCHAR(64) DEFAULT NULL COMMENT '文件完整性哈希(SHA-256)',
    `aes_key_encrypted` VARCHAR(256) NOT NULL COMMENT 'AES密钥(加密后存储)',
    `chunk_count` INT NOT NULL DEFAULT 1 COMMENT '分片数量',
    `chunk_size` BIGINT DEFAULT NULL COMMENT '分片大小(字节)',
    `upload_user_id` BIGINT NOT NULL COMMENT '上传用户ID',
    `storage_path` VARCHAR(512) DEFAULT NULL COMMENT '存储路径(小文件直接路径)',
    `deleted` CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_key` (`file_key`),
    KEY `idx_upload_user` (`upload_user_id`),
    KEY `idx_file_name` (`file_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';

-- -----------------------------------------------------
-- 文件分片表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分片ID',
    `file_id` BIGINT NOT NULL COMMENT '文件ID',
    `chunk_index` INT NOT NULL COMMENT '分片序号(从0开始)',
    `chunk_size` BIGINT NOT NULL COMMENT '分片大小(字节)',
    `chunk_hash` VARCHAR(64) DEFAULT NULL COMMENT '分片哈希(SHA-256)',
    `storage_node_name` VARCHAR(64) NOT NULL COMMENT '主存储节点名称(唯一标识)',
    `replica_node_names` VARCHAR(256) DEFAULT NULL COMMENT '副本节点名称列表(逗号分隔)',
    `storage_path` VARCHAR(512) NOT NULL COMMENT '存储路径',
    `deleted` CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_storage_node_name` (`storage_node_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分片表';

-- -----------------------------------------------------
-- 存储节点表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `storage_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点ID',
    `node_name` VARCHAR(64) NOT NULL COMMENT '节点名称(唯一标识)',
    `node_host` VARCHAR(128) NOT NULL COMMENT '节点地址',
    `node_port` INT NOT NULL COMMENT '节点端口',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-离线, 1-在线, 2-隔离',
    `available_space` BIGINT DEFAULT 0 COMMENT '可用空间(字节)',
    `total_space` BIGINT DEFAULT 0 COMMENT '总空间(字节)',
    `zk_path` VARCHAR(256) DEFAULT NULL COMMENT 'Zookeeper注册路径',
    `last_heartbeat` DATETIME DEFAULT NULL COMMENT '最后心跳时间',
    `deleted` CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_node_name` (`node_name`),
    UNIQUE KEY `uk_node_host_port` (`node_host`, `node_port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存储节点表';

-- -----------------------------------------------------
-- 初始化数据：管理员角色和管理员用户
-- -----------------------------------------------------
INSERT INTO `sys_role` (`role_code`, `role_name`, `description`) VALUES
('ADMIN', '管理员', '系统管理员，拥有全部权限'),
('USER', '普通用户', '普通用户，拥有基本文件操作权限');

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `status`) VALUES
('admin', '$2a$10$gMeKyEmhJmy/dJ/gQ8ss8.7tEnCrNY2XZpBfaXtG/IgRTu9RK1lSG', '管理员', 1);
-- 默认密码: admin123

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- -----------------------------------------------------
-- 初始化数据：权限码
-- -----------------------------------------------------
INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `resource_type`, `parent_id`, `sort_order`) VALUES
-- 文件权限 (resource_type=2: 按钮/接口)
('file:upload', '文件上传', 2, NULL, 1),
('file:download', '文件下载', 2, NULL, 2),
('file:delete', '文件删除', 2, NULL, 3),
('file:list', '文件列表', 2, NULL, 4),
('file:recycle', '回收站管理', 2, NULL, 40),
-- 用户管理权限 (resource_type=2: 按钮/接口)
('user:view', '用户查看', 2, NULL, 10),
('user:create', '用户新建', 2, NULL, 11),
('user:edit', '用户编辑', 2, NULL, 12),
('user:delete', '用户删除', 2, NULL, 13),
('user:assign-role', '分配角色', 2, NULL, 14),
('user:reset-password', '重置密码', 2, NULL, 15),
-- 角色管理权限 (resource_type=2: 按钮/接口)
('role:view', '角色查看', 2, NULL, 20),
('role:create', '角色新建', 2, NULL, 21),
('role:edit', '角色编辑', 2, NULL, 22),
('role:delete', '角色删除', 2, NULL, 23),
('role:assign-permission', '分配权限', 2, NULL, 24),
-- 节点管理权限 (resource_type=2: 按钮/接口)
('node:view', '节点监控', 2, NULL, 30),
('node:manage', '节点管理', 2, NULL, 31);

-- -----------------------------------------------------
-- 初始化数据：角色-权限关联
-- -----------------------------------------------------
-- ADMIN角色：拥有所有权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM `sys_permission`;

-- USER角色：只有文件基本操作权限（排除回收站管理）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `sys_permission`
WHERE `permission_code` IN ('file:upload', 'file:download', 'file:delete', 'file:list');
