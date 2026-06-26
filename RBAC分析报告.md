# RBAC 功能实现现状分析报告

## 一、总体评估

| 维度 | 完成度 | 状态 |
|------|--------|------|
| 数据库设计 | 100% | 已完成 |
| 后端基础架构 | 70% | 骨架完成，缺Controller |
| 前端页面 | 20% | 仅展示列表 |
| 权限校验生效 | 0% | 未启用 |
| **综合完成度** | **约40%** | **需补齐** |

---

## 二、已实现部分（数据库层）

### 2.1 表结构设计 ✅

已创建完整的RBAC五张核心表：

```
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐
│  sys_user   │────<│ sys_user_   │>────│   sys_role       │
│  (用户表)    │     │ role        │     │   (角色表)         │
└─────────────┘     └─────────────┘     └────────┬─────────┘
                                               │
                                         ┌─────┴──────────┐
                                         │ sys_role_       │
                                         │ permission      │
                                         └─────┬──────────┘
                                               │
                                        ┌──────┴──────────┐
                                        │ sys_permission  │
                                        │ (权限表)         │
                                        └─────────────────┘
```

| 表名 | 用途 | 字段数 | 状态 |
|------|------|--------|------|
| `sys_user` | 用户基本信息 | 12 | ✅ 完成 |
| `sys_role` | 角色定义 | 8 | ✅ 完成 |
| `sys_permission` | 权限/菜单/按钮定义 | 9 | ✅ 完成 |
| `sys_user_role` | 用户-角色关联 | 3 | ✅ 完成 |
| `sys_role_permission` | 角色-权限关联 | 3 | ✅ 完成 |

### 2.2 初始数据 ✅

```sql
-- 已初始化的角色
INSERT INTO sys_role VALUES ('ADMIN', '管理员', '系统管理员，拥有全部权限');
INSERT INTO sys_role VALUES ('USER', '普通用户', '普通用户，拥有基本文件操作权限');

-- 已初始化的管理员用户
INSERT INTO sys_user VALUES ('admin', '$2a$10...', '管理员', 1);
-- 默认密码: admin123

-- admin 已分配 ADMIN 角色
INSERT INTO sys_user_role VALUES (1, 1);  -- user_id=1, role_id=1(ADMIN)
```

**问题：`sys_permission` 表无初始数据！** 权限码未定义。

---

## 三、后端代码现状

### 3.1 已完成的组件 ✅

