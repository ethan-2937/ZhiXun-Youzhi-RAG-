from __future__ import annotations

import argparse
import json
import math
import re
import sys
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "harness" / "quality-baseline.json"
MAX_FILE_BYTES = 2 * 1024 * 1024
MAX_LINE_BYTES = 64 * 1024
CASE_ID = re.compile(r"^[a-z0-9][a-z0-9-]{2,63}$")
FIXTURE_USER_ID = re.compile(r"^test-user-[a-z0-9-]+$")
FIXTURE_DEPARTMENT_ID = re.compile(r"^DEPT_TEST_[A-Z0-9_]+$")
FIXTURE_ROLE = re.compile(r"^ROLE_TEST_[A-Z0-9_]+$")
FIXTURE_SPACE_ID = re.compile(r"^space-test-[a-z0-9-]+$")
FIXTURE_DOCUMENT_ID = re.compile(r"^doc-test-[a-z0-9-]+$")
PREDICTION_STATUSES = {"answered", "insufficient", "refused"}


class EvaluationError(ValueError):
    pass


def _fail(code: str) -> None:
    raise EvaluationError(code)


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    try:
        if path.stat().st_size > MAX_FILE_BYTES:
            _fail("JSONL_FILE_TOO_LARGE")
        lines = path.read_bytes().splitlines()
    except OSError as exc:
        raise EvaluationError("JSONL_READ_FAILED") from exc
    values: list[dict[str, Any]] = []
    for raw in lines:
        if not raw.strip():
            continue
        if len(raw) > MAX_LINE_BYTES:
            _fail("JSONL_LINE_TOO_LARGE")
        try:
            value = json.loads(raw.decode("utf-8"))
        except (UnicodeError, json.JSONDecodeError) as exc:
            raise EvaluationError("JSONL_INVALID") from exc
        if not isinstance(value, dict):
            _fail("JSONL_OBJECT_REQUIRED")
        values.append(value)
    if not values:
        _fail("JSONL_EMPTY")
    return values


def _string_list(value: Any, code: str, limit: int = 30) -> list[str]:
    if not isinstance(value, list) or len(value) > limit or any(not isinstance(item, str) or not item for item in value):
        _fail(code)
    if len(set(value)) != len(value):
        _fail(code)
    return value


def validate_dataset(cases: Iterable[dict[str, Any]]) -> tuple[dict[str, Any], ...]:
    validated: list[dict[str, Any]] = []
    seen: set[str] = set()
    expected_keys = {"caseId", "question", "principal", "scope", "expected"}
    for case in cases:
        if set(case) != expected_keys:
            _fail("DATASET_FIELDS_INVALID")
        case_id = case.get("caseId")
        if not isinstance(case_id, str) or not CASE_ID.fullmatch(case_id) or case_id in seen:
            _fail("DATASET_CASE_ID_INVALID")
        seen.add(case_id)
        question = case.get("question")
        if not isinstance(question, str) or not 2 <= len(question) <= 1000:
            _fail("DATASET_QUESTION_INVALID")

        principal = case.get("principal")
        if not isinstance(principal, dict) or set(principal) != {"userId", "departmentIds", "roleCodes"}:
            _fail("DATASET_PRINCIPAL_INVALID")
        if not isinstance(principal["userId"], str) or not FIXTURE_USER_ID.fullmatch(principal["userId"]):
            _fail("DATASET_FIXTURE_ID_REQUIRED")
        departments = _string_list(principal["departmentIds"], "DATASET_DEPARTMENTS_INVALID", 20)
        roles = _string_list(principal["roleCodes"], "DATASET_ROLES_INVALID", 20)
        if any(not FIXTURE_DEPARTMENT_ID.fullmatch(item) for item in departments):
            _fail("DATASET_FIXTURE_DEPARTMENT_REQUIRED")
        if any(not FIXTURE_ROLE.fullmatch(item) for item in roles):
            _fail("DATASET_FIXTURE_ROLE_REQUIRED")

        scope = case.get("scope")
        if not isinstance(scope, dict) or set(scope) != {"spaceIds"}:
            _fail("DATASET_SCOPE_INVALID")
        spaces = _string_list(scope["spaceIds"], "DATASET_SPACES_INVALID", 10)
        if not spaces or any(not FIXTURE_SPACE_ID.fullmatch(item) for item in spaces):
            _fail("DATASET_FIXTURE_SPACE_REQUIRED")

        expected = case.get("expected")
        expected_fields = {
            "answerable", "requiredDocumentIds", "forbiddenDocumentIds", "requiredTerms", "forbiddenTerms"
        }
        if not isinstance(expected, dict) or set(expected) != expected_fields or not isinstance(expected["answerable"], bool):
            _fail("DATASET_EXPECTED_INVALID")
        required_docs = _string_list(expected["requiredDocumentIds"], "DATASET_REQUIRED_DOCS_INVALID", 20)
        forbidden_docs = _string_list(expected["forbiddenDocumentIds"], "DATASET_FORBIDDEN_DOCS_INVALID", 20)
        required_terms = _string_list(expected["requiredTerms"], "DATASET_REQUIRED_TERMS_INVALID")
        _string_list(expected["forbiddenTerms"], "DATASET_FORBIDDEN_TERMS_INVALID")
        if any(not FIXTURE_DOCUMENT_ID.fullmatch(item) for item in required_docs + forbidden_docs):
            _fail("DATASET_FIXTURE_DOCUMENT_REQUIRED")
        if set(required_docs) & set(forbidden_docs):
            _fail("DATASET_DOCUMENT_EXPECTATION_CONFLICT")
        if expected["answerable"] and (not required_docs or not required_terms):
            _fail("DATASET_ANSWERABLE_EVIDENCE_REQUIRED")
        if not expected["answerable"] and required_docs:
            _fail("DATASET_UNANSWERABLE_REQUIRED_DOCS_FORBIDDEN")
        validated.append(case)
    return tuple(validated)


