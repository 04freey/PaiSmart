# PaiSmart 后端阅读顺序清单

> 目标读者：
>
> - 已经把项目跑起来
> - 但打开后端代码后，不知道该先看什么
> - 希望按“最省力、最容易建立理解”的顺序来读

---

# 1. 不要从头顺序看，按链路看

后端代码量不算特别小，如果你从第一行开始顺序读，容易陷入：

- 代码都看过一点
- 但整体还是不明白

正确方式是：

> **按业务链路一条条看**

建议阅读顺序：

1. 启动与配置
2. 登录与鉴权
3. 文件上传
4. 文档解析与向量化
5. 聊天与检索
6. WebSocket 流式输出
7. 管理与初始化逻辑

---

# 2. 第一阶段：先建立“系统骨架感”

这一阶段不要急着深挖细节，只要知道项目怎么启动、依赖什么、主入口在哪里。

---

## 2.1 `SmartPaiApplication`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/SmartPaiApplication.java
```

### 为什么先看？

这是 Spring Boot 主入口。

### 你要得到什么？

- 知道项目是标准 Spring Boot 应用
- 知道后端启动从这里开始

---

## 2.2 `application.yml` / `application-local.yml`

### 文件

```text
src/main/resources/application.yml
src/main/resources/application-local.yml
```

### 为什么第二个看？

因为你后面看到的一切业务代码，几乎都依赖配置。

### 你要重点看什么？

- 端口
- MySQL
- Redis
- Kafka
- MinIO
- Elasticsearch
- DeepSeek
- Embedding

### 你要得到什么？

能回答这几个问题：

- 项目连哪个数据库？
- 向量存在哪？
- 文件存在哪？
- 聊天模型调用哪个 API？

---

## 2.3 `logback-spring.xml`

### 文件

```text
src/main/resources/logback-spring.xml
```

### 为什么现在看？

你后面调试时会非常依赖日志。

### 你要得到什么？

- 知道日志怎么输出
- 知道有哪些日志文件
- 知道哪些 logger 被单独配置了

---

# 3. 第二阶段：先看登录与权限

这是最适合作为第一条“业务链路”的部分，因为相对独立，而且好理解。

---

## 3.1 `SecurityConfig`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/config/SecurityConfig.java
```

### 为什么优先看？

它决定：

- 哪些接口放行
- 哪些接口必须登录
- 哪些接口必须管理员

### 重点看什么？

- `requestMatchers(...)`
- 哪些 URL 是 `permitAll`
- 哪些 URL 是 `hasRole("ADMIN")`
- JWT 过滤器怎么挂进去的

### 看完后你应该能说出来：

> 登录接口为什么不用 token  
> 管理接口为什么必须管理员  
> 普通接口为什么会被拦截

---

## 3.2 `JwtAuthenticationFilter`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/config/JwtAuthenticationFilter.java
```

### 为什么接着看？

因为真正“识别 token”的是它。

### 重点看什么？

- 请求头里 token 从哪拿
- 如何校验 token
- 校验成功后把用户信息放到了哪里

### 看完后你应该能说出来：

> 前端带了 token 后，后端为什么知道当前用户是谁

---

## 3.3 `JwtUtils`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/utils/JwtUtils.java
```

### 为什么现在看？

你前面知道“过滤器会校验 token”，这里就是 token 的生成和解析细节。

### 重点看什么？

- token 如何生成
- refresh token 如何生成
- 过期时间
- 解析用户名的方法

---

## 3.4 `UserController`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/controller/UserController.java
```

### 为什么此时看？

因为你现在已经知道：

- 安全规则是什么
- token 怎么校验

再去看登录 / 注册接口，就会很顺。

### 重点看什么？

- `/register`
- `/login`
- 返回前端的数据结构

---

## 3.5 `UserService`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/service/UserService.java
```

### 为什么要看？

Controller 只是入口，真正业务逻辑通常在这里。

### 重点看什么？