| 组件 | 文件路径 | 功能 |
|------|----------|------|
| `RequirePermission` 注解 | [RequirePermission.java](file:///e:/trea-workspace/FileManager/file-manager-common/src/main/java/com/filemanager/common/annotation/RequirePermission.java) | 标记接口所需权限 |
| `AuthInterceptor` 拦截器 | [AuthInterceptor.java](file:///e:/trea-workspace/FileManager/file-manager-web/src/main/java/com/filemanager/web/interceptor/AuthInterceptor.java) | 解析Token + 校验权限 |
| `AuthService` 服务 | [AuthService.java](file:///e:/trea-workspace/FileManager/file-manager-service/src/main/java/com/filemanager/service/auth/AuthService.java) | 登录/注册/刷新Token + 加载角色权限 |
| Mapper层 | UserMapper, RoleMapper, PermissionMapper, UserRoleMapper, RolePermissionMapper | 数据访问 |

### 3.2 AuthService 核心逻辑 ✅

```java
// 登录时正确加载用户的角色和权限
List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(user.getId());
List<String> permissionCodes = rolePermissionMapper.selectPermissionCodesByUserId(user.getId());

// JWT Token 中携带 roles 和 permissions
accessClaims.put("roles", roleCodes);
accessClaims.put("permissions", permissionCodes);
```

### 3.3 AuthInterceptor 权限校验 ✅

```java
// 支持 @RequirePermission 注解校验
RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
if (requirePermission != null && permissions != null) {
    // requireAll=true: 需要拥有所有权限
    // requireAll=false: 拥有任一即可
}
```

### 3.4 缺失的后端模块 ❌

| 缺失模块 | 说明 | 影响 |
|----------|------|------|
| **RoleController** | 角色CRUD接口 | 无法管理角色 |
| **PermissionController** | 权限CRUD接口 | 无法管理权限 |
| **UserController增强** | 分配角色、禁用用户等 | 无法进行用户-角色绑定 |
| **@RequirePermission 使用** | 所有Controller均未标注 | 权限校验不生效！ |
| **sys_permission 初始数据** | 无权限码定义 | 即使启用校验也无权限可匹配 |

---

## 四、前端代码现状

### 4.1 路由与页面结构

```
/login          → Login.vue      （登录页）
/
  /files        → Files.vue      （文件管理）
  /users        → Users.vue      （用户管理 - 极简）
  /nodes        → Nodes.vue      （节点监控）
```

### 4.2 MainLayout.vue 菜单 ❌

[MainLayout.vue](file:///e:/trea-workspace/FileManager/file-manager-frontend/src/layout/MainLayout.vue)

当前菜单固定显示三项，**未根据角色动态控制**：

```vue
<el-menu-item index="/files">文件管理</el-menu-item>
<el-menu-item index="/users">用户管理</el-menu-item>  <!-- 普通用户也能看到 -->
<el-menu-item index="/nodes">节点监控</el-menu-item>  <!-- 普通用户也能看到 -->
```

### 4.3 Users.vue 用户管理 ⚠️

[Users.vue](file:///e:/trea-workspace/FileManager/file-manager-frontend/src/views/Users.vue)

仅实现了最基础的列表展示：

| 功能 | 状态 |
|------|------|
| 显示用户列表 | ✅ |
| 新建用户 | ❌ |
| 编辑用户信息 | ❌ |
| 重置密码 | ❌ |
| 禁用/启用用户 | ❌ |
| 分配角色 | ❌ |
| 查看用户权限 | ❌ |

### 4.4 缺失的前端页面 ❌

| 缺失页面 | 用途 |
|----------|------|
| **Roles.vue** | 角色管理（列表、新建、编辑、分配权限） |
| **Permissions.vue** | 权限管理（树形展示、菜单/按钮/接口分类） |
| **UserDetail.vue** | 用户详情（含角色分配） |
| **动态路由/菜单** | 根据用户角色动态渲染侧边栏菜单 |

---

## 五、核心问题汇总

### 问题1：权限校验形同虚设 🔴 严重

虽然 `@RequirePermission` 注解和 `AuthInterceptor` 都已写好，但：

- **没有任何Controller方法使用 `@RequirePermission` 注解**
- **`sys_permission` 表为空，无任何权限码数据**
- **结果：所有认证用户拥有相同权限，ADMIN与USER无区别**

### 问题2：超级管理员标识缺失 🔴 严重

- admin用户仅有 `role_code = 'ADMIN'`
- 后端代码中**无硬编码的超级管理员判断逻辑**
- 无法区分"超级管理员"与"普通管理员"

### 问题3：用户-角色无法绑定 🔴 严重

- 注册用户默认不分配任何角色（代码注释说分配但实际未执行）
- 无界面可给用户分配角色
- 无API支持修改用户角色

### 问题4：前端菜单无权限控制 🟡 中等

- 所有登录用户看到完全相同的菜单
- 普通用户可以看到"用户管理"、"节点监控"等管理入口

---

## 六、建议实施方案

### 方案A：最小可用方案（推荐先做）

补齐关键缺口，让RBAC真正跑起来：

#### 第一步：定义权限码并写入数据库

```sql
-- 在 init.sql 中补充权限初始化数据
INSERT INTO sys_permission (permission_code, permission_name, resource_type, parent_id, sort_order) VALUES
('file:upload', '文件上传', 2, NULL, 1),
('file:download', '文件下载', 2, NULL, 2),
('file:delete', '文件删除', 2, NULL, 3),
('file:list', '文件列表', 2, NULL, 4),
('user:view', '用户查看', 2, NULL, 10),
('user:create', '用户新建', 2, NULL, 11),
('user:edit', '用户编辑', 2, NULL, 12),
('user:delete', '用户删除', 2, NULL, 13),
('user:assign-role', '分配角色', 2, NULL, 14),
('role:view', '角色查看', 2, NULL, 20),
('role:create', '角色新建', 2, NULL, 21),
('role:edit', '角色编辑', 2, NULL, 22),
('role:delete', '角色删除', 2, NULL, 23),
('node:view', '节点监控', 2, NULL, 30);

-- ADMIN角色拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- USER角色只有文件操作权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE permission_code LIKE 'file:%';
```

#### 第二步：在Controller上添加权限注解

```java
// FileController.java
@PostMapping("/upload")
@RequirePermission("file:upload")   // ← 添加
public Result<?> upload(...){ ... }

@GetMapping("/list")
@RequirePermission("file:list")     // ← 添加
public Result<?> listFiles(...){ ... }

@DeleteMapping("/{id}")
@RequirePermission("file:delete")   // ← 添加
public Result<?> delete(...){ ... }

// AuthController.java
@GetMapping("/users")
@RequirePermission("user:view")     // ← 添加
public Result<List<UserVO>> userList(){ ... }
```

#### 第三步：前端菜单按角色过滤

```vue
<!-- MainLayout.vue 改造 -->
<script setup>
import { computed } from 'vue'

const isAdmin = computed(() => {
  const user = JSON.parse(localStorage.getItem('userInfo') || '{}')
  return user.roles?.includes('ADMIN')
})
</script>

<template>
  <el-menu>
    <el-menu-item index="/files">文件管理</el-menu-item>
    <el-menu-item v-if="isAdmin" index="/users">用户管理</el-menu-item>
    <el-menu-item v-if="isAdmin" index="/nodes">节点监控</el-menu-item>
  </el-menu>
</template>
```

### 方案B：完整方案（后续迭代）

如果需要完整的RBAC管理后台：

| 功能模块 | 工作量估计 | 内容 |
|----------|-----------|------|
| 角色管理页面 | 1天 | Roles.vue + RoleController |
| 权限管理页面 | 0.5天 | Permissions.vue（树形结构） |
| 用户管理增强 | 0.5天 | Users.vue 增加 CRUD + 角色分配 |
| 动态菜单路由 | 0.5天 | 根据权限动态生成菜单 |
| 超级管理员逻辑 | 0.5天 | ADMIN角色硬编码跳过部分校验 |
| **合计** | **3天** | |

---

## 七、当前系统的实际权限模型

```
                    当前实际情况（非预期）

  ┌─────────────────────────────────────────┐
  │           所有已登录用户                   │
  │           （无差别对待）                   │
  │                                          │
  │   ✅ 可以：登录、上传、下载、删除文件        │
  │   ✅ 可以：查看用户列表                     │
  │   ✅ 可以：查看节点监控                     │
  │   ✅ 可以：注册新用户                       │
  │                                          │
  │   ❌ 不可以：（无限制）                     │
  │                                          │
  └─────────────────────────────────────────┘

  RBAC 五张表存在，但未被业务逻辑引用
```

---

## 八、结论与建议

### 当前状态
> **RBAC是"空壳"** — 数据库表和Java类都就位了，但没有真正接入业务流程。admin用户与普通注册用户在系统中拥有完全相同的权限。

### 建议优先级
1. **🔴 P0（立即修复）**：添加 `@RequirePermission` 注解 + 初始化权限数据，让权限校验生效
2. **🟠 P1（本周完成）**：前端菜单按角色过滤，隐藏非授权入口
3. **🟡 P2（下周完成）**：完善用户管理页面，支持角色分配
4. **🟢 P3（可选）**：开发完整的角色/权限管理后台界面

是否需要我立即执行 **P0 + P1** 的修复工作？
