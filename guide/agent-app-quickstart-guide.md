# PaiSmart 项目入门指南（面向 Agent 应用开发小白）

> 适合对象：
>
> - 刚开始接触 AI 应用 / Agent 应用开发
> - 对 RAG、向量检索、知识库问答了解不多
> - 想借 PaiSmart 这个项目，建立“一个完整 AI 应用是怎么跑起来的”全局认识

---

# 1. 先说结论：这个项目本质上是什么？

PaiSmart 不是一个“纯聊天机器人项目”，它更接近一个：

**“基于知识库的企业级 AI 问答系统”**

你可以把它理解成：

1. 用户上传文档
2. 系统把文档拆分、解析、向量化
3. 向量存入 Elasticsearch
4. 用户提问时，系统先去知识库里检索相关内容
5. 再把检索结果连同用户问题一起交给大模型
6. 最后把结果通过接口 / WebSocket 返回给前端

这就是一个很典型的 **RAG（Retrieval-Augmented Generation，检索增强生成）** 应用。

如果你想做 Agent 应用，这个项目非常有价值，因为它已经包含了很多 Agent 系统常见的基础能力：

- 用户输入处理
- 上下文管理
- 知识检索
- 外部模型调用
- 实时流式输出
- 多租户权限
- 文档上传与解析

所以你可以把它当成：

> **从“聊天应用”进阶到“具备知识与工具能力的 Agent 系统”的中间桥梁项目**

---

# 2. 作为小白，先建立一张全局地图

先不要一上来就扎进某个类里看代码。  
你应该先知道这个项目由哪几部分组成。

## 2.1 项目结构

这个仓库大致分 3 部分：

| 目录 | 作用 | 你前期要不要重点看 |
|---|---|---|
| `src/` | 后端 Spring Boot 主体 | **要重点看** |
| `frontend/` | 主业务前端，Vue3 | **后面要看** |
| `homepage/` | 官网 / 展示页 | 前期可不看 |
| `docs/` | 启动相关、DDL、Docker 等 | **先看一点** |

---

## 2.2 你最该关心的后端结构

后端主代码在：

```text
src/main/java/com/yizhaoqi/smartpai/
```

核心目录可以这样理解：

| 目录 | 作用 | 小白理解方式 |
|---|---|---|
| `controller/` | HTTP 接口层 | “前端发请求，先到这里” |
| `service/` | 业务逻辑层 | “真正干活的地方” |
| `repository/` | 数据库访问层 | “查 MySQL 的地方” |
| `model/` / `entity/` | 数据对象 | “数据库表对应的 Java 类” |
| `config/` | 各种配置与初始化 | “系统启动规则、鉴权、Bean 配置” |
| `client/` | 调外部 AI / Embedding API | “和大模型、向量模型打交道” |
| `consumer/` | Kafka 消费者 | “异步处理任务的地方” |
| `handler/` | WebSocket 处理 | “聊天流式输出入口” |
| `utils/` | 工具类 | “JWT、日志、加密等辅助能力” |

---

# 3. 先不要学 Agent，先学这个项目的业务链路

如果你是小白，我建议你先把下面这 4 条链路看明白。

---

## 3.1 链路一：登录 / 鉴权链路

### 你要知道什么？

用户登录后，系统怎么知道“你是谁”？

### 大致流程

1. 前端调用登录接口
2. 后端校验用户名密码
3. 后端生成 JWT
4. 前端后续请求都带上这个 JWT
5. 后端过滤器解析 JWT，识别用户身份

### 重点看哪里？

推荐阅读顺序：

1. `controller/UserController.java`
2. `utils/JwtUtils.java`
3. `config/JwtAuthenticationFilter.java`
4. `config/SecurityConfig.java`

### 学到什么？

这是 Agent 应用里非常重要的一部分，因为：

- 不管你以后做聊天 Agent、工具 Agent、企业内部智能助手
- 只要是多用户系统
- 就一定绕不开登录、鉴权、权限控制

---

## 3.2 链路二：文档上传与知识入库链路

### 你要知道什么？

用户上传一个文档后，系统怎么把它变成“可检索知识”？

### 大致流程

1. 前端上传文件
2. 后端接收文件 / 分片
3. 文件存到 MinIO
4. 系统解析文件内容
5. 文本切块（chunk）
6. 调 Embedding 模型生成向量
7. 向量与元数据写入 Elasticsearch

### 重点看哪里？

推荐阅读顺序：