- 用户创建逻辑
- 管理员创建逻辑
- 用户角色与组织标签逻辑

---

## 3.6 `UserRepository`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/repository/UserRepository.java
```

### 为什么最后看？

你已经知道业务层想干什么，再看 repository 更容易。

### 重点看什么？

- `findByUsername`
- 自定义查询方法

---

# 4. 第三阶段：看文件上传链路

这是理解“知识库是怎么建起来的”的第一步。

---

## 4.1 `UploadController`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/controller/UploadController.java
```

### 为什么先看？

文件从前端上传，首先到这里。

### 重点看什么？

- 分片上传接口
- 查询已上传分片接口
- 合并文件接口

### 看完后你应该能说出来：

> 前端上传一个大文件，不是一次传完，而是分片上传

---

## 4.2 `UploadService`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/service/UploadService.java
```

### 为什么这一步最关键？

这里是真正把上传逻辑跑通的核心类。

### 重点看什么？

- 分片写到哪了
- MinIO 是怎么用的
- 合并逻辑怎么做
- 上传元数据存到了哪里

### 建议重点盯住这些关键词：

- `bucket("uploads")`
- `putObject`
- `composeObject`
- `removeObject`

---

## 4.3 `FileUpload` / `ChunkInfo`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/model/FileUpload.java
src/main/java/com/yizhaoqi/smartpai/model/ChunkInfo.java
```

### 为什么要看？

因为上传系统不只是“文件放到 MinIO”，还要保存业务元数据。

### 你要得到什么？

- 数据库里存了什么
- 文件和分片分别怎么记录

---

## 4.4 相关 Repository

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/repository/FileUploadRepository.java
src/main/java/com/yizhaoqi/smartpai/repository/ChunkInfoRepository.java
```

### 为什么看？

帮助你理解数据库表和业务操作是怎么对应起来的。

---

# 5. 第四阶段：看“文档怎么变知识”

这是 RAG 最关键的部分。

---

## 5.1 `FileProcessingConsumer`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/consumer/FileProcessingConsumer.java
```

### 为什么先看？

因为上传成功后，很多后续处理不是同步做的，而是异步走 Kafka。

### 重点看什么？

- `@KafkaListener`
- 收到消息后做了哪些事
- 调了哪些 service

### 看完后你应该能说出来：

> 为什么上传成功后，向量化不是立刻在接口里做，而是异步做

---

## 5.2 `ParseService`

### 文件

项目里如果存在：

```text
src/main/java/com/yizhaoqi/smartpai/service/ParseService.java
```

### 为什么要看？

它负责从文档中提取文本内容。

### 重点看什么？

- 如何解析 PDF / Word / 文本
- 切块逻辑如何处理

---

## 5.3 `VectorizationService`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/service/VectorizationService.java
```

### 为什么要看？

因为“文本 -> 向量 -> ES”通常在这里发生。

### 重点看什么？

- 切块后的文本怎么处理
- 调用了哪个 embedding client
- 向量如何落库 / 入索引

---

## 5.4 `EmbeddingClient`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/client/EmbeddingClient.java
```

### 为什么重要？

这是你第一次真正碰到“AI 能力调用”。

### 重点看什么？

- 请求体长什么样
- 调的是哪个 API
- 返回结果怎么解析

### 看完后你应该能说出来：

> 向量模型不是魔法，本质也是一次 HTTP API 调用

---

## 5.5 `EsIndexInitializer`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/config/EsIndexInitializer.java
```

### 为什么看？

它帮助你理解 ES 索引是怎么初始化的。

### 重点看什么？

- 索引名
- mapping 文件
- 启动时如何检查索引是否存在

---

# 6. 第五阶段：看问答链路

如果你是冲着 AI / Agent 来的，这一部分最值得反复看。

---

## 6.1 `ChatWebSocketHandler`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/handler/ChatWebSocketHandler.java
```

### 为什么是问答链路第一站？