def validate_predictions(predictions: Iterable[dict[str, Any]]) -> tuple[dict[str, Any], ...]:
    validated: list[dict[str, Any]] = []
    seen: set[str] = set()
    expected_keys = {"caseId", "status", "answer", "citations", "latencyMs"}
    for prediction in predictions:
        if set(prediction) != expected_keys:
            _fail("PREDICTION_FIELDS_INVALID")
        case_id = prediction.get("caseId")
        if not isinstance(case_id, str) or not CASE_ID.fullmatch(case_id) or case_id in seen:
            _fail("PREDICTION_CASE_ID_INVALID")
        seen.add(case_id)
        if prediction.get("status") not in PREDICTION_STATUSES:
            _fail("PREDICTION_STATUS_INVALID")
        answer = prediction.get("answer")
        if not isinstance(answer, str) or len(answer) > 12000:
            _fail("PREDICTION_ANSWER_INVALID")
        latency = prediction.get("latencyMs")
        if not isinstance(latency, int) or isinstance(latency, bool) or not 0 <= latency <= 300000:
            _fail("PREDICTION_LATENCY_INVALID")
        citations = prediction.get("citations")
        if not isinstance(citations, list) or len(citations) > 30:
            _fail("PREDICTION_CITATIONS_INVALID")
        citation_keys: set[tuple[str, str]] = set()
        for citation in citations:
            if not isinstance(citation, dict) or set(citation) != {"documentId", "chunkId"}:
                _fail("PREDICTION_CITATION_INVALID")
            document_id, chunk_id = citation["documentId"], citation["chunkId"]
            if not isinstance(document_id, str) or not FIXTURE_DOCUMENT_ID.fullmatch(document_id):
                _fail("PREDICTION_DOCUMENT_ID_INVALID")
            if not isinstance(chunk_id, str) or not 1 <= len(chunk_id) <= 128:
                _fail("PREDICTION_CHUNK_ID_INVALID")
            if (document_id, chunk_id) in citation_keys:
                _fail("PREDICTION_CITATION_DUPLICATE")
            citation_keys.add((document_id, chunk_id))
        if prediction["status"] != "answered" and citations:
            _fail("PREDICTION_REFUSAL_WITH_CITATIONS")
        validated.append(prediction)
    return tuple(validated)


def _ratio(numerator: int, denominator: int) -> float:
    return round(numerator / denominator, 6) if denominator else 1.0


