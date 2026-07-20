from __future__ import annotations

import argparse
import json
import math
import re
import sys
from pathlib import Path
from typing import Any, Iterable

from rag_eval import EvaluationError, load_jsonl


ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "harness" / "quality-baseline.json"
CASE_ID = re.compile(r"^[a-z0-9][a-z0-9-]{2,63}$")
USER_ID = re.compile(r"^test-user-[a-z0-9-]+$")
DOCUMENT_ID = re.compile(r"^doc-test-[a-z0-9-]+$")
MAX_CASES = 10_000
MAX_CANDIDATES = 50


def fail(code: str) -> None:
    raise EvaluationError(code)


def string_list(value: Any, code: str, limit: int = 20) -> list[str]:
    if not isinstance(value, list) or len(value) > limit:
        fail(code)
    if any(not isinstance(item, str) or not item for item in value) or len(set(value)) != len(value):
        fail(code)
    return value


def validate_dataset(
    cases: Iterable[dict[str, Any]], fixture_only: bool = True
) -> tuple[dict[str, Any], ...]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    fields = {"caseId", "question", "principal", "scope", "expected"}
    for case in cases:
        if len(result) >= MAX_CASES or set(case) != fields:
            fail("RETRIEVAL_DATASET_FIELDS_INVALID")
        case_id = case.get("caseId")
        if not isinstance(case_id, str) or not CASE_ID.fullmatch(case_id) or case_id in seen:
            fail("RETRIEVAL_CASE_ID_INVALID")
        seen.add(case_id)
        question = case.get("question")
        if not isinstance(question, str) or not 2 <= len(question) <= 1000:
            fail("RETRIEVAL_QUESTION_INVALID")
        principal = case.get("principal")
        if not isinstance(principal, dict) or set(principal) != {"userId"}:
            fail("RETRIEVAL_PRINCIPAL_INVALID")
        user_id = principal["userId"]
        if (not isinstance(user_id, str) or not 1 <= len(user_id) <= 128
                or fixture_only and not USER_ID.fullmatch(user_id)):
            fail("RETRIEVAL_FIXTURE_USER_REQUIRED")
        scope = case.get("scope")
        if not isinstance(scope, dict) or set(scope) != {"spaceId"}:
            fail("RETRIEVAL_SCOPE_INVALID")
        space_id = scope["spaceId"]
        if space_id is not None and (not isinstance(space_id, str) or not 1 <= len(space_id) <= 64):
            fail("RETRIEVAL_SPACE_INVALID")
        expected = case.get("expected")
        if not isinstance(expected, dict) or set(expected) != {
            "answerable", "requiredDocumentIds", "forbiddenDocumentIds"
        } or not isinstance(expected["answerable"], bool):
            fail("RETRIEVAL_EXPECTED_INVALID")
        required = string_list(expected["requiredDocumentIds"], "RETRIEVAL_REQUIRED_DOCS_INVALID")
        forbidden = string_list(expected["forbiddenDocumentIds"], "RETRIEVAL_FORBIDDEN_DOCS_INVALID")
        if any(len(item) > 128 or fixture_only and not DOCUMENT_ID.fullmatch(item) for item in required + forbidden):
            fail("RETRIEVAL_FIXTURE_DOCUMENT_REQUIRED")
        if set(required) & set(forbidden):
            fail("RETRIEVAL_DOCUMENT_CONFLICT")
        if expected["answerable"] != bool(required):
            fail("RETRIEVAL_ANSWERABILITY_EVIDENCE_INVALID")
        result.append(case)
    if not result:
        fail("RETRIEVAL_DATASET_EMPTY")
    return tuple(result)


