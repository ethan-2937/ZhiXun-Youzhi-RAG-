# 有界 Agentic RAG

## 当前形态

Agentic RAG 是现有 ACL 前置向量检索外的一层受控编排，默认关闭。它不是开放式 ReAct Agent，不允许模型自行选择身份、调用互联网、读取文件、访问钉钉或执行写操作。

```text
当前会话主体 + 问题
  -> 授权知识是否存在（不调用模型）
  -> ChatModel 生成 1~3 个检索查询
  -> 服务端注入 userId/spaceId 执行 ACL 前置检索
  -> 证据不足时最多再规划一轮
  -> 裁剪并编号授权证据
  -> ChatModel 生成严格 JSON 回答和引用 ID
  -> 服务端引用白名单复核
  -> 通过：Agentic 回答；失败：抽取式回答或拒答
```

模型只负责规划和基于证据写作。唯一“工具”是服务端的 `AuthorizedKnowledgeSearch`，其 `userId` 来自已认证会话，不来自模型输出。文档标题、正文、文件名和元数据都被序列化为不可信 evidence；即使包含“忽略规则”或工具指令，也不会创建新的工具调用。

## 公司模型网关

网关已验证兼容 `/v1/chat/completions`。MVP 默认使用能稳定遵守 `response_format=json_object` 的 `qwen-plus`；Chat 的地址和密钥未单独配置时复用 Embedding 网关配置。切换模型前必须先验证严格 JSON 和延迟，不能只比较模型名称。

```dotenv
RAG_ENABLED=true
AGENTIC_RAG_ENABLED=true
CHAT_MODEL=qwen-plus

# 只有 Chat 使用不同网关或凭据时才需要单独设置
# CHAT_BASE_URL=http://127.0.0.1:11434/v1
# CHAT_API_KEY=
```

生产环境默认保持 `AGENTIC_RAG_ENABLED=false`，完成固定集评测和灰度后再启用。

## 默认预算

| 预算 | 默认值 |
|---|---:|
| Agent 检索轮数 | 2 |
| 每轮查询数 | 3 |
| 单条规划查询 | 500 字符 |
| 每条查询候选 | 8 |
| 进入模型的证据块 | 6 |
| 上下文 | 8,000 字符且按 6,000 token 保守预算裁剪 |
| Chat 请求 | 20,000 字符 |
| Chat 响应 | 12,000 字符 |
| Chat 输出 | 800 token |
| 最终答案 | 4,000 字符 |
| 最终引用 | 沿用检索配置，默认 3 份文档 |

当前 token 边界采用“一个字符最多计一个 token”的保守裁剪，不依赖供应商 tokenizer。未来切换模型时应接入对应 tokenizer，并用固定评测集重新校准。

## 失败与降级

以下情况不会把未经验证的模型输出返回给用户：

- 当前主体在所选空间没有任何授权知识。
- ChatModel 超时、网络错误、空响应或响应超限。
- 规划或回答不是严格 JSON、包含额外字段或超过集合上限。
- 两轮检索后仍没有达到正式检索阈值的证据。
- `answered` 没有引用，或引用了本轮 evidence 之外的文档 ID。
- 配置预算不合法。

模型/格式/引用失败时尝试现有抽取式 RAG；没有足够授权证据时返回明确拒答。普通日志不记录问题、计划、证据、回答或模型原始响应。

## 调优与评测

Agentic RAG 不替代检索评测。先用 `docs/RETRIEVAL_TUNING.md` 保证 ACL、Recall@K 和排序，再比较 Agent 开关前后的：

- 必需文档召回和越权泄漏。
- 回答可回答性、关键术语和引用正确率。
- Prompt Injection 文档下的工具零执行与引用白名单。
- P95 延迟、每题模型调用次数和降级率。

任何 Prompt、模型、轮数、阈值或上下文预算变更都需要保留基线，不能只凭单个演示问题判断效果。