1. `controller/UploadController.java`
2. `service/UploadService.java`
3. `consumer/FileProcessingConsumer.java`
4. `service/ParseService.java`（如果有）
5. `service/VectorizationService.java`
6. `client/EmbeddingClient.java`
7. `config/EsIndexInitializer.java`

### 学到什么？

这是 RAG 项目的根。

未来你做 Agent 时：

- 要么接知识库
- 要么接工具
- 要么接业务数据

本项目这部分就是在告诉你：

> “原始文档如何变成 Agent 可以使用的知识”

---

## 3.3 链路三：用户提问到 AI 回复链路

### 你要知道什么？

用户在前端问一句话，后端怎么变成一段回答？

### 大致流程

1. 前端发起提问
2. 后端拿到用户问题
3. 先去知识库检索相关内容
4. 拼装 prompt
5. 调大模型
6. 把结果返回给前端

### 重点看哪里？

推荐阅读顺序：

1. `handler/ChatWebSocketHandler.java`
2. `service/ChatHandler.java`
3. `service/HybridSearchService.java`
4. `client/DeepSeekClient.java`（或其他大模型客户端）
5. `service/ConversationService.java`（如果有）

### 学到什么？

这是最接近 Agent 思维的部分。

你可以重点理解：

- 用户输入怎么处理
- 系统提示词怎么组织
- 检索结果怎么拼进上下文
- 输出为什么要走流式返回

---

## 3.4 链路四：WebSocket 实时输出链路

### 你要知道什么？

为什么聊天不是等 10 秒一次性返回，而是像打字机一样一段段出来？

### 大致流程

1. 前端建立 WebSocket 连接
2. 后端收到问题
3. 大模型逐步返回内容
4. 后端一块块推给前端
5. 前端边收边展示

### 重点看哪里？

1. `handler/ChatWebSocketHandler.java`
2. `frontend` 中聊天页面相关代码

### 学到什么？

做 Agent 应用时，流式输出几乎是标配。

因为用户希望看到：

- 系统正在思考
- 系统不是卡死了
- 响应体验更顺滑

---

# 4. 你应该怎么读这个项目？——推荐阅读顺序

这是最重要的一部分。

很多小白最大的误区是：

> 从第一行代码开始顺序看

这是效率最低的方式。

## 正确顺序如下：

### 第 1 步：先跑起来

你已经基本完成这一阶段了。  
目标不是“完全理解”，而是：

- 知道系统能启动
- 知道前后端分别在哪
- 知道请求能打通

---

### 第 2 步：先看配置，再看启动类

先看：

1. `src/main/resources/application.yml`
2. `src/main/resources/application-local.yml`
3. `src/main/java/com/yizhaoqi/smartpai/SmartPaiApplication.java`

你要回答这几个问题：

- 应用端口是多少？
- 数据库连哪？
- Redis 连哪？
- Kafka、MinIO、ES 怎么配？
- 启动时有哪些初始化逻辑？

---

### 第 3 步：看安全与登录

先看：

1. `SecurityConfig`
2. `JwtAuthenticationFilter`
3. `UserController`
4. `JwtUtils`

目标：

- 弄懂“请求怎么被放行/拦截”
- 弄懂“JWT 怎么生成与校验”

---

### 第 4 步：看上传链路

先看：

1. `UploadController`
2. `UploadService`
3. 文件上传到 MinIO 的逻辑

目标：

- 知道文件从前端到后端，再到对象存储是怎么走的

---

### 第 5 步：看知识处理链路

先看：

1. `FileProcessingConsumer`
2. `ParseService`
3. `VectorizationService`
4. `EmbeddingClient`

目标：

- 弄懂“一个文档怎么被拆成文本块并转成向量”

---

### 第 6 步：看问答链路

先看：

1. `ChatWebSocketHandler`
2. `ChatHandler`
3. `HybridSearchService`
4. `DeepSeekClient`

目标：

- 弄懂“用户提问 -> 检索 -> 生成 -> 返回”的全流程

---

### 第 7 步：最后再看前端

等你后端链路差不多懂了，再看：

- 前端如何发请求
- 前端如何建立 WebSocket
- 前端如何显示流式输出

不然你会被前端细节分散注意力。

---

# 5. 从 Agent 开发视角，PaiSmart 对应哪些核心能力？

如果你未来想做 Agent，不要只把这个项目看成“知识库问答”。

你要把它映射成 Agent 能力模型：