def validate_predictions(
    predictions: Iterable[dict[str, Any]], fixture_only: bool = True
) -> tuple[dict[str, Any], ...]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    fields = {"caseId", "latencyMs", "hasAuthorizedCandidate", "candidates"}
    for prediction in predictions:
        if len(result) >= MAX_CASES or set(prediction) != fields:
            fail("RETRIEVAL_PREDICTION_FIELDS_INVALID")
        case_id = prediction.get("caseId")
        if not isinstance(case_id, str) or not CASE_ID.fullmatch(case_id) or case_id in seen:
            fail("RETRIEVAL_PREDICTION_CASE_ID_INVALID")
        seen.add(case_id)
        latency = prediction.get("latencyMs")
        if not isinstance(latency, int) or isinstance(latency, bool) or not 0 <= latency <= 300_000:
            fail("RETRIEVAL_LATENCY_INVALID")
        has_candidate = prediction.get("hasAuthorizedCandidate")
        candidates = prediction.get("candidates")
        if not isinstance(has_candidate, bool) or not isinstance(candidates, list) or len(candidates) > MAX_CANDIDATES:
            fail("RETRIEVAL_CANDIDATES_INVALID")
        if has_candidate != bool(candidates):
            fail("RETRIEVAL_CANDIDATE_STATE_INVALID")
        chunks: set[str] = set()
        previous_score = math.inf
        for index, candidate in enumerate(candidates, 1):
            if not isinstance(candidate, dict) or set(candidate) != {"rank", "documentId", "chunkId", "score"}:
                fail("RETRIEVAL_CANDIDATE_INVALID")
            document_id, chunk_id, score = candidate["documentId"], candidate["chunkId"], candidate["score"]
            document_valid = (isinstance(document_id, str) and 1 <= len(document_id) <= 128
                              and (not fixture_only or bool(DOCUMENT_ID.fullmatch(document_id))))
            if candidate["rank"] != index or not document_valid:
                fail("RETRIEVAL_CANDIDATE_RANK_INVALID")
            if not isinstance(chunk_id, str) or not 1 <= len(chunk_id) <= 128 or chunk_id in chunks:
                fail("RETRIEVAL_CANDIDATE_CHUNK_INVALID")
            if not isinstance(score, (int, float)) or isinstance(score, bool) or not math.isfinite(score) or not -1 <= score <= 1:
                fail("RETRIEVAL_CANDIDATE_SCORE_INVALID")
            if score > previous_score:
                fail("RETRIEVAL_CANDIDATE_ORDER_INVALID")
            chunks.add(chunk_id)
            previous_score = score
        result.append(prediction)
    if not result:
        fail("RETRIEVAL_PREDICTIONS_EMPTY")
    return tuple(result)


def ratio(numerator: int | float, denominator: int | float) -> float:
    return round(numerator / denominator, 6) if denominator else 1.0


def p95(values: list[int]) -> int:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)] if ordered else 0


def ndcg(relevant_positions: list[int], relevant_total: int, top_k: int) -> float:
    dcg = sum(1 / math.log2(position + 1) for position in relevant_positions)
    ideal = sum(1 / math.log2(position + 1) for position in range(1, min(relevant_total, top_k) + 1))
    return dcg / ideal if ideal else 1.0


