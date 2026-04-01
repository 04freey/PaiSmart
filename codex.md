# PaiSmart 项目本地配置与启动说明

## 1. 项目结构

- 后端：项目根目录（Spring Boot + Maven）
- 主前端：`frontend/`（Vue 3 + Vite + pnpm）
- 展示首页：`homepage/`（可选启动）

---

## 2. 启动前准备

### 2.1 环境要求

- JDK 17+（本机当前建议直接用 JDK 21）
- Maven 3.8.6+
- Node.js 18.20.0+
- pnpm 8.7.0+
- Docker / Docker Compose

### 2.2 当前已发现的注意点

1. 当前机器 `java -version` 正常，但 `mvn -v` 报错，原因是 `JAVA_HOME` 未正确设置。
2. 项目中的后端配置与 `docs/docker-compose.yaml` 存在不一致：
   - MySQL / Redis / Elasticsearch / MinIO 的连接参数并不完全一致
   - Kafka topic 名称也不完全一致
3. MinIO 使用的 bucket 为 `uploads`，代码里看起来没有自动创建逻辑，建议手动创建。

---

## 3. 先修复 Java / Maven

在 PowerShell 中临时设置：

```powershell
$env:JAVA_HOME='E:\develop\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

java -version
mvn -v
```

如果 `mvn -v` 正常输出，再继续下面步骤。

---

## 4. 启动中间件

项目提供了 Docker Compose：

```powershell
docker compose -f docs/docker-compose.yaml up -d
docker compose -f docs/docker-compose.yaml ps
```

涉及组件：

- MySQL
- Redis
- Kafka
- Elasticsearch
- MinIO

### 常见问题

#### MySQL 3306 端口冲突

如果出现类似报错：

```text
ports are not available ... listen tcp 0.0.0.0:3306 ... bind ...
```

说明本机已经有其他程序占用了 `3306`，通常是本地 MySQL 服务。

处理思路二选一：

1. 释放本机 `3306` 端口后，再重新启动容器
2. 把 `docs/docker-compose.yaml` 中 MySQL 映射端口从 `3306:3306` 改成别的端口，例如 `3307:3306`

> 注意：如果改成 `3307`，后端数据库连接配置也要同步改成 `localhost:3307`。

---

## 5. 创建数据库

后端使用数据库名：

- `PaiSmart`

如果 MySQL 已正常启动，可执行：

```powershell
docker exec -it mysql mysql -uroot -pPaiSmart2025 -e "CREATE DATABASE IF NOT EXISTS PaiSmart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

说明：

- 项目里 `ddl-auto=update` 可以帮助自动建表
- 但数据库本身最好先手动创建

---

## 6. 创建 MinIO bucket

MinIO 相关端口：

- API：`http://localhost:19000`
- Console：`http://localhost:19001`

Compose 中默认账号：

- 用户名：`admin`
- 密码：`PaiSmart2025`

登录后手动创建 bucket：

- `uploads`

---

## 7. 创建 Kafka topic

后端配置实际用到的 topic：

- `file-processing-topic1`
- `file-processing-dlt`

建议手动创建：

```powershell
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic file-processing-topic1 --partitions 1 --replication-factor 1

docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic file-processing-dlt --partitions 1 --replication-factor 1
```

---

## 8. 推荐新增本地配置文件

建议新增：

- `src/main/resources/application-local.yml`

用于覆盖本地实际环境，避免直接改默认配置。

建议内容示例：

```yml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/PaiSmart?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: PaiSmart2025

  data:
    redis:
      host: localhost
      port: 6379
      password: PaiSmart2025

  kafka:
    bootstrap-servers: 127.0.0.1:9092
    topic:
      file-processing: file-processing-topic1
      dlt: file-processing-dlt

minio:
  endpoint: http://localhost:19000
  accessKey: admin
  secretKey: PaiSmart2025
  bucketName: uploads
  publicUrl: http://localhost:19000

elasticsearch:
  host: localhost
  port: 9200
  scheme: http
  username: elastic
  password: PaiSmart2025

deepseek:
  api:
    key: 你的 DeepSeek Key

embedding:
  api:
    key: 你的 Embedding Key
```

说明：

- 不填 AI Key，项目可能能启动
- 但聊天、向量化、知识库检索链路大概率不可用

---

## 9. 启动后端

在项目根目录执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

默认访问：

- 后端：`http://localhost:8081`

项目里有管理员初始化逻辑，默认账号看起来是：

- 用户名：`admin`
- 密码：`admin123`

---

## 10. 启动主前端

```powershell
cd frontend
pnpm install
pnpm run dev
```

默认访问：

- 前端：`http://localhost:9527`

前端测试环境 `.env.test` 已指向：

- `http://localhost:8081/api/v1`

所以后端保持 `8081` 最省事。

---

## 11. 可选：启动首页 homepage

```powershell
cd homepage
pnpm install
pnpm run tw:dev
pnpm run dev
```

通常访问：

- `http://localhost:5173`

---

## 12. 推荐启动顺序

1. 修复 `JAVA_HOME`
2. 启动 Docker 中间件
3. 创建数据库 `PaiSmart`
4. 创建 MinIO bucket `uploads`
5. 创建 Kafka topic
6. 新增并启用 `application-local.yml`
7. 启动后端
8. 启动 `frontend`
9. 按需启动 `homepage`

---

## 13. 当前最可能继续遇到的问题

1. `3306` 端口被本机 MySQL 占用
2. `mvn` 因 `JAVA_HOME` 不正确而无法运行
3. MinIO bucket 未创建导致上传失败
4. Kafka topic 名称不一致导致消费者/生产者异常
5. AI Key 未配置导致问答或向量化失败

---

## 14. 建议的排查策略

优先保证以下链路逐层打通：

1. Docker 中间件全部 healthy
2. Maven 可执行
3. 后端成功启动
4. 前端可打开并可登录
5. 文件上传可用
6. 向量化与检索可用
7. AI 对话可用

