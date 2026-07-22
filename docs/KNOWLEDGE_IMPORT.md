# 真实语义检索试运行

当前切片使用 OpenAI 兼容 Embedding 接口和进程内向量索引。默认问答仍执行“外部知识导出 -> 分块 -> Embedding -> ACL 前置语义检索 -> 原文摘录与引用”；可通过独立开关启用有界 Agentic RAG 和生成式 ChatModel。它不替代后续 PostgreSQL/pgvector。

## 知识文件

默认读取 `/knowledge/documents.jsonl`。Docker Compose 将仓库下未跟踪的 `data/knowledge/` 以只读方式挂载到该目录。

每行是一份文档，必须是单行 JSON：

```json
{"documentId":"doc-test-001","title":"虚构测试制度","spaceId":"space-test","spaceName":"测试空间","nodeId":"node-test","nodeName":"测试目录","section":"第一节","updatedAt":"2026-07-01","content":"完全虚构的测试正文。","sourceFile":"虚构测试资料.md","sourceFormat":"md","allowedUserIds":["test-user-demo-001"]}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `documentId` | 稳定文档或版本标识，文件内不得重复 |
| `spaceId/spaceName` | 左侧一级资料空间 |
| `nodeId/nodeName` | 左侧二级目录 |
| `title/section/updatedAt` | 引用所需的标题、章节和版本日期 |
| `content` | 将被分块并发送到 Embedding 服务的正文 |
| `sourceFile/sourceFormat` | 未跟踪原始导出文件的安全相对路径和格式，用于授权预览与下载 |
| `allowedUserIds` | 有权检索该文档的稳定用户 ID；不能为空，不使用姓名 |

真实文件、正文、员工标识和生成的向量都不能进入 Git。修改知识文件后需要重启后端，当前索引会在启动阶段重新构建。

## 启用

在未跟踪的 `.env` 中配置：

```dotenv
RAG_ENABLED=true
EMBEDDING_BASE_URL=<OpenAI 兼容服务的 /v1 地址>
EMBEDDING_API_KEY=<部署环境提供>
EMBEDDING_MODEL=text-embedding-v4
EMBEDDING_DIMENSION=1024
RAG_KNOWLEDGE_FILE=/knowledge/documents.jsonl
```

然后执行：

```powershell
docker compose up --build -d
docker compose ps
docker compose logs backend --tail 100
```

启用成功后，页面会显示“真实语义检索 · 试运行”。问题只在当前用户至少拥有一个候选文档时发送到 Embedding 服务；文档先按用户和空间过滤，再计算相似度；引用返回前再次检查用户授权。

## 默认预算

- 知识文件：5 MiB。
- 文档：200 份，每份正文 100,000 字符。
- 分块：1,200 字符、120 字符重叠，最多 2,000 块。
- Embedding：每批 8 条，每条最多 1,600 字符，维度必须为 1,024。
- 检索：最多计算后返回 Top 4，最多 3 个去重引用，每段摘录最多 260 字符。
- 问题：沿用 API 的 1,000 字符上限。

任何缺少 ACL、超限、模型超时、响应数量/索引/维度错误、零向量或非有限数值都会失败关闭，不回退到关键词或模型常识。

当前公司 Embedding 网关对长中文文本批处理较敏感。本地 MVP 使用兼容配置 `EMBEDDING_BATCH_SIZE=1`、`EMBEDDING_MAX_INPUT_CHARS=500`、`RAG_CHUNK_CHARS=300`、`RAG_CHUNK_OVERLAP_CHARS=40`；通用默认预算仍保留在 `.env.example`，更换服务后应重新压测再调整。

Agentic 模式、对话模型配置、上下文预算和安全降级见 `docs/AGENTIC_RAG.md`。

## 钉钉知识空间

目前知识空间页面需要登录，且尚未获得知识 API/管理员权限。本切片先把知识源隔离为 `KnowledgeSource` 接口；取得权限后新增钉钉适配器，保留外部文档 ID、版本、目录和 ACL，再逐步替换 JSONL 人工导出。

## 本地 Office/压缩包导入

无法使用钉钉知识 API 时，可将经批准的导出文件放入未跟踪的 `data/knowledge/raw/`，再生成当前 MVP 使用的 JSONL：

```powershell
python scripts/import_knowledge.py `
  --input data/knowledge/raw `
  --output data/knowledge/documents.jsonl `
  --allowed-user-id test-user-demo-001 `
  --space-id space-ai-practice `
  --space-name "内部 AI 实践资料"
```

`--allowed-user-id` 可重复提供，但必须是当前系统认证得到的稳定用户 ID，不能使用姓名。演示阶段使用 `test-user-demo-001`；切换钉钉认证后必须重新导入并显式配置真实稳定 ID。

当前支持顶层 PPTX、Markdown、UTF-8 TXT 和 ZIP。ZIP 内只读取 PPTX、Markdown 和 UTF-8 TXT；不执行脚本，不读取 HTML/JSON 作为正文，不解析外链，默认跳过 README 和包清单。PPTX 按幻灯片生成可引用文档，Markdown 按三级以内标题生成文档；纯图片幻灯片在没有 OCR 时跳过。

左侧文件预览通过 `GET /api/knowledge/files/{nodeId}` 返回当前稳定用户 ID 有权访问的提取原文，最多 50,000 字符。原文件下载通过 `GET /api/knowledge/files/{nodeId}/content`；下载前再次解析受控相对路径，并拒绝路径穿越、符号链接、缺失文件和超过 50 MiB 的文件。若同一个 ZIP 原包中存在当前用户无权访问的文档，即使其中一个节点可预览，也禁止整包下载。

导入器失败关闭并原子替换输出文件。默认限制为 100 个输入文件、每个输入 50 MiB、压缩包 1,000 个条目/解压后 100 MiB、单条目 10 MiB、压缩比 100、200 份生成文档、单文档 100,000 字符和 5 MiB JSONL。路径穿越、符号链接、重复路径、压缩炸弹、损坏文件、缺失 ACL、非 UTF-8 文本或输出超限都会拒绝整个导入。