因为用户不是普通 HTTP 调聊天，而是 WebSocket。

### 重点看什么？

- 连接建立后做了什么
- 收到消息后怎么处理
- 用户 ID 怎么从 token 中提取

### 看完后你应该能说出来：

> 聊天为什么用 WebSocket，而不是普通 REST 接口

---

## 6.2 `ChatHandler`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/service/ChatHandler.java
```

### 为什么最重要？

它大概率是“提问 -> 检索 -> 生成 -> 返回”的总调度中心。

### 重点看什么？

- 用户消息如何进入核心逻辑
- 会话如何管理
- 模型如何被调用
- 返回如何被流式发送

---

## 6.3 `HybridSearchService`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/service/HybridSearchService.java
```

### 为什么一定要看？

因为它很可能代表了：

- 关键词检索
- 向量检索
- 混合检索

### 重点看什么？

- 检索输入是什么
- 返回了哪些候选片段
- 如何和文档元信息关联

### 看完后你应该能说出来：

> 用户提问后，系统怎么从知识库里找到最相关的内容

---

## 6.4 `DeepSeekClient`

### 文件

```text
src/main/java/com/yizhaoqi/smartpai/client/DeepSeekClient.java
```

### 为什么要看？

这是真正和大模型对话的地方。

### 重点看什么？

- prompt 怎么组
- system prompt 怎么加
- 历史消息怎么传
- 是否支持流式返回

---

## 6.5 会话 / 历史相关类

你可以搜索：

- `Conversation`
- `ConversationService`
- `ConversationRepository`

### 为什么看？

因为 Agent 应用里“上下文”和“历史记忆”都很重要。

---

# 7. 第六阶段：看系统初始化与管理能力

这些不是第一优先级，但后面很重要。

---

## 7.1 `AdminUserInitializer`

### 为什么看？

理解系统启动时如何创建管理员。

---

## 7.2 `OrgTagInitializer`

### 为什么看？

理解多租户 / 组织标签能力是怎么初始化的。

---

## 7.3 `AdminController`

### 为什么看？

理解管理员能做哪些事：

- 用户管理
- 组织管理
- 迁移类操作

---

# 8. 最推荐的小白阅读顺序（按天版）

---

## 第一天

看：

1. `SmartPaiApplication`
2. `application.yml`
3. `application-local.yml`
4. `SecurityConfig`

目标：

- 知道项目怎么启动
- 知道系统有哪些基础依赖
- 知道接口权限规则

---

## 第二天

看：

1. `JwtAuthenticationFilter`
2. `JwtUtils`
3. `UserController`
4. `UserService`

目标：

- 弄懂登录鉴权链路

---

## 第三天

看：

1. `UploadController`
2. `UploadService`
3. `FileUpload`
4. `ChunkInfo`

目标：

- 弄懂文件上传与元数据保存

---

## 第四天

看：

1. `FileProcessingConsumer`
2. `VectorizationService`
3. `EmbeddingClient`
4. `EsIndexInitializer`

目标：

- 弄懂文档如何变成可检索知识

---

## 第五天

看：

1. `ChatWebSocketHandler`
2. `ChatHandler`
3. `HybridSearchService`
4. `DeepSeekClient`

目标：

- 弄懂 RAG 问答主链路

---

# 9. 阅读时每个类都问自己这 5 个问题

无论你打开哪个类，都先回答：

1. 它属于哪一层？
2. 它的输入是什么？
3. 它的输出是什么？
4. 它依赖了谁？
5. 它在整个链路里负责哪一步？

只要这 5 个问题能答出来，你就不是“看天书”了。

---

# 10. 最后一条建议

**不要试图一遍读懂。**

这个项目你至少会反复读 3 轮：

1. 第一轮：知道每个模块是干嘛的
2. 第二轮：知道链路怎么串起来
3. 第三轮：知道为什么这样设计，以及你能改什么

只要你按这份顺序读，效率会高很多。

