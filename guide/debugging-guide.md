# PaiSmart 调试指南（适合刚入门的开发者）

> 目标：
>
> - 知道断点该打在哪
> - 知道每条业务链路怎么跟
> - 出问题时知道先查哪一层

---

# 1. 调试前先记住一个原则

不要一上来就全局断点、全局搜代码。

正确顺序应该是：

1. 先看前端操作触发了哪个接口
2. 找到对应 Controller
3. 沿着 Controller -> Service -> Repository / Client 往下走
4. 中间关键点打断点

---

# 2. 调试准备工作

---

## 2.1 确保使用正确 profile

IDEA 启动 / Debug 时要确保使用：

```text
local
```

否则会连错数据库。

日志里应该看到：

```text
The following 1 profile is active: "local"
```

---

## 2.2 先确认中间件都在

至少确认：

- MySQL
- Redis
- Kafka
- Elasticsearch
- MinIO

---

## 2.3 调试时优先看 3 类日志

| 日志 | 作用 |
|---|---|
| 控制台日志 | 快速看启动与实时问题 |
| `logs/smartpai.*.log` | 核心业务日志 |
| `logs/business.*.log` | 业务侧重点日志 |

---

# 3. 调试链路一：登录

这是最适合新手练手的一条链路。

---

## 3.1 断点建议

按顺序打在：

1. `UserController.login(...)`
2. `UserService` 中登录处理方法
3. `JwtUtils.generateToken(...)`

---

## 3.2 你要观察什么？

### Controller 层

看：

- 前端传来的用户名密码是什么
- 返回给前端的数据结构是什么

### Service 层

看：

- 数据库怎么查用户
- 密码怎么校验

### JWT 层

看：

- token 里放了什么字段

---

## 3.3 常见报错怎么理解

| 现象 | 可能原因 |
|---|---|
| 登录 401 | 用户名密码不对 / token 失效 |
| 登录接口进不去 | `SecurityConfig` 拦截规则有问题 |
| 登录成功但后续接口仍 401 | 前端没带 token / filter 没解析成功 |

---

# 4. 调试链路二：文件上传

这条链路能帮你理解：

- 前端上传
- MinIO
- MySQL
- Kafka

---

## 4.1 推荐断点位置

1. `UploadController` 中上传接口
2. `UploadService.uploadChunk(...)`
3. `UploadService.mergeChunks(...)`

---

## 4.2 你要观察什么？

### 在 Controller 看

- 前端传来的分片编号
- 文件 MD5
- 总分片数

### 在 Service 看

- 分片有没有真正写入 MinIO
- 分片记录有没有写入数据库
- 合并成功后有没有发 Kafka 消息

---

## 4.3 这条链路最常见的问题

| 现象 | 根因方向 |
|---|---|
| 上传直接失败 | 前端请求参数 / 鉴权问题 |
| 分片上传成功但合并失败 | MinIO / 分片缺失 / MD5 问题 |
| 合并成功但后面没处理 | Kafka 没发成功 / Consumer 没消费 |

---

# 5. 调试链路三：文档解析与向量化

这条链路最像“知识库构建”。

---

## 5.1 推荐断点位置

1. `FileProcessingConsumer` 的 Kafka 监听方法
2. `ParseService` 文档解析方法
3. `VectorizationService.vectorize(...)`
4. `EmbeddingClient.embed(...)`

---

## 5.2 你要观察什么？

### Consumer

看：

- Kafka 消息里带了什么
- 收到消息后依次做了什么事

### ParseService

看：

- 文档被解析成什么文本
- 切块结果大概有多少段

### EmbeddingClient

看：

- 请求发给哪个 API
- 文本列表是否正确
- 返回向量数量是否和输入对齐

---

## 5.3 这条链路最常见的问题

| 现象 | 根因方向 |
|---|---|
| Kafka 没触发 | topic 不对 / consumer 没起来 |
| 向量化失败 | embedding key 不对 / API 返回异常 |
| ES 没有数据 | 入索引逻辑没执行 / mapping 有问题 |

---

# 6. 调试链路四：聊天 / RAG

这是最接近 AI / Agent 核心能力的链路。

---

## 6.1 推荐断点位置

1. `ChatWebSocketHandler.handleTextMessage(...)`
2. `ChatHandler.processMessage(...)`
3. `HybridSearchService` 的主搜索方法
4. `DeepSeekClient` 的请求发送方法

