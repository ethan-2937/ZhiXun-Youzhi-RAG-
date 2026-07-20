# Harness 使用指南

## 日常开发

1. 从 `docs/tasks/TEMPLATE.md` 创建任务文件并写明可观察结果。
2. 开发过程中运行快速检查：`python scripts/check_harness.py`。
3. 修改 RAG 时验证数据集：`python scripts/rag_eval.py validate`。
4. 获得系统预测 JSONL 后运行：

   ```powershell
   python scripts/rag_eval.py score --predictions <预测文件>
   ```

5. 交付前运行统一验证：

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
   ```

## 预测文件协议

一行一个 JSON，`caseId` 必须覆盖数据集中的每个用例且不得重复：

```json
{"caseId":"travel-policy-001","status":"answered","answer":"虚构回答","citations":[{"documentId":"doc-travel-public","chunkId":"chunk-001"}],"latencyMs":850}
```

`status` 只能是 `answered`、`insufficient` 或 `refused`。离线评分不会调用模型或网络。

## 扩展规则

- 新增重复出现的工程失误：加入 `quality-baseline.json` 和 `check_harness.py` 的通用规则。
- 新增业务不变量：先加测试，再加最小静态检查；不要用正则假装证明复杂业务正确。
- 新知识域：增加脱敏评测用例和对应来源，不修改既有预期来迎合当前模型。
- 接入 Spring/Vue 后，`verify.ps1` 会自动发现标准目录并运行 Maven/Vitest/build。

## 禁止事项

- 不把真实员工问题、真实文档片段、Token 或生产预测提交为夹具。
- 不因失败而降低 ACL 阈值、移除禁止来源或扩大复杂度预算。
- 不让默认 Harness 访问钉钉、真实模型或真实基础设施。
