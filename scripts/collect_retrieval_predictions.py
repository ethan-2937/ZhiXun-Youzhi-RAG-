from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = ROOT / "harness" / "retrieval-evaluation" / "dataset.sample.jsonl"
OUTPUT_ROOT = ROOT / "harness" / "retrieval-evaluation" / "runs"
DEFAULT_OUTPUT = OUTPUT_ROOT / "predictions.local.jsonl"
MAX_DATASET_BYTES = 1_048_576
MAX_CASES = 500
MAX_RESPONSE_BYTES = 524_288
MAX_CANDIDATES = 50


class CollectionError(ValueError):
    pass


class JsonSession:
    def __init__(self, base_url: str, timeout_seconds: float = 30.0):
        cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
        self.base_url = base_url.rstrip("/") + "/"
        self.timeout_seconds = timeout_seconds

    def request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        csrf_token: str = "",
    ) -> dict[str, Any]:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if csrf_token:
            headers["X-XSRF-TOKEN"] = csrf_token
        url = urllib.parse.urljoin(self.base_url, path.lstrip("/"))
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=self.timeout_seconds) as response:
                payload = response.read(MAX_RESPONSE_BYTES + 1)
        except urllib.error.HTTPError as exc:
            raise CollectionError(f"HTTP_REQUEST_FAILED:{exc.code}") from exc
        except urllib.error.URLError as exc:
            raise CollectionError("HTTP_REQUEST_FAILED") from exc
        if len(payload) > MAX_RESPONSE_BYTES:
            raise CollectionError("HTTP_RESPONSE_TOO_LARGE")
        try:
            value = json.loads(payload.decode("utf-8"))
        except (UnicodeError, json.JSONDecodeError) as exc:
            raise CollectionError("HTTP_RESPONSE_INVALID") from exc
        if not isinstance(value, dict):
            raise CollectionError("HTTP_RESPONSE_INVALID")
        return value


def read_cases(path: Path) -> list[dict[str, Any]]:
    if not path.is_file() or path.stat().st_size > MAX_DATASET_BYTES:
        raise CollectionError("DATASET_SIZE_INVALID")
    cases: list[dict[str, Any]] = []
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        if len(cases) >= MAX_CASES:
            raise CollectionError("DATASET_CASE_LIMIT_EXCEEDED")
        try:
            case = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise CollectionError(f"DATASET_JSON_INVALID:{line_number}") from exc
        validate_case(case, line_number)
        cases.append(case)
    if not cases:
        raise CollectionError("DATASET_EMPTY")
    if len({case["caseId"] for case in cases}) != len(cases):
        raise CollectionError("DATASET_CASE_ID_DUPLICATE")
    return cases


def validate_case(case: Any, line_number: int) -> None:
    if not isinstance(case, dict) or set(case) != {"caseId", "question", "principal", "scope", "expected"}:
        raise CollectionError(f"DATASET_CASE_INVALID:{line_number}")
    try:
        case_id = case["caseId"]
        question = case["question"]
        user_id = case["principal"]["userId"]
        space_id = case["scope"]["spaceId"]
        expected = case["expected"]
        required = expected["requiredDocumentIds"]
        forbidden = expected["forbiddenDocumentIds"]
    except (KeyError, TypeError) as exc:
        raise CollectionError(f"DATASET_CASE_INVALID:{line_number}") from exc
    valid = (
        isinstance(case_id, str) and 3 <= len(case_id) <= 64
        and isinstance(question, str) and 2 <= len(question) <= 1000
        and set(case["principal"]) == {"userId"} and set(case["scope"]) == {"spaceId"}
        and isinstance(user_id, str) and 1 <= len(user_id) <= 128
        and (space_id is None or isinstance(space_id, str) and len(space_id) <= 64)
        and isinstance(expected, dict)
        and set(expected) == {"answerable", "requiredDocumentIds", "forbiddenDocumentIds"}
        and isinstance(expected["answerable"], bool)
        and all(isinstance(items, list) and len(items) <= 20 for items in (required, forbidden))
        and all(isinstance(item, str) and 1 <= len(item) <= 128 for item in required + forbidden)
        and len(set(required)) == len(required) and len(set(forbidden)) == len(forbidden)
        and not set(required) & set(forbidden) and expected["answerable"] == bool(required)
    )
    if not valid:
        raise CollectionError(f"DATASET_CASE_INVALID:{line_number}")