---

## 6.2 建议怎么跟？

### 第一步：先看消息有没有到后端

在：

```java
ChatWebSocketHandler.handleTextMessage(...)
```

打断点。

如果这里都进不来，先别看后面。

#### 说明

如果这里进不来，问题通常在：

- 前端没发消息
- WebSocket 没连上
- token 有问题

---

### 第二步：看有没有进入聊天主逻辑

在：

```java
ChatHandler.processMessage(...)
```

打断点。

观察：

- 用户消息是什么
- session 是谁
- 会话上下文有没有取到

---

### 第三步：看有没有检索知识

在：

```java
HybridSearchService
```

里打断点。

观察：

- 查询词是什么
- 检索命中了哪些文档片段
- 返回的上下文长度大概多少

---

### 第四步：看模型调用

在：

```java
DeepSeekClient
```

里打断点。

观察：

- 最终 prompt 是怎么拼的
- 历史消息是否带上了
- 检索内容是否真的进了上下文

---

# 7. 调试链路五：WebSocket 流式返回

---

## 7.1 推荐断点位置

1. `ChatWebSocketHandler.afterConnectionEstablished(...)`
2. `ChatHandler` 中发送 chunk 的地方
3. `ChatWebSocketHandler.afterConnectionClosed(...)`

---

## 7.2 你要观察什么？

### 连接建立时

看：

- 用户 ID 是否解析正确
- sessionId 是否保存成功

### 返回 chunk 时

看：

- 每个 chunk 的内容是什么
- chunk 是从模型流式返回来的，还是后端自己切的

### 连接关闭时

看：

- session 是否移除
- 是否有资源清理

---

# 8. 怎么快速判断问题在哪一层？

你可以用下面这个定位方法。

---

## 8.1 前端层

如果页面没反应，先看浏览器：

- Network
- Console

---

## 8.2 Controller 层

如果请求到了后端，但没走下去：

- 先看对应 Controller 断点能不能进

---

## 8.3 Service 层

如果 Controller 进了，但结果不对：

- 看 Service 的业务逻辑

---

## 8.4 Repository / DB 层

如果业务逻辑里查不到数据：

- 看 SQL
- 看数据库表数据

---

## 8.5 中间件层

如果和以下能力有关，就去看对应中间件：

| 能力 | 看哪里 |
|---|---|
| 用户数据 | MySQL |
| 缓存 / token 状态 | Redis |
| 异步任务 | Kafka |
| 文件 | MinIO |
| 检索 / 向量 | Elasticsearch |

---

# 9. 你最值得打断点的 10 个地方

按实用程度排序：

1. `UserController.login`
2. `JwtAuthenticationFilter.doFilterInternal`
3. `UploadController`
4. `UploadService.mergeChunks`
5. `FileProcessingConsumer`
6. `EmbeddingClient.embed`
7. `ChatWebSocketHandler.handleTextMessage`
8. `ChatHandler.processMessage`
9. `HybridSearchService`
10. `DeepSeekClient`

---

# 10. 小白调试时最容易犯的错

---

## 10.1 一次打太多断点

结果：

- 程序走得乱
- 自己也乱

### 建议

一条链路只打 2~4 个关键断点。

---

## 10.2 没先看日志就断点

有些问题日志已经说得很清楚了，比如：

- 数据库连不上
- Kafka topic 不存在
- MinIO bucket 不存在

先看日志，能省很多时间。

---

## 10.3 不知道自己现在在跟哪条链路

建议你每次调试前先说清楚：

> 我这次是在调登录  
> 还是在调上传  
> 还是在调聊天

不要几条链路一起看。

---

# 11. 最推荐的小白调试顺序

如果你明天想实操，建议按这个顺序：

## 第一步：登录调试

先把登录打通。

## 第二步：上传调试

看文件是否成功进入 MinIO 和数据库。

## 第三步：Kafka / 向量化调试

看上传后是否触发后续知识处理。

## 第四步：聊天调试

看用户问题如何流转。

## 第五步：WebSocket 流式返回调试

看输出体验和 session 管理。

---

# 12. 一句话总结

调试这个项目时，最重要的不是“记住所有代码”，而是：

> **每次只盯住一条链路，把输入、处理、输出看明白。**

只要你能稳定跟通：

- 登录链路
- 上传链路
- RAG 聊天链路

你对整个项目的理解就会越来越扎实。

