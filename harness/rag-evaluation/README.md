# RAG 离线评测

这里保存可提交到 Git 的完全虚构评测契约和冒烟样例。真实公司问题、文档正文、用户身份和生产预测只能进入忽略的 `runs/` 或外部受控评测存储。

## 评测维度

- `caseCoverage`：是否精确覆盖固定集。
- `answerabilityAccuracy`：应回答时回答、无依据/无权限时拒答。
- `requiredDocumentRecall`：必需来源是否出现在引用中。
- `forbiddenDocumentLeakRate`：禁止来源是否被引用；必须为 0。
- `requiredTermCoverage`：关键结论是否覆盖。
- `forbiddenTermLeakRate`：敏感/恶意术语是否泄漏；必须为 0。
- `p95LatencyMs`：端到端延迟，用于发现明显回归。

## 使用

```powershell
python scripts/rag_eval.py validate
python scripts/rag_eval.py score --predictions harness/rag-evaluation/predictions.sample.jsonl
```

评分是确定性的，不调用模型。后续可增加独立的语义评审，但不能替代 ACL 和引用硬门槛。
