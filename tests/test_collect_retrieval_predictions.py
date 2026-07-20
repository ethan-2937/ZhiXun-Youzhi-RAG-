from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.collect_retrieval_predictions import (
    CollectionError,
    OUTPUT_ROOT,
    collect_predictions,
    to_prediction,
    write_predictions,
)


class FakeSession:
    def __init__(self, response):
        self.response = response
        self.calls = []

    def request(self, method, path, body=None, csrf_token=""):
        self.calls.append((method, path, body, csrf_token))
        return self.response


class RetrievalPredictionCollectionTests(unittest.TestCase):
    def case(self, user_id="test-user-demo-001"):
        return {
            "caseId": "case-test-001",
            "question": "虚构测试问题？",
            "principal": {"userId": user_id},
            "scope": {"spaceId": "space-test-public"},
        }

    def response(self):
        return {
            "mode": "REAL_EMBEDDING_RETRIEVAL",
            "hasAuthorizedCandidate": True,
            "candidates": [
                {"rank": 1, "documentId": "doc-test-public", "chunkId": "doc-test-public#0", "score": 0.8}
            ],
        }

    def test_collects_only_contract_fields(self):
        session = FakeSession(self.response())

        predictions = collect_predictions(
            [self.case()], session, "test-user-demo-001", "csrf-test", 4
        )

        self.assertEqual("case-test-001", predictions[0]["caseId"])
        self.assertEqual({"caseId", "latencyMs", "hasAuthorizedCandidate", "candidates"}, set(predictions[0]))
        self.assertEqual("虚构测试问题？", session.calls[0][2]["question"])

    def test_rejects_dataset_for_another_principal_before_http(self):
        session = FakeSession(self.response())

        with self.assertRaisesRegex(CollectionError, "DATASET_PRINCIPAL_MISMATCH"):
            collect_predictions([self.case("test-user-other")], session, "test-user-demo-001", "csrf-test", 4)

        self.assertEqual([], session.calls)

    def test_rejects_candidate_with_content_field(self):
        response = self.response()
        response["candidates"][0]["content"] = "不应出现在诊断响应中的正文"

        with self.assertRaisesRegex(CollectionError, "DIAGNOSTICS_CANDIDATE_INVALID"):
            to_prediction("case-test-001", response, 1, 4)

    def test_rejects_non_contiguous_ranks(self):
        response = self.response()
        response["candidates"][0]["rank"] = 2

        with self.assertRaisesRegex(CollectionError, "DIAGNOSTICS_CANDIDATE_INVALID"):
            to_prediction("case-test-001", response, 1, 4)

    def test_output_must_stay_in_ignored_runs_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "predictions.jsonl"
            with self.assertRaisesRegex(CollectionError, "OUTPUT_MUST_BE_IN_IGNORED_RUNS_DIRECTORY"):
                write_predictions(path, [])

        self.assertTrue(OUTPUT_ROOT.is_absolute())


if __name__ == "__main__":
    unittest.main()