def authenticate_demo(session: JsonSession) -> tuple[str, str]:
    config = session.request("GET", "/api/auth/config")
    if config.get("mode") != "demo" or not isinstance(config.get("csrfToken"), str):
        raise CollectionError("DEMO_AUTH_REQUIRED")
    csrf_token = config["csrfToken"]
    user = session.request("POST", "/api/auth/demo", csrf_token=csrf_token)
    user_id = user.get("userId")
    if not isinstance(user_id, str) or not user_id:
        raise CollectionError("AUTH_RESPONSE_INVALID")
    return user_id, csrf_token


def collect_predictions(
    cases: list[dict[str, Any]],
    session: JsonSession,
    user_id: str,
    csrf_token: str,
    limit: int,
) -> list[dict[str, Any]]:
    predictions: list[dict[str, Any]] = []
    for case in cases:
        if case["principal"]["userId"] != user_id:
            raise CollectionError("DATASET_PRINCIPAL_MISMATCH")
        started = time.perf_counter()
        response = session.request(
            "POST",
            "/api/admin/retrieval-diagnostics",
            {
                "question": case["question"],
                "spaceId": case["scope"]["spaceId"],
                "limit": limit,
            },
            csrf_token,
        )
        latency_ms = min(round((time.perf_counter() - started) * 1000), 300_000)
        predictions.append(to_prediction(case["caseId"], response, latency_ms, limit))
    return predictions


def to_prediction(
    case_id: str,
    response: dict[str, Any],
    latency_ms: int,
    limit: int,
) -> dict[str, Any]:
    candidates = response.get("candidates")
    has_authorized = response.get("hasAuthorizedCandidate")
    if not isinstance(candidates, list) or not isinstance(has_authorized, bool):
        raise CollectionError("DIAGNOSTICS_RESPONSE_INVALID")
    if has_authorized != bool(candidates):
        raise CollectionError("DIAGNOSTICS_RESPONSE_INVALID")
    if len(candidates) > min(limit, MAX_CANDIDATES):
        raise CollectionError("DIAGNOSTICS_CANDIDATE_LIMIT_EXCEEDED")
    expected_keys = {"rank", "documentId", "chunkId", "score"}
    for index, candidate in enumerate(candidates, 1):
        valid = (
            isinstance(candidate, dict) and set(candidate) == expected_keys
            and candidate.get("rank") == index
            and isinstance(candidate.get("documentId"), str)
            and isinstance(candidate.get("chunkId"), str)
            and isinstance(candidate.get("score"), (int, float))
            and not isinstance(candidate.get("score"), bool)
            and -1 <= candidate["score"] <= 1
        )
        if not valid:
            raise CollectionError("DIAGNOSTICS_CANDIDATE_INVALID")
    return {
        "caseId": case_id,
        "latencyMs": latency_ms,
        "hasAuthorizedCandidate": has_authorized,
        "candidates": candidates,
    }


def write_predictions(path: Path, predictions: list[dict[str, Any]]) -> None:
    output = path.resolve()
    try:
        output.relative_to(OUTPUT_ROOT.resolve())
    except ValueError as exc:
        raise CollectionError("OUTPUT_MUST_BE_IN_IGNORED_RUNS_DIRECTORY") from exc
    output.parent.mkdir(parents=True, exist_ok=True)
    lines = "".join(json.dumps(item, ensure_ascii=False, separators=(",", ":")) + "\n" for item in predictions)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=output.parent, delete=False) as handle:
        temporary = Path(handle.name)
        handle.write(lines)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect ACL-filtered retrieval candidates from a local MVP.")
    parser.add_argument("--base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--timeout-seconds", type=float, default=30.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not 1 <= args.limit <= MAX_CANDIDATES or not 0.1 <= args.timeout_seconds <= 120:
        raise CollectionError("ARGUMENT_LIMIT_INVALID")
    cases = read_cases(args.dataset.resolve())
    session = JsonSession(args.base_url, args.timeout_seconds)
    user_id, csrf_token = authenticate_demo(session)
    predictions = collect_predictions(cases, session, user_id, csrf_token, args.limit)
    write_predictions(args.output, predictions)
    print(f"Collected {len(predictions)} retrieval predictions.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except CollectionError as exc:
        print(f"Retrieval collection failed: {exc}", file=sys.stderr)
        raise SystemExit(1) from None
