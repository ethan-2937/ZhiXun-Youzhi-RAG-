from __future__ import annotations

import copy
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from rag_eval import (  # noqa: E402
    EvaluationError,
    evaluate_thresholds,
    load_evaluation_config,
    load_jsonl,
    score,
    validate_dataset,
    validate_predictions,
)


class RagEvaluationHarnessTests(unittest.TestCase):
    def setUp(self) -> None:
        dataset, predictions, self.thresholds = load_evaluation_config()
        self.cases = validate_dataset(load_jsonl(dataset))
        self.predictions = validate_predictions(load_jsonl(predictions))

    def test_sample_dataset_and_predictions_pass_all_thresholds(self) -> None:
        report = score(self.cases, self.predictions)

        self.assertEqual(1.0, report["caseCoverage"])
        self.assertEqual(1.0, report["answerabilityAccuracy"])
        self.assertEqual(0.0, report["forbiddenDocumentLeakRate"])
        self.assertEqual((), evaluate_thresholds(report, self.thresholds))

    def test_forbidden_document_and_term_are_hard_failures(self) -> None:
        predictions = [copy.deepcopy(item) for item in self.predictions]
        predictions[0]["citations"].append(
            {"documentId": "doc-test-finance-private", "chunkId": "chunk-private-001"}
        )
        predictions[0]["answer"] += " 内部财务密钥"
        report = score(self.cases, validate_predictions(predictions))
        failures = evaluate_thresholds(report, self.thresholds)

        self.assertGreater(report["forbiddenDocumentLeakRate"], 0)
        self.assertGreater(report["forbiddenTermLeakRate"], 0)
        self.assertIn("THRESHOLD_MAX_FAILED:forbiddenDocumentLeakRate", failures)
        self.assertIn("THRESHOLD_MAX_FAILED:forbiddenTermLeakRate", failures)

    def test_unanswerable_case_cannot_return_citations(self) -> None:
        prediction = copy.deepcopy(self.predictions[1])
        prediction["citations"] = [{"documentId": "doc-test-salary-private", "chunkId": "private-001"}]

        with self.assertRaisesRegex(EvaluationError, "PREDICTION_REFUSAL_WITH_CITATIONS"):
            validate_predictions([prediction])

    def test_dataset_rejects_conflicting_acl_expectations(self) -> None:
        case = copy.deepcopy(self.cases[0])
        case["expected"]["forbiddenDocumentIds"].append("doc-test-travel-public")

        with self.assertRaisesRegex(EvaluationError, "DATASET_DOCUMENT_EXPECTATION_CONFLICT"):
            validate_dataset([case])

    def test_missing_case_is_visible_and_fails_coverage(self) -> None:
        report = score(self.cases, self.predictions[:-1])
        failures = evaluate_thresholds(report, self.thresholds)

        self.assertEqual(["security-injection-001"], report["missingCaseIds"])
        self.assertIn("PREDICTION_CASE_COVERAGE_INVALID", failures)

    def test_jsonl_payload_limit_is_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "oversized.jsonl"
            path.write_bytes(b'{' + b'"value":"' + b'x' * (65 * 1024) + b'"}')

            with self.assertRaisesRegex(EvaluationError, "JSONL_LINE_TOO_LARGE"):
                load_jsonl(path)


if __name__ == "__main__":
    unittest.main()
