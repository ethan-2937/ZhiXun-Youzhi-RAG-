# 检索评测 v2

该目录只保存完全虚构的检索评测契约和冒烟数据。真实问题、稳定用户 ID、候选文档 ID 和运行报告必须写入被忽略的 `harness/retrieval-evaluation/runs/` 或其他受控存储。

预测保存阈值应用前的授权候选，最多 50 条，只包含排名、文档 ID、分块 ID、余弦分数和延迟，不包含问题之外的原文、标题、摘录或模型上下文。

```powershell
python scripts/retrieval_eval.py validate
python scripts/retrieval_eval.py score
python scripts/retrieval_eval.py sweep --top-k 2,4,8 --min-score 0.30,0.45,0.55
python scripts/retrieval_eval.py compare --baseline <baseline.jsonl> --candidate <candidate.jsonl>
```

本地候选采集方法、指标含义和参数调优顺序见 `docs/RETRIEVAL_TUNING.md`。
