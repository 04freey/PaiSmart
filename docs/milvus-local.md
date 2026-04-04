# Milvus 本地启动说明

当前后端已移除 Elasticsearch，默认改为依赖 **Milvus 2.5.x**。

## 1. 启动方式

项目内已经把 `docs/docker-compose.yaml` 替换成了 **Milvus standalone 版本**，直接用项目 compose 即可：

```bash
docker compose -f docs/docker-compose.yaml up -d
```

如果你在 Windows PowerShell 中执行：

```powershell
docker compose -f .\docs\docker-compose.yaml up -d
```

## 2. 默认端口

- gRPC / SDK：`19530`
- HTTP / WebUI：`9091`
- 依赖组件：
  - `milvus-etcd`
  - `milvus-minio`
  - `milvus`

## 3. 项目配置

当前项目中的 `application*.yml` 已默认按以下连接方式配置：

```yml
milvus:
  host: localhost
  port: 19530
  collection-name: knowledge_base
```

只要本机 Milvus 正常启动，Spring Boot 启动时会自动：

- 连接 Milvus
- 检查 `knowledge_base` 集合是否存在
- 不存在时自动建表/建索引
- 在需要时自动加载集合
- 为 `document_vectors` 自动补齐常用 MySQL 索引
- 尝试创建 `FULLTEXT` 文本索引；失败时自动回退为 `LIKE/LOCATE`

## 4. 验证方式

启动后可检查：

```bash
docker compose -f docs/docker-compose.yaml ps
```

或者直接访问：

- `http://127.0.0.1:9091/webui/`

## 5. 说明

- 当前实现使用 **Milvus + MySQL 文本召回**，不再依赖 ES。
- 文本搜索现在会优先走 **MySQL FULLTEXT**，如果数据库环境不支持，则自动回退到 `LIKE/LOCATE` 方案。
