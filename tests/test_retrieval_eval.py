from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from rag_eval import EvaluationError, load_jsonl  # noqa: E402
from retrieval_eval import (  # noqa: E402
    compare_reports,
    config,
    evaluate_thresholds,
    score,
    validate_dataset,
    validate_predictions,
)


class RetrievalEvaluationTests(unittest.TestCase):
    def setUp(self) -> None:
        dataset, predictions, self.default_config, self.thresholds = config()
        self.cases = validate_dataset(load_jsonl(dataset))
        self.predictions = validate_predictions(load_jsonl(predictions))

    def test_sample_predictions_pass_all_retrieval_thresholds(self) -> None:
        report = score(
            self.cases,
            self.predictions,
            self.default_config["topK"],
            self.default_config["minScore"],
        )

        self.assertEqual(1.0, report["requiredDocumentRecallAtK"])
        self.assertEqual(1.0, report["mrrAtK"])
        self.assertEqual(0.0, report["forbiddenCandidateLeakRate"])
        self.assertEqual((), evaluate_thresholds(report, self.thresholds))

    def test_forbidden_candidate_is_a_hard_failure_even_below_threshold(self) -> None:
        predictions = [copy.deepcopy(item) for item in self.predictions]
        predictions[1]["candidates"].append({
            "rank": 2,
            "documentId": "doc-test-salary-private",
            "chunkId": "doc-test-salary-private#0",
            "score": 0.1,
        })
        report = score(self.cases, validate_predictions(predictions), 4, 0.45)
        failures = evaluate_thresholds(report, self.thresholds)

        self.assertGreater(report["forbiddenCandidateLeakRate"], 0)
        self.assertIn("RETRIEVAL_THRESHOLD_MAX_FAILED:forbiddenCandidateLeakRate", failures)

    def test_threshold_sweep_exposes_false_answers_and_false_refusals(self) -> None:
        low_threshold = score(self.cases, self.predictions, 4, 0.15)
        high_threshold = score(self.cases, self.predictions, 4, 0.8)

        self.assertGreater(low_threshold["failureCounts"]["false_answer"], 0)
        self.assertGreater(high_threshold["failureCounts"]["false_refusal"], 0)

    def test_duplicate_documents_are_measured(self) -> None:
        predictions = [copy.deepcopy(item) for item in self.predictions]
        predictions[0]["candidates"].insert(1, {
            "rank": 2,
            "documentId": "doc-test-travel-public",
            "chunkId": "doc-test-travel-public#1",
            "score": 0.7,
        })
        predictions[0]["candidates"][2]["rank"] = 3

        report = score(self.cases, validate_predictions(predictions), 4, 0.45)

        self.assertGreater(report["duplicateDocumentRate"], 0)

    def test_compare_marks_recall_regression(self) -> None:
        candidate_predictions = [copy.deepcopy(item) for item in self.predictions]
        candidate_predictions[0]["candidates"][0]["score"] = 0.44
        baseline = score(self.cases, self.predictions, 4, 0.45)
        candidate = score(self.cases, validate_predictions(candidate_predictions), 4, 0.45)

        comparison = compare_reports(baseline, candidate)

        self.assertIn("requiredDocumentRecallAtK", comparison["regressions"])
        self.assertLess(comparison["deltas"]["requiredDocumentRecallAtK"], 0)

    def test_candidate_ranks_must_be_contiguous(self) -> None:
        predictions = [copy.deepcopy(item) for item in self.predictions]
        predictions[0]["candidates"][0]["rank"] = 2

        with self.assertRaisesRegex(EvaluationError, "RETRIEVAL_CANDIDATE_RANK_INVALID"):
            validate_predictions(predictions)

    def test_real_data_shape_is_not_allowed_in_committed_fixture_contract(self) -> None:
        cases = [copy.deepcopy(item) for item in self.cases]
        cases[0]["principal"]["userId"] = "real-user-001"

        with self.assertRaisesRegex(EvaluationError, "RETRIEVAL_FIXTURE_USER_REQUIRED"):
            validate_dataset(cases)

        self.assertEqual("real-user-001", validate_dataset(cases, fixture_only=False)[0]["principal"]["userId"])


if __name__ == "__main__":
    unittest.main()