def _p95(values: list[int]) -> int:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def score(cases: Iterable[dict[str, Any]], predictions: Iterable[dict[str, Any]]) -> dict[str, Any]:
    case_list = list(cases)
    prediction_list = list(predictions)
    by_case = {item["caseId"]: item for item in prediction_list}
    case_ids = {item["caseId"] for item in case_list}
    coverage = len(case_ids & set(by_case))
    answerability_correct = required_found = required_total = 0
    forbidden_doc_cases = required_terms_found = required_terms_total = forbidden_terms_found = forbidden_terms_total = 0
    latencies: list[int] = []
    for case in case_list:
        prediction = by_case.get(case["caseId"])
        if prediction is None:
            continue
        expected = case["expected"]
        answered = prediction["status"] == "answered"
        answerability_correct += answered == expected["answerable"]
        cited_docs = {item["documentId"] for item in prediction["citations"]}
        required_docs = set(expected["requiredDocumentIds"])
        required_found += len(required_docs & cited_docs)
        required_total += len(required_docs)
        forbidden_doc_cases += bool(set(expected["forbiddenDocumentIds"]) & cited_docs)
        answer_folded = prediction["answer"].casefold()
        for term in expected["requiredTerms"]:
            required_terms_total += 1
            required_terms_found += term.casefold() in answer_folded
        for term in expected["forbiddenTerms"]:
            forbidden_terms_total += 1
            forbidden_terms_found += term.casefold() in answer_folded
        latencies.append(prediction["latencyMs"])
    return {
        "caseCount": len(case_list),
        "predictionCount": len(prediction_list),
        "caseCoverage": _ratio(coverage, len(case_list)),
        "answerabilityAccuracy": _ratio(answerability_correct, len(case_list)),
        "requiredDocumentRecall": _ratio(required_found, required_total),
        "forbiddenDocumentLeakRate": _ratio(forbidden_doc_cases, len(case_list)),
        "requiredTermCoverage": _ratio(required_terms_found, required_terms_total),
        "forbiddenTermLeakRate": _ratio(forbidden_terms_found, forbidden_terms_total),
        "p95LatencyMs": _p95(latencies) if latencies else 0,
        "unknownCaseIds": sorted(set(by_case) - case_ids),
        "missingCaseIds": sorted(case_ids - set(by_case)),
    }


def evaluate_thresholds(report: dict[str, Any], thresholds: dict[str, Any]) -> tuple[str, ...]:
    failures: list[str] = []
    minimum_metrics = ("caseCoverage", "answerabilityAccuracy", "requiredDocumentRecall", "requiredTermCoverage")
    maximum_metrics = ("forbiddenDocumentLeakRate", "forbiddenTermLeakRate")
    for metric in minimum_metrics:
        if float(report[metric]) < float(thresholds[metric]):
            failures.append(f"THRESHOLD_MIN_FAILED:{metric}")
    for metric in maximum_metrics:
        if float(report[metric]) > float(thresholds[metric]):
            failures.append(f"THRESHOLD_MAX_FAILED:{metric}")
    if int(report["p95LatencyMs"]) > int(thresholds["maxP95LatencyMs"]):
        failures.append("THRESHOLD_MAX_FAILED:p95LatencyMs")
    if report["unknownCaseIds"] or report["missingCaseIds"]:
        failures.append("PREDICTION_CASE_COVERAGE_INVALID")
    return tuple(failures)


def load_evaluation_config() -> tuple[Path, Path, dict[str, Any]]:
    config = json.loads(BASELINE.read_text(encoding="utf-8"))["ragEvaluation"]
    return ROOT / config["dataset"], ROOT / config["samplePredictions"], config["thresholds"]


def main(argv: list[str] | None = None) -> int:
    dataset_default, predictions_default, thresholds = load_evaluation_config()
    parser = argparse.ArgumentParser(description="Validate and score deterministic RAG evaluation JSONL.")
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("--dataset", type=Path, default=dataset_default)
    score_parser = subparsers.add_parser("score")
    score_parser.add_argument("--dataset", type=Path, default=dataset_default)
    score_parser.add_argument("--predictions", type=Path, default=predictions_default)
    args = parser.parse_args(argv)
    try:
        cases = validate_dataset(load_jsonl(args.dataset))
        if args.command == "validate":
            print(f"RAG dataset validation passed ({len(cases)} cases).")
            return 0
        predictions = validate_predictions(load_jsonl(args.predictions))
        report = score(cases, predictions)
        failures = evaluate_thresholds(report, thresholds)
    except (EvaluationError, OSError, KeyError, json.JSONDecodeError) as exc:
        print(f"RAG evaluation failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if failures:
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1
    print("RAG evaluation thresholds passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
