# 分布式文件管理系统

一个基于 Spring Boot + Vue3 的分布式文件管理系统，支持文件分片上传、加密存储、多节点分布式存储、节点自动注册与健康监控、RBAC权限控制等功能。

## 技术栈

### 后端
- **Java 17** + **Spring Boot 3.x**
- **MyBatis-Plus** - 数据持久化
- **MySQL** - 元数据存储
- **Redis** + **Redisson** - 缓存 & 分布式锁
- **Zookeeper** - 节点存活检测（临时节点）
- **JWT** - 用户认证

### 前端
- **Vue 3** + **Vite**
- **Element Plus** - UI组件库
- **Axios** - HTTP请求

### 基础设施
- **Docker Compose** - 一键部署 MySQL、Redis、Zookeeper

---

## 核心功能

### 1. 文件管理
- ✅ 文件上传（小文件直接上传，大文件分片上传）
- ✅ 文件下载（支持断点续传）
- ✅ 文件加密存储（AES-256）
- ✅ 文件删除（逻辑删除，支持回收站）
- ✅ 文件列表（分页、搜索）
- ✅ 回收站管理（恢复、彻底删除）

### 2. 分布式存储
- ✅ 多节点分布式存储
- ✅ 一致性哈希路由
- ✅ 数据副本（默认3副本）
- ✅ 故障转移（主节点不可用时从副本读取）
- ✅ 跨节点数据同步

### 3. 节点管理
- ✅ **节点自动注册**（服务启动时自动注册到 Zookeeper + MySQL）
- ✅ **心跳检测**（定时更新心跳时间）
- ✅ **全局健康检测**（分布式锁协调，双机制存活判断）
- ✅ **节点隔离**（人工干预强制下线）
- ✅ **节点恢复**（取消隔离）
- ✅ **节点删除**（清理离线节点记录）

### 4. 用户权限（RBAC）
- ✅ 用户管理（注册、登录、信息修改）
- ✅ 角色管理（角色创建、权限分配）
- ✅ 权限管理（细粒度权限控制）
- ✅ 接口权限校验（注解式权限校验）

### 5. 安全特性
- ✅ JWT Token 认证
- ✅ 文件 AES 加密存储
- ✅ 接口限流（Bucket4j + Redis）
- ✅ 操作日志记录

---

## 项目结构

```
FileManager/
├── docker/                          # Docker 部署配置
│   ├── init-sql/init.sql            # 数据库初始化脚本
│   ├── docker-compose.yml           # Docker Compose 配置
│   └── nginx.conf                   # Nginx 配置
│
├── file-manager-common/             # 公共模块
│   ├── annotation/                  # 自定义注解
│   ├── exception/                   # 异常处理
│   ├── result/                      # 统一响应
│   └── util/                        # 工具类
│
├── file-manager-dao/                # 数据访问层
│   ├── config/                      # MyBatis-Plus 配置
│   └── mapper/                      # Mapper 接口
│
├── file-manager-model/              # 实体模型
│   ├── dto/                         # 数据传输对象
│   ├── entity/                      # 实体类
│   └── vo/                          # 视图对象
│
├── file-manager-service/            # 业务逻辑层
│   ├── auth/                        # 认证服务
│   ├── file/                        # 文件服务
│   ├── node/                        # 节点管理服务
│   ├── storage/                     # 分布式存储服务
│   ├── cache/                       # 缓存服务
│   └── lock/                        # 分布式锁服务
│
├── file-manager-storage/            # 本地存储模块
│   ├── service/                     # 本地存储服务
│
├── file-manager-web/                # Web 层
│   ├── controller/                  # 控制器
│   ├── interceptor/                 # 拦截器
│   ├── aspect/                      # AOP 切面
│   └── config/                      # Web 配置
│
├── file-manager-frontend/           # 前端
│   ├── views/                       # 页面组件
│   ├── router/                      # 路由配置
│   ├── utils/                       # 工具函数
│   └── layout/                      # 布局组件
│
├── pom.xml                          # Maven 父 POM
├── start.bat                        # 启动脚本
├── stop.bat                         # 停止脚本
└ .gitignore                         # Git 忽略配置
```

---

## 快速开始

### 1. 环境准备

确保已安装：
- Java 17+
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose

### 2. 启动基础设施

```bash
cd docker
docker-compose up -d
```

启动服务：
- MySQL (端口 3306)
- Redis (端口 6379)
- Zookeeper (端口 2181)

数据库会自动初始化（执行 `init-sql/init.sql`）。

### 3. 构建后端

```bash
mvn clean package -DskipTests
```

### 4. 启动节点

**单节点测试**：
```bash
java -jar file-manager-web/target/file-manager-web-1.0.0-SNAPSHOT.jar
```

**多节点测试**：
```bash
# 节点1 (8080端口)
java -jar file-manager-web/target/file-manager-web-1.0.0-SNAPSHOT.jar

# 节点2 (8081端口)
java -jar file-manager-web/target/file-manager-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=8081
```

节点配置（`application.yml`）：
```yaml
node:
  name: node0                       # 节点名称（唯一标识）
  host: localhost                   # 节点地址
  port: ${server.port}              # 节点端口
  heartbeat-interval: 10            # 心跳间隔（秒）
  heartbeat-tolerance: 20           # 心跳容忍时间（秒）
```

### 5. 启动前端

```bash
cd file-manager-frontend
npm install
npm run dev
```

访问：http://localhost:5173

### 6. 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |

---

## API 文档

启动后端后访问：http://localhost:8080/api/swagger-ui.html

### 主要接口

