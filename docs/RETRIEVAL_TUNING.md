# RAG 检索调优

## 目标

先把“正确资料能否被找到”和“无权资料是否完全不可见”测清楚，再考虑生成模型。当前 v2 只评估检索，不调用 ChatModel，也不会自动修改运行参数。

## 数据集

每个用例必须声明：

- `principal.userId`：执行检索的稳定主体 ID。
- `scope.spaceId`：限定空间；跨空间问题可为 `null`。
- `expected.answerable`：在当前授权范围内是否应该有答案。
- `requiredDocumentIds`：回答必须召回的资料。
- `forbiddenDocumentIds`：该主体绝不能看到的资料。

提交到 Git 的样例只能使用 `test-user-*`、`doc-test-*` 和完全虚构的问题。真实或内部问题、员工 ID、文档 ID、预测和报告只保存在受控环境，不提交仓库。

## 指标

| 指标 | 用途 | 调优方向 |
|---|---|---|
| `answerabilityAccuracy` | 可回答问题是否有候选、不可回答问题是否拒答 | 越高越好 |
| `requiredDocumentRecallAtK` | 必需资料在阈值和 TopK 后的召回比例 | 越高越好 |
| `hitRateAtK` | 可回答用例至少命中一份必需资料的比例 | 越高越好 |
| `mrrAtK` | 第一份正确资料的排名质量 | 越高越好 |
| `ndcgAtK` | 多份必需资料的整体排序质量 | 越高越好 |
| `forbiddenCandidateLeakRate` | ACL 过滤后仍出现禁止资料的用例比例 | 必须为 0 |
| `duplicateDocumentRate` | 选中候选中同一文档重复占位比例 | 越低越好 |
| `p95LatencyMs` | 95% 用例的检索延迟上界 | 在质量达标后越低越好 |

`forbiddenCandidateLeakRate` 检查阈值应用前的全部候选。降低阈值不能掩盖 ACL 泄漏。

## 本地采集

诊断路由只有在 `RAG_ENABLED=true` 且 `RAG_DIAGNOSTICS_ENABLED=true` 时才可用。它只适用于本机受控调优，生产保持关闭。

1. 为一次评测准备不超过 500 条的本地 JSONL；其中所有 `principal.userId` 必须与当前演示会话用户一致。
2. 在未跟踪的 `.env` 中临时启用诊断，重建本地容器。
3. 采集候选；输出被强制写入忽略目录，并采用原子替换。

```powershell
docker compose up --build -d
python scripts/collect_retrieval_predictions.py `
  --dataset harness/retrieval-evaluation/runs/dataset.local.jsonl `
  --output harness/retrieval-evaluation/runs/candidate.jsonl `
  --limit 50
```

提交样例中包含多个虚构主体，不能直接由单一演示会话整批采集。实际采集文件应按主体拆分；采集器会在首次主体不一致时、发送问题前失败。

## 评分与扫描

```powershell
python scripts/retrieval_eval.py score `
  --dataset harness/retrieval-evaluation/dataset.sample.jsonl `
  --predictions harness/retrieval-evaluation/predictions.sample.jsonl `
  --top-k 4 --min-score 0.45

python scripts/retrieval_eval.py sweep `
  --dataset harness/retrieval-evaluation/dataset.sample.jsonl `
  --predictions harness/retrieval-evaluation/predictions.sample.jsonl `
  --top-k 2,4,8 --min-score 0.30,0.45,0.55

python scripts/retrieval_eval.py compare `
  --dataset harness/retrieval-evaluation/runs/dataset.local.jsonl `
  --baseline harness/retrieval-evaluation/runs/baseline.jsonl `
  --candidate harness/retrieval-evaluation/runs/candidate.jsonl `
  --private-identifiers
```

`--private-identifiers` 只放宽本地受控文件中的用户和文档 ID 形状，不放宽字段、长度、候选条数、排名或分数检查；默认提交夹具仍强制使用虚构 ID。

## 推荐调优顺序

1. **先查 ACL**：任何禁止候选泄漏先停下修权限过滤，不调阈值掩盖。
2. **再查数据与切分**：确认资料版本、标题/章节元数据和边界完整；比较 300/500/800 字符及约 10% 重叠。
3. **再选 TopK**：优先提高 Recall@K 和 hit rate，同时观察重复率、上下文预算与延迟；MVP 通常从 4、8 比较。
4. **再选阈值**：用不可回答用例抑制 false answer，用可回答用例约束 false refusal；不要只取平均分最高的一组。
5. **最后加能力**：纯向量仍无法处理缩写、精确编号或专名时，再以同一基线评估 BM25、融合检索和 Reranker。

候选集、Embedding 模型、分块策略或权限逻辑任一变化，都应生成新预测并用 `compare` 与固定基线比较。未经人工审核，不把扫描结果直接写入生产配置。
