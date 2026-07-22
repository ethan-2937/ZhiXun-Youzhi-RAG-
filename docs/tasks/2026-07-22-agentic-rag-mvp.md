# Task: 有界 Agentic RAG MVP

## 问题

当前真实问答只执行单次向量检索并拼接原文摘录，没有 ChatModel、问题规划、检索重试或生成后引用校验。公司模型网关已验证兼容 `/v1/chat/completions`，需要在不破坏 ACL 和拒答边界的前提下增加 Agentic RAG。

## 范围

- 包含：OpenAI 兼容 ChatModel 客户端；结构化问题规划；最多两轮 ACL 前置检索；有界上下文；基于证据生成；服务端引用白名单校验；默认关闭和抽取式安全降级。
- 不包含：开放式 ReAct 循环；写操作工具；互联网/钉钉/文件系统工具；多轮会话记忆；BM25、Reranker；生产默认启用。

## 验收标准

- [x] 正常行为：Agent 可生成有限检索计划，合并授权证据并输出带有效引用的回答。
- [x] 身份/ACL/隐私行为：主体由服务端注入；无权文档不进入计划参数、候选、上下文、答案、引用或日志；文档指令不触发工具。
- [x] 失败行为和 payload 上限：模型超时、非法 JSON、无证据、引用越界和超限均安全降级；查询数、轮数、候选数、上下文和输出有上限。

## 约束

- 相关产品不变量：回答必须有授权来源；依据不足拒答；模型不能决定身份或 ACL。
- 相关架构边界：Controller 仍只依赖 Service 接口；Agent 只能使用只读授权检索能力。
- 需要保留的无关工作区修改：任务开始时工作区干净，基线提交为 `80a5d3f`。

## 验证

- 单元/契约测试：Chat API 契约、规划 JSON、ACL 前置、Prompt Injection、引用越界、超时/非法输出降级和预算。
- RAG 离线评测：现有端到端与检索固定集保持通过，新增 Agent 虚构契约测试。
- 手工/集成证据：本地 Docker 使用公司网关和虚构问题完成一次 Agentic 回答，普通输出不打印问题或上下文。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`

## 交付

- 决策：采用代码状态机而非开放式 ReAct；只提供 `AuthorizedKnowledgeSearch`，主体与空间由服务端注入。公司网关中 `qwen-plus + response_format=json_object` 的结构化规划/回答延迟和契约最稳定，因此作为默认对话模型；`qwen3.5-*` 本次未满足结构化输出或时延要求。
- 数据读取/写入与日志：只读取当前主体授权片段；上下文按字符和保守 token 预算裁剪，不持久化模型上下文；普通日志只记录稳定降级码和预定义原因，不记录问题、计划、证据、回答或模型原始响应。
- 验证：统一 Harness、检索固定集和端到端固定集通过；52 个 Spring 测试与 10 个 Vue 测试覆盖规划、模型契约、无授权前置拒绝、两轮上限、Prompt Injection、上下文裁剪、引用越界和降级。真实本地 JVM 冒烟中，未知问题以 `AGENTIC_RAG/insufficient` 在约 3.3 秒拒答；资料内问题以 `AGENTIC_RAG/answered` 在约 5.3 秒返回 3 个授权引用。
- 剩余风险：当前仅验证引用 ID 属于 evidence，尚未做声明级蕴含校验；固定集仍小；Docker Desktop Engine 在交付时未运行，Compose 配置已验证但本次镜像重建需待 Engine 启动后复验。
- 延后工作：混合检索、Reranker、对话记忆、流式输出、声明级 LLM 复核、真实脱敏 Golden Set 和生产灰度。