#### 文件管理
| 接口 | 方法 | 说明 |
|------|------|------|
| `/file/upload` | POST | 文件上传 |
| `/file/download/{fileKey}` | GET | 文件下载 |
| `/file/list` | GET | 文件列表 |
| `/file/delete/{fileKey}` | DELETE | 删除文件 |
| `/file/recycle/list` | GET | 回收站列表 |
| `/file/recycle/restore/{fileKey}` | POST | 恢复文件 |

#### 节点管理
| 接口 | 方法 | 说明 |
|------|------|------|
| `/node/current` | GET | 当前节点信息 |
| `/node/list` | GET | 所有节点列表 |
| `/node/isolate/{nodeId}` | POST | 隔离节点 |
| `/node/recover/{nodeId}` | POST | 恢复节点 |
| `/node/{nodeId}` | DELETE | 删除节点 |

#### 用户管理
| 接口 | 方法 | 说明 |
|------|------|------|
| `/auth/login` | POST | 用户登录 |
| `/auth/register` | POST | 用户注册 |
| `/user/list` | GET | 用户列表 |
| `/user/{userId}` | PUT | 更新用户 |

---

## 节点管理机制

### 自动注册流程
```
服务启动 → 检查ZK同名节点 → 创建临时节点 → 同步MySQL → 加入哈希环
```

### 存活检测机制
| 机制 | 说明 |
|------|------|
| **Zookeeper临时节点** | 服务停止时自动删除，实时感知节点存活 |
| **心跳超时判断** | 超过20秒未更新心跳，判定为离线（防止假死） |

### 全局健康检测
```
分布式锁 → 检查上次检测时间 → 获取ZK在线节点 → 双重判断存活 → 重建哈希环
```

### 节点状态
| 状态值 | 说明 | 可用操作 |
|--------|------|----------|
| 0 | 离线（服务停止） | 删除 |
| 1 | 在线（正常运行） | 隔离 |
| 2 | 隔离（人工干预） | 恢复、删除 |

---

## 分布式存储原理

### 一致性哈希路由
- 每个节点150个虚拟节点
- 文件分片按哈希值分配到节点
- 节点增减时自动重新分配

### 数据副本
- 默认3副本（可配置）
- 主节点存储原始路径：`chunk_0`
- 副本节点存储副本路径：`chunk_0_replica_0`

### 故障转移
```
主节点不可用 → 从副本节点列表选择 → 按副本路径读取 → 成功返回
```

---

## 权限设计

### 权限码
| 权限码 | 说明 |
|--------|------|
| `file:upload` | 文件上传 |
| `file:download` | 文件下载 |
| `file:delete` | 文件删除 |
| `file:list` | 文件列表 |
| `file:recycle` | 回收站管理 |
| `node:view` | 节点监控 |
| `node:manage` | 节点管理 |
| `user:*` | 用户管理 |
| `role:*` | 角色管理 |

### 角色权限
| 角色 | 权限范围 |
|------|----------|
| **ADMIN** | 全部权限 |
| **USER** | 文件基本操作（上传、下载、删除、列表） |

---

## 配置说明

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/file_manager
    username: root
    password: root123
```

### Redis 配置
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123
```

### Zookeeper 配置
```yaml
curator:
  connect-string: localhost:2181
  session-timeout-ms: 30000
```

### 文件存储配置
```yaml
file:
  storage:
    base-path: ./storage           # 本地存储路径
    chunk-size: 8388608            # 分片大小 8MB
    max-file-size: 5368709120      # 最大文件 5GB
    replica-count: 3               # 副本数
```

---

## 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Nginx (反向代理)                        │
│                 http://localhost                            │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
┌───────▼───────┐    ┌───────▼───────┐    ┌───────▼───────┐
│   Node 0      │    │   Node 1      │    │   Node N      │
│  (port 8080)  │    │  (port 8081)  │    │  (port 808N)  │
│               │    │               │    │               │
│  storage0/    │    │  storage1/    │    │  storageN/    │
└───────────────┘    └───────────────┘    └───────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────┐
│                    共享基础设施                             │
│  ┌─────────┐  ┌─────────┐  ┌───────────────────────────┐  │
│  │ MySQL   │  │ Redis   │  │ Zookeeper                 │  │
│  │(3306)   │  │(6379)   │  │ (2181) - 临时节点存活检测  │  │
│  └─────────┘  └─────────┘  └───────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
```

---

## 日志说明

关键业务日志（INFO级别）：

```
节点自动注册开始: name=node0, host=localhost, port=8080
Zookeeper临时节点创建成功: path=/nodes/node0
MySQL节点记录同步成功: nodeId=1, status=在线
一致性哈希环添加节点成功: nodeId=1

心跳更新成功: nodeId=1, nodeName=node0
开始全局健康检测...
Zookeeper在线节点列表: count=2, nodes=[node0, node1]
存活节点列表: count=2, nodes=[node0, node1]
一致性哈希环已重建: 节点数=2

文件上传完成: fileKey=xxx, chunkCount=5
分片可用节点: chunkIndex=0, primaryNode=node0, replicaNodes=[node1]
分片读取成功: chunkIndex=0, node=node1, path=...chunk_0_replica_0
```

---

## 开发指南

### 调试 SQL 日志

临时开启 SQL 输出：
```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

或在 `logback-spring.xml` 中：
```xml
<logger name="com.filemanager.dao.mapper" level="DEBUG"/>
```

### 新增节点配置

创建 `application-node2.yml`：
```yaml
server:
  port: 8082

node:
  name: node2
  port: ${server.port}

file:
  storage:
    base-path: ./storage2
```

启动：
```bash
java -jar file-manager-web.jar --spring.profiles.active=node2
```

---

## License

MIT License

---

## 联系方式

如有问题或建议，请提交 Issue 或 Pull Request。