def score(
    cases: Iterable[dict[str, Any]],
    predictions: Iterable[dict[str, Any]],
    top_k: int,
    min_score: float,
) -> dict[str, Any]:
    case_list, prediction_list = list(cases), list(predictions)
    by_case = {item["caseId"]: item for item in prediction_list}
    case_ids = {item["caseId"] for item in case_list}
    covered = answerable_correct = required_found = required_total = hit_cases = answerable_cases = 0
    forbidden_candidate_cases = forbidden_selected_cases = selected_total = duplicate_total = 0
    reciprocal_sum = ndcg_sum = 0.0
    latencies: list[int] = []
    failures: dict[str, list[str]] = {}
    for case in case_list:
        prediction = by_case.get(case["caseId"])
        codes: list[str] = []
        if prediction is None:
            failures[case["caseId"]] = ["missing_prediction"]
            continue
        covered += 1
        latencies.append(prediction["latencyMs"])
        expected = case["expected"]
        required, forbidden = set(expected["requiredDocumentIds"]), set(expected["forbiddenDocumentIds"])
        candidates = prediction["candidates"]
        selected = [item for item in candidates if item["score"] >= min_score][:top_k]
        selected_docs = [item["documentId"] for item in selected]
        candidate_docs = {item["documentId"] for item in candidates}
        predicted_answerable = bool(selected)
        answerable_correct += predicted_answerable == expected["answerable"]
        if predicted_answerable != expected["answerable"]:
            codes.append("false_answer" if predicted_answerable else "false_refusal")
        required_found += len(required & set(selected_docs))
        required_total += len(required)
        forbidden_candidate_cases += bool(forbidden & candidate_docs)
        forbidden_selected_cases += bool(forbidden & set(selected_docs))
        if forbidden & candidate_docs:
            codes.append("forbidden_candidate")
        selected_total += len(selected_docs)
        duplicate_total += len(selected_docs) - len(set(selected_docs))
        if required:
            answerable_cases += 1
            positions = [index for index, item in enumerate(selected, 1) if item["documentId"] in required]
            if positions:
                hit_cases += 1
                reciprocal_sum += 1 / positions[0]
            else:
                codes.append("required_miss")
            ndcg_sum += ndcg(positions, len(required), top_k)
        if codes:
            failures[case["caseId"]] = codes
    return {
        "config": {"topK": top_k, "minScore": min_score},
        "caseCount": len(case_list),
        "predictionCount": len(prediction_list),
        "caseCoverage": ratio(covered, len(case_list)),
        "answerabilityAccuracy": ratio(answerable_correct, len(case_list)),
        "requiredDocumentRecallAtK": ratio(required_found, required_total),
        "hitRateAtK": ratio(hit_cases, answerable_cases),
        "mrrAtK": round(reciprocal_sum / answerable_cases, 6) if answerable_cases else 1.0,
        "ndcgAtK": round(ndcg_sum / answerable_cases, 6) if answerable_cases else 1.0,
        "forbiddenCandidateLeakRate": ratio(forbidden_candidate_cases, len(case_list)),
        "forbiddenSelectedLeakRate": ratio(forbidden_selected_cases, len(case_list)),
        "duplicateDocumentRate": ratio(duplicate_total, selected_total) if selected_total else 0.0,
        "p95LatencyMs": p95(latencies),
        "missingCaseIds": sorted(case_ids - set(by_case)),
        "unknownCaseIds": sorted(set(by_case) - case_ids),
        "failureCounts": dict(sorted((code, sum(code in items for items in failures.values()))
                                     for code in {value for items in failures.values() for value in items})),
        "failedCases": failures,
    }


def evaluate_thresholds(report: dict[str, Any], thresholds: dict[str, Any]) -> tuple[str, ...]:
    failures: list[str] = []
    for metric in ("caseCoverage", "answerabilityAccuracy", "requiredDocumentRecallAtK", "hitRateAtK", "mrrAtK", "ndcgAtK"):
        if report[metric] < thresholds[metric]:
            failures.append(f"RETRIEVAL_THRESHOLD_MIN_FAILED:{metric}")
    for metric in ("forbiddenCandidateLeakRate", "forbiddenSelectedLeakRate", "duplicateDocumentRate"):
        if report[metric] > thresholds["max" + metric[0].upper() + metric[1:]]:
            failures.append(f"RETRIEVAL_THRESHOLD_MAX_FAILED:{metric}")
    if report["p95LatencyMs"] > thresholds["maxP95LatencyMs"]:
        failures.append("RETRIEVAL_THRESHOLD_MAX_FAILED:p95LatencyMs")
    if report["missingCaseIds"] or report["unknownCaseIds"]:
        failures.append("RETRIEVAL_CASE_COVERAGE_INVALID")
    return tuple(failures)


def config() -> tuple[Path, Path, dict[str, Any], dict[str, Any]]:
    value = json.loads(BASELINE.read_text(encoding="utf-8"))["retrievalEvaluation"]
    return ROOT / value["dataset"], ROOT / value["samplePredictions"], value["config"], value["thresholds"]