| Agent 能力 | PaiSmart 中对应部分 |
|---|---|
| 用户管理 | 登录、JWT、权限 |
| 长期知识 | 文档上传 + Elasticsearch |
| 上下文检索 | HybridSearchService |
| 模型调用 | DeepSeekClient / EmbeddingClient |
| 实时交互 | WebSocket |
| 状态管理 | 会话、历史记录、数据库 |
| 异步任务 | Kafka Consumer |
| 存储层 | MySQL / Redis / MinIO / ES |

换句话说：

> PaiSmart 已经具备了一个实用型 Agent 系统的骨架。

它暂时还不算特别“强 Agent”，因为它的重点仍然是：

- 检索增强问答
- 文档知识库
- 多租户管理

但这已经足够你打基础了。

---

# 6. 小白最容易卡住的点

---

## 6.1 卡在“概念太多”

你会同时遇到：

- Spring Boot
- JWT
- MySQL
- Redis
- Kafka
- MinIO
- Elasticsearch
- RAG
- Embedding
- WebSocket

### 正确心态

不要想“一次全学会”。  
你应该把它们分成三层：

| 层级 | 内容 |
|---|---|
| 第一层 | 系统能启动、接口能跑 |
| 第二层 | 知道请求链路怎么走 |
| 第三层 | 知道每个中间件在系统里的职责 |

---

## 6.2 卡在“看不懂业务代码”

不要死盯实现细节，先回答：

1. 这个类属于哪一层？
2. 它的输入是什么？
3. 它的输出是什么？
4. 它调用了谁？
5. 它为什么存在？

你只要先把这五个问题搞清楚，代码就不难了。

---

## 6.3 卡在“我不知道从哪开始调试”

最推荐的调试起点有 3 个：

### 起点 A：登录接口

因为它最简单，最容易建立信心。

### 起点 B：文件上传接口

因为它能帮助你理解 MinIO、数据库、Kafka 的协作。

### 起点 C：聊天接口 / WebSocket

因为它能帮助你理解 Agent / RAG 的核心价值链。

---

# 7. 最适合小白的学习方式：不要“读代码”，要“跟链路”

下面给你一套最实用的方法。

---

## 方法 1：从前端操作反推后端

比如你在页面上点击一次“登录”：

1. 看浏览器 Network 请求发到哪个 URL
2. 去后端找这个接口
3. 看 Controller 调了哪个 Service
4. 看 Service 又查了哪个 Repository / Client
5. 看最后返回了什么

这样学习最有效。

---

## 方法 2：边打断点边看

推荐断点位置：

- `UserController.login`
- `UploadController`
- `ChatWebSocketHandler.handleTextMessage`
- `ChatHandler.processMessage`
- `HybridSearchService`

你只要断点走一遍，就比看 100 页文档更清楚。

---

## 方法 3：自己画流程图

比如画：

### 登录流程图

```text
前端 -> UserController -> UserService -> UserRepository -> JWT -> 返回前端
```

### 上传流程图

```text
前端 -> UploadController -> UploadService -> MinIO / MySQL -> Kafka -> Consumer -> Parse -> Embedding -> ES
```

### 问答流程图

```text
前端 -> WebSocket -> ChatWebSocketHandler -> ChatHandler -> HybridSearchService -> DeepSeekClient -> 流式返回
```

你自己画一遍，理解会暴涨。

---

# 8. 给 Agent 小白的学习路线（非常重要）

如果你未来想做 Agent 应用，我建议按这个顺序补知识。

---

## 第一阶段：先搞懂“普通 Web 应用”

你至少要理解：

- Controller / Service / Repository 分层
- 数据库 CRUD
- 登录鉴权
- HTTP 接口
- 前后端交互

如果这些不懂，直接学 Agent 会非常飘。

---

## 第二阶段：再搞懂“AI 应用”

重点理解：

- Prompt 是什么
- LLM 调用接口长什么样
- 流式输出是怎么回事
- 上下文怎么拼
- Token 限制为什么重要

---

## 第三阶段：再搞懂“RAG”

重点理解：

- 文档切块
- Embedding
- 向量检索
- TopK
- 重排（如果以后接入 rerank）
- 引用与溯源

---

## 第四阶段：再搞懂“Agent”

重点理解：

- Agent 和普通聊天机器人的区别
- 工具调用（Tool Calling）
- 任务分解
- 记忆机制
- 状态管理
- 多步推理与工作流编排

PaiSmart 目前更偏：

- AI 应用
- RAG 应用

它能帮你打好 Agent 的基础，但它本身还不是一个完整工具型 Agent 平台。

---

# 9. 你现在最值得做的 10 个练手任务

下面这些任务很适合小白，而且能快速提升。

