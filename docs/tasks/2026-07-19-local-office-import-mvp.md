# Task: 本地 Office 导出可受控导入真实 RAG

## 问题

钉钉知识空间网页登录后无法稳定加载。用户已将两个 PPTX 和一个 ZIP 导出到未跟踪的 `data/knowledge/raw/`，需要把这些原始文件转换为当前 MVP 可读取的受控 JSONL 知识源，并保持内容、ACL 和 payload 边界。

## 范围

- 包含：只读盘点原始导出；受限解压；PPTX 和 ZIP 内支持格式解析；文本清洗与分块前文档生成；显式 ACL 映射；生成未跟踪的 `data/knowledge/documents.jsonl`；本地 Docker/RAG 验证。
- 不包含：钉钉知识空间在线同步；OCR；宏或脚本执行；复杂表格语义还原；自动推断生产 ACL；生成式 ChatModel。

## 验收标准

- [x] 正常行为：支持的本地导出能生成稳定、可引用的 JSONL 文档，并可由现有 RAG 加载。
- [x] 身份/ACL/隐私行为：每份文档必须有显式稳定用户 ID；原文、解析正文和生成文件不进入 Git 或普通日志。
- [x] 失败行为和 payload 上限：拒绝路径穿越、压缩炸弹、超大文件、超量文件、缺失 ACL、损坏或不支持的文件。

## 约束

- 相关产品不变量：ACL 在检索前生效；回答给出授权来源；依据不足时拒答；上传、解析和模型请求有明确上限。
- 相关架构边界：导入逻辑进入 `ingestion`/知识源边界；Controller 不直接访问解析器或存储。
- 需要保留的无关工作区修改：保留当前前后端、Harness、钉钉 TODO 和本地运行配置。

## 验证

- 单元/契约测试：新增 6 个导入测试；统一 Harness 共 16 个 Python 测试、25 个 Spring 测试和 8 个 Vue 测试通过。
- RAG 离线评测：3 个完全虚构固定用例全部通过，越权文档和禁止术语泄漏率均为 0。
- 手工/集成证据：3 个来源生成 29 份文档、39,205 字节 JSONL；Docker 健康模式为 `REAL_EMBEDDING_RETRIEVAL`；演示用户可见 29 份文档、1 个空间、3 个节点；真实问题返回 3 条引用。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`

## 交付

- 决策：使用标准库实现离线导入器；PPTX 按页、Markdown 按标题生成稳定引用；ZIP 只读取受支持文档，不执行或索引脚本/HTML/JSON；当前 29 份文档显式绑定 `test-user-demo-001`。公司 Embedding 网关使用 1 条批次、500 字符输入、300/40 分块兼容配置。
- 数据读取/写入与日志：只读 `data/knowledge/raw/`；原子写入未跟踪的 `data/knowledge/documents.jsonl`，旧文件备份到未跟踪的 `data/knowledge/backups/`；命令输出只包含来源数、文档数、跳过数、字节数和稳定错误码，不包含原文。
- 剩余风险：导出包不包含可验证的钉钉原始 ACL，当前仅适合本地演示；一页纯图片幻灯片在无 OCR 时跳过；向量仍在进程内且每次重启重建；网关长度限制基于当前探测，需要服务端契约确认。
- 延后工作：钉钉知识 API 增量同步、生产 ACL 同步、OCR、复杂表格解析和 pgvector 持久索引。
