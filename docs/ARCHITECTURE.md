# 架构地图

## 主链路

```text
钉钉工作台 -> Vue -> Spring Auth -> 钉钉免登 OpenAPI
                       -> 用户/部门/角色/ACL

上传/同步 -> 对象存储 -> 解析 Worker -> 分块/Embedding -> PostgreSQL + pgvector

问题 + 当前用户 -> AuthorizedRetrievalService
                -> ACL 前置过滤 + 检索
                -> 可选有界 Agent 规划（最多两轮）
                -> 有界上下文 -> ChatModel -> 引用二次校验
```

## 边界

- `auth`：钉钉端内免登、备用网页登录、服务端 Session。
- `dingtalk`：应用 Token、用户/部门、知识库和机器人适配器。
- `identity`：用户、部门、角色与同步。
- `knowledge`：空间、目录、文档、版本和 ACL。
- `ingestion`：上传、解析、切分、Embedding 和发布。
- `retrieval`：唯一允许访问向量/关键词检索的业务入口，始终携带授权主体。
- `chat`：会话、流式回答、引用和反馈。
- `agent`：只读检索规划、证据预算、生成和引用白名单；不持有身份裁决或通用工具。
- `admin`：配置、任务、审计和指标。

## 依赖方向

```text
controller -> service interface -> application/domain service -> repository/adapter
```

Controller 不得直接依赖 Repository、Mapper、具体 Service 实现、向量库或模型客户端。权限过滤由服务端业务边界完成。

## 运行时组件

- Spring Boot/Java 21：API、身份、权限和 RAG 编排。
- Vue 3：钉钉端内 Web UI。
- PostgreSQL + pgvector：元数据、ACL、分块和向量。
- Redis：Session、Token、限流和权限版本缓存。
- MinIO/对象存储：原文件和安全预览。
- Worker：受限文档解析和异步索引。

## 当前认证切片

```text
GET /api/auth/config -> 创建 Session 绑定的 CSRF Token
普通浏览器 + demo 模式 -> POST /api/auth/demo -> 虚构测试主体
钉钉工作台 + dingtalk 模式 -> requestAuthCode
                              -> POST /api/auth/dingtalk/inside
                              -> AccessToken + code 换稳定 userId/unionId
                              -> 服务端稳定 userId 白名单
                              -> HttpOnly JSESSIONID
```

CSRF Token 只保存在前端内存并通过 `X-XSRF-TOKEN` 发送；长期身份只在服务端 Session 和 HttpOnly Cookie 中。免登码摘要在单实例内保留 5 分钟以拒绝重放，生产多实例时迁移到 Redis。

## 变化检查

- 身份协议变化：同步后端、前端、钉钉配置、拒绝路径测试和安全文档。
- ACL 结构变化：同步入库、检索、引用、缓存、删除和评测夹具。
- 分块/模型变化：保留版本，重跑固定评测集，不直接覆盖生产索引。
- API payload 变化：同步字节/token 上限、错误契约和压力测试。