def parse_values(value: str, cast: type) -> list[Any]:
    try:
        result = [cast(item.strip()) for item in value.split(",") if item.strip()]
    except ValueError as exc:
        raise EvaluationError("RETRIEVAL_SWEEP_VALUES_INVALID") from exc
    if not result:
        fail("RETRIEVAL_SWEEP_VALUES_INVALID")
    return result


def compare_reports(baseline: dict[str, Any], candidate: dict[str, Any]) -> dict[str, Any]:
    metrics = ("answerabilityAccuracy", "requiredDocumentRecallAtK", "hitRateAtK", "mrrAtK", "ndcgAtK",
               "forbiddenCandidateLeakRate", "forbiddenSelectedLeakRate", "duplicateDocumentRate", "p95LatencyMs")
    deltas = {metric: round(candidate[metric] - baseline[metric], 6) for metric in metrics}
    regressions = [metric for metric in metrics[:5] if candidate[metric] < baseline[metric]]
    regressions += [metric for metric in metrics[5:8] if candidate[metric] > baseline[metric]]
    return {"baseline": baseline, "candidate": candidate, "deltas": deltas, "regressions": regressions}


def main(argv: list[str] | None = None) -> int:
    dataset_default, predictions_default, default_config, thresholds = config()
    parser = argparse.ArgumentParser(description="Score and tune bounded retrieval candidate traces.")
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("validate", "score", "sweep"):
        command = sub.add_parser(name)
        command.add_argument("--dataset", type=Path, default=dataset_default)
        command.add_argument("--private-identifiers", action="store_true")
        if name != "validate":
            command.add_argument("--predictions", type=Path, default=predictions_default)
    sub.choices["score"].add_argument("--top-k", type=int, default=default_config["topK"])
    sub.choices["score"].add_argument("--min-score", type=float, default=default_config["minScore"])
    sub.choices["sweep"].add_argument("--top-k", default="2,4,8")
    sub.choices["sweep"].add_argument("--min-score", default="0.30,0.45,0.55")
    compare = sub.add_parser("compare")
    compare.add_argument("--dataset", type=Path, default=dataset_default)
    compare.add_argument("--private-identifiers", action="store_true")
    compare.add_argument("--baseline", type=Path, required=True)
    compare.add_argument("--candidate", type=Path, required=True)
    compare.add_argument("--top-k", type=int, default=default_config["topK"])
    compare.add_argument("--min-score", type=float, default=default_config["minScore"])
    args = parser.parse_args(argv)
    try:
        fixture_only = not args.private_identifiers
        cases = validate_dataset(load_jsonl(args.dataset), fixture_only)
        if args.command == "validate":
            print(f"Retrieval dataset validation passed ({len(cases)} cases).")
            return 0
        if args.command == "compare":
            baseline = score(cases, validate_predictions(load_jsonl(args.baseline), fixture_only), args.top_k, args.min_score)
            candidate = score(cases, validate_predictions(load_jsonl(args.candidate), fixture_only), args.top_k, args.min_score)
            report = compare_reports(baseline, candidate)
            print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
            return 1 if report["regressions"] else 0
        predictions = validate_predictions(load_jsonl(args.predictions), fixture_only)
        if args.command == "sweep":
            reports = [score(cases, predictions, top_k, min_score)
                       for top_k in parse_values(args.top_k, int)
                       for min_score in parse_values(args.min_score, float)]
            print(json.dumps(reports, ensure_ascii=False, indent=2, sort_keys=True))
            return 0
        report = score(cases, predictions, args.top_k, args.min_score)
        failures = evaluate_thresholds(report, thresholds)
    except (EvaluationError, OSError, KeyError, json.JSONDecodeError) as exc:
        print(f"Retrieval evaluation failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if failures:
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1
    print("Retrieval evaluation thresholds passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
