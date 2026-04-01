# CLAUDECN.md

本文档用于在本仓库中协作开发时，为 Claude Code（claude.ai/code）提供项目级工作说明。

## 一、项目简介

PaiSmart（派聪明）是一个基于 RAG（Retrieval-Augmented Generation，检索增强生成）技术构建的企业级 AI 知识管理系统。系统面向企业知识沉淀、文档处理与智能问答等场景，依托 Spring Boot、Vue 3、Elasticsearch 以及 AI 服务，提供完整的文档解析、检索与对话能力。

## 二、开发环境准备

### 2.1 基础依赖
- Java 17
- Maven 3.8.6+
- Node.js 18.20.0+
- pnpm 8.7.0+
- MySQL 8.0
- Elasticsearch 8.10.0
- MinIO 8.5.12
- Kafka 3.2.1
- Redis 7.0.11
- Docker（可选，建议用于本地拉起依赖服务）

### 2.2 使用 Docker 快速启动
```bash
# 启动依赖服务
cd docs && docker-compose up -d

# 启动后端
mvn spring-boot:run

# 启动前端
cd frontend && pnpm install && pnpm dev
```

## 三、常用开发命令

### 3.1 后端（Spring Boot）
```bash
# 启动应用
mvn spring-boot:run

# 构建项目
mvn clean package

# 运行测试
mvn test

# 指定 profile 启动
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3.2 前端（Vue 3 + TypeScript）
```bash
# 安装依赖
cd frontend && pnpm install

# 启动开发环境
pnpm dev

# 生产构建
pnpm build

# 类型检查
pnpm typecheck

# 代码检查
pnpm lint

# 预览构建结果
pnpm preview
```

## 四、项目架构概览

### 4.1 后端目录结构
```
src/main/java/com/yizhaoqi/smartpai/
├── SmartPaiApplication.java      # 应用主入口
├── client/                       # 外部 API 客户端（如 DeepSeek、Embedding）
├── config/                       # 配置类（如 Security、JWT 等）
├── consumer/                     # Kafka 消费者，处理异步任务
├── controller/                   # REST API 接口层
├── entity/                       # JPA 实体
├── exception/                    # 自定义异常
├── handler/                      # WebSocket 处理器
├── model/                        # 领域模型
├── repository/                   # 数据访问层
├── service/                      # 业务逻辑层
└── utils/                        # 通用工具类
```

### 4.2 前端目录结构
```
frontend/src/
├── assets/                       # 静态资源（SVG、图片等）
├── components/                   # 通用组件
├── layouts/                      # 页面布局
├── router/                       # 路由配置
├── service/                      # API 请求封装
├── store/                        # Pinia 状态管理
├── views/                        # 页面级组件
└── utils/                        # 工具函数
```

## 五、核心模块说明

### 5.1 核心服务
- **DocumentService**：负责文档上传、解析和文档管理
- **ElasticsearchService**：负责文档索引构建与检索
- **VectorizationService**：负责将文本转换为向量嵌入
- **ChatHandler**：负责基于 RAG 的对话处理
- **UserService**：负责用户认证与用户管理
- **ConversationService**：负责会话记录与聊天历史管理

### 5.2 AI 能力集成
- **DeepSeek API**：主要用于对话回复生成
- **Embedding API**：使用 `text-embedding-v4` 完成文本向量化
- **RAG 流程**：文档 → 分块 → 向量化 → 检索 → 生成回答

### 5.3 多租户能力
- **Organization Tags**：支持租户级隔离
- **Permission System**：支持公开 / 私有文档权限控制
- **User-Organization Mapping**：支持灵活的用户与组织映射关系

## 六、配置文件说明

### 6.1 后端配置
- `application.yml`：主配置文件，包含数据库、Redis、Kafka、AI 服务等核心配置
- `application-dev.yml`：开发环境配置
- `application-docker.yml`：Docker 部署配置

### 6.2 前端配置
- `vite.config.ts`：Vite 构建配置
- `tsconfig.json`：TypeScript 配置
- `pnpm-workspace.yaml`：monorepo 工作区配置

## 七、数据库设计说明

系统使用 MySQL 作为主数据库，并通过 JPA / Hibernate 进行 ORM 映射。关键实体包括：
- `User`：用户账户与认证信息
- `FileUpload`：文档元数据与存储信息
- `Conversation`：对话会话及历史记录
- `OrganizationTag`：多租户组织结构
- `ChunkInfo`：文档分块信息，用于向量检索

## 八、外部依赖

### 8.1 基础服务
- **Elasticsearch 8.10.0**：用于文档检索和向量存储
- **Kafka 3.2.1**：用于异步文件处理
- **Redis 7.0.11**：用于缓存和会话管理
- **MinIO 8.5.12**：用于文件存储
- **MySQL 8.0**：主数据库

### 8.2 AI 服务
- **DeepSeek API**：用于生成问答结果
- **DashScope Embedding**：基于 `text-embedding-v4` 进行文本向量化

## 九、开发约定与工作流

### 9.1 新功能开发流程
1. 后端按照 `entity → repository → service → controller` 的顺序补齐能力
2. 前端按照 `API service → store 模块 → Vue 组件 → router 配置` 的顺序接入
3. 如有必要，补充数据库结构变更（JPA 可自动生成 DDL）
4. 完成后补充单元测试与集成测试验证

### 9.2 API 开发建议
- 遵循 RESTful 风格
- 使用合理的 HTTP 状态码
- 通过 JWT 完成认证与授权
- 做好请求参数校验与异常处理

### 9.3 前端开发建议
- 使用 Vue 3 Composition API + TypeScript
- 优先遵循 `/src/components/` 中现有组件风格
- 使用 Pinia 管理状态
- 为接口响应定义清晰的 TypeScript 类型

## 十、测试说明

### 10.1 后端测试
```bash
# 运行全部测试
mvn test

# 运行指定测试类
mvn test -Dtest=UserServiceTest

# 执行校验构建
mvn clean verify
```

### 10.2 前端测试
```bash
# 类型检查
pnpm typecheck

# 代码规范检查
pnpm lint

# 构建验证
pnpm build
```

## 十一、部署说明

### 11.1 Docker 部署
```bash
# 构建后端
mvn clean package

# 构建前端
cd frontend && pnpm build

# 启动服务
cd docs && docker-compose up -d
```

### 11.2 生产环境变量
生产环境建议重点配置以下变量：
- `JWT_SECRET_KEY`：JWT 签名密钥
- `DEEPSEEK_API_KEY`：DeepSeek API 凭证
- `EMBEDDING_API_KEY`：Embedding 服务 API Key
- 数据库账号密码及各类服务访问地址

## 十二、安全注意事项

- 使用基于 Spring Security 的 JWT 认证机制
- 支持基于角色的访问控制（admin / user）
- 支持组织级数据隔离
- 对文件上传进行类型与大小校验
- 配置前后端联调用的 CORS 策略
- 对输入内容进行校验与必要清洗

## 十三、性能优化关注点

- 使用 Elasticsearch 提升文档检索效率
- 使用 Redis 缓存热点数据
- 使用 Kafka 处理异步文件任务
- 通过文档分块提升大文件处理能力
- 通过向量嵌入支持语义检索
- 对数据库和外部服务连接启用连接池优化