| 任务 | 难度 | 价值 |
|---|---|---|
| 跑通登录接口 | 低 | 建立后端信心 |
| 跑通注册接口 | 低 | 看懂用户模型 |
| 跑通前端登录页 | 低 | 理解前后端联动 |
| 跑通一次文档上传 | 中 | 理解知识入库链路 |
| 看一次 Kafka 消费日志 | 中 | 理解异步任务 |
| 看 ES 索引结构 | 中 | 理解向量存储 |
| 看一次问答 WebSocket 流式返回 | 中 | 理解聊天核心链路 |
| 给日志加一条自己的业务日志 | 低 | 熟悉代码改动 |
| 给用户表加一个简单字段并打通前后端 | 中 | 熟悉全栈改动 |
| 给聊天增加一个“系统提示词配置项” | 中 | 开始接触 AI 应用改造 |

---

# 10. 如果你想把它继续往 Agent 方向演进，可以怎么做？

这是进阶部分。

你后面可以把这个项目逐步改造成更“像 Agent”的系统。

例如：

## 10.1 增加工具调用能力

让模型不只是回答问题，而是可以：

- 查询天气
- 查询数据库（安全前提下）
- 调内部业务接口
- 执行知识库检索之外的动作

---

## 10.2 增加工作流能力

比如用户问一个复杂问题时，系统自动拆成：

1. 先检索知识
2. 再总结
3. 再生成行动建议

这就开始接近 Agent Workflow。

---

## 10.3 增加长期记忆 / 会话记忆

让系统记住用户偏好、上下文、历史任务状态。

---

## 10.4 增加多工具编排

例如：

- 先查知识库
- 再查外部 API
- 再综合总结

这就是 Agent 的典型形态。

---

# 11. 你接下来最推荐的学习顺序（实操版）

如果你问我：

> “我明天开始，应该一共怎么学？”

那我建议你这样：

---

## Day 1：先熟悉项目与启动

目标：

- 知道前后端怎么启动
- 知道各中间件用途
- 知道主要模块在哪

---

## Day 2：读登录链路

看：

- `UserController`
- `JwtUtils`
- `SecurityConfig`

目标：

- 知道一个请求怎么被鉴权

---

## Day 3：读上传链路

看：

- `UploadController`
- `UploadService`

目标：

- 知道文件去哪了

---

## Day 4：读知识处理链路

看：

- `FileProcessingConsumer`
- `VectorizationService`
- `EmbeddingClient`

目标：

- 知道文档怎么变向量

---

## Day 5：读问答链路

看：

- `ChatWebSocketHandler`
- `ChatHandler`
- `HybridSearchService`

目标：

- 知道问题怎么变回答

---

## Day 6：前后端联动调试

目标：

- 自己从页面发请求
- 打断点跟一遍后端

---

## Day 7：做一个小改动

例如：

- 新增一个配置项
- 新增一条日志
- 修改一个默认提示词
- 在聊天返回里加一点额外元信息

只有你自己改过，理解才算真正开始扎实。

---

# 12. 最后给你的建议

如果你是 Agent 开发小白，不要给自己太大压力。

你现在最重要的不是：

- 一次性搞懂全部技术
- 一口气看完整个项目
- 一上来就改成高级 Agent 系统

你最重要的是先建立这三个能力：

## 能力 1：看懂系统链路

知道：

- 请求从哪来
- 经过哪里
- 去了哪里
- 为什么这样设计

## 能力 2：能独立调试

会：

- 打断点
- 看日志
- 看配置
- 看数据库 / 中间件状态

## 能力 3：能做小改动

只要你能稳定地做出小改动并跑通，你就已经不是纯小白了。

---

# 13. 一句话总结

PaiSmart 对你最有价值的地方，不只是“它能聊天”，而是它让你看到：

> **一个真实 AI 应用，从用户输入、知识入库、检索增强、模型调用，到实时返回，是如何完整串起来的。**

对 Agent 开发来说，这就是最重要的地基。

---

# 14. 你下一步最建议做什么？

如果你让我给一个最务实的建议：

## 先做这 3 件事

1. 把登录链路看懂  
2. 把上传 -> 向量化 -> ES 入库链路看懂  
3. 把聊天 -> 检索 -> 大模型返回链路看懂  

只要这三条链路通了，你再学 Agent，就会非常顺。

---

如果你愿意，我下一步还可以继续给你补两份文档：

1. **《PaiSmart 后端阅读顺序（按类名逐个列出来）》**
2. **《PaiSmart 的 RAG / Chat / WebSocket 调试指南》**